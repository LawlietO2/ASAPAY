package br.com.asa.smartpos.domain

import java.util.UUID

enum class PaymentType(val apiValue: String, val label: String) {
    CREDIT("Credito", "Crédito"),
    DEBIT("Debito", "Débito")
}

enum class TransactionStatus(val label: String) {
    SENDING("Enviando"),
    WAITING("Aguardando resultado"),
    APPROVED("APROVADA"),
    REJECTED("REJEITADA"),
    UNDONE("DESFEITA"),
    ERROR("Erro de comunicação")
}

data class Sale(
    val transactionId: String = UUID.randomUUID().toString(),
    val amountInCents: Long,
    val paymentType: PaymentType,
    val merchantId: String = "123456789012345",
    val terminalId: String = "12345678"
) {
    val amount: Double get() = amountInCents / 100.0
}

data class TransactionRecord(
    val sale: Sale,
    val status: TransactionStatus,
    val detail: String? = null,
    val autoPrint: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
