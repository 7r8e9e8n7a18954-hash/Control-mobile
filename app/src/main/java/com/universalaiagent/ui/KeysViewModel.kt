package com.universalaiagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalaiagent.AppContainer
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ProviderError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KeysViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val configs: StateFlow<List<ApiConfig>> = container.providers.configs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    private val _busyId = MutableStateFlow<String?>(null)
    val busyId: StateFlow<String?> = _busyId

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { container.providers.setEnabled(id, enabled) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            container.providers.delete(id)
            _status.value = "Configuration deleted."
        }
    }

    fun test(id: String) {
        if (_busyId.value != null) return
        viewModelScope.launch {
            _busyId.value = id
            _status.value = null
            val config = container.providers.byId(id)
            if (config == null) {
                _status.value = "Configuration not found."
                _busyId.value = null
                return@launch
            }
            val key = container.providers.keyFor(config)
            if (key.isNullOrBlank() && config.provider != com.universalaiagent.data.model.ProviderType.CUSTOM_OPENAI) {
                val error = ProviderError(
                    com.universalaiagent.data.model.ErrorCategory.AUTHENTICATION,
                    "No API key stored. Edit this configuration and add a key.",
                )
                container.providers.recordTest(id, error)
                _status.value = error.message
                _busyId.value = null
                return@launch
            }
            runCatching {
                container.registry.adapterFor(config.provider)
                    .discoverModels(config, key.orEmpty())
            }.onSuccess { models ->
                container.providers.setModels(id, models)
                container.providers.recordTest(id, null)
                _status.value = "Connection OK — ${models.size} models available."
            }.onFailure { error ->
                val providerError = error as? ProviderError
                    ?: ProviderError(
                        com.universalaiagent.data.model.ErrorCategory.UNKNOWN,
                        error.message ?: "Connection test failed.",
                    )
                container.providers.recordTest(id, providerError)
                _status.value = "${providerError.category.name}: ${providerError.message}"
            }
            _busyId.value = null
        }
    }

    fun refreshModels(id: String, onModels: (List<com.universalaiagent.data.model.ModelInfo>) -> Unit = {}) {
        if (_busyId.value != null) return
        viewModelScope.launch {
            _busyId.value = id
            val config = container.providers.byId(id)
            val key = config?.let { container.providers.keyFor(it) }
            if (config == null) {
                _status.value = "Configuration not found."
                _busyId.value = null
                return@launch
            }
            if (key.isNullOrBlank() && config.provider != com.universalaiagent.data.model.ProviderType.CUSTOM_OPENAI) {
                _status.value = "Add an API key before refreshing models."
                _busyId.value = null
                return@launch
            }
            runCatching {
                container.registry.adapterFor(config.provider).discoverModels(config, key.orEmpty())
            }.onSuccess { models ->
                container.providers.setModels(id, models)
                onModels(models)
                _status.value = "Loaded ${models.size} models."
            }.onFailure { error ->
                _status.value = "Model discovery failed: ${error.message}"
            }
            _busyId.value = null
        }
    }

    fun selectModel(id: String, model: com.universalaiagent.data.model.ModelInfo) {
        viewModelScope.launch {
            container.providers.selectModel(
                id = id,
                model = model.id,
                supportsVision = model.capabilities.contains(com.universalaiagent.data.model.Capability.VISION),
            )
            _status.value = "Model set to ${model.displayName}."
        }
    }

    fun clearStatus() {
        _status.value = null
    }
}
