package br.com.asa.smartpos.data

import br.com.asa.smartpos.domain.ReceiptCopy
import br.com.asa.smartpos.domain.TransactionStatus
import java.util.concurrent.CopyOnWriteArrayList

/** Data sent to a printer adapter; the transaction ID remains the payment's idempotency key. */
data class ReceiptPrintJob(
    val transactionId: String,
    val amountInCents: Long,
    val status: TransactionStatus,
    val copies: Set<ReceiptCopy>
)

/** Output port that can be implemented by a SmartPOS printer SDK in a real terminal. */
interface ReceiptPrinter {
    suspend fun print(job: ReceiptPrintJob)
}

/** In-memory printer substitute for the challenge environment, which has no printer hardware. */
class SimulatedReceiptPrinter : ReceiptPrinter {
    private val jobsInternal = CopyOnWriteArrayList<ReceiptPrintJob>()

    /** Each call is recorded, including reprints of the same transaction. */
    val printJobs: List<ReceiptPrintJob>
        get() = jobsInternal.toList()

    override suspend fun print(job: ReceiptPrintJob) {
        jobsInternal += job
        println(
            "Impressão simulada: transactionId=${job.transactionId}, " +
                "amountInCents=${job.amountInCents}, status=${job.status}, copies=${job.copies}"
        )
    }
}
