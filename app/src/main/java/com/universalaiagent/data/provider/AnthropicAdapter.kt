package com.universalaiagent.data.provider

import com.universalaiagent.data.model.AiRequest
import com.universalaiagent.data.model.AiResponse
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ErrorCategory
import com.universalaiagent.data.model.ModelInfo
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.ProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import kotlin.coroutines.coroutineContext

class AnthropicAdapter : ProviderAdapter {
    override val type: ProviderType = ProviderType.ANTHROPIC

    override suspend fun discoverModels(config: ApiConfig, apiKey: String): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            val base = validatedBase(config)
            if (apiKey.isBlank()) {
                throw ProviderError(ErrorCategory.AUTHENTICATION, "No API key is stored for ${config.label}.")
            }
            val request = Request.Builder()
                .url("$base/models?limit=1000")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
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
                    ProviderParsers.parseAnthropicModels(root, config.provider).ifEmpty {
                        throw ProviderError(
                            ErrorCategory.MODEL_UNAVAILABLE,
                            "Anthropic returned no models. Enter a model ID manually.",
                        )
                    }
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
        if (apiKey.isBlank()) {
            throw ProviderError(ErrorCategory.AUTHENTICATION, "No API key is stored for ${config.label}.")
        }
        if (config.model.isBlank()) {
            throw ProviderError(ErrorCategory.MODEL_UNAVAILABLE, "No model is selected for ${config.label}.")
        }
        val base = validatedBase(config)
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", request.maxOutputTokens)
            put("stream", true)
            if (request.systemPrompt.isNotBlank()) {
                put("system", request.systemPrompt)
            }
            put("messages", buildJsonArray {
                request.messages.forEachIndexed { index, turn ->
                    val role = if (turn.role == "assistant") "assistant" else "user"
                    val isLastUser = index == request.messages.lastIndex && role == "user"
                    add(buildJsonObject {
                        put("role", role)
                        if (isLastUser && request.images.isNotEmpty()) {
                            putJsonArray("content") {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", turn.content)
                                })
                                request.images.forEach { image ->
                                    add(buildJsonObject {
                                        put("type", "image")
                                        putJsonObject("source") {
                                            put("type", "base64")
                                            put("media_type", image.mimeType)
                                            put(
                                                "data",
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
            })
        }
        val httpRequest = Request.Builder()
            .url("$base/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val call = Http.client.newCall(httpRequest)
        onCall(call)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw ErrorClassifier.fromHttp(
                        response.code,
                        Http.errorMessage(response.body?.string()),
                        response.header("Retry-After")?.toLongOrNull(),
                    )
                }
                val rateLimit = Http.rateLimitSummary(response.headers)
                val source = response.body?.source()
                    ?: throw ProviderError(ErrorCategory.UNKNOWN, "Anthropic returned an empty response.")
                val text = StringBuilder()
                var inputTokens: Long? = null
                var outputTokens: Long? = null
                while (!source.exhausted()) {
                    coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    val delta = ProviderParsers.anthropicDelta(payload)
                    if (delta.error != null) {
                        throw ProviderError(ErrorCategory.UNKNOWN, delta.error)
                    }
                    delta.inputTokens?.let { inputTokens = it }
                    delta.outputTokens?.let { outputTokens = it }
                    delta.text?.takeIf { it.isNotEmpty() }?.let {
                        text.append(it)
                        onDelta(it)
                    }
                }
                if (text.isBlank()) {
                    throw ProviderError(ErrorCategory.UNKNOWN, "Anthropic returned an empty response.")
                }
                AiResponse(
                    text = text.toString(),
                    providerLabel = config.label,
                    model = config.model,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
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

    private fun validatedBase(config: ApiConfig): String {
        val base = config.effectiveBaseUrl
        EndpointValidator.validate(base, config.allowInsecureHttp)?.let {
            throw ProviderError(ErrorCategory.INVALID_REQUEST, it)
        }
        return EndpointValidator.normalized(base)
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
