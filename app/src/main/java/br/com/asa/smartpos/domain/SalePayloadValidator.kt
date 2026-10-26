package br.com.asa.smartpos.domain

/** Validates required authorization/undo payload fields before any network request. */
object SalePayloadValidator {
    fun validate(sale: Sale): String? = when {
        sale.transactionId.isBlank() -> "O identificador da transação é obrigatório."
        sale.amountInCents <= 0L -> "O valor da transação deve ser maior que zero."
        sale.merchantId.isBlank() -> "O identificador do estabelecimento é obrigatório."
        sale.merchantId.length != MERCHANT_ID_LENGTH || !sale.merchantId.all { it in '0'..'9' } ->
            "O identificador do estabelecimento deve conter 15 dígitos numéricos."
        sale.terminalId.isBlank() -> "O identificador do terminal é obrigatório."
        sale.terminalId.length != TERMINAL_ID_LENGTH || !sale.terminalId.all { it in '0'..'9' } ->
            "O identificador do terminal deve conter 8 dígitos numéricos."
        else -> null
    }

    fun requireValid(sale: Sale) {
        validate(sale)?.let { throw SalePayloadValidationException(it) }
    }

    private const val MERCHANT_ID_LENGTH = 15
    private const val TERMINAL_ID_LENGTH = 8
}

class SalePayloadValidationException(message: String) : IllegalArgumentException(message)
