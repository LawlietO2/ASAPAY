package br.com.asa.smartpos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.asa.smartpos.BuildConfig
import br.com.asa.smartpos.data.HttpPaymentApi
import br.com.asa.smartpos.data.PaymentApi
import br.com.asa.smartpos.data.ReceiptPrinter
import br.com.asa.smartpos.data.ReceiptPrintJob
import br.com.asa.smartpos.data.SimulatedReceiptPrinter
import br.com.asa.smartpos.data.MockPaymentApi
import br.com.asa.smartpos.data.MockOutcome
import br.com.asa.smartpos.data.PaymentApiException
import br.com.asa.smartpos.data.PaymentApiResponseException
import br.com.asa.smartpos.domain.ReceiptCopy
import br.com.asa.smartpos.domain.PaymentType
import br.com.asa.smartpos.domain.Sale
import br.com.asa.smartpos.domain.SalePayloadValidationException
import br.com.asa.smartpos.domain.TransactionRecord
import br.com.asa.smartpos.domain.TransactionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PaymentViewModel(
    private val api: PaymentApi = if (BuildConfig.USE_MOCK_API) {
        MockPaymentApi()
    } else {
        HttpPaymentApi(BuildConfig.API_BASE_URL, BuildConfig.PAYLOAD_KEY_BASE64)
    },
    private val operationScope: CoroutineScope? = null,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val monotonicTimeMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val receiptPrinter: ReceiptPrinter = SimulatedReceiptPrinter()
) : ViewModel() {
    private val mockApi = api as? MockPaymentApi
    private val processingScope: CoroutineScope get() = operationScope ?: viewModelScope
    private val _transactions = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val transactions: StateFlow<List<TransactionRecord>> = _transactions.asStateFlow()

    fun setMockOutcome(outcome: MockOutcome) {
        mockApi?.outcome = outcome
    }

    fun setMockResponseDelaySeconds(seconds: Int) {
        mockApi?.responseDelayMs = seconds.coerceAtLeast(0) * 1_000L
    }

    fun sell(amountInCents: Long, type: PaymentType, autoPrint: Boolean): String? {
        if (amountInCents <= 0L) return null
        val sale = Sale(amountInCents = amountInCents, paymentType = type)
        append(TransactionRecord(sale, TransactionStatus.SENDING, autoPrint = autoPrint))

        processingScope.launch {
            try {
                processSaleUntilDeadline(sale)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalidPayload: SalePayloadValidationException) {
                update(sale.transactionId, TransactionStatus.ERROR, invalidPayload.message)
            } catch (invalidResponse: PaymentApiResponseException) {
                requestUndoAfterFailure(
                    sale,
                    "Resposta inválida da API; desfazimento solicitado.",
                    "Resposta inválida da API e falha ao solicitar desfazimento."
                )
            } catch (apiFailure: PaymentApiException) {
                if (apiFailure.isClientError && !apiFailure.requiresUndo) {
                    update(
                        sale.transactionId,
                        TransactionStatus.ERROR,
                        "A API recusou a solicitação (HTTP ${apiFailure.statusCode}): ${apiFailure.userMessage}"
                    )
                } else {
                    handleCommunicationFailure(sale)
                }
            } catch (failure: Exception) {
                handleCommunicationFailure(sale)
            }
        }
        return sale.transactionId
    }

    /** Requests a simulated/device print for the selected copies of a completed transaction. */
    fun printReceipt(transactionId: String, copies: Set<ReceiptCopy>) {
        if (copies.isEmpty()) return
        val record = _transactions.value.firstOrNull { it.sale.transactionId == transactionId } ?: return
        if (record.status == TransactionStatus.SENDING || record.status == TransactionStatus.WAITING) return

        val job = ReceiptPrintJob(
            transactionId = record.sale.transactionId,
            amountInCents = record.sale.amountInCents,
            status = record.status,
            copies = copies.toSet()
        )
        processingScope.launch {
            try {
                receiptPrinter.print(job)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // A printer failure must not change the already completed payment result.
                System.err.println("Impressão simulada falhou para ${job.transactionId}: ${failure.message}")
            }
        }
    }

    private suspend fun processSaleUntilDeadline(sale: Sale) = supervisorScope {
        val deadline = monotonicTimeMs() + timeoutMs
        val stateLock = Mutex()
        var resultResolved = false

        // Isolate the payment calls so the deadline coordinator can run while a blocking
        // HttpURLConnection call is still unwinding on Dispatchers.IO.
        val paymentWork = async<TransactionStatus>(start = CoroutineStart.LAZY) {
            api.authorize(sale)
            stateLock.withLock {
                if (!resultResolved) update(sale.transactionId, TransactionStatus.WAITING)
            }
            while (true) {
                val result = api.poll(sale.transactionId)
                if (result != null) return@async result
                delay(pollIntervalMs)
            }
            @Suppress("UNREACHABLE_CODE")
            error("Polling encerrado sem status final.")
        }

        val remainingMs = (deadline - monotonicTimeMs()).coerceAtLeast(0L)
        val result = withTimeoutOrNull(remainingMs) { paymentWork.await() }
        val timedOut = result == null || monotonicTimeMs() >= deadline

        stateLock.withLock { resultResolved = true }
        if (timedOut) {
            // Do not join here: cancellation of a blocking HTTP call is cooperative and the
            // socket may take until its configured connect/read timeout to unwind.
            paymentWork.cancel()
            requestTimeoutUndo(sale)
        } else {
            update(sale.transactionId, result!!)
        }
    }

    private suspend fun requestTimeoutUndo(sale: Sale) {
        try {
            api.undo(sale)
            update(sale.transactionId, TransactionStatus.UNDONE, "Tempo limite excedido; desfazimento solicitado.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            update(
                sale.transactionId,
                TransactionStatus.ERROR,
                "Tempo limite excedido; falha ao solicitar desfazimento."
            )
        }
    }

    private fun append(record: TransactionRecord) {
        _transactions.value = listOf(record) + _transactions.value
    }

    private fun update(id: String, status: TransactionStatus, detail: String? = null) {
        _transactions.value = _transactions.value.map {
            if (it.sale.transactionId == id) it.copy(status = status, detail = detail) else it
        }
    }

    private suspend fun handleCommunicationFailure(sale: Sale) {
        requestUndoAfterFailure(
            sale,
            "Falha de comunicação; desfazimento solicitado.",
            "Falha de comunicação e falha ao solicitar desfazimento."
        )
    }

    private suspend fun requestUndoAfterFailure(sale: Sale, successDetail: String, failureDetail: String) {
        val undoSucceeded = try {
            api.undo(sale)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            false
        }
        update(
            sale.transactionId,
            if (undoSucceeded) TransactionStatus.UNDONE else TransactionStatus.ERROR,
            if (undoSucceeded) successDetail else failureDetail
        )
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 500L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
