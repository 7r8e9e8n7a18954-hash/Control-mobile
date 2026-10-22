package com.universalaiagent.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalaiagent.AppContainer
import com.universalaiagent.data.model.AiRequest
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ChatMessage
import com.universalaiagent.data.model.ChatRole
import com.universalaiagent.data.model.ChatTurn
import com.universalaiagent.data.model.Conversation
import com.universalaiagent.data.model.ImageAttachment
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.TaskType
import com.universalaiagent.voice.VoiceManager
import com.universalaiagent.voice.VoiceManagerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ChatUiState(
    val input: String = "",
    val attachments: List<String> = emptyList(),
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val status: String? = null,
    val fallbackNotice: String? = null,
    val error: String? = null,
    val activeProvider: String? = null,
    val activeModel: String? = null,
    val manualMode: Boolean = false,
    val manualConfigId: String? = null,
    val manualAllowFallback: Boolean = false,
    val historyEnabled: Boolean = true,
    val sessionRequests: Int = 0,
    val sessionLimit: Int = 0,
    val warnBeforeRequests: Boolean = false,
    val awaitingCostWarning: Boolean = false,
    val agentMode: Boolean = false,
    val ttsEnabled: Boolean = true,
)

class ChatViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val conversations: StateFlow<List<Conversation>> =
        container.conversations.conversations

    val configs: StateFlow<List<ApiConfig>> = container.providers.configs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    val selectedId = MutableStateFlow<String?>(null)

    val selectedConversation: StateFlow<Conversation?> = combine(
        conversations,
        selectedId,
    ) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui

    private val voiceManager = VoiceManager(container.appContext)
    val voiceState: StateFlow<VoiceManagerState> = voiceManager.state

    private var generationJob: Job? = null
    private var initialConversationCreated = false
    private var warnedThisSession = false
    private var agentConversationId: String? = null
    private var agentInstructionJob: Job? = null
    private val reportedAgentTasks = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            voiceManager.finalResults.collect { text ->
                _ui.update { it.copy(input = text) }
            }
        }
        viewModelScope.launch {
            combine(
                container.conversations.loaded,
                container.conversations.conversations,
            ) { loaded, list -> loaded to list }
                .collect { (loaded, list) ->
                    if (!loaded) return@collect
                    if (list.isEmpty() && !initialConversationCreated) {
                        initialConversationCreated = true
                        val created = container.conversations.create()
                        selectedId.value = created.id
                    }
                    if (selectedId.value == null || list.none { it.id == selectedId.value }) {
                        selectedId.value = list.firstOrNull()?.id
                    }
                }
        }
        viewModelScope.launch {
            container.settings.data.collect { settings ->
                _ui.update {
                    it.copy(
                        manualMode = settings.manualMode,
                        manualConfigId = settings.manualConfigId,
                        manualAllowFallback = settings.manualAllowFallback,
                        historyEnabled = settings.historyEnabled,
                        sessionLimit = settings.sessionRequestLimit,
                        warnBeforeRequests = settings.warnBeforeRequests,
                        ttsEnabled = settings.ttsEnabled,
                    )
                }
            }
        }
    }

    fun selectConversation(id: String) {
        selectedId.value = id
    }

    fun newConversation() {
        if (_ui.value.isGenerating) return
        viewModelScope.launch {
            val created = container.conversations.create()
            selectedId.value = created.id
            _ui.update { it.copy(error = null, streamingText = "", fallbackNotice = null) }
        }
    }

    fun updateInput(value: String) {
        _ui.update { it.copy(input = value) }
    }

    fun setAgentMode(enabled: Boolean) {
        _ui.update { it.copy(agentMode = enabled) }
    }

    fun toggleAgentMode() {
        _ui.update { it.copy(agentMode = !it.agentMode) }
    }

    fun startVoiceInput() {
        voiceManager.startListening()
    }

    fun stopVoiceInput() {
        voiceManager.stopListening()
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settings.setTtsEnabled(enabled) }
    }

    fun takeAgentInstruction(): String? {
        if (_ui.value.isGenerating) return null
        val state = _ui.value
        val text = state.input.trim()
        if (text.isBlank()) return null
        val current = selectedConversation.value
        if (current == null) {
            _ui.update { it.copy(error = "No conversation is selected.") }
            return null
        }
        agentConversationId = current.id
        _ui.update {
            it.copy(
                input = "",
                attachments = emptyList(),
                error = null,
                fallbackNotice = null,
                status = null,
            )
        }
        agentInstructionJob = viewModelScope.launch {
            container.conversations.appendMessage(
                current.id,
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.USER,
                    content = text,
                ),
            )
        }
        return text
    }

    fun appendAgentOutcome(
        taskId: String,
        success: Boolean?,
        report: String?,
    ) {
        if (!reportedAgentTasks.add(taskId)) return
        val conversationId = agentConversationId ?: selectedConversation.value?.id ?: return
        val heading = if (success == true) "Agent finished" else "Agent stopped"
        val body = report?.takeIf { it.isNotBlank() } ?: "No report was produced."
        viewModelScope.launch {
            agentInstructionJob?.join()
            agentInstructionJob = null
            container.conversations.appendMessage(
                conversationId,
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    content = "$heading: $body",
                    providerLabel = "Agent",
                    model = "Accessibility",
                ),
            )
        }
        agentConversationId = null
    }

    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            runCatching { container.attachments.saveImage(uri) }
                .onSuccess { path ->
                    _ui.update { it.copy(attachments = it.attachments + path) }
                }
                .onFailure { error ->
                    _ui.update { it.copy(error = "Could not attach image: ${error.message}") }
                }
        }
    }

    fun removeAttachment(path: String) {
        viewModelScope.launch {
            container.attachments.delete(listOf(path))
            _ui.update { it.copy(attachments = it.attachments - path) }
        }
    }

    fun send() {
        if (_ui.value.isGenerating) return
        val state = _ui.value
        if (state.warnBeforeRequests && !warnedThisSession) {
            _ui.update { it.copy(awaitingCostWarning = true) }
            return
        }
        sendInternal()
    }

    fun confirmSendAfterWarning() {
        warnedThisSession = true
        _ui.update { it.copy(awaitingCostWarning = false) }
        sendInternal()
    }

    fun dismissCostWarning() {
        _ui.update { it.copy(awaitingCostWarning = false) }
    }

    private fun sendInternal() {
        val state = _ui.value
        val text = state.input.trim()
        if (text.isBlank() && state.attachments.isEmpty()) return
        val current = selectedConversation.value
        if (current == null) {
            _ui.update { it.copy(error = "No conversation is selected.") }
            return
        }
        if (state.sessionLimit > 0 && state.sessionRequests >= state.sessionLimit) {
            _ui.update {
                it.copy(error = "Session request limit reached (${state.sessionLimit}). Raise it in Settings.")
            }
            return
        }
        _ui.update {
            it.copy(
                input = "",
                attachments = emptyList(),
                sessionRequests = it.sessionRequests + 1,
                error = null,
                fallbackNotice = null,
            )
        }
        generate(
            conversation = current,
            text = text,
            attachmentPaths = state.attachments,
            addUserMessage = true,
        )
    }

    fun regenerate() {
        if (_ui.value.isGenerating) return
        val current = selectedConversation.value ?: return
        val lastUserIndex = current.messages.indexOfLast { it.role == ChatRole.USER }
        if (lastUserIndex < 0) return
        val trimmed = current.copy(messages = current.messages.take(lastUserIndex + 1))
        val userMessage = trimmed.messages.last()
        viewModelScope.launch {
            container.conversations.replaceConversation(trimmed)
            _ui.update {
                it.copy(error = null, fallbackNotice = null, streamingText = "", status = "Regenerating…")
            }
            generate(
                conversation = trimmed,
                text = userMessage.content,
                attachmentPaths = userMessage.attachmentPaths,
                addUserMessage = false,
            )
        }
    }

    private fun generate(
        conversation: Conversation,
        text: String,
        attachmentPaths: List<String>,
        addUserMessage: Boolean,
    ) {
        generationJob?.cancel()
        _ui.update {
            it.copy(
                isGenerating = true,
                streamingText = "",
                status = "Preparing request…",
                error = null,
                fallbackNotice = null,
                activeProvider = null,
                activeModel = null,
            )
        }
        generationJob = viewModelScope.launch {
            var base = conversation
            if (addUserMessage) {
                val message = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.USER,
                    content = text.ifBlank { "[Image attachment]" },
                    attachmentPaths = attachmentPaths,
                )
                base = container.conversations.appendMessage(base.id, message) ?: base
            }
            val images = loadImages(attachmentPaths)
            val task = if (images.isNotEmpty()) TaskType.IMAGE_UNDERSTANDING else TaskType.GENERAL_CHAT
            val turns = base.messages
                .filterNot { it.isError }
                .takeLast(30)
                .map { message ->
                    ChatTurn(
                        role = when (message.role) {
                            ChatRole.USER -> "user"
                            ChatRole.ASSISTANT -> "assistant"
                            ChatRole.SYSTEM -> "system"
                        },
                        content = message.content,
                    )
                }
            if (turns.none { it.role == "user" }) {
                _ui.update { it.copy(isGenerating = false, status = null) }
                return@launch
            }
            val request = AiRequest(
                systemPrompt = CHAT_SYSTEM_PROMPT,
                messages = turns,
                images = images,
            )

            try {
                val response = container.router.execute(
                    task = task,
                    request = request,
                    onDelta = { delta ->
                        _ui.update {
                            it.copy(
                                streamingText = it.streamingText + delta,
                                status = null,
                            )
                        }
                    },
                    onNotice = { notice ->
                        _ui.update {
                            it.copy(
                                status = notice.message,
                                fallbackNotice = if (notice.isFallback) notice.message else it.fallbackNotice,
                            )
                        }
                    },
                )
                val assistant = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    content = response.text,
                    providerLabel = response.providerLabel,
                    model = response.model,
                )
                container.conversations.appendMessage(base.id, assistant)
                _ui.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        status = null,
                        activeProvider = response.providerLabel,
                        activeModel = response.model,
                    )
                }
            } catch (cancelled: CancellationException) {
                val partial = _ui.value.streamingText
                withContext(NonCancellable) {
                    if (partial.isNotBlank()) {
                        container.conversations.appendMessage(
                            base.id,
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = ChatRole.ASSISTANT,
                                content = partial,
                                providerLabel = _ui.value.activeProvider,
                                model = _ui.value.activeModel,
                            ),
                        )
                    }
                }
                _ui.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        status = "Generation stopped",
                    )
                }
            } catch (error: ProviderError) {
                val detail = error.message
                val assistant = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.ASSISTANT,
                    content = "Request failed: $detail",
                    isError = true,
                )
                withContext(NonCancellable) {
                    container.conversations.appendMessage(base.id, assistant)
                }
                _ui.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        status = null,
                        error = detail,
                    )
                }
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        isGenerating = false,
                        streamingText = "",
                        status = null,
                        error = "Request failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            } finally {
                if (generationJob == this.coroutineContext[Job]) {
                    generationJob = null
                }
            }
        }
    }

    private suspend fun loadImages(paths: List<String>): List<ImageAttachment> =
        paths.mapNotNull { path ->
            runCatching { container.attachments.loadAttachment(path) }.getOrNull()
        }

    fun stopGeneration() {
        container.router.cancelActive()
        generationJob?.cancel()
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    fun setAutoMode() {
        viewModelScope.launch {
            container.settings.setManualMode(false)
            _ui.update { it.copy(manualMode = false) }
        }
    }

    fun setManualMode(configId: String, allowFallback: Boolean) {
        viewModelScope.launch {
            container.settings.setManualMode(true)
            container.settings.setManualConfigId(configId)
            container.settings.setManualAllowFallback(allowFallback)
            _ui.update {
                it.copy(
                    manualMode = true,
                    manualConfigId = configId,
                    manualAllowFallback = allowFallback,
                )
            }
        }
    }

    fun setManualAllowFallback(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setManualAllowFallback(enabled)
            _ui.update { it.copy(manualAllowFallback = enabled) }
        }
    }

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch { container.conversations.rename(id, title) }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val conversation = container.conversations.get(id)
            val paths = conversation?.messages?.flatMap { it.attachmentPaths }.orEmpty()
            container.attachments.delete(paths)
            container.conversations.delete(id)
            if (selectedId.value == id) {
                selectedId.value = container.conversations.conversations.value.firstOrNull()?.id
            }
        }
    }

    fun clearErrorAndNotice() {
        _ui.update { it.copy(error = null, fallbackNotice = null) }
    }

    override fun onCleared() {
        voiceManager.shutdown()
        super.onCleared()
    }

    private companion object {
        const val CHAT_SYSTEM_PROMPT =
            "You are Universal AI Agent, a helpful Android assistant. Answer clearly and accurately. " +
                "Format code in fenced blocks when useful. Never invent API results. When Agent mode is " +
                "active, the app can control the phone through Android Accessibility, and every on-screen " +
                "action is confirmed locally. Symptoms like a phone UI or app screen should be explained " +
                "step by step. If you are unsure, say so."
    }
}
