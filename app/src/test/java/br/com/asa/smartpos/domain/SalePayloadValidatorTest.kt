package br.com.asa.smartpos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SalePayloadValidatorTest {
    @Test
    fun completeSalePayloadIsAccepted() {
        logSeparator()
        logScenario("[CENARIO] Validar payload completo de venda")
        val result = SalePayloadValidator.validate(Sale(amountInCents = 1_000L, paymentType = PaymentType.CREDIT))
        logProcessing("[VALIDANDO] transactionId, valor, merchantId e terminalId presentes.")
        logResult("[RESULTADO] Payload aceito: $result")
        assertNull(result)
        logSeparator()
    }

    @Test
    fun blankTransactionIdIsRejected() {
        logSeparator()
        logScenario("[CENARIO] Rejeitar transactionId vazio")
        val sale = Sale(transactionId = " ", amountInCents = 1_000L, paymentType = PaymentType.CREDIT)
        val result = SalePayloadValidator.validate(sale)
        logProcessing("[VALIDANDO] transactionId está em branco.")
        logResult("[RESULTADO] $result")
        assertEquals("O identificador da transação é obrigatório.", result)
        logSeparator()
    }

    @Test
    fun zeroOrNegativeAmountIsRejected() {
        logSeparator()
        logScenario("[CENARIO] Rejeitar valores zero e negativo")
        val zeroSale = Sale(amountInCents = 0L, paymentType = PaymentType.CREDIT)
        val negativeSale = Sale(amountInCents = -1L, paymentType = PaymentType.CREDIT)
        val zeroResult = SalePayloadValidator.validate(zeroSale)
        val negativeResult = SalePayloadValidator.validate(negativeSale)
        logProcessing("[VALIDANDO] amountInCents = ${zeroSale.amountInCents} e ${negativeSale.amountInCents}.")
        logResult("[RESULTADO] Zero: $zeroResult; negativo: $negativeResult")
        assertEquals("O valor da transação deve ser maior que zero.", zeroResult)
        assertEquals("O valor da transação deve ser maior que zero.", negativeResult)
        logSeparator()
    }

    @Test
    fun blankMerchantIdIsRejected() {
        logSeparator()
        logScenario("[CENARIO] Rejeitar merchantId vazio")
        val sale = Sale(amountInCents = 1_000L, paymentType = PaymentType.CREDIT, merchantId = " ")
        val result = SalePayloadValidator.validate(sale)
        logProcessing("[VALIDANDO] merchantId está em branco.")
        logResult("[RESULTADO] $result")
        assertEquals("O identificador do estabelecimento é obrigatório.", result)
        logSeparator()
    }

    @Test
    fun merchantIdWithWrongLengthIsRejected() {
        logSeparator()
        logScenario("[CENARIO] Rejeitar merchantId com quantidade incorreta de dígitos")
        val sale = Sale(amountInCents = 1_000L, paymentType = PaymentType.CREDIT, merchantId = "12345678901234")
        val result = SalePayloadValidator.validate(sale)
        logProcessing("[VALIDANDO] merchantId possui ${sale.merchantId.length} dígitos; esperado: 15.")
        logResult("[RESULTADO] $result")
        assertEquals("O identificador do estabelecimento deve conter 15 dígitos numéricos.", result)
        logSeparator()
    }

    @Test
    fun nonNumericMerchantIdIsRejected() {
        logSeparator()
        logScenario("[CENARIO] Rejeitar merchantId com caractere não numérico")
        val sale = Sale(amountInCents = 1_000L, paymentType = PaymentType.CREDIT, merchantId = "12345678901234A")
        val result = SalePayloadValidator.validate(sale)
        logProcessing("[VALIDANDO] merchantId deve conter somente dígitos numéricos.")
        logResult("[RESULTADO] $result")
        assertEquals("O identificador do estabelecimento deve conter 15 dígitos numéricos.", result)
        logSeparator()
    }

    @Test
    fun blankTerminalIdIsRejected() {
        logSeparator()
        logScenario("[CENARIO] Rejeitar terminalId vazio")
        val sale = Sale(amountInCents = 1_000L, paymentType = PaymentType.CREDIT, terminalId = "")
        val result = SalePayloadValidator.validate(sale)
        logProcessing("[VALIDANDO] terminalId está vazio.")
        logResult("[RESULTADO] $result")
        assertEquals("O identificador do terminal é obrigatório.", result)
        logSeparator()
    }

    @Test
    fun terminalIdWithWrongLengthIsRejected() {
        logSeparator()
        logScenario("[CENARIO] Rejeitar terminalId com quantidade incorreta de dígitos")
        val sale = Sale(amountInCents = 1_000L, paymentType = PaymentType.CREDIT, terminalId = "1234567")
        val result = SalePayloadValidator.validate(sale)
        logProcessing("[VALIDANDO] terminalId possui ${sale.terminalId.length} dígitos; esperado: 8.")
        logResult("[RESULTADO] $result")
        assertEquals("O identificador do terminal deve conter 8 dígitos numéricos.", result)
        logSeparator()
    }

    @Test
    fun nonNumericTerminalIdIsRejected() {
        logSeparator()
        logScenario("[CENARIO] Rejeitar terminalId com caractere não numérico")
        val sale = Sale(amountInCents = 1_000L, paymentType = PaymentType.CREDIT, terminalId = "1234567A")
        val result = SalePayloadValidator.validate(sale)
        logProcessing("[VALIDANDO] terminalId deve conter somente dígitos numéricos.")
        logResult("[RESULTADO] $result")
        assertEquals("O identificador do terminal deve conter 8 dígitos numéricos.", result)
        logSeparator()
    }
}

private const val ANSI_RESET = "\u001B[0m"
private const val ANSI_CYAN = "\u001B[36m"
private const val ANSI_YELLOW = "\u001B[33m"
private const val ANSI_GREEN = "\u001B[32m"
private const val ANSI_GRAY = "\u001B[90m"

private fun logScenario(message: String) = println("$ANSI_CYAN$message$ANSI_RESET")
private fun logProcessing(message: String) = println("$ANSI_YELLOW$message$ANSI_RESET")
private fun logResult(message: String) = println("$ANSI_GREEN$message$ANSI_RESET")
private fun logSeparator() = println("$ANSI_GRAY===================================================================$ANSI_RESET")
