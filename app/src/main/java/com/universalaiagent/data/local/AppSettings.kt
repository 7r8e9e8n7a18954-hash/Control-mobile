package com.universalaiagent.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.universalaiagent.data.model.TaskType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.appSettingsStore by preferencesDataStore(name = "app_settings")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettingsData(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val historyEnabled: Boolean = true,
    val cloudImageProcessing: Boolean = true,
    val confirmSensitiveActions: Boolean = true,
    val ttsEnabled: Boolean = true,
    val ttsRate: Float = 1.0f,
    val onboardingComplete: Boolean = false,
    val manualMode: Boolean = false,
    val manualConfigId: String? = null,
    val manualAllowFallback: Boolean = false,
    val sessionRequestLimit: Int = 0,
    val warnBeforeRequests: Boolean = false,
    val routes: Map<String, List<String>> = emptyMap(),
) {
    fun routeFor(task: TaskType): List<String> = routes[task.name].orEmpty()
}

class AppSettings(context: Context) {
    private val dataStore = context.applicationContext.appSettingsStore
    private val json = Json { ignoreUnknownKeys = true }

    val data: Flow<AppSettingsData> = dataStore.data.map { prefs ->
        AppSettingsData(
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[THEME] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs[DYNAMIC_COLOR] ?: false,
            historyEnabled = prefs[HISTORY] ?: true,
            cloudImageProcessing = prefs[CLOUD_IMAGES] ?: true,
            confirmSensitiveActions = prefs[CONFIRM_SENSITIVE] ?: true,
            ttsEnabled = prefs[TTS] ?: true,
            ttsRate = (prefs[TTS_RATE] ?: 100).coerceIn(50, 200) / 100f,
            onboardingComplete = prefs[ONBOARDING] ?: false,
            manualMode = prefs[MANUAL_MODE] ?: false,
            manualConfigId = prefs[MANUAL_CONFIG],
            manualAllowFallback = prefs[MANUAL_FALLBACK] ?: false,
            sessionRequestLimit = prefs[SESSION_LIMIT] ?: 0,
            warnBeforeRequests = prefs[WARN_REQUESTS] ?: false,
            routes = parseRoutes(prefs[ROUTES]),
        )
    }

    suspend fun snapshot(): AppSettingsData = data.first()

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[DYNAMIC_COLOR] = enabled }
    suspend fun setHistoryEnabled(enabled: Boolean) = edit { it[HISTORY] = enabled }
    suspend fun setCloudImageProcessing(enabled: Boolean) = edit { it[CLOUD_IMAGES] = enabled }
    suspend fun setConfirmSensitiveActions(enabled: Boolean) = edit { it[CONFIRM_SENSITIVE] = enabled }
    suspend fun setTtsEnabled(enabled: Boolean) = edit { it[TTS] = enabled }

    suspend fun setTtsRate(rate: Float) = edit {
        it[TTS_RATE] = (rate.coerceIn(0.5f, 2.0f) * 100).toInt()
    }

    suspend fun setOnboardingComplete(complete: Boolean) = edit { it[ONBOARDING] = complete }
    suspend fun setManualMode(manual: Boolean) = edit { it[MANUAL_MODE] = manual }
    suspend fun setManualConfigId(id: String?) = edit {
        if (id == null) it.remove(MANUAL_CONFIG) else it[MANUAL_CONFIG] = id
    }

    suspend fun setManualAllowFallback(enabled: Boolean) = edit { it[MANUAL_FALLBACK] = enabled }
    suspend fun setSessionRequestLimit(limit: Int) = edit { it[SESSION_LIMIT] = limit.coerceAtLeast(0) }
    suspend fun setWarnBeforeRequests(enabled: Boolean) = edit { it[WARN_REQUESTS] = enabled }

    suspend fun setRoute(task: TaskType, ids: List<String>) {
        val current = snapshot().routes.toMutableMap()
        current[task.name] = ids
        edit { prefs -> prefs[ROUTES] = json.encodeToString(current) }
    }

    suspend fun resetRoutes() = edit { it.remove(ROUTES) }

    suspend fun resetAll() {
        dataStore.edit { it.clear() }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun parseRoutes(raw: String?): Map<String, List<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, List<String>>>(raw)
        }.getOrDefault(emptyMap())
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HISTORY = booleanPreferencesKey("history_enabled")
        val CLOUD_IMAGES = booleanPreferencesKey("cloud_images")
        val CONFIRM_SENSITIVE = booleanPreferencesKey("confirm_sensitive")
        val TTS = booleanPreferencesKey("tts_enabled")
        val TTS_RATE = intPreferencesKey("tts_rate")
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val MANUAL_MODE = booleanPreferencesKey("manual_mode")
        val MANUAL_CONFIG = stringPreferencesKey("manual_config")
        val MANUAL_FALLBACK = booleanPreferencesKey("manual_fallback")
        val SESSION_LIMIT = intPreferencesKey("session_limit")
        val WARN_REQUESTS = booleanPreferencesKey("warn_requests")
        val ROUTES = stringPreferencesKey("routes_json")
    }
}
