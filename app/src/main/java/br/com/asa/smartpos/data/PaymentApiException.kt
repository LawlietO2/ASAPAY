package br.com.asa.smartpos.data

class PaymentApiException(
    val statusCode: Int,
    val userMessage: String
) : RuntimeException("HTTP $statusCode: $userMessage") {
    val isClientError: Boolean get() = statusCode in 400..499
}
