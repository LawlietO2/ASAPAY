package br.com.asa.smartpos.data

import br.com.asa.smartpos.domain.Sale
import br.com.asa.smartpos.domain.TransactionStatus

interface PaymentApi {
    suspend fun authorize(sale: Sale)
    suspend fun poll(transactionId: String): TransactionStatus?
    suspend fun undo(sale: Sale)
}
