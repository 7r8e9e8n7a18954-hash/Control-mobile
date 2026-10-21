package com.universalaiagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalaiagent.AppContainer
import com.universalaiagent.data.local.AppSettingsData
import com.universalaiagent.data.local.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.buildJsonObject

class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val settings: StateFlow<AppSettingsData> = container.settings.data.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettingsData(),
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.settings.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { container.settings.setDynamicColor(enabled) }
    fun setHistoryEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setHistoryEnabled(enabled) }
    fun setCloudImages(enabled: Boolean) = viewModelScope.launch { container.settings.setCloudImageProcessing(enabled) }
    fun setConfirmSensitive(enabled: Boolean) = viewModelScope.launch {
        container.settings.setConfirmSensitiveActions(enabled)
    }

    fun setTtsEnabled(enabled: Boolean) = viewModelScope.launch { container.settings.setTtsEnabled(enabled) }
    fun setTtsRate(rate: Float) = viewModelScope.launch { container.settings.setTtsRate(rate) }
    fun setSessionLimit(limit: Int) = viewModelScope.launch { container.settings.setSessionRequestLimit(limit) }
    fun setWarnBeforeRequests(enabled: Boolean) = viewModelScope.launch {
        container.settings.setWarnBeforeRequests(enabled)
    }

    fun buildExport(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val bundle = buildJsonObject {
                put("app", "Universal AI Agent")
                put("exportVersion", 1)
                put("exportedAt", System.currentTimeMillis())
                putJsonObject("settings") {
                    val value = settings.value
                    put("theme", value.themeMode.name)
                    put("historyEnabled", value.historyEnabled)
                    put("cloudImageProcessing", value.cloudImageProcessing)
                    put("confirmSensitiveActions", value.confirmSensitiveActions)
                    put("ttsEnabled", value.ttsEnabled)
                    put("ttsRate", value.ttsRate)
                    put("sessionRequestLimit", value.sessionRequestLimit)
                }
                put(
                    "providers",
                    Json.parseToJsonElement(container.providers.exportSanitized()),
                )
            }
            onReady(bundle.toString())
        }
    }

    fun importConfig(raw: String) {
        viewModelScope.launch {
            runCatching {
                val root = Json.parseToJsonElement(raw).jsonObject
                val providerElement = root["providers"] ?: root
                val count = container.providers.importSanitized(providerElement.toString())
                root["settings"]?.let { settingsElement ->
                    val settingsObject = settingsElement.jsonObject
                    settingsObject["theme"]?.jsonPrimitive?.content?.let { theme ->
                        runCatching { ThemeMode.valueOf(theme) }.getOrNull()?.let {
                            container.settings.setThemeMode(it)
                        }
                    }
                    settingsObject["historyEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()?.let {
                        container.settings.setHistoryEnabled(it)
                    }
                    settingsObject["cloudImageProcessing"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()?.let {
                        container.settings.setCloudImageProcessing(it)
                    }
                    settingsObject["confirmSensitiveActions"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()?.let {
                        container.settings.setConfirmSensitiveActions(it)
                    }
                    settingsObject["ttsEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()?.let {
                        container.settings.setTtsEnabled(it)
                    }
                    settingsObject["ttsRate"]?.jsonPrimitive?.content?.toFloatOrNull()?.let {
                        container.settings.setTtsRate(it)
                    }
                    settingsObject["sessionRequestLimit"]?.jsonPrimitive?.content?.toIntOrNull()?.let {
                        container.settings.setSessionRequestLimit(it)
                    }
                }
                "Imported $count provider configuration(s). API keys must be re-entered."
            }.onSuccess { _message.value = it }
                .onFailure { _message.value = "Import failed: ${it.message}" }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.conversations.clearAll()
            container.attachments.clearAll()
            container.usage.clear()
            _message.value = "Conversation history, attachments, and usage records cleared."
        }
    }

    fun clearUsage() {
        viewModelScope.launch {
            container.usage.clear()
            _message.value = "Usage records cleared."
        }
    }

    fun deleteAllKeys() {
        viewModelScope.launch {
            container.providers.clearAll()
            _message.value = "All API configurations and stored keys deleted."
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            container.settings.resetAll()
            _message.value = "Settings reset to defaults."
        }
    }

    fun notifyExported() {
        _message.value = "Sanitized configuration copied to the clipboard. API keys were excluded."
    }

    fun clearMessage() {
        _message.value = null
    }
}
