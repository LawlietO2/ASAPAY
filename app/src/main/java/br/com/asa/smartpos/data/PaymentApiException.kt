package br.com.asa.smartpos.data

class PaymentApiException(
    val statusCode: Int,
    val userMessage: String
) : RuntimeException("HTTP $statusCode: $userMessage") {
    val isClientError: Boolean get() = statusCode in 400..499

    /**
     * Request timeouts, rate limits, and server/redirect errors leave the payment flow without
     * a final result; the caller should request one undo with the same transactionId, not retry.
     */
    val requiresUndo: Boolean
        get() = statusCode == HTTP_REQUEST_TIMEOUT ||
            statusCode == HTTP_TOO_MANY_REQUESTS ||
            statusCode !in 400..499

    private companion object {
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

/** A successful HTTP response that does not satisfy the polling response contract. */
class PaymentApiResponseException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
