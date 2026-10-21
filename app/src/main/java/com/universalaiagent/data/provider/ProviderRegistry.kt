package com.universalaiagent.data.provider

import com.universalaiagent.data.model.ProviderType

class ProviderRegistry {
    private val adapters: Map<ProviderType, ProviderAdapter> = listOf(
        OpenAiAdapter(ProviderType.OPENAI),
        OpenAiAdapter(ProviderType.GROQ),
        OpenAiAdapter(ProviderType.OPENROUTER),
        OpenAiAdapter(ProviderType.CUSTOM_OPENAI),
        GeminiAdapter(),
        AnthropicAdapter(),
    ).associateBy { it.type }

    fun adapterFor(type: ProviderType): ProviderAdapter =
        adapters[type] ?: error("No adapter registered for $type")
}
