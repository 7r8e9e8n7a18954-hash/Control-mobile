package com.universalaiagent.data.model

import kotlinx.serialization.Serializable

enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
) {
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1"),
    GROQ("Groq", "https://api.groq.com/openai/v1"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    CUSTOM_OPENAI("Custom (OpenAI-compatible)", ""),
    ;

    val isOpenAiCompatible: Boolean
        get() = this != GEMINI && this != ANTHROPIC
}

enum class Capability {
    TEXT,
    VISION,
    AUDIO_INPUT,
    AUDIO_OUTPUT,
    TRANSCRIPTION,
}

enum class TaskType(val label: String, val description: String) {
    GENERAL_CHAT("General chat", "Everyday questions and conversation"),
    CODING("Coding", "Code generation and debugging"),
    IMAGE_UNDERSTANDING("Image understanding", "Analyze images you attach"),
    TRANSCRIPTION("Voice transcription", "Speech-to-text"),
    AUDIO_INPUT("Audio input", "Provider audio understanding"),
    AUDIO_OUTPUT("Audio output", "Spoken responses"),
    SCREEN_ANALYSIS("Screen analysis", "Understand a captured screen"),
    ACTION_PLANNING("Agent tasks", "Plan and execute on-device UI actions"),
    ;

    val requiresVision: Boolean
        get() = this == IMAGE_UNDERSTANDING || this == SCREEN_ANALYSIS
}

@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String = id,
    val provider: ProviderType,
    val capabilities: Set<Capability> = setOf(Capability.TEXT),
    val capabilitySource: String = "Not reported by provider",
    val contextWindow: Long? = null,
    val maxOutputTokens: Long? = null,
    val description: String? = null,
)

@Serializable
data class ApiConfig(
    val id: String,
    val provider: ProviderType,
    val nickname: String,
    val model: String = "",
    val baseUrl: String = "",
    val priority: Int = 0,
    val enabled: Boolean = true,
    val allowInsecureHttp: Boolean = false,
    val keyId: String = "",
    val keyLast4: String = "",
    val supportsVision: Boolean = false,
    val discoveredModels: List<ModelInfo> = emptyList(),
    val lastSuccessAt: Long? = null,
    val lastTestAt: Long? = null,
    val lastError: String? = null,
    val available: Boolean = true,
    val cooldownUntil: Long = 0,
    val requestCount: Long = 0,
    val errorCount: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val reportedCost: Double? = null,
    val rateLimitInfo: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val label: String
        get() = if (nickname.isBlank()) provider.displayName else nickname

    val effectiveBaseUrl: String
        get() = baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')

    fun isCoolingDown(now: Long = System.currentTimeMillis()): Boolean =
        enabled && cooldownUntil > now

    fun maskedKey(): String =
        if (keyLast4.isBlank()) "No API key stored" else "•••• •••• $keyLast4"
}

@Serializable
enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val providerLabel: String? = null,
    val model: String? = null,
    val attachmentPaths: List<String> = emptyList(),
    val isError: Boolean = false,
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
)

@Serializable
data class UsageRecord(
    val id: String,
    val timestamp: Long,
    val provider: ProviderType,
    val configLabel: String,
    val model: String,
    val success: Boolean,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val reportedCost: Double? = null,
    val errorCategory: String? = null,
    val errorMessage: String? = null,
)

data class ChatTurn(
    val role: String,
    val content: String,
)

data class ImageAttachment(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg",
)

data class AiRequest(
    val systemPrompt: String,
    val messages: List<ChatTurn>,
    val images: List<ImageAttachment> = emptyList(),
    val maxOutputTokens: Int = 2048,
)

data class AiResponse(
    val text: String,
    val providerLabel: String,
    val model: String,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val reportedCost: Double? = null,
    val rateLimitInfo: String? = null,
)

enum class ErrorCategory(val permanent: Boolean) {
    AUTHENTICATION(true),
    PERMISSION(true),
    RATE_LIMIT(false),
    QUOTA_EXHAUSTED(false),
    SERVER_ERROR(false),
    NETWORK(false),
    TIMEOUT(false),
    INVALID_REQUEST(true),
    MODEL_UNAVAILABLE(false),
    CONTEXT_LENGTH(true),
    UNSUPPORTED_CAPABILITY(true),
    CANCELLED(false),
    UNKNOWN(false),
}

class ProviderError(
    val category: ErrorCategory,
    override val message: String,
    val httpCode: Int? = null,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

data class RoutingNotice(
    val message: String,
    val isFallback: Boolean = false,
)

data class AppEntry(
    val label: String,
    val packageName: String,
)
