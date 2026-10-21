package com.universalaiagent.security

import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigTest {

    @Test
    fun `keys are masked to the last four characters`() {
        val config = config().copy(keyLast4 = "a1b2")

        assertEquals("•••• •••• a1b2", config.maskedKey())
    }

    @Test
    fun `missing key is described without exposing a placeholder secret`() {
        assertEquals("No API key stored", config().maskedKey())
    }

    @Test
    fun `cooldown only applies while the provider is enabled`() {
        val now = 1_000_000L
        val cooling = config().copy(cooldownUntil = now + 30_000)

        assertTrue(cooling.isCoolingDown(now))
        assertFalse(cooling.copy(enabled = false).isCoolingDown(now))
        assertFalse(cooling.isCoolingDown(now + 60_000))
    }

    @Test
    fun `blank base url falls back to the provider default`() {
        val config = config().copy(baseUrl = "")

        assertEquals("https://api.openai.com/v1", config.effectiveBaseUrl)
    }

    @Test
    fun `base url is normalized without trailing slash`() {
        val config = config().copy(baseUrl = "https://example.com/v1/")

        assertEquals("https://example.com/v1", config.effectiveBaseUrl)
    }

    private fun config() = ApiConfig(
        id = "id",
        provider = ProviderType.OPENAI,
        nickname = "Test",
    )
}
