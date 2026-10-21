package com.universalaiagent.data.provider

import com.universalaiagent.data.model.AiRequest
import com.universalaiagent.data.model.AiResponse
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ModelInfo
import com.universalaiagent.data.model.ProviderType
import okhttp3.Call

interface ProviderAdapter {
    val type: ProviderType

    suspend fun discoverModels(config: ApiConfig, apiKey: String): List<ModelInfo>

    suspend fun chat(
        config: ApiConfig,
        apiKey: String,
        request: AiRequest,
        onDelta: (String) -> Unit,
        onCall: (Call) -> Unit,
    ): AiResponse
}
