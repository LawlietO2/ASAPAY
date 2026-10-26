package br.com.asa.smartpos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.asa.smartpos.BuildConfig
import br.com.asa.smartpos.data.HttpPaymentApi
import br.com.asa.smartpos.data.PaymentApi
import br.com.asa.smartpos.data.MockPaymentApi
import br.com.asa.smartpos.data.MockOutcome
import br.com.asa.smartpos.data.PaymentApiException
import br.com.asa.smartpos.domain.PaymentType
import br.com.asa.smartpos.domain.Sale
import br.com.asa.smartpos.domain.SalePayloadValidationException
import br.com.asa.smartpos.domain.TransactionRecord
import br.com.asa.smartpos.domain.TransactionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val monotonicTimeMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLI }
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
                api.authorize(sale)
                update(sale.transactionId, TransactionStatus.WAITING)
                val deadline = monotonicTimeMs() + timeoutMs
                while (monotonicTimeMs() < deadline) {
                    val result = api.poll(sale.transactionId)
                    if (monotonicTimeMs() >= deadline) break
                    if (result != null) {
                        update(sale.transactionId, result)
                        return@launch
                    }
                    delay(pollIntervalMs)
                }
                api.undo(sale)
                update(sale.transactionId, TransactionStatus.UNDONE, "Tempo limite excedido; desfazimento solicitado.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalidPayload: SalePayloadValidationException) {
                update(sale.transactionId, TransactionStatus.ERROR, invalidPayload.message)
            } catch (apiFailure: PaymentApiException) {
                if (apiFailure.isClientError) {
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

    private fun append(record: TransactionRecord) {
        _transactions.value = listOf(record) + _transactions.value
    }

    private fun update(id: String, status: TransactionStatus, detail: String? = null) {
        _transactions.value = _transactions.value.map {
            if (it.sale.transactionId == id) it.copy(status = status, detail = detail) else it
        }
    }

    private suspend fun handleCommunicationFailure(sale: Sale) {
        val undoSucceeded = runCatching { api.undo(sale) }.isSuccess
        update(
            sale.transactionId,
            if (undoSucceeded) TransactionStatus.UNDONE else TransactionStatus.ERROR,
            if (undoSucceeded) "Falha de comunicação; desfazimento solicitado." else "Falha de comunicação e falha ao solicitar desfazimento."
        )
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 500L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
