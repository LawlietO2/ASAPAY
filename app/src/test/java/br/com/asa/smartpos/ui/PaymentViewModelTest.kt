package br.com.asa.smartpos.ui

import br.com.asa.smartpos.data.MockOutcome
import br.com.asa.smartpos.data.MockPaymentApi
import br.com.asa.smartpos.data.PaymentApi
import br.com.asa.smartpos.data.PaymentApiException
import br.com.asa.smartpos.data.PaymentApiResponseException
import br.com.asa.smartpos.data.SimulatedReceiptPrinter
import br.com.asa.smartpos.domain.ReceiptCopy
import br.com.asa.smartpos.domain.PaymentType
import br.com.asa.smartpos.domain.Sale
import br.com.asa.smartpos.domain.TransactionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {
    @Test
    fun mockApprovalFinishesAsApproved() = runTest {
        logSeparator()

        logScenario("[CENARIO] Iniciando venda com resultado simulado: APROVADA")
        val api = mockApi(MockOutcome.APPROVED)
        val viewModel = viewModel(api, this)

        val id = viewModel.sell(1_250L, PaymentType.CREDIT, autoPrint = false)
        logProcessing("[PROCESSANDO] Transação $id enviada; aguardando polling.")
        advanceUntilIdle()

        assertNotNull(id)
        val result = viewModel.transactions.value.single().status
        logResult("[RESULTADO] Status final: $result; solicitações de undo: ${api.undoRequests}.")
        assertEquals(TransactionStatus.APPROVED, result)
        assertEquals(0, api.undoRequests)

        logSeparator()
    }

    @Test
    fun mockRejectionFinishesAsRejectedWithoutUndo() = runTest {
        logSeparator()

        logScenario("[CENARIO] Iniciando venda com resultado simulado: RECUSADA")
        val api = mockApi(MockOutcome.REJECTED)
        val viewModel = viewModel(api, this)

        viewModel.sell(2_500L, PaymentType.DEBIT, autoPrint = false)
        logProcessing("[PROCESSANDO] Transação enviada; aguardando resposta do polling.")
        advanceUntilIdle()

        val result = viewModel.transactions.value.single().status
        logResult("[RESULTADO] Status final: $result; solicitações de undo: ${api.undoRequests}.")
        assertEquals(TransactionStatus.REJECTED, result)
        assertEquals(0, api.undoRequests)

        logSeparator()
    }

    @Test
    fun mockTimeoutRequestsUndoAndFinishesAsUndone() = runTest {

        logSeparator()

        logScenario("[CENARIO] Iniciando venda com resultado simulado: TIMEOUT + UNDO")
        val api = mockApi(MockOutcome.TIMEOUT_UNDO)
        val viewModel = viewModel(api, this)

        viewModel.sell(3_000L, PaymentType.CREDIT, autoPrint = true)
        logProcessing("[PROCESSANDO] Mock permanece pendente; avançando o relógio virtual até o timeout.")
        advanceUntilIdle()

        val transaction = viewModel.transactions.value.single()
        logResult("[RESULTADO] Status final: ${transaction.status}; solicitações de undo: ${api.undoRequests}.")
        assertEquals(TransactionStatus.UNDONE, transaction.status)
        assertEquals("Tempo limite excedido; desfazimento solicitado.", transaction.detail)
        assertEquals(1, api.undoRequests)
        assertEquals(TransactionStatus.UNDONE, api.poll(transaction.sale.transactionId))

        logSeparator()
    }

    @Test
    fun configuredEightSecondResponseDelayKeepsSaleProcessingThenApproves() = runTest {
        logSeparator()
        logScenario("[CENARIO] Resposta do mock com atraso configurado de 8 segundos")
        val api = MockPaymentApi(
            outcome = MockOutcome.APPROVED,
            responseDelayMs = 8_000,
            authorizeDelayMs = 0,
            pollDelayMs = 0,
            undoDelayMs = 0,
            monotonicTimeMs = { testScheduler.currentTime }
        )
        val viewModel = PaymentViewModel(
            api = api,
            operationScope = this,
            timeoutMs = 15_000,
            pollIntervalMs = 500,
            monotonicTimeMs = { testScheduler.currentTime }
        )

        viewModel.sell(8_000L, PaymentType.CREDIT, autoPrint = false)
        runCurrent()
        assertEquals(TransactionStatus.WAITING, viewModel.transactions.value.single().status)

        advanceTimeBy(7_999)
        runCurrent()
        assertEquals(TransactionStatus.WAITING, viewModel.transactions.value.single().status)

        advanceUntilIdle()
        val finalStatus = viewModel.transactions.value.single().status
        logResult("[RESULTADO] Após o atraso simulado, status final: $finalStatus.")
        assertEquals(TransactionStatus.APPROVED, finalStatus)
        assertEquals(0, api.undoRequests)
        logSeparator()
    }

    @Test
    fun simultaneousTransactionsKeepIndependentIdsAndResults() = runTest {
        logSeparator()
        logScenario("[CENARIO] Duas transações processadas simultaneamente")
        val api = ControlledPaymentApi()
        val viewModel = PaymentViewModel(
            api = api,
            operationScope = this,
            timeoutMs = 15_000,
            pollIntervalMs = 500,
            monotonicTimeMs = { testScheduler.currentTime }
        )

        val firstId = viewModel.sell(1_500L, PaymentType.CREDIT, autoPrint = false)!!
        val secondId = viewModel.sell(2_500L, PaymentType.DEBIT, autoPrint = false)!!
        runCurrent()

        assertEquals(2, api.authorizedSales.size)
        assertEquals(2, viewModel.transactions.value.size)
        assertEquals(
            setOf(TransactionStatus.WAITING),
            viewModel.transactions.value.map { it.status }.toSet()
        )
        logProcessing("[PROCESSANDO] Ambas aguardam resposta; IDs independentes: $firstId e $secondId.")

        api.respond(secondId, TransactionStatus.REJECTED)
        runCurrent()
        var byId = viewModel.transactions.value.associateBy { it.sale.transactionId }
        assertEquals(TransactionStatus.REJECTED, byId.getValue(secondId).status)
        assertEquals(TransactionStatus.WAITING, byId.getValue(firstId).status)
        logWarning("[RESULTADO PARCIAL] $secondId recusada; $firstId continua aguardando.")

        api.respond(firstId, TransactionStatus.APPROVED)
        runCurrent()
        byId = viewModel.transactions.value.associateBy { it.sale.transactionId }
        assertEquals(TransactionStatus.APPROVED, byId.getValue(firstId).status)
        assertEquals(TransactionStatus.REJECTED, byId.getValue(secondId).status)
        assertEquals(1_500L, byId.getValue(firstId).sale.amountInCents)
        assertEquals(2_500L, byId.getValue(secondId).sale.amountInCents)
        assertEquals(0, api.undoRequests)
        logResult("[RESULTADO FINAL] Uma aprovada e outra recusada; os registros permaneceram associados aos IDs corretos.")
        logSeparator()
    }

    @Test
    fun clientPayloadErrorIsShownWithoutRequestingUndo() = runTest {
        logSeparator()
        logScenario("[CENARIO] API rejeita payload incompleto com HTTP 400")
        val api = ClientErrorPaymentApi("O campo merchantId é obrigatório.")
        val viewModel = PaymentViewModel(
            api = api,
            operationScope = this,
            timeoutMs = 50,
            pollIntervalMs = 10,
            monotonicTimeMs = { testScheduler.currentTime }
        )

        val id = viewModel.sell(1_500L, PaymentType.CREDIT, autoPrint = false)!!
        runCurrent()

        val transaction = viewModel.transactions.value.single()
        logResult("[RESULTADO] Status: ${transaction.status}; detalhe: ${transaction.detail}; undo: ${api.undoRequests}.")
        assertEquals(TransactionStatus.ERROR, transaction.status)
        assertEquals(
            "A API recusou a solicitação (HTTP 400): O campo merchantId é obrigatório.",
            transaction.detail
        )
        assertEquals(id, transaction.sale.transactionId)
        assertEquals(0, api.undoRequests)
        logSeparator()
    }

    @Test
    fun requestTimeoutAndRateLimitEachRequestOneUndoWithoutRetryingAuthorization() = runTest {
        logSeparator()
        logScenario("[CENARIO] HTTP 408 e 429 não provocam retentativa financeira")

        listOf(408, 429).forEach { statusCode ->
            val api = ClientErrorPaymentApi("Resposta HTTP $statusCode", statusCode)
            val viewModel = PaymentViewModel(api = api, operationScope = this)

            val id = viewModel.sell(1_750L, PaymentType.CREDIT, autoPrint = false)!!
            runCurrent()

            val transaction = viewModel.transactions.value.single { it.sale.transactionId == id }
            assertEquals("HTTP $statusCode solicita desfazimento", TransactionStatus.UNDONE, transaction.status)
            assertEquals("HTTP $statusCode solicita exatamente um undo", 1, api.undoRequests)
            assertEquals("HTTP $statusCode autoriza uma única vez", 1, api.authorizationRequests)
        }

        logResult("[RESULTADO] Para 408 e 429, cada venda teve uma autorização e uma solicitação de undo.")
        logSeparator()
    }

    @Test
    fun invalidPollingResponseRequestsUndoInsteadOfRemainingPending() = runTest {
        logSeparator()
        logScenario("[CENARIO] Resposta HTTP válida mas incompatível com o contrato de polling")
        val api = InvalidPollingResponseApi()
        val viewModel = PaymentViewModel(api = api, operationScope = this)

        val id = viewModel.sell(2_000L, PaymentType.DEBIT, autoPrint = false)!!
        runCurrent()

        val transaction = viewModel.transactions.value.single()
        assertEquals(id, transaction.sale.transactionId)
        assertEquals(TransactionStatus.UNDONE, transaction.status)
        assertEquals(1, api.undoRequests)
        logResult("[RESULTADO] Resposta inválida não ficou como pendente; um undo foi solicitado.")
        logSeparator()
    }

    @Test
    fun communicationFailureDuringAuthorizationRequestsUndo() = runTest {
        logSeparator()
        logScenario("[CENARIO] Falha de comunicação durante a autorização")
        val api = CommunicationFailureApi(authorizationFailure = IOException("Conexão encerrada"))
        val viewModel = PaymentViewModel(api = api, operationScope = this)

        val id = viewModel.sell(1_900L, PaymentType.CREDIT, autoPrint = false)!!
        runCurrent()

        val transaction = viewModel.transactions.value.single()
        logProcessing("[PROCESSANDO] Autorização falhou; desfazimento solicitado para $id.")
        logResult("[RESULTADO] Status: ${transaction.status}; undo: ${api.undoRequests}.")
        assertEquals(TransactionStatus.UNDONE, transaction.status)
        assertEquals("Falha de comunicação; desfazimento solicitado.", transaction.detail)
        assertEquals(1, api.undoRequests)
        logSeparator()
    }

    @Test
    fun communicationFailureDuringPollingRequestsUndo() = runTest {
        logSeparator()
        logScenario("[CENARIO] Falha de comunicação durante o polling")
        val api = CommunicationFailureApi(pollFailure = IOException("Servidor indisponível"))
        val viewModel = PaymentViewModel(api = api, operationScope = this)

        viewModel.sell(2_100L, PaymentType.DEBIT, autoPrint = false)
        runCurrent()

        val transaction = viewModel.transactions.value.single()
        logProcessing("[PROCESSANDO] Polling falhou; solicitando desfazimento da venda.")
        logResult("[RESULTADO] Status: ${transaction.status}; detalhe: ${transaction.detail}")
        assertEquals(TransactionStatus.UNDONE, transaction.status)
        assertEquals(1, api.undoRequests)
        logSeparator()
    }

    @Test
    fun failedUndoAfterCommunicationFailureFinishesAsError() = runTest {
        logSeparator()
        logScenario("[CENARIO] Falha de comunicação seguida de falha no desfazimento")
        val api = CommunicationFailureApi(
            authorizationFailure = IOException("Conexão encerrada"),
            undoFailure = IOException("Servidor indisponível")
        )
        val viewModel = PaymentViewModel(api = api, operationScope = this)

        viewModel.sell(3_200L, PaymentType.CREDIT, autoPrint = false)
        runCurrent()

        val transaction = viewModel.transactions.value.single()
        logProcessing("[PROCESSANDO] A autorização e a solicitação de desfazimento falharam.")
        logResult("[RESULTADO] Status: ${transaction.status}; detalhe: ${transaction.detail}")
        assertEquals(TransactionStatus.ERROR, transaction.status)
        assertEquals("Falha de comunicação e falha ao solicitar desfazimento.", transaction.detail)
        assertEquals(1, api.undoRequests)
        logSeparator()
    }

    @Test
    fun pollingResultAtDeadlineIsIgnoredAndUndoIsRequested() = runTest {
        logSeparator()
        logScenario("[CENARIO] Resultado do polling chega exatamente no limite de 15 segundos")
        val api = LateResponsePaymentApi(responseDelayMs = 15_000L, result = TransactionStatus.APPROVED)
        val viewModel = PaymentViewModel(
            api = api,
            operationScope = this,
            timeoutMs = 15_000L,
            pollIntervalMs = 500L,
            monotonicTimeMs = { testScheduler.currentTime }
        )

        viewModel.sell(4_400L, PaymentType.DEBIT, autoPrint = false)
        advanceUntilIdle()

        val transaction = viewModel.transactions.value.single()
        logProcessing("[PROCESSANDO] A resposta chegou no prazo limite; o polling não pode aprová-la após o timeout.")
        logResult("[RESULTADO] Status: ${transaction.status}; undo: ${api.undoRequests}.")
        assertEquals(TransactionStatus.UNDONE, transaction.status)
        assertEquals(1, api.undoRequests)
        logSeparator()
    }

    @Test
    fun totalTimeoutIncludesAuthorizationAndPolling() = runTest {
        logSeparator()
        logScenario("[CENARIO] Autorização e polling ultrapassam juntos o prazo total de 15 segundos")
        val api = DelayedAuthorizationPaymentApi(
            authorizationDelayMs = 12_000,
            pollingDelayMs = 4_000,
            result = TransactionStatus.APPROVED
        )
        val viewModel = PaymentViewModel(
            api = api,
            operationScope = this,
            timeoutMs = 15_000,
            pollIntervalMs = 500,
            monotonicTimeMs = { testScheduler.currentTime }
        )

        val id = viewModel.sell(4_500L, PaymentType.CREDIT, autoPrint = false)!!
        advanceUntilIdle()

        val transaction = viewModel.transactions.value.single()
        assertEquals(TransactionStatus.UNDONE, transaction.status)
        assertEquals(1, api.undoSales.size)
        assertEquals(id, api.undoSales.single().transactionId)
        logResult("[RESULTADO] O tempo da autorização entrou no prazo total; status=${transaction.status}, undo=${api.undoSales.size}.")
        logSeparator()
    }

    @Test
    fun timeoutRequestsUndoWhileAuthorizationIsStillUnresponsive() = runTest {
        logSeparator()
        logScenario("[CENARIO] A autorização não responde nem à solicitação de cancelamento")
        val api = UnresponsiveAuthorizationPaymentApi()
        val viewModel = PaymentViewModel(
            api = api,
            operationScope = this,
            timeoutMs = 15_000,
            pollIntervalMs = 500,
            monotonicTimeMs = { testScheduler.currentTime }
        )

        val id = viewModel.sell(5_100L, PaymentType.DEBIT, autoPrint = false)!!
        runCurrent()
        assertEquals(true, api.authorizationStarted.isCompleted)

        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(1, api.undoRequests)
        assertEquals(id, api.undoSales.single().transactionId)
        assertEquals(TransactionStatus.UNDONE, viewModel.transactions.value.single().status)
        assertEquals(false, api.releaseAuthorization.isCompleted)
        api.releaseAuthorization.complete(Unit)
        runCurrent()
        assertEquals(TransactionStatus.UNDONE, viewModel.transactions.value.single().status)
        logResult("[RESULTADO] O timeout iniciou /undo no prazo e a autorização pendente não alterou o resultado.")
        logSeparator()
    }

    @Test
    fun timeoutRequestsUndoWhilePollingIsStillUnresponsive() = runTest {
        logSeparator()
        logScenario("[CENARIO] Uma chamada de polling permanece sem resposta após o prazo total")
        val api = UnresponsivePollingPaymentApi()
        val viewModel = PaymentViewModel(
            api = api,
            operationScope = this,
            timeoutMs = 15_000,
            pollIntervalMs = 500,
            monotonicTimeMs = { testScheduler.currentTime }
        )

        val id = viewModel.sell(5_200L, PaymentType.CREDIT, autoPrint = false)!!
        runCurrent()
        assertEquals(true, api.pollStarted.isCompleted)

        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(1, api.undoRequests)
        assertEquals(id, api.undoSales.single().transactionId)
        assertEquals(TransactionStatus.UNDONE, viewModel.transactions.value.single().status)
        assertEquals(false, api.releasePoll.isCompleted)
        api.releasePoll.complete(Unit)
        runCurrent()
        assertEquals(TransactionStatus.UNDONE, viewModel.transactions.value.single().status)
        logResult("[RESULTADO] O timeout solicitou /undo sem aguardar a chamada de polling pendente.")
        logSeparator()
    }

    @Test
    fun receiptPrinterReceivesTransactionAmountStatusAndAllowsReprints() = runTest {
        logSeparator()
        logScenario("[CENARIO] Imprimir e reimprimir o comprovante de uma transação aprovada")
        val printer = SimulatedReceiptPrinter()
        val viewModel = PaymentViewModel(
            api = mockApi(MockOutcome.APPROVED),
            operationScope = this,
            timeoutMs = 50,
            pollIntervalMs = 10,
            monotonicTimeMs = { testScheduler.currentTime },
            receiptPrinter = printer
        )
        val id = viewModel.sell(1_234L, PaymentType.CREDIT, autoPrint = false)!!
        advanceUntilIdle()

        val copies = setOf(ReceiptCopy.ESTABLISHMENT, ReceiptCopy.CUSTOMER)
        viewModel.printReceipt(id, copies)
        runCurrent()
        val firstJob = printer.printJobs.single()
        assertEquals(id, firstJob.transactionId)
        assertEquals(1_234L, firstJob.amountInCents)
        assertEquals(TransactionStatus.APPROVED, firstJob.status)
        assertEquals(copies, firstJob.copies)

        // Reprinting intentionally records another print job for the same payment ID.
        viewModel.printReceipt(id, copies)
        runCurrent()
        assertEquals(2, printer.printJobs.size)
        assertEquals(id, printer.printJobs.last().transactionId)
        logResult("[RESULTADO] O job contém transactionId, valor, status e vias; a reimpressão é aceita.")
        logSeparator()
    }

    private fun mockApi(outcome: MockOutcome) = MockPaymentApi(
        outcome = outcome,
        authorizeDelayMs = 0,
        pollDelayMs = 0,
        undoDelayMs = 0
    )

    private fun viewModel(api: MockPaymentApi, testScope: TestScope) = PaymentViewModel(
        api = api,
        operationScope = testScope,
        timeoutMs = 50,
        pollIntervalMs = 10,
        monotonicTimeMs = { testScope.testScheduler.currentTime }
    )
}

private const val ANSI_RESET = "\u001B[0m"
private const val ANSI_CYAN = "\u001B[36m"
private const val ANSI_YELLOW = "\u001B[33m"
private const val ANSI_GREEN = "\u001B[32m"
private const val ANSI_GRAY = "\u001B[90m"

private fun logScenario(message: String) = println("$ANSI_CYAN$message$ANSI_RESET")
private fun logProcessing(message: String) = println("$ANSI_YELLOW$message$ANSI_RESET")
private fun logResult(message: String) = println("$ANSI_GREEN$message$ANSI_RESET")
private fun logWarning(message: String) = println("$ANSI_YELLOW$message$ANSI_RESET")
private fun logSeparator() = println("$ANSI_GRAY===================================================================$ANSI_RESET")

private class ControlledPaymentApi : PaymentApi {
    val authorizedSales = mutableListOf<Sale>()
    private val pendingResults = mutableMapOf<String, CompletableDeferred<TransactionStatus?>>()
    var undoRequests = 0
        private set

    override suspend fun authorize(sale: Sale) {
        authorizedSales += sale
        pendingResults[sale.transactionId] = CompletableDeferred()
    }

    override suspend fun poll(transactionId: String): TransactionStatus? =
        pendingResults.getValue(transactionId).await()

    override suspend fun undo(sale: Sale) {
        undoRequests += 1
    }

    fun respond(transactionId: String, status: TransactionStatus) {
        pendingResults.getValue(transactionId).complete(status)
    }
}

private class ClientErrorPaymentApi(
    private val serverMessage: String,
    private val statusCode: Int = 400
) : PaymentApi {
    var undoRequests = 0
        private set
    var authorizationRequests = 0
        private set

    override suspend fun authorize(sale: Sale) {
        authorizationRequests += 1
        throw PaymentApiException(statusCode, serverMessage)
    }

    override suspend fun poll(transactionId: String): TransactionStatus? = null

    override suspend fun undo(sale: Sale) {
        undoRequests += 1
    }
}

private class InvalidPollingResponseApi : PaymentApi {
    var undoRequests = 0
        private set

    override suspend fun authorize(sale: Sale) = Unit

    override suspend fun poll(transactionId: String): TransactionStatus? {
        throw PaymentApiResponseException("A resposta não contém status.")
    }

    override suspend fun undo(sale: Sale) {
        undoRequests += 1
    }
}

private class CommunicationFailureApi(
    private val authorizationFailure: IOException? = null,
    private val pollFailure: IOException? = null,
    private val undoFailure: IOException? = null
) : PaymentApi {
    var undoRequests = 0
        private set

    override suspend fun authorize(sale: Sale) {
        authorizationFailure?.let { throw it }
    }

    override suspend fun poll(transactionId: String): TransactionStatus? {
        pollFailure?.let { throw it }
        return null
    }

    override suspend fun undo(sale: Sale) {
        undoRequests += 1
        undoFailure?.let { throw it }
    }
}

private class LateResponsePaymentApi(
    private val responseDelayMs: Long,
    private val result: TransactionStatus
) : PaymentApi {
    var undoRequests = 0
        private set

    override suspend fun authorize(sale: Sale) = Unit

    override suspend fun poll(transactionId: String): TransactionStatus? {
        kotlinx.coroutines.delay(responseDelayMs)
        return result
    }

    override suspend fun undo(sale: Sale) {
        undoRequests += 1
    }
}

private class DelayedAuthorizationPaymentApi(
    private val authorizationDelayMs: Long,
    private val pollingDelayMs: Long,
    private val result: TransactionStatus
) : PaymentApi {
    val undoSales = mutableListOf<Sale>()

    override suspend fun authorize(sale: Sale) {
        delay(authorizationDelayMs)
    }

    override suspend fun poll(transactionId: String): TransactionStatus? {
        delay(pollingDelayMs)
        return result
    }

    override suspend fun undo(sale: Sale) {
        undoSales += sale
    }
}

private class UnresponsiveAuthorizationPaymentApi : PaymentApi {
    val authorizationStarted = CompletableDeferred<Unit>()
    val releaseAuthorization = CompletableDeferred<Unit>()
    val undoSales = mutableListOf<Sale>()
    val undoRequests: Int get() = undoSales.size

    override suspend fun authorize(sale: Sale) {
        authorizationStarted.complete(Unit)
        withContext(NonCancellable) { releaseAuthorization.await() }
    }

    override suspend fun poll(transactionId: String): TransactionStatus? = TransactionStatus.APPROVED

    override suspend fun undo(sale: Sale) {
        undoSales += sale
    }
}

private class UnresponsivePollingPaymentApi : PaymentApi {
    val pollStarted = CompletableDeferred<Unit>()
    val releasePoll = CompletableDeferred<Unit>()
    val undoSales = mutableListOf<Sale>()
    val undoRequests: Int get() = undoSales.size

    override suspend fun authorize(sale: Sale) = Unit

    override suspend fun poll(transactionId: String): TransactionStatus? {
        pollStarted.complete(Unit)
        withContext(NonCancellable) { releasePoll.await() }
        return TransactionStatus.APPROVED
    }

    override suspend fun undo(sale: Sale) {
        undoSales += sale
    }
}
