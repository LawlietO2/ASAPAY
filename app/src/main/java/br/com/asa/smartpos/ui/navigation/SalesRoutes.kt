package br.com.asa.smartpos.ui.navigation

internal object SalesRoutes {
    const val HOME = "home"
    const val SALE = "sale"
    const val SETTINGS = "settings"

    const val TRANSACTION_ID = "transactionId"
    const val AUTO_OPEN_RECEIPT = "autoOpenReceipt"
    const val OPENED_FROM_HOME = "openedFromHome"
    const val RECEIPT_COPIES = "receiptCopies"

    const val RESULT_PATTERN = "result/{$TRANSACTION_ID}/{$AUTO_OPEN_RECEIPT}"
    const val RECEIPT_OPTIONS_PATTERN = "receipt-options/{$TRANSACTION_ID}/{$OPENED_FROM_HOME}"
    const val RECEIPT_PREVIEW_PATTERN = "receipt-preview/{$TRANSACTION_ID}/{$RECEIPT_COPIES}"

    fun result(transactionId: String, autoOpenReceipt: Boolean) =
        "result/$transactionId/$autoOpenReceipt"

    fun receiptOptions(transactionId: String, openedFromHome: Boolean) =
        "receipt-options/$transactionId/$openedFromHome"

    fun receiptPreview(transactionId: String, copies: String) =
        "receipt-preview/$transactionId/$copies"
}
