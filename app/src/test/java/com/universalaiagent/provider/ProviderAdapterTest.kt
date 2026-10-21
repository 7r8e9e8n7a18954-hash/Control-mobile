package com.universalaiagent.provider

import com.universalaiagent.data.model.AiRequest
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ChatTurn
import com.universalaiagent.data.model.ErrorCategory
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.ProviderType
import com.universalaiagent.data.provider.AnthropicAdapter
import com.universalaiagent.data.provider.GeminiAdapter
import com.universalaiagent.data.provider.OpenAiAdapter
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ProviderAdapterTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `openai model discovery uses bearer auth`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"gpt-4o-mini","context_length":128000}]}"""),
        )
        val config = config(ProviderType.OPENAI)

        val models = OpenAiAdapter(ProviderType.OPENAI).discoverModels(config, "secret-key")

        assertEquals("gpt-4o-mini", models.single().id)
        val request = server.takeRequest()
        assertEquals("/models", request.path)
        assertEquals("Bearer secret-key", request.getHeader("Authorization"))
    }

    @Test
    fun `gemini model discovery sends api key header`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"models":[{"name":"models/gemini-2.0-flash",
                       "supportedGenerationMethods":["generateContent"]}]}""",
                ),
        )

        val models = GeminiAdapter().discoverModels(config(ProviderType.GEMINI), "gemini-key")

        assertEquals("gemini-2.0-flash", models.single().id)
        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/models?pageSize=1000"))
        assertEquals("gemini-key", request.getHeader("x-goog-api-key"))
    }

    @Test
    fun `anthropic model discovery sends required headers`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"claude-3-5-haiku-latest"}]}"""),
        )

        val models = AnthropicAdapter().discoverModels(config(ProviderType.ANTHROPIC), "anthropic-key")

        assertEquals("claude-3-5-haiku-latest", models.single().id)
        val request = server.takeRequest()
        assertEquals("anthropic-key", request.getHeader("x-api-key"))
        assertTrue(request.getHeader("anthropic-version")!!.isNotBlank())
    }

    @Test
    fun `openai streaming chat parses deltas and usage`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"role":"assistant"}}]}
                    data: {"choices":[{"delta":{"content":"Hello"}}]}
                    data: {"choices":[{"delta":{"content":" world"}}]}
                    data: {"choices":[],"usage":{"prompt_tokens":9,"completion_tokens":2,"cost":0.0003}}
                    data: [DONE]

                    """.trimIndent(),
                ),
        )
        val deltas = mutableListOf<String>()
        val request = AiRequest(
            systemPrompt = "Be brief.",
            messages = listOf(ChatTurn("user", "Say hello")),
        )

        val response = OpenAiAdapter(ProviderType.OPENAI).chat(
            config = config(ProviderType.OPENAI).copy(model = "gpt-4o-mini"),
            apiKey = "secret-key",
            request = request,
            onDelta = { deltas += it },
            onCall = {},
        )

        assertEquals(listOf("Hello", " world"), deltas)
        assertEquals("Hello world", response.text)
        assertEquals(9L, response.inputTokens)
        assertEquals(2L, response.outputTokens)
        assertEquals(0.0003, response.reportedCost!!, 0.0000001)
    }

    @Test
    fun `provider errors are classified with auth category`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"invalid api key"}}"""),
        )

        try {
            OpenAiAdapter(ProviderType.OPENAI).chat(
                config = config(ProviderType.OPENAI).copy(model = "gpt-4o-mini"),
                apiKey = "wrong",
                request = AiRequest("", listOf(ChatTurn("user", "hi"))),
                onDelta = {},
                onCall = {},
            )
            fail("Expected a ProviderError")
        } catch (error: ProviderError) {
            assertEquals(ErrorCategory.AUTHENTICATION, error.category)
            assertEquals(401, error.httpCode)
            assertTrue(error.message.contains("invalid api key"))
        }
    }

    private fun config(provider: ProviderType) = ApiConfig(
        id = "test",
        provider = provider,
        nickname = "Test",
        model = "test-model",
        baseUrl = server.url("/").toString().trimEnd('/'),
        allowInsecureHttp = true,
    )
}
