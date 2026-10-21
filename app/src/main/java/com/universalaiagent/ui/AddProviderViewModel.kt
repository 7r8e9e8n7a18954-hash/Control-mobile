package com.universalaiagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalaiagent.AppContainer
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.Capability
import com.universalaiagent.data.model.ModelInfo
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.ProviderType
import com.universalaiagent.data.provider.EndpointValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AddProviderUiState(
    val configId: String? = null,
    val provider: ProviderType = ProviderType.OPENAI,
    val nickname: String = "",
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val models: List<ModelInfo> = emptyList(),
    val supportsVision: Boolean = false,
    val priority: Int = 0,
    val allowInsecureHttp: Boolean = false,
    val apiKeyVisible: Boolean = false,
    val loading: Boolean = false,
    val discovering: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val saved: Boolean = false,
    val hasStoredKey: Boolean = false,
) {
    val isEditing: Boolean get() = configId != null
}

class AddProviderViewModel(
    private val container: AppContainer,
    private val configId: String?,
) : ViewModel() {
    private val _ui = MutableStateFlow(AddProviderUiState(configId = configId))
    val ui: StateFlow<AddProviderUiState> = _ui

    init {
        if (configId != null) {
            viewModelScope.launch {
                val config = container.providers.byId(configId)
                if (config == null) {
                    _ui.update { it.copy(error = "Configuration not found.") }
                    return@launch
                }
                val hasKey = config.keyId.isNotBlank() && !container.providers.keyFor(config).isNullOrBlank()
                _ui.update {
                    it.copy(
                        provider = config.provider,
                        nickname = config.nickname,
                        baseUrl = config.baseUrl,
                        model = config.model,
                        models = config.discoveredModels,
                        supportsVision = config.supportsVision,
                        priority = config.priority,
                        allowInsecureHttp = config.allowInsecureHttp,
                        hasStoredKey = hasKey,
                    )
                }
            }
        }
    }

    fun setProvider(provider: ProviderType) {
        _ui.update {
            if (it.isEditing) {
                it.copy(provider = provider, error = null)
            } else {
                it.copy(
                    provider = provider,
                    baseUrl = "",
                    model = "",
                    models = emptyList(),
                    supportsVision = false,
                    error = null,
                    message = null,
                )
            }
        }
    }

    fun setNickname(value: String) = _ui.update { it.copy(nickname = value) }
    fun setApiKey(value: String) = _ui.update { it.copy(apiKey = value) }
    fun setBaseUrl(value: String) = _ui.update { it.copy(baseUrl = value) }
    fun setModel(value: String) = _ui.update {
        val match = it.models.firstOrNull { model -> model.id == value }
        it.copy(
            model = value,
            supportsVision = match?.capabilities?.contains(Capability.VISION) ?: it.supportsVision,
        )
    }

    fun setSupportsVision(enabled: Boolean) = _ui.update { it.copy(supportsVision = enabled) }
    fun setPriority(value: Int) = _ui.update { it.copy(priority = value.coerceIn(0, 999)) }
    fun setAllowInsecureHttp(enabled: Boolean) = _ui.update { it.copy(allowInsecureHttp = enabled) }
    fun toggleKeyVisibility() = _ui.update { it.copy(apiKeyVisible = !it.apiKeyVisible) }

    fun selectModel(model: ModelInfo) {
        _ui.update {
            it.copy(
                model = model.id,
                supportsVision = model.capabilities.contains(Capability.VISION),
                message = null,
            )
        }
    }

    fun discoverModels() {
        if (_ui.value.discovering) return
        viewModelScope.launch {
            val state = _ui.value
            val key = resolveKey() ?: run {
                _ui.update { it.copy(error = "Enter an API key before discovering models.") }
                return@launch
            }
            val baseError = validateBase(state)
            if (baseError != null) {
                _ui.update { it.copy(error = baseError) }
                return@launch
            }
            _ui.update { it.copy(discovering = true, error = null, message = null) }
            val temp = ApiConfig(
                id = state.configId ?: UUID.randomUUID().toString(),
                provider = state.provider,
                nickname = state.nickname.ifBlank { state.provider.displayName },
                model = state.model,
                baseUrl = state.baseUrl.trim(),
                allowInsecureHttp = state.allowInsecureHttp,
                supportsVision = state.supportsVision,
            )
            runCatching {
                container.registry.adapterFor(state.provider).discoverModels(temp, key)
            }.onSuccess { models ->
                val selected = models.firstOrNull { it.id == state.model } ?: models.firstOrNull()
                _ui.update {
                    it.copy(
                        discovering = false,
                        models = models,
                        model = selected?.id ?: it.model,
                        supportsVision = selected?.capabilities?.contains(Capability.VISION)
                            ?: it.supportsVision,
                        message = "Discovered ${models.size} models.",
                    )
                }
            }.onFailure { error ->
                _ui.update {
                    it.copy(
                        discovering = false,
                        error = "Model discovery failed: ${error.message}. You can still enter a model ID manually.",
                    )
                }
            }
        }
    }

    fun save() {
        if (_ui.value.loading) return
        viewModelScope.launch {
            val state = _ui.value
            val baseError = validateBase(state)
            if (baseError != null) {
                _ui.update { it.copy(error = baseError) }
                return@launch
            }
            if (state.model.isBlank()) {
                _ui.update { it.copy(error = "Select a model or enter a model ID.") }
                return@launch
            }
            val key = resolveKey()
            if (key.isNullOrBlank() && !state.hasStoredKey && state.provider != ProviderType.CUSTOM_OPENAI) {
                _ui.update { it.copy(error = "Enter an API key for ${state.provider.displayName}.") }
                return@launch
            }
            _ui.update { it.copy(loading = true, error = null, message = null) }
            val existing = state.configId?.let { container.providers.byId(it) }
            val config = ApiConfig(
                id = existing?.id ?: UUID.randomUUID().toString(),
                provider = state.provider,
                nickname = state.nickname.trim().ifBlank { state.provider.displayName },
                model = state.model.trim(),
                baseUrl = state.baseUrl.trim(),
                priority = state.priority,
                enabled = existing?.enabled ?: true,
                allowInsecureHttp = state.allowInsecureHttp,
                keyId = existing?.keyId.orEmpty(),
                keyLast4 = existing?.keyLast4.orEmpty(),
                supportsVision = state.supportsVision,
                discoveredModels = state.models,
                lastSuccessAt = existing?.lastSuccessAt,
                lastTestAt = existing?.lastTestAt,
                lastError = existing?.lastError,
                available = existing?.available ?: true,
                cooldownUntil = existing?.cooldownUntil ?: 0,
                requestCount = existing?.requestCount ?: 0,
                errorCount = existing?.errorCount ?: 0,
                inputTokens = existing?.inputTokens ?: 0,
                outputTokens = existing?.outputTokens ?: 0,
                reportedCost = existing?.reportedCost,
                rateLimitInfo = existing?.rateLimitInfo,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
            val newKey = when {
                state.apiKey.isNotBlank() -> state.apiKey
                existing == null -> state.apiKey
                else -> null
            }
            runCatching { container.providers.save(config, newKey) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            loading = false,
                            saved = true,
                            message = "Configuration saved.",
                            hasStoredKey = newKey != null || it.hasStoredKey,
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            loading = false,
                            error = "Could not save: ${error.message}",
                        )
                    }
                }
        }
    }

    fun clearMessages() = _ui.update { it.copy(error = null, message = null) }

    private suspend fun resolveKey(): String? {
        val state = _ui.value
        if (state.apiKey.isNotBlank()) return state.apiKey
        val id = state.configId ?: return null
        val config = container.providers.byId(id) ?: return null
        return container.providers.keyFor(config)
    }

    private fun validateBase(state: AddProviderUiState): String? {
        val base = state.baseUrl.trim()
        if (base.isBlank()) {
            if (state.provider == ProviderType.CUSTOM_OPENAI) {
                return "Custom providers require a base URL, for example https://host/v1."
            }
            return null
        }
        val normalized = if (state.provider == ProviderType.CUSTOM_OPENAI) base else base
        return EndpointValidator.validate(normalized, state.allowInsecureHttp)
    }
}
