package com.universalaiagent.data.provider

import com.universalaiagent.data.model.ErrorCategory
import com.universalaiagent.data.model.ProviderError
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorClassifier {
    fun fromHttp(code: Int, detail: String?, retryAfterSeconds: Long? = null): ProviderError {
        val message = detail?.takeIf { it.isNotBlank() } ?: "Provider returned HTTP $code"
        val lower = message.lowercase()
        val category = when {
            code == 401 -> ErrorCategory.AUTHENTICATION
            code == 403 -> ErrorCategory.PERMISSION
            code == 402 -> ErrorCategory.QUOTA_EXHAUSTED
            code == 404 -> ErrorCategory.MODEL_UNAVAILABLE
            code == 413 -> ErrorCategory.CONTEXT_LENGTH
            code == 429 && lower.hasAny(QUOTA_HINTS) -> ErrorCategory.QUOTA_EXHAUSTED
            code == 429 -> ErrorCategory.RATE_LIMIT
            code == 408 || code == 504 -> ErrorCategory.TIMEOUT
            code >= 500 -> ErrorCategory.SERVER_ERROR
            code == 400 && lower.hasAny(CONTEXT_HINTS) -> ErrorCategory.CONTEXT_LENGTH
            code == 400 || code == 422 -> ErrorCategory.INVALID_REQUEST
            else -> ErrorCategory.UNKNOWN
        }
        return ProviderError(
            category = category,
            message = message,
            httpCode = code,
            retryAfterSeconds = retryAfterSeconds,
        )
    }

    fun fromException(error: Throwable): ProviderError {
        if (error is ProviderError) return error
        return when (error) {
            is SocketTimeoutException, is InterruptedIOException -> ProviderError(
                ErrorCategory.TIMEOUT,
                "The provider took too long to respond. Check your connection and try again.",
                cause = error,
            )
            is UnknownHostException -> ProviderError(
                ErrorCategory.NETWORK,
                "Cannot reach the provider. Check your internet connection or the custom base URL.",
                cause = error,
            )
            is IOException -> ProviderError(
                ErrorCategory.NETWORK,
                "Network error: ${error.message?.take(300) ?: "connection failed"}",
                cause = error,
            )
            else -> ProviderError(
                ErrorCategory.UNKNOWN,
                "Unexpected provider error: ${error.message?.take(300) ?: error.javaClass.simpleName}",
                cause = error,
            )
        }
    }

    fun retryDelaySeconds(category: ErrorCategory, retryAfterSeconds: Long?): Long? {
        val delay = when (category) {
            ErrorCategory.RATE_LIMIT -> retryAfterSeconds?.coerceIn(1, 30) ?: 3
            ErrorCategory.SERVER_ERROR,
            ErrorCategory.NETWORK,
            ErrorCategory.TIMEOUT,
            -> retryAfterSeconds?.coerceIn(1, 10) ?: 2
            else -> return null
        }
        if (retryAfterSeconds != null && retryAfterSeconds > 30) return null
        return delay
    }

    fun cooldownSeconds(category: ErrorCategory, retryAfterSeconds: Long?): Long? = when (category) {
        ErrorCategory.RATE_LIMIT -> retryAfterSeconds?.coerceAtLeast(30) ?: 60
        ErrorCategory.QUOTA_EXHAUSTED -> 900
        ErrorCategory.SERVER_ERROR -> retryAfterSeconds?.coerceAtLeast(30) ?: 30
        ErrorCategory.NETWORK -> 20
        ErrorCategory.TIMEOUT -> 20
        ErrorCategory.MODEL_UNAVAILABLE -> 120
        ErrorCategory.AUTHENTICATION,
        ErrorCategory.PERMISSION,
        -> null
        else -> null
    }

    private fun String.hasAny(hints: List<String>): Boolean = hints.any { contains(it) }

    private val QUOTA_HINTS = listOf(
        "insufficient_quota",
        "quota",
        "billing",
        "credit",
        "out of tokens",
        "exceeded your current quota",
    )

    private val CONTEXT_HINTS = listOf(
        "context length",
        "context_length",
        "maximum context",
        "too many tokens",
        "prompt is too long",
        "input is too long",
        "token limit",
    )
}
