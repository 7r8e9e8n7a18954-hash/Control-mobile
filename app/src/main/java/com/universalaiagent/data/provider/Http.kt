package com.universalaiagent.data.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    fun errorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val error = root["error"]
            when (error) {
                is JsonObject -> {
                    val message = error["message"]?.jsonPrimitive?.content
                    val detail = error["detail"]?.jsonPrimitive?.content
                    message ?: detail
                }
                else -> root["message"]?.jsonPrimitive?.content
                    ?: root["detail"]?.jsonPrimitive?.content
            }
        }.getOrNull()?.take(1000)
    }

    fun rateLimitSummary(headers: okhttp3.Headers): String? {
        val wanted = listOf(
            "x-ratelimit-remaining-requests",
            "x-ratelimit-remaining-tokens",
            "anthropic-ratelimit-requests-remaining",
            "anthropic-ratelimit-tokens-remaining",
            "retry-after",
        )
        val parts = wanted.mapNotNull { name ->
            headers[name]?.let { "$name: $it" }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }
}
