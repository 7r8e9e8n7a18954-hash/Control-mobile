package com.universalaiagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalaiagent.AppContainer
import com.universalaiagent.data.local.AppSettingsData
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ErrorCategory
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.ProviderType
import com.universalaiagent.data.model.UsageRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val configs: StateFlow<List<ApiConfig>> = container.providers.configs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val records: StateFlow<List<UsageRecord>> = container.usage.records

    val settings: StateFlow<AppSettingsData> = container.settings.data.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettingsData(),
    )

    private val _busyId = MutableStateFlow<String?>(null)
    val busyId: StateFlow<String?> = _busyId

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun test(id: String) {
        if (_busyId.value != null) return
        viewModelScope.launch {
            _busyId.value = id
            _message.value = null
            val config = container.providers.byId(id)
            if (config == null) {
                _message.value = "Configuration not found."
                _busyId.value = null
                return@launch
            }
            val key = container.providers.keyFor(config)
            if (key.isNullOrBlank() && config.provider != ProviderType.CUSTOM_OPENAI) {
                val error = ProviderError(ErrorCategory.AUTHENTICATION, "No API key is stored.")
                container.providers.recordTest(id, error)
                _message.value = "${config.label}: ${error.message}"
                _busyId.value = null
                return@launch
            }
            runCatching {
                container.registry.adapterFor(config.provider).discoverModels(config, key.orEmpty())
            }.onSuccess { models ->
                container.providers.setModels(id, models)
                container.providers.recordTest(id, null)
                _message.value = "${config.label}: connection OK, ${models.size} models."
            }.onFailure { error ->
                val providerError = error as? ProviderError
                    ?: ProviderError(ErrorCategory.UNKNOWN, error.message ?: "Test failed")
                container.providers.recordTest(id, providerError)
                _message.value = "${config.label}: ${providerError.category.name} — ${providerError.message}"
            }
            _busyId.value = null
        }
    }

    fun clearUsage() {
        viewModelScope.launch {
            container.usage.clear()
            _message.value = "Usage records cleared."
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
