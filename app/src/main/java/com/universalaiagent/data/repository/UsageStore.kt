package com.universalaiagent.data.repository

import android.content.Context
import com.universalaiagent.data.model.ProviderType
import com.universalaiagent.data.model.UsageRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class UsageStore(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "usage_history.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _records = MutableStateFlow<List<UsageRecord>>(emptyList())
    val records: StateFlow<List<UsageRecord>> = _records.asStateFlow()

    init {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                if (!file.exists()) return@withContext emptyList()
                runCatching {
                    json.decodeFromString<List<UsageRecord>>(file.readText())
                }.getOrDefault(emptyList())
            }
            _records.value = loaded.sortedByDescending { it.timestamp }
        }
    }

    suspend fun add(
        provider: ProviderType,
        configLabel: String,
        model: String,
        success: Boolean,
        inputTokens: Long?,
        outputTokens: Long?,
        reportedCost: Double?,
        errorCategory: String? = null,
        errorMessage: String? = null,
    ) {
        val record = UsageRecord(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            provider = provider,
            configLabel = configLabel,
            model = model,
            success = success,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            reportedCost = reportedCost,
            errorCategory = errorCategory,
            errorMessage = errorMessage?.take(300),
        )
        val updated = (listOf(record) + _records.value).take(2000)
        _records.value = updated
        mutex.withLock {
            withContext(Dispatchers.IO) {
                file.writeText(json.encodeToString(updated))
            }
        }
    }

    suspend fun clear() {
        _records.value = emptyList()
        withContext(Dispatchers.IO) {
            if (file.exists()) file.delete()
        }
    }
}
