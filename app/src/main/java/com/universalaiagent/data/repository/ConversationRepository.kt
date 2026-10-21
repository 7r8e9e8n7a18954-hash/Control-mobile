package com.universalaiagent.data.repository

import android.content.Context
import com.universalaiagent.data.local.AppSettings
import com.universalaiagent.data.model.ChatMessage
import com.universalaiagent.data.model.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ConversationRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val settings: AppSettings,
) {
    private val file = File(context.filesDir, "conversations.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                if (!file.exists()) return@withContext emptyList()
                runCatching {
                    json.decodeFromString<List<Conversation>>(file.readText())
                }.getOrDefault(emptyList())
            }
            _conversations.value = loaded.sortedByDescending { it.updatedAt }
            _loaded.value = true
        }
    }

    suspend fun get(id: String): Conversation? = _conversations.value.firstOrNull { it.id == id }

    suspend fun create(title: String = "New chat"): Conversation {
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
        )
        commit(_conversations.value + conversation)
        return conversation
    }

    suspend fun appendMessage(conversationId: String, message: ChatMessage): Conversation? {
        val current = get(conversationId) ?: return null
        val title = if (current.title == "New chat" && message.role == com.universalaiagent.data.model.ChatRole.USER) {
            message.content.trim().take(60).ifBlank { "New chat" }
        } else {
            current.title
        }
        val updated = current.copy(
            title = title,
            updatedAt = System.currentTimeMillis(),
            messages = current.messages + message,
        )
        commit(_conversations.value.map { if (it.id == current.id) updated else it })
        return updated
    }

    suspend fun replaceConversation(conversation: Conversation) {
        commit(
            _conversations.value.map {
                if (it.id == conversation.id) conversation.copy(updatedAt = System.currentTimeMillis()) else it
            },
        )
    }

    suspend fun rename(conversationId: String, title: String) {
        commit(
            _conversations.value.map {
                if (it.id == conversationId) it.copy(title = title.trim().take(80).ifBlank { "Untitled" }) else it
            },
        )
    }

    suspend fun delete(conversationId: String) {
        commit(_conversations.value.filterNot { it.id == conversationId })
    }

    suspend fun clearAll() {
        commit(emptyList())
        withContext(Dispatchers.IO) {
            if (file.exists()) file.delete()
        }
    }

    private suspend fun commit(list: List<Conversation>) {
        val sorted = list.sortedByDescending { it.updatedAt }.take(MAX_CONVERSATIONS)
        _conversations.value = sorted
        val enabled = runCatching { settings.data.first().historyEnabled }.getOrDefault(true)
        if (!enabled) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(json.encodeToString(sorted))
                if (!temp.renameTo(file)) {
                    file.writeText(temp.readText())
                    temp.delete()
                }
            }
        }
    }

    private companion object {
        const val MAX_CONVERSATIONS = 100
    }
}
