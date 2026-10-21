package com.universalaiagent.data.provider

import com.universalaiagent.data.model.Capability
import com.universalaiagent.data.model.ModelInfo
import com.universalaiagent.data.model.ProviderType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class StreamDelta(
    val text: String? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cost: Double? = null,
    val error: String? = null,
)

object ProviderParsers {
    fun parseOpenAiModels(root: JsonObject, provider: ProviderType): List<ModelInfo> {
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val model = element as? JsonObject ?: return@mapNotNull null
            val id = model["id"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
            val name = model["name"]?.jsonPrimitive?.contentOrNullSafe() ?: id
            val inputModalities = (model["architecture"] as? JsonObject)
                ?.get("input_modalities")
                ?.let { it as? JsonArray }
                ?.mapNotNull { it.jsonPrimitive.contentOrNullSafe() }
                .orEmpty()
            val supportsVisionFromMeta = inputModalities.any { it.equals("image", true) }
            val contextWindow = model["context_length"]?.jsonPrimitive?.longOrNull
                ?: (model["top_provider"] as? JsonObject)?.get("context_length")?.jsonPrimitive?.longOrNull
            val maxOutput = (model["top_provider"] as? JsonObject)
                ?.get("max_completion_tokens")
                ?.jsonPrimitive
                ?.longOrNull
            val capabilities = buildSet {
                add(Capability.TEXT)
                if (supportsVisionFromMeta || inferVision(provider, id)) add(Capability.VISION)
            }
            ModelInfo(
                id = id,
                displayName = name,
                provider = provider,
                capabilities = capabilities,
                capabilitySource = if (inputModalities.isNotEmpty()) {
                    "Provider metadata"
                } else if (capabilities.contains(Capability.VISION)) {
                    "Inferred from model ID"
                } else {
                    "Text assumed; provider did not report capabilities"
                },
                contextWindow = contextWindow,
                maxOutputTokens = maxOutput,
                description = model["description"]?.jsonPrimitive?.contentOrNullSafe(),
            )
        }.sortedBy { it.id }
    }

    fun parseGeminiModels(root: JsonObject, provider: ProviderType): List<ModelInfo> {
        val models = root["models"] as? JsonArray ?: return emptyList()
        return models.mapNotNull { element ->
            val model = element as? JsonObject ?: return@mapNotNull null
            val rawName = model["name"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
            val id = rawName.removePrefix("models/")
            val methods = (model["supportedGenerationMethods"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNullSafe() }
                .orEmpty()
            if (methods.isNotEmpty() && "generateContent" !in methods) return@mapNotNull null
            val capabilities = buildSet {
                add(Capability.TEXT)
                if (inferVision(provider, id)) add(Capability.VISION)
            }
            ModelInfo(
                id = id,
                displayName = model["displayName"]?.jsonPrimitive?.contentOrNullSafe() ?: id,
                provider = provider,
                capabilities = capabilities,
                capabilitySource = if (capabilities.contains(Capability.VISION)) {
                    "Inferred from model ID"
                } else {
                    "Text assumed; provider did not report capabilities"
                },
                contextWindow = model["inputTokenLimit"]?.jsonPrimitive?.longOrNull,
                maxOutputTokens = model["outputTokenLimit"]?.jsonPrimitive?.longOrNull,
                description = model["description"]?.jsonPrimitive?.contentOrNullSafe(),
            )
        }.sortedBy { it.id }
    }

    fun parseAnthropicModels(root: JsonObject, provider: ProviderType): List<ModelInfo> {
        val data = root["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val model = element as? JsonObject ?: return@mapNotNull null
            val id = model["id"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null
            val capabilities = buildSet {
                add(Capability.TEXT)
                if (inferVision(provider, id)) add(Capability.VISION)
            }
            ModelInfo(
                id = id,
                displayName = model["display_name"]?.jsonPrimitive?.contentOrNullSafe() ?: id,
                provider = provider,
                capabilities = capabilities,
                capabilitySource = if (capabilities.contains(Capability.VISION)) {
                    "Inferred from model ID"
                } else {
                    "Text assumed; provider did not report capabilities"
                },
            )
        }.sortedBy { it.id }
    }

    fun openAiDelta(text: String): StreamDelta {
        val root = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return StreamDelta()
        val error = root["error"]
        if (error != null) {
            val message = when (error) {
                is JsonObject -> error["message"]?.jsonPrimitive?.contentOrNullSafe()
                is JsonPrimitive -> error.contentOrNullSafe()
                else -> null
            }
            return StreamDelta(error = message ?: "Provider stream error")
        }
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val content = (choice?.get("delta") as? JsonObject)
            ?.get("content")
            ?.let { value ->
                when (value) {
                    is JsonPrimitive -> value.contentOrNullSafe()
                    is JsonArray -> value.mapNotNull {
                        (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNullSafe()
                    }.joinToString("")
                    else -> null
                }
            }
        val usage = root["usage"] as? JsonObject
        return StreamDelta(
            text = content,
            inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.longOrNull,
            outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.longOrNull,
            cost = usage?.get("cost")?.jsonPrimitive?.contentOrNullSafe()?.toDoubleOrNull()
                ?: usage?.get("total_cost")?.jsonPrimitive?.contentOrNullSafe()?.toDoubleOrNull(),
        )
    }

    fun anthropicDelta(text: String): StreamDelta {
        val root = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return StreamDelta()
        return when (root["type"]?.jsonPrimitive?.contentOrNullSafe()) {
            "message_start" -> {
                val usage = (root["message"] as? JsonObject)?.get("usage") as? JsonObject
                StreamDelta(
                    inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.longOrNull,
                )
            }
            "content_block_delta" -> {
                val delta = root["delta"] as? JsonObject
                StreamDelta(
                    text = delta?.get("text")?.jsonPrimitive?.contentOrNullSafe(),
                )
            }
            "message_delta" -> {
                val usage = root["usage"] as? JsonObject
                StreamDelta(
                    outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.longOrNull,
                )
            }
            "error" -> {
                val error = root["error"] as? JsonObject
                StreamDelta(error = error?.get("message")?.jsonPrimitive?.contentOrNullSafe())
            }
            else -> StreamDelta()
        }
    }

    fun geminiDelta(text: String): StreamDelta {
        val root = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return StreamDelta()
        val blockReason = (root["promptFeedback"] as? JsonObject)
            ?.get("blockReason")
            ?.jsonPrimitive
            ?.contentOrNullSafe()
        if (blockReason != null) {
            return StreamDelta(error = "Gemini blocked this request: $blockReason")
        }
        val candidates = root["candidates"] as? JsonArray
        val candidate = candidates?.firstOrNull() as? JsonObject
        val parts = ((candidate?.get("content") as? JsonObject)?.get("parts") as? JsonArray)
        val content = parts?.mapNotNull {
            (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNullSafe()
        }?.joinToString("")
        val usage = root["usageMetadata"] as? JsonObject
        val finish = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNullSafe()
        val errorText = if (finish != null && finish != "STOP" && finish != "MAX_TOKENS") {
            "Gemini stopped the response: $finish"
        } else {
            null
        }
        return StreamDelta(
            text = content,
            inputTokens = usage?.get("promptTokenCount")?.jsonPrimitive?.longOrNull,
            outputTokens = usage?.get("candidatesTokenCount")?.jsonPrimitive?.longOrNull,
            error = errorText,
        )
    }

    fun inferVision(provider: ProviderType, modelId: String): Boolean {
        val id = modelId.lowercase()
        return when (provider) {
            ProviderType.OPENAI -> listOf(
                "gpt-4o", "gpt-4.1", "gpt-4-turbo", "gpt-4-vision", "gpt-5", "o3", "o4", "chatgpt-4o",
            ).any { id.contains(it) }
            ProviderType.ANTHROPIC -> id.contains("claude-3") || id.contains("claude-4") ||
                id.contains("claude-sonnet") || id.contains("claude-opus") || id.contains("claude-haiku")
            ProviderType.GEMINI -> id.contains("gemini-1.5") || id.contains("gemini-2") ||
                id.contains("gemini-3") || id.contains("gemini-exp")
            ProviderType.GROQ -> listOf("llama-4", "vision", "qwen2.5-vl", "qwen3-vl").any { id.contains(it) }
            ProviderType.OPENROUTER -> listOf(
                "gpt-4o", "gpt-4.1", "gpt-5", "claude", "gemini", "llama-4", "vision", "vl",
            ).any { id.contains(it) }
            ProviderType.CUSTOM_OPENAI -> listOf(
                "gpt-4o", "gpt-4.1", "gpt-5", "claude", "gemini", "vision", "vl", "llama-4",
            ).any { id.contains(it) }
        }
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        content.takeIf { it.isNotBlank() && it != "null" }

    @Suppress("unused")
    private fun JsonPrimitive.booleanOrNullSafe(): Boolean? = booleanOrNull
}
