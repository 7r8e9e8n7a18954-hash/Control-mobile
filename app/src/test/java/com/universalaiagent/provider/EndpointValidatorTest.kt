package com.universalaiagent.provider

import com.universalaiagent.data.provider.EndpointValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointValidatorTest {

    @Test
    fun `accepts https base url`() {
        assertNull(EndpointValidator.validate("https://api.example.com/v1", allowInsecureHttp = false))
    }

    @Test
    fun `rejects empty url`() {
        assertEquals("Base URL is required", EndpointValidator.validate("   ", false))
    }

    @Test
    fun `rejects invalid url`() {
        val error = EndpointValidator.validate("not a url", false)
        assertTrue(error != null && error.contains("valid HTTPS"))
    }

    @Test
    fun `rejects credentials in url`() {
        val error = EndpointValidator.validate("https://user:pass@example.com/v1", false)
        assertTrue(error != null && error.contains("credentials"))
    }

    @Test
    fun `blocks public http unless explicitly allowed and local`() {
        assertTrue(
            EndpointValidator.validate("http://example.com", allowInsecureHttp = false)!!
                .contains("HTTP is blocked"),
        )
        assertTrue(
            EndpointValidator.validate("http://example.com", allowInsecureHttp = true)!!
                .contains("only allowed for localhost"),
        )
    }

    @Test
    fun `allows opted-in local http`() {
        assertNull(EndpointValidator.validate("http://localhost:8080/v1", allowInsecureHttp = true))
        assertNull(EndpointValidator.validate("http://127.0.0.1:1234", allowInsecureHttp = true))
        assertNull(EndpointValidator.validate("http://10.0.2.2:8000", allowInsecureHttp = true))
    }

    @Test
    fun `normalizes trailing slash and whitespace`() {
        assertEquals("https://api.example.com/v1", EndpointValidator.normalized(" https://api.example.com/v1/ "))
    }

    @Test
    fun `recognizes loopback hosts only`() {
        assertTrue(EndpointValidator.isLoopback("LOCALHOST"))
        assertTrue(EndpointValidator.isLoopback("::1"))
        assertFalse(EndpointValidator.isLoopback("192.168.1.10"))
    }
}
