package br.com.asa.smartpos.data

import java.io.Closeable
import java.io.DataInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.InetAddress
import java.net.SocketException
import java.net.URI
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import br.com.asa.smartpos.domain.PaymentType
import br.com.asa.smartpos.domain.Sale
import br.com.asa.smartpos.domain.TransactionStatus

class HttpPaymentApiTest {
    @Test
    fun authorizationAndUndoSendEncryptedBodiesToHttpsServer() = runBlocking {
        logSeparator()
        logScenario("[CENARIO] Envio de autorização e desfazimento a um servidor HTTPS falso")
        withTrustedFakeServer { server ->
            server.enqueue("/authorization", 200, "{\"received\":true}")
            server.enqueue("/undo", 200, "{\"received\":true}")
            val api = HttpPaymentApi(server.baseUrl, TEST_KEY_BASE64)
            val sale = Sale(
                transactionId = "asa-test-authorization-001",
                amountInCents = 1_234L,
                paymentType = PaymentType.DEBIT
            )

            api.authorize(sale)
            api.undo(sale)

            val requests = server.requests
            assertEquals(2, requests.size)
            assertEquals("POST", requests[0].method)
            assertEquals("/authorization", requests[0].path)
            assertEquals("POST", requests[1].method)
            assertEquals("/undo", requests[1].path)
            requests.forEach { request ->
                assertEquals("application/json; charset=utf-8", request.contentType)
                assertEncryptedSale(request.body, sale)
            }
            val authorizationIv = JSONObject(requests[0].body).getString("iv")
            val undoIv = JSONObject(requests[1].body).getString("iv")
            assertFalse("Cada requisição deve usar um IV novo.", authorizationIv == undoIv)
            logProcessing("[VALIDANDO] Corpo HTTPS cifrado, campos da venda e IVs diferentes em cada operação.")
            logResult("[RESULTADO] Autorização e undo chegaram cifrados ao servidor falso.")
        }
        logSeparator()
    }

    @Test
    fun pollingMapsFinalAndPendingStatusesAndEncodesTransactionId() = runBlocking {
        logSeparator()
        logScenario("[CENARIO] Polling HTTPS converte status e codifica o transactionId")
        withTrustedFakeServer { server ->
            server.enqueue("/polling", 200, "{\"status\":\"APROVADA\"}")
            server.enqueue("/polling", 200, "{\"status\":\"PROCESSANDO\"}")
            server.enqueue("/polling", 200, "{\"status\":\"REJEITADA\"}")
            val api = HttpPaymentApi(server.baseUrl, TEST_KEY_BASE64)
            val transactionId = "id com/espaco"

            val approved = api.poll(transactionId)
            val pending = api.poll(transactionId)
            val rejected = api.poll(transactionId)

            assertEquals(TransactionStatus.APPROVED, approved)
            assertNull(pending)
            assertEquals(TransactionStatus.REJECTED, rejected)
            assertEquals("transactionId=id+com%2Fespaco", server.requests.first().rawQuery)
            logProcessing("[VALIDANDO] APROVADA, PROCESSANDO e REJEITADA; query URL-encoded.")
            logResult("[RESULTADO] Status finais mapeados e status pendente representado por null.")
        }
        logSeparator()
    }

    @Test
    fun httpClientErrorIncludesReadableServerMessage() = runBlocking {
        logSeparator()
        logScenario("[CENARIO] Servidor HTTPS falso responde HTTP 400")
        withTrustedFakeServer { server ->
            server.enqueue("/authorization", 400, "{\"message\":\"O campo amount é obrigatório.\"}")
            val api = HttpPaymentApi(server.baseUrl, TEST_KEY_BASE64)
            val sale = Sale(amountInCents = 1_000L, paymentType = PaymentType.CREDIT)
            var capturedException: PaymentApiException? = null

            try {
                api.authorize(sale)
            } catch (exception: PaymentApiException) {
                capturedException = exception
            }

            assertNotNull(capturedException)
            assertEquals(400, capturedException!!.statusCode)
            assertEquals("O campo amount é obrigatório.", capturedException!!.userMessage)
            logProcessing("[VALIDANDO] Código HTTP e mensagem JSON retornada pelo servidor.")
            logResult("[RESULTADO] HTTP ${capturedException!!.statusCode}: ${capturedException!!.userMessage}")
        }
        logSeparator()
    }

    @Test
    fun cleartextHttpUrlIsRejected() = runBlocking {
        logSeparator()
        logScenario("[CENARIO] Rejeitar endpoint sem HTTPS")
        val api = HttpPaymentApi("http://localhost:8080", TEST_KEY_BASE64)
        var capturedException: IllegalArgumentException? = null

        try {
            api.authorize(Sale(amountInCents = 1_000L, paymentType = PaymentType.CREDIT))
        } catch (exception: IllegalArgumentException) {
            capturedException = exception
        }

        assertNotNull(capturedException)
        assertEquals("A API deve usar HTTPS.", capturedException!!.message)
        logProcessing("[VALIDANDO] A chamada foi interrompida antes de transmitir os dados.")
        logResult("[RESULTADO] A URL HTTP foi rejeitada; HTTPS é obrigatório.")
        logSeparator()
    }

    private fun assertEncryptedSale(envelopeText: String, expectedSale: Sale) {
        val envelope = JSONObject(envelopeText)
        assertEquals("AES-256-GCM", envelope.getString("algorithm"))
        val iv = Base64.getDecoder().decode(envelope.getString("iv"))
        val ciphertext = Base64.getDecoder().decode(envelope.getString("ciphertext"))
        val decryptor = Cipher.getInstance("AES/GCM/NoPadding")
        decryptor.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(TEST_KEY_BYTES, "AES"),
            GCMParameterSpec(128, iv)
        )
        val clearJson = String(decryptor.doFinal(ciphertext), Charsets.UTF_8)
        val payload = JSONObject(clearJson)

        assertEquals(expectedSale.transactionId, payload.getString("transactionId"))
        assertEquals(expectedSale.paymentType.apiValue, payload.getString("paymentType"))
        assertEquals(expectedSale.amount, payload.getDouble("amount"), 0.000001)
        assertEquals(expectedSale.merchantId, payload.getString("merchantId"))
        assertEquals(expectedSale.terminalId, payload.getString("terminalId"))
    }

    private companion object {
        val TEST_KEY_BYTES = ByteArray(32) { (it + 1).toByte() }
        val TEST_KEY_BASE64: String = Base64.getEncoder().encodeToString(TEST_KEY_BYTES)
        const val KEYSTORE_PASSWORD = "testpass"
    }
}

private suspend fun withTrustedFakeServer(block: suspend (FakeHttpsPaymentServer) -> Unit) {
    val previousSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
    FakeHttpsPaymentServer().use { server ->
        HttpsURLConnection.setDefaultSSLSocketFactory(server.clientSslContext().socketFactory)
        try {
            block(server)
        } finally {
            HttpsURLConnection.setDefaultSSLSocketFactory(previousSocketFactory)
        }
    }
}

private data class CapturedHttpRequest(
    val method: String,
    val path: String,
    val rawQuery: String?,
    val contentType: String?,
    val body: String
)

private data class FakeHttpResponse(val statusCode: Int, val body: String)

private class FakeHttpsPaymentServer : Closeable {
    private val requestsInternal = CopyOnWriteArrayList<CapturedHttpRequest>()
    private val responsesByPath = ConcurrentHashMap<String, ConcurrentLinkedQueue<FakeHttpResponse>>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "asa-fake-https-server").apply { isDaemon = true }
    }
    private val serverSocket: SSLServerSocket

    val baseUrl: String
        get() = "https://localhost:${serverSocket.localPort}"

    val requests: List<CapturedHttpRequest>
        get() = requestsInternal.toList()

    init {
        val keyStore = loadKeyStore()
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, KEYSTORE_PASSWORD.toCharArray())
        }
        val serverSslContext = SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, null, SecureRandom())
        }
        serverSocket = serverSslContext.serverSocketFactory.createServerSocket(
            0,
            0,
            InetAddress.getByName("127.0.0.1")
        ) as SSLServerSocket
        executor.submit {
            while (!serverSocket.isClosed) {
                try {
                    handleConnection(serverSocket.accept() as SSLSocket)
                } catch (closed: SocketException) {
                    if (!serverSocket.isClosed) throw closed
                }
            }
        }
    }

    fun enqueue(path: String, statusCode: Int, body: String) {
        responsesByPath.computeIfAbsent(path) { ConcurrentLinkedQueue() }
            .add(FakeHttpResponse(statusCode, body))
    }

    fun clientSslContext(): SSLContext {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(loadKeyStore())
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagerFactory.trustManagers, SecureRandom())
        }
    }

    override fun close() {
        serverSocket.close()
        executor.shutdownNow()
    }

    private fun handleConnection(socket: SSLSocket) {
        socket.use { secureSocket ->
            secureSocket.startHandshake()
            val input = DataInputStream(secureSocket.inputStream)
            val requestLine = readHttpLine(input) ?: return
            val requestParts = requestLine.split(' ', limit = 3)
            require(requestParts.size == 3) { "Linha de requisição HTTP inválida: $requestLine" }
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readHttpLine(input).orEmpty()
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
                }
            }
            val bodyBytes = ByteArray(headers["content-length"]?.toIntOrNull() ?: 0)
            input.readFully(bodyBytes)
            val requestUri = URI.create(requestParts[1])
            requestsInternal += CapturedHttpRequest(
                method = requestParts[0],
                path = requestUri.path,
                rawQuery = requestUri.rawQuery,
                contentType = headers["content-type"],
                body = String(bodyBytes, Charsets.UTF_8)
            )
            val response = responsesByPath[requestUri.path]?.poll() ?: FakeHttpResponse(200, "{}")
            val responseBytes = response.body.toByteArray(Charsets.UTF_8)
            val statusText = if (response.statusCode in 200..299) "OK" else "Bad Request"
            val responseHeaders = buildString {
                append("HTTP/1.1 ${response.statusCode} $statusText\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${responseBytes.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            secureSocket.outputStream.apply {
                write(responseHeaders.toByteArray(Charsets.US_ASCII))
                write(responseBytes)
                flush()
            }
        }
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance("PKCS12").apply {
        val stream = FakeHttpsPaymentServer::class.java.getResourceAsStream("/asa-test-server.p12")
            ?: error("Keystore de teste não encontrado.")
        stream.use { load(it, KEYSTORE_PASSWORD.toCharArray()) }
    }

    private companion object {
        const val KEYSTORE_PASSWORD = "testpass"
    }
}

private fun readHttpLine(input: InputStream): String? {
    val bytes = ArrayList<Byte>()
    while (true) {
        val next = input.read()
        if (next < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(Charsets.US_ASCII)
        if (next == '\n'.code) break
        if (next != '\r'.code) bytes += next.toByte()
    }
    return bytes.toByteArray().toString(Charsets.US_ASCII)
}

private const val HTTP_TEST_LOG_RESET = "\u001B[0m"
private const val HTTP_TEST_LOG_CYAN = "\u001B[36m"
private const val HTTP_TEST_LOG_YELLOW = "\u001B[33m"
private const val HTTP_TEST_LOG_GREEN = "\u001B[32m"
private const val HTTP_TEST_LOG_GRAY = "\u001B[90m"

private fun logScenario(message: String) = println("$HTTP_TEST_LOG_CYAN$message$HTTP_TEST_LOG_RESET")
private fun logProcessing(message: String) = println("$HTTP_TEST_LOG_YELLOW$message$HTTP_TEST_LOG_RESET")
private fun logResult(message: String) = println("$HTTP_TEST_LOG_GREEN$message$HTTP_TEST_LOG_RESET")
private fun logSeparator() = println("$HTTP_TEST_LOG_GRAY===================================================================$HTTP_TEST_LOG_RESET")
