package com.universalaiagent.provider

import com.universalaiagent.data.model.Capability
import com.universalaiagent.data.model.ProviderType
import com.universalaiagent.data.provider.Http
import com.universalaiagent.data.provider.ProviderParsers
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderParsersTest {

    @Test
    fun `parses openai models and provider metadata`() {
        val root = json(
            """
            {"data":[
              {"id":"gpt-4o","name":"GPT-4o","context_length":128000,
               "architecture":{"input_modalities":["text","image"]}},
              {"id":"text-embedding-3-small","context_length":8191},
              {"id":""}
            ]}
            """,
        )

        val models = ProviderParsers.parseOpenAiModels(root, ProviderType.OPENAI)

        assertEquals(listOf("gpt-4o", "text-embedding-3-small"), models.map { it.id })
        val vision = models.first { it.id == "gpt-4o" }
        assertTrue(vision.capabilities.contains(Capability.VISION))
        assertEquals(128000L, vision.contextWindow)
        assertEquals("Provider metadata", vision.capabilitySource)
        val text = models.first { it.id == "text-embedding-3-small" }
        assertFalse(text.capabilities.contains(Capability.VISION))
    }

    @Test
    fun `infers vision from model id when metadata is absent`() {
        val root = json("""{"data":[{"id":"claude-3-5-sonnet-latest"}]}""")

        val model = ProviderParsers.parseOpenAiModels(root, ProviderType.OPENROUTER).single()

        assertTrue(model.capabilities.contains(Capability.VISION))
        assertEquals("Inferred from model ID", model.capabilitySource)
    }

    @Test
    fun `returns empty list for malformed openai payload`() {
        assertEquals(emptyList<Any>(), ProviderParsers.parseOpenAiModels(json("""{"nope":[]}"""), ProviderType.OPENAI))
    }

    @Test
    fun `parses gemini models and filters unsupported methods`() {
        val root = json(
            """
            {"models":[
              {"name":"models/gemini-2.0-flash","displayName":"Gemini 2.0 Flash",
               "supportedGenerationMethods":["generateContent","countTokens"],
               "inputTokenLimit":1048576,"outputTokenLimit":8192},
              {"name":"models/text-embedding-004",
               "supportedGenerationMethods":["embedContent"]},
              {"name":"models/gemini-exp-1206"}
            ]}
            """,
        )

        val models = ProviderParsers.parseGeminiModels(root, ProviderType.GEMINI)

        assertEquals(listOf("gemini-2.0-flash", "gemini-exp-1206"), models.map { it.id })
        val flash = models.first { it.id == "gemini-2.0-flash" }
        assertEquals(1048576L, flash.contextWindow)
        assertEquals(8192L, flash.maxOutputTokens)
        assertTrue(flash.capabilities.contains(Capability.VISION))
    }

    @Test
    fun `parses anthropic models`() {
        val root = json(
            """{"data":[{"id":"claude-sonnet-4-20250514","display_name":"Claude Sonnet 4"}]}""",
        )

        val model = ProviderParsers.parseAnthropicModels(root, ProviderType.ANTHROPIC).single()

        assertEquals("claude-sonnet-4-20250514", model.id)
        assertEquals("Claude Sonnet 4", model.displayName)
        assertTrue(model.capabilities.contains(Capability.VISION))
    }

    @Test
    fun `parses openai stream deltas with usage and cost`() {
        val text = ProviderParsers.openAiDelta(
            """{"choices":[{"delta":{"content":"Hello"}}]}""",
        )
        val usage = ProviderParsers.openAiDelta(
            """{"choices":[],"usage":{"prompt_tokens":11,"completion_tokens":7,"cost":0.0012}}""",
        )

        assertEquals("Hello", text.text)
        assertEquals(11L, usage.inputTokens)
        assertEquals(7L, usage.outputTokens)
        assertEquals(0.0012, usage.cost!!, 0.0000001)
    }

    @Test
    fun `parses openai stream errors`() {
        val delta = ProviderParsers.openAiDelta(
            """{"error":{"message":"model overloaded"}}""",
        )

        assertEquals("model overloaded", delta.error)
    }

    @Test
    fun `parses anthropic stream lifecycle`() {
        val start = ProviderParsers.anthropicDelta(
            """{"type":"message_start","message":{"usage":{"input_tokens":19}}}""",
        )
        val content = ProviderParsers.anthropicDelta(
            """{"type":"content_block_delta","delta":{"text":"Hi "}}""",
        )
        val end = ProviderParsers.anthropicDelta(
            """{"type":"message_delta","usage":{"output_tokens":4}}""",
        )
        val error = ProviderParsers.anthropicDelta(
            """{"type":"error","error":{"message":"bad request"}}""",
        )

        assertEquals(19L, start.inputTokens)
        assertEquals("Hi ", content.text)
        assertEquals(4L, end.outputTokens)
        assertEquals("bad request", error.error)
    }

    @Test
    fun `parses gemini deltas and safety blocks`() {
        val content = ProviderParsers.geminiDelta(
            """
            {"candidates":[{"content":{"parts":[{"text":"Sunny"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":2}}
            """,
        )
        val blocked = ProviderParsers.geminiDelta(
            """{"promptFeedback":{"blockReason":"SAFETY"}}""",
        )
        val stopped = ProviderParsers.geminiDelta(
            """{"candidates":[{"finishReason":"RECITATION"}]}""",
        )

        assertEquals("Sunny", content.text)
        assertEquals(5L, content.inputTokens)
        assertEquals(2L, content.outputTokens)
        assertNull(content.error)
        assertTrue(blocked.error!!.contains("SAFETY"))
        assertTrue(stopped.error!!.contains("RECITATION"))
    }

    @Test
    fun `invalid delta json is ignored`() {
        assertNull(ProviderParsers.openAiDelta("{broken").text)
        assertNull(ProviderParsers.anthropicDelta("{broken").text)
        assertNull(ProviderParsers.geminiDelta("{broken").text)
    }

    @Test
    fun `vision inference covers known model families`() {
        assertTrue(ProviderParsers.inferVision(ProviderType.OPENAI, "gpt-4o-mini"))
        assertTrue(ProviderParsers.inferVision(ProviderType.OPENAI, "o3-mini"))
        assertTrue(ProviderParsers.inferVision(ProviderType.GEMINI, "gemini-2.5-pro"))
        assertTrue(ProviderParsers.inferVision(ProviderType.GROQ, "meta-llama/llama-4-scout"))
        assertTrue(ProviderParsers.inferVision(ProviderType.CUSTOM_OPENAI, "qwen2.5-vl-72b"))
        assertFalse(ProviderParsers.inferVision(ProviderType.OPENAI, "gpt-3.5-turbo"))
        assertFalse(ProviderParsers.inferVision(ProviderType.ANTHROPIC, "claude-2.1"))
    }

    private fun json(raw: String) = Http.json.parseToJsonElement(raw).jsonObject
}
