package br.com.asa.smartpos.data

import br.com.asa.smartpos.domain.Sale
import br.com.asa.smartpos.domain.SalePayloadValidator
import br.com.asa.smartpos.domain.TransactionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** HTTP adapter. Each request runs on IO; TLS is enforced by the configured HTTPS base URL. */
class HttpPaymentApi(
    private val baseUrl: String,
    keyBase64: String
) : PaymentApi {
    private val cipher = PayloadCipher(keyBase64)

    override suspend fun authorize(sale: Sale) = postEncrypted("authorization", sale)

    override suspend fun undo(sale: Sale) = postEncrypted("undo", sale)

    override suspend fun poll(transactionId: String): TransactionStatus? = withContext(Dispatchers.IO) {
        val encodedId = java.net.URLEncoder.encode(transactionId, Charsets.UTF_8.name())
        val response = request("GET", "polling?transactionId=$encodedId")
        parsePollingResponse(response, transactionId)
    }

    private fun parsePollingResponse(response: String, requestedTransactionId: String): TransactionStatus? {
        val json = try {
            JSONObject(response)
        } catch (failure: JSONException) {
            throw PaymentApiResponseException("A API retornou um JSON inválido no polling.", failure)
        }

        val responseTransactionId = json.opt("transactionId") as? String
        if (responseTransactionId.isNullOrBlank()) {
            throw PaymentApiResponseException("A resposta do polling não contém transactionId.")
        }
        if (responseTransactionId != requestedTransactionId) {
            throw PaymentApiResponseException("A resposta do polling pertence a outra transação.")
        }

        val status = json.opt("status") as? String
            ?: throw PaymentApiResponseException("A resposta do polling não contém um status válido.")
        return when (status) {
            "APROVADA" -> TransactionStatus.APPROVED
            "REJEITADA" -> TransactionStatus.REJECTED
            "DESFEITA" -> TransactionStatus.UNDONE
            "PENDENTE", "PROCESSANDO" -> null
            else -> throw PaymentApiResponseException("Status de transação desconhecido: $status")
        }
    }

    private suspend fun postEncrypted(path: String, sale: Sale) {
        SalePayloadValidator.requireValid(sale)
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("paymentType", sale.paymentType.apiValue)
                .put("transactionId", sale.transactionId)
                .put("amount", sale.amount)
                .put("merchantId", sale.merchantId)
                .put("terminalId", sale.terminalId)
            request("POST", path, cipher.encrypt(body.toString()).toString())
        }
    }

    private fun request(method: String, path: String, body: String? = null): String {
        val url = URL(baseUrl.trimEnd('/') + "/" + path)
        require(url.protocol == "https") { "A API deve usar HTTPS." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                throw PaymentApiException(statusCode, readableError(response))
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun readableError(response: String): String {
        val body = response.trim()
        val messageFromJson = runCatching {
            val json = JSONObject(body)
            listOf("message", "error", "detail")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } }
        }.getOrNull()
        return messageFromJson ?: body.take(MAX_ERROR_BODY_LENGTH).ifBlank { "A API não informou o motivo da rejeição." }
    }

    private companion object {
        const val MAX_ERROR_BODY_LENGTH = 300
    }
}
