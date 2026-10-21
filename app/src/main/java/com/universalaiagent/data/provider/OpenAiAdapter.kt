package com.universalaiagent.data.provider

import com.universalaiagent.data.model.AiRequest
import com.universalaiagent.data.model.AiResponse
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ErrorCategory
import com.universalaiagent.data.model.ModelInfo
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import kotlin.coroutines.coroutineContext

class OpenAiAdapter(
    override val type: ProviderType,
) : ProviderAdapter {
    override suspend fun discoverModels(config: ApiConfig, apiKey: String): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            val base = validatedBase(config)
            requireKey(config, apiKey)
            val request = Request.Builder()
                .url("$base/models")
                .applyAuth(apiKey, config)
                .get()
                .build()
            try {
                Http.client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!response.isSuccessful) {
                        throw ErrorClassifier.fromHttp(
                            response.code,
                            Http.errorMessage(body),
                            response.header("Retry-After")?.toLongOrNull(),
                        )
                    }
                    val root = Http.json.parseToJsonElement(body ?: "{}").jsonObject
                    val models = ProviderParsers.parseOpenAiModels(root, config.provider)
                    if (models.isEmpty()) {
                        throw ProviderError(
                            ErrorCategory.MODEL_UNAVAILABLE,
                            "The provider returned no models. Enter a model ID manually.",
                        )
                    }
                    models
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                throw ErrorClassifier.fromException(error)
            }
        }

    override suspend fun chat(
        config: ApiConfig,
        apiKey: String,
        request: AiRequest,
        onDelta: (String) -> Unit,
        onCall: (Call) -> Unit,
    ): AiResponse = withContext(Dispatchers.IO) {
        val base = validatedBase(config)
        if (config.model.isBlank()) {
            throw ProviderError(ErrorCategory.MODEL_UNAVAILABLE, "No model is selected for ${config.label}.")
        }
        requireKey(config, apiKey)
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", true)
            put("messages", buildMessages(request))
            if (type == ProviderType.OPENAI || type == ProviderType.GROQ || type == ProviderType.OPENROUTER) {
                putJsonObject("stream_options") { put("include_usage", true) }
            }
        }
        val httpRequest = Request.Builder()
            .url("$base/chat/completions")
            .applyAuth(apiKey, config)
            .header("Content-Type", "application/json")
            .apply {
                if (type == ProviderType.OPENROUTER) {
                    header("X-Title", "Universal AI Agent")
                }
            }
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        val call = Http.client.newCall(httpRequest)
        onCall(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    throw ErrorClassifier.fromHttp(
                        response.code,
                        Http.errorMessage(errorBody),
                        response.header("Retry-After")?.toLongOrNull(),
                    )
                }
                val rateLimit = Http.rateLimitSummary(response.headers)
                val source = response.body?.source()
                    ?: throw ProviderError(ErrorCategory.UNKNOWN, "The provider returned an empty response body.")
                val text = StringBuilder()
                var inputTokens: Long? = null
                var outputTokens: Long? = null
                var cost: Double? = null
                while (!source.exhausted()) {
                    coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    if (payload == "[DONE]") break
                    val delta = ProviderParsers.openAiDelta(payload)
                    if (delta.error != null) {
                        throw ProviderError(ErrorCategory.UNKNOWN, delta.error)
                    }
                    delta.inputTokens?.let { inputTokens = it }
                    delta.outputTokens?.let { outputTokens = it }
                    delta.cost?.let { cost = it }
                    delta.text?.takeIf { it.isNotEmpty() }?.let {
                        text.append(it)
                        onDelta(it)
                    }
                }
                if (text.isBlank()) {
                    throw ProviderError(
                        ErrorCategory.UNKNOWN,
                        "The provider returned an empty response. The model may not support streaming.",
                    )
                }
                AiResponse(
                    text = text.toString(),
                    providerLabel = config.label,
                    model = config.model,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    reportedCost = cost,
                    rateLimitInfo = rateLimit,
                )
            }
        } catch (error: CancellationException) {
            call.cancel()
            throw error
        } catch (error: ProviderError) {
            throw error
        } catch (error: Exception) {
            if (coroutineContext[Job]?.isActive == false) {
                call.cancel()
                throw CancellationException("Request cancelled")
            }
            throw ErrorClassifier.fromException(error)
        }
    }

    private fun buildMessages(request: AiRequest): JsonArray = buildJsonArray {
        if (request.systemPrompt.isNotBlank()) {
            add(buildJsonObject {
                put("role", "system")
                put("content", request.systemPrompt)
            })
        }
        request.messages.forEachIndexed { index, turn ->
            val isLastUser = index == request.messages.lastIndex && turn.role == "user"
            add(buildJsonObject {
                put("role", turn.role)
                if (isLastUser && request.images.isNotEmpty()) {
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", turn.content)
                        })
                        request.images.forEach { image ->
                            add(buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put(
                                        "url",
                                        "data:${image.mimeType};base64," +
                                            Base64.getEncoder().encodeToString(image.bytes),
                                    )
                                }
                            })
                        }
                    }
                } else {
                    put("content", turn.content)
                }
            })
        }
    }

    private fun validatedBase(config: ApiConfig): String {
        val base = config.effectiveBaseUrl
        EndpointValidator.validate(base, config.allowInsecureHttp)?.let {
            throw ProviderError(ErrorCategory.INVALID_REQUEST, it)
        }
        return EndpointValidator.normalized(base)
    }

    private fun requireKey(config: ApiConfig, apiKey: String) {
        if (apiKey.isBlank() && config.provider != ProviderType.CUSTOM_OPENAI) {
            throw ProviderError(ErrorCategory.AUTHENTICATION, "No API key is stored for ${config.label}.")
        }
    }

    private fun Request.Builder.applyAuth(apiKey: String, config: ApiConfig): Request.Builder {
        if (apiKey.isNotBlank()) {
            header("Authorization", "Bearer $apiKey")
        }
        if (config.provider == ProviderType.OPENROUTER) {
            header("HTTP-Referer", "https://github.com/7r8e9e8n7a18954-hash/Control-mobile")
        }
        return this
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
