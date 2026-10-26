package br.com.asa.smartpos.ui



internal enum class ReceiptCopies(val label: String) {
    BOTH("Via do estabelecimento + Via do cliente"),
    MERCHANT("Via do estabelecimento"),
    CUSTOMER("Via do cliente")
}
