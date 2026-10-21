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
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import kotlin.coroutines.coroutineContext

class GeminiAdapter : ProviderAdapter {
    override val type: ProviderType = ProviderType.GEMINI

    override suspend fun discoverModels(config: ApiConfig, apiKey: String): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            val base = validatedBase(config)
            if (apiKey.isBlank()) {
                throw ProviderError(ErrorCategory.AUTHENTICATION, "No API key is stored for ${config.label}.")
            }
            val request = Request.Builder()
                .url("$base/models?pageSize=1000")
                .header("x-goog-api-key", apiKey)
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
                    ProviderParsers.parseGeminiModels(root, config.provider).ifEmpty {
                        throw ProviderError(
                            ErrorCategory.MODEL_UNAVAILABLE,
                            "Gemini returned no text models. Check that this key has model access.",
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
        if (!config.model.matches(Regex("[A-Za-z0-9._-]+"))) {
            throw ProviderError(ErrorCategory.INVALID_REQUEST, "The Gemini model ID contains invalid characters.")
        }
        val base = validatedBase(config)
        val body = buildJsonObject {
            if (request.systemPrompt.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", request.systemPrompt) })
                    })
                }
            }
            put("contents", buildJsonArray {
                request.messages.forEachIndexed { index, turn ->
                    val isLastUser = index == request.messages.lastIndex && turn.role == "user"
                    add(buildJsonObject {
                        put("role", if (turn.role == "assistant") "model" else "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", turn.content) })
                            if (isLastUser) {
                                request.images.forEach { image ->
                                    add(buildJsonObject {
                                        putJsonObject("inlineData") {
                                            put("mimeType", image.mimeType)
                                            put(
                                                "data",
                                                Base64.getEncoder().encodeToString(image.bytes),
                                            )
                                        }
                                    })
                                }
                            }
                        })
                    })
                }
            })
            putJsonObject("generationConfig") {
                put("maxOutputTokens", request.maxOutputTokens)
            }
        }
        val httpRequest = Request.Builder()
            .url("$base/models/${config.model}:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", apiKey)
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
                val source = response.body?.source()
                    ?: throw ProviderError(ErrorCategory.UNKNOWN, "Gemini returned an empty response.")
                val text = StringBuilder()
                var inputTokens: Long? = null
                var outputTokens: Long? = null
                while (!source.exhausted()) {
                    coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val delta = ProviderParsers.geminiDelta(payload)
                    if (delta.error != null) {
                        throw ProviderError(ErrorCategory.INVALID_REQUEST, delta.error)
                    }
                    delta.inputTokens?.let { inputTokens = it }
                    delta.outputTokens?.let { outputTokens = it }
                    delta.text?.takeIf { it.isNotEmpty() }?.let {
                        text.append(it)
                        onDelta(it)
                    }
                }
                if (text.isBlank()) {
                    throw ProviderError(ErrorCategory.UNKNOWN, "Gemini returned an empty response.")
                }
                AiResponse(
                    text = text.toString(),
                    providerLabel = config.label,
                    model = config.model,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    rateLimitInfo = Http.rateLimitSummary(response.headers),
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
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
