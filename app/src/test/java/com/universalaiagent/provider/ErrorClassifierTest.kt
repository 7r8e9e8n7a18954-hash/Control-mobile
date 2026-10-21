package com.universalaiagent.provider

import com.universalaiagent.data.model.ErrorCategory
import com.universalaiagent.data.provider.ErrorClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorClassifierTest {

    @Test
    fun `auth errors are not retried`() {
        val error = ErrorClassifier.fromHttp(401, "invalid key")

        assertEquals(ErrorCategory.AUTHENTICATION, error.category)
        assertNull(ErrorClassifier.retryDelaySeconds(error.category, null))
        assertNull(ErrorClassifier.cooldownSeconds(error.category, null))
    }

    @Test
    fun `rate limit gets bounded retry delay`() {
        val error = ErrorClassifier.fromHttp(429, "slow down", retryAfterSeconds = 7)

        assertEquals(ErrorCategory.RATE_LIMIT, error.category)
        assertEquals(7L, ErrorClassifier.retryDelaySeconds(error.category, 7))
        assertEquals(3L, ErrorClassifier.retryDelaySeconds(error.category, null))
        assertEquals(60L, ErrorClassifier.cooldownSeconds(error.category, null))
    }

    @Test
    fun `quota hints are distinguished from rate limits`() {
        val quota = ErrorClassifier.fromHttp(429, "insufficient_quota for this project")
        val rate = ErrorClassifier.fromHttp(429, "requests per minute exceeded")

        assertEquals(ErrorCategory.QUOTA_EXHAUSTED, quota.category)
        assertEquals(ErrorCategory.RATE_LIMIT, rate.category)
        assertEquals(900L, ErrorClassifier.cooldownSeconds(quota.category, null))
    }

    @Test
    fun `context length hints are classified`() {
        val error = ErrorClassifier.fromHttp(400, "maximum context length exceeded")

        assertEquals(ErrorCategory.CONTEXT_LENGTH, error.category)
    }

    @Test
    fun `server and model errors map correctly`() {
        assertEquals(ErrorCategory.SERVER_ERROR, ErrorClassifier.fromHttp(503, null).category)
        assertEquals(ErrorCategory.MODEL_UNAVAILABLE, ErrorClassifier.fromHttp(404, null).category)
        assertEquals(ErrorCategory.QUOTA_EXHAUSTED, ErrorClassifier.fromHttp(402, null).category)
        assertEquals(ErrorCategory.TIMEOUT, ErrorClassifier.fromHttp(504, null).category)
        assertEquals(ErrorCategory.INVALID_REQUEST, ErrorClassifier.fromHttp(422, null).category)
    }

    @Test
    fun `long retry-after is not converted into an in-request retry`() {
        val error = ErrorClassifier.fromHttp(503, null, retryAfterSeconds = 31)

        assertNull(ErrorClassifier.retryDelaySeconds(error.category, 31))
        assertEquals(31L, ErrorClassifier.cooldownSeconds(error.category, 31))
    }

    @Test
    fun `network exceptions are classified`() {
        assertEquals(
            ErrorCategory.NETWORK,
            ErrorClassifier.fromException(UnknownHostException("missing")).category,
        )
        assertEquals(
            ErrorCategory.NETWORK,
            ErrorClassifier.fromException(IOException("offline")).category,
        )
        assertEquals(
            ErrorCategory.TIMEOUT,
            ErrorClassifier.fromException(SocketTimeoutException("timeout")).category,
        )
    }

    @Test
    fun `unknown exception keeps a useful message`() {
        val error = ErrorClassifier.fromException(IllegalStateException("weird state"))

        assertEquals(ErrorCategory.UNKNOWN, error.category)
        assertTrue(error.message.contains("weird state"))
    }
}
