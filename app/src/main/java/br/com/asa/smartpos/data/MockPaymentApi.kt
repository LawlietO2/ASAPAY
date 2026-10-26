package br.com.asa.smartpos.data

import br.com.asa.smartpos.domain.Sale
import br.com.asa.smartpos.domain.TransactionStatus
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** In-process backend substitute so the UI and async flow can be explored without credentials. */
enum class MockOutcome(val label: String) {
    APPROVED("Aprovada"),
    REJECTED("Rejeitada"),
    TIMEOUT_UNDO("Desfeita por timeout")
}

class MockPaymentApi(
    @Volatile var outcome: MockOutcome = MockOutcome.APPROVED,
    @Volatile var responseDelayMs: Long = 0L,
    private val authorizeDelayMs: Long = 250L,
    private val pollDelayMs: Long = 100L,
    private val undoDelayMs: Long = 150L,
    private val monotonicTimeMs: () -> Long = { System.nanoTime() / 1_000_000L }
) : PaymentApi {
    private val outcomeById = ConcurrentHashMap<String, MockOutcome>()
    private val pollsById = ConcurrentHashMap<String, Int>()
    private val responseDelayById = ConcurrentHashMap<String, Long>()
    private val authorizedAtById = ConcurrentHashMap<String, Long>()
    private val undoCounter = AtomicInteger()
    val undoRequests: Int get() = undoCounter.get()

    override suspend fun authorize(sale: Sale) {
        val selectedOutcome = outcome
        val selectedDelay = responseDelayMs
        delay(authorizeDelayMs)
        outcomeById.putIfAbsent(sale.transactionId, selectedOutcome)
        responseDelayById.putIfAbsent(sale.transactionId, selectedDelay)
        authorizedAtById.putIfAbsent(sale.transactionId, monotonicTimeMs())
    }

    override suspend fun poll(transactionId: String): TransactionStatus? {
        delay(pollDelayMs)
        val selectedOutcome = outcomeById[transactionId] ?: return null
        if (selectedOutcome == MockOutcome.TIMEOUT_UNDO) return null
        val polls = pollsById.merge(transactionId, 1) { previous, increment -> previous + increment } ?: 1
        val elapsedMs = monotonicTimeMs() - (authorizedAtById[transactionId] ?: monotonicTimeMs())
        val responseDelayMs = responseDelayById[transactionId] ?: 0L
        return if (polls >= 4 && elapsedMs >= responseDelayMs) when (selectedOutcome) {
            MockOutcome.APPROVED -> TransactionStatus.APPROVED
            MockOutcome.REJECTED -> TransactionStatus.REJECTED
            MockOutcome.TIMEOUT_UNDO -> null
        } else null
    }

    override suspend fun undo(sale: Sale) {
        delay(undoDelayMs)
        undoCounter.incrementAndGet()
    }
}
