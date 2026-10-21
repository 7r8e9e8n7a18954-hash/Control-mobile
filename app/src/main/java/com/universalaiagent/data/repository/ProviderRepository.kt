package com.universalaiagent.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.universalaiagent.data.model.AiResponse
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ModelInfo
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.ProviderType
import com.universalaiagent.data.security.SecureKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.providerConfigStore by preferencesDataStore(name = "provider_configs")

@Serializable
data class ExportedConfig(
    val provider: ProviderType,
    val nickname: String,
    val model: String,
    val baseUrl: String,
    val priority: Int,
    val enabled: Boolean,
    val supportsVision: Boolean,
    val discoveredModels: List<ModelInfo> = emptyList(),
)

@Serializable
data class ExportedConfigBundle(
    val version: Int = 1,
    val note: String = "API keys and key identifiers are excluded from this export.",
    val configs: List<ExportedConfig>,
)

class ProviderRepository(
    context: Context,
    private val secureStore: SecureKeyStore,
) {
    private val store = context.applicationContext.providerConfigStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()

    val configs: Flow<List<ApiConfig>> = store.data.map { prefs ->
        decode(prefs[CONFIGS])
    }

    suspend fun all(): List<ApiConfig> = configs.first()

    suspend fun byId(id: String): ApiConfig? = all().firstOrNull { it.id == id }

    suspend fun keyFor(config: ApiConfig): String? =
        if (config.keyId.isBlank()) null else secureStore.get(config.keyId)

    suspend fun save(config: ApiConfig, apiKey: String?): ApiConfig = mutex.withLock {
        val existing = all().firstOrNull { it.id == config.id }
        var keyId = existing?.keyId?.takeIf { it.isNotBlank() } ?: config.keyId
        var last4 = existing?.keyLast4 ?: config.keyLast4
        if (apiKey != null) {
            if (keyId.isBlank()) keyId = UUID.randomUUID().toString()
            secureStore.put(keyId, apiKey)
            last4 = apiKey.takeLast(4)
        }
        val saved = config.copy(
            keyId = keyId,
            keyLast4 = last4,
            available = if (apiKey != null) true else config.available,
            lastError = if (apiKey != null) null else config.lastError,
            cooldownUntil = if (apiKey != null) 0 else config.cooldownUntil,
            createdAt = existing?.createdAt ?: config.createdAt,
        )
        val updated = all().toMutableList()
        val index = updated.indexOfFirst { it.id == saved.id }
        if (index >= 0) updated[index] = saved else updated.add(saved)
        write(updated)
        saved
    }

    suspend fun update(id: String, transform: (ApiConfig) -> ApiConfig): ApiConfig? = mutex.withLock {
        val updated = all().toMutableList()
        val index = updated.indexOfFirst { it.id == id }
        if (index < 0) return@withLock null
        val saved = transform(updated[index])
        updated[index] = saved
        write(updated)
        saved
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = update(id) {
        it.copy(enabled = enabled)
    }

    suspend fun setModels(id: String, models: List<ModelInfo>) = update(id) {
        it.copy(discoveredModels = models)
    }

    suspend fun selectModel(id: String, model: String, supportsVision: Boolean) = update(id) {
        it.copy(model = model, supportsVision = supportsVision)
    }

    suspend fun delete(id: String) = mutex.withLock {
        val config = all().firstOrNull { it.id == id }
        val updated = all().filterNot { it.id == id }
        write(updated)
        if (config?.keyId?.isNotBlank() == true) secureStore.remove(config.keyId)
    }

    suspend fun recordSuccess(id: String, response: AiResponse) = update(id) {
        it.copy(
            lastSuccessAt = System.currentTimeMillis(),
            lastError = null,
            available = true,
            cooldownUntil = 0,
            requestCount = it.requestCount + 1,
            inputTokens = it.inputTokens + (response.inputTokens ?: 0),
            outputTokens = it.outputTokens + (response.outputTokens ?: 0),
            reportedCost = response.reportedCost ?: it.reportedCost,
            rateLimitInfo = response.rateLimitInfo ?: it.rateLimitInfo,
        )
    }

    suspend fun recordError(id: String, error: ProviderError, cooldownSeconds: Long?) = update(id) {
        it.copy(
            lastError = "${error.category.name}: ${error.message.take(500)}",
            available = !error.category.permanent,
            cooldownUntil = cooldownSeconds?.let { seconds ->
                System.currentTimeMillis() + seconds * 1000
            } ?: it.cooldownUntil,
            errorCount = it.errorCount + 1,
            requestCount = it.requestCount + 1,
        )
    }

    suspend fun recordTest(id: String, error: ProviderError?) = update(id) {
        it.copy(
            lastTestAt = System.currentTimeMillis(),
            lastError = error?.let { e -> "${e.category.name}: ${e.message.take(500)}" } ?: it.lastError,
            available = error == null || !error.category.permanent,
        )
    }

    suspend fun clearAll() = mutex.withLock {
        write(emptyList())
        secureStore.clear()
    }

    suspend fun exportSanitized(): String {
        val configs = all().sortedBy { it.priority }.map { config ->
            ExportedConfig(
                provider = config.provider,
                nickname = config.nickname,
                model = config.model,
                baseUrl = config.baseUrl,
                priority = config.priority,
                enabled = config.enabled,
                supportsVision = config.supportsVision,
                discoveredModels = config.discoveredModels,
            )
        }
        return json.encodeToString(ExportedConfigBundle(configs = configs))
    }

    suspend fun importSanitized(raw: String): Int = mutex.withLock {
        val bundle = json.decodeFromString<ExportedConfigBundle>(raw)
        val existing = all().toMutableList()
        bundle.configs.forEach { exported ->
            val config = ApiConfig(
                id = UUID.randomUUID().toString(),
                provider = exported.provider,
                nickname = exported.nickname.ifBlank { exported.provider.displayName },
                model = exported.model,
                baseUrl = exported.baseUrl,
                priority = exported.priority,
                enabled = false,
                supportsVision = exported.supportsVision,
                discoveredModels = exported.discoveredModels,
                lastError = "API key required after import",
            )
            existing.add(config)
        }
        write(existing)
        bundle.configs.size
    }

    private suspend fun write(configs: List<ApiConfig>) {
        store.edit { prefs ->
            prefs[CONFIGS] = json.encodeToString(configs.sortedBy { it.priority })
        }
    }

    private fun decode(raw: String?): List<ApiConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ApiConfig>>(raw)
        }.getOrDefault(emptyList())
    }

    private companion object {
        val CONFIGS = stringPreferencesKey("configs_json")
    }
}
