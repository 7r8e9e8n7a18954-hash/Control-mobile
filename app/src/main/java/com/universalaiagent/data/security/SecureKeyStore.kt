package com.universalaiagent.data.security

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SecureKeyStore(context: Context) {
    private val vaultFile = File(context.filesDir, "secure/api_keys.vault")
    private val crypto = KeystoreCrypto()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun put(keyId: String, apiKey: String) {
        if (keyId.isBlank()) return
        mutex.withLock {
            val entries = readLocked().toMutableMap()
            entries[keyId] = apiKey
            writeLocked(entries)
        }
    }

    suspend fun get(keyId: String): String? = mutex.withLock {
        readLocked()[keyId]
    }

    suspend fun remove(keyId: String) {
        mutex.withLock {
            val entries = readLocked().toMutableMap()
            if (entries.remove(keyId) != null) writeLocked(entries)
        }
    }

    suspend fun clear() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                if (vaultFile.exists()) vaultFile.delete()
            }
        }
    }

    suspend fun hasAnyKeys(): Boolean = mutex.withLock { readLocked().isNotEmpty() }

    private suspend fun readLocked(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!vaultFile.exists()) return@withContext emptyMap()
        runCatching {
            val decoded = crypto.decrypt(vaultFile.readText())
            json.decodeFromString<Map<String, String>>(decoded)
        }.getOrElse { emptyMap() }
    }

    private suspend fun writeLocked(entries: Map<String, String>) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) {
            if (vaultFile.exists()) vaultFile.delete()
            return@withContext
        }
        vaultFile.parentFile?.mkdirs()
        val encrypted = crypto.encrypt(json.encodeToString(entries))
        val temp = File(vaultFile.parentFile, "${vaultFile.name}.tmp")
        temp.writeText(encrypted)
        if (!temp.renameTo(vaultFile)) {
            vaultFile.writeText(encrypted)
            temp.delete()
        }
    }
}
