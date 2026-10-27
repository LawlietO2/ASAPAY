package br.com.asa.smartpos.ui

import br.com.asa.smartpos.domain.ReceiptCopy

internal enum class ReceiptCopies(val label: String, val targets: Set<ReceiptCopy>) {
    BOTH(
        "Via do estabelecimento + Via do cliente",
        setOf(ReceiptCopy.ESTABLISHMENT, ReceiptCopy.CUSTOMER)
    ),
    MERCHANT("Via do estabelecimento", setOf(ReceiptCopy.ESTABLISHMENT)),
    CUSTOMER("Via do cliente", setOf(ReceiptCopy.CUSTOMER))
}
