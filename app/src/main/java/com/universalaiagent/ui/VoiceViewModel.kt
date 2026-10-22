package com.universalaiagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalaiagent.AppContainer
import com.universalaiagent.data.model.AiRequest
import com.universalaiagent.data.model.ChatTurn
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.TaskType
import com.universalaiagent.voice.VoiceManager
import com.universalaiagent.voice.VoiceManagerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceUiState(
    val transcript: String = "",
    val response: String = "",
    val responseProvider: String? = null,
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
)

class VoiceViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val manager = VoiceManager(container.appContext)
    val voiceState: StateFlow<VoiceManagerState> = manager.state

    private val _ui = MutableStateFlow(VoiceUiState())
    val ui: StateFlow<VoiceUiState> = _ui

    private val sessionTurns = mutableListOf<ChatTurn>()
    private var generationJob: Job? = null
    private var ttsEnabled = true

    init {
        viewModelScope.launch {
            container.settings.data.collect { settings ->
                ttsEnabled = settings.ttsEnabled
                manager.configure(settings.ttsRate, settings.ttsEnabled)
            }
        }
        viewModelScope.launch {
            manager.finalResults.collect { text ->
                _ui.update { it.copy(transcript = text) }
                generateResponse(text)
            }
        }
    }

    fun startListening() {
        if (_ui.value.busy) return
        manager.stopSpeaking()
        _ui.update { it.copy(error = null, status = "Listening…") }
        manager.startListening()
    }

    fun stopListening() {
        manager.stopListening()
        _ui.update { it.copy(status = null) }
    }

    fun stopSpeaking() {
        manager.stopSpeaking()
    }

    fun stopGeneration() {
        container.router.cancelActive()
        generationJob?.cancel()
    }

    fun retryLast() {
        val lastUser = sessionTurns.lastOrNull { it.role == "user" } ?: return
        generateResponse(lastUser.content)
    }

    fun clear() {
        manager.stopSpeaking()
        sessionTurns.clear()
        _ui.value = VoiceUiState()
    }

    private fun generateResponse(text: String) {
        if (_ui.value.busy) return
        _ui.update {
            it.copy(
                busy = true,
                response = "",
                status = "Thinking…",
                error = null,
                responseProvider = null,
            )
        }
        sessionTurns += ChatTurn("user", text)
        if (sessionTurns.size > 20) sessionTurns.subList(0, sessionTurns.size - 20).clear()
        generationJob = viewModelScope.launch {
            try {
                val response = container.router.execute(
                    task = TaskType.GENERAL_CHAT,
                    request = AiRequest(
                        systemPrompt = VOICE_SYSTEM_PROMPT,
                        messages = sessionTurns.toList(),
                    ),
                    onDelta = { delta ->
                        _ui.update { it.copy(response = it.response + delta, status = null) }
                    },
                    onNotice = { notice ->
                        _ui.update { it.copy(status = notice.message) }
                    },
                )
                sessionTurns += ChatTurn("assistant", response.text)
                _ui.update {
                    it.copy(
                        busy = false,
                        response = response.text,
                        status = null,
                        responseProvider = "${response.providerLabel} · ${response.model}",
                    )
                }
                if (ttsEnabled) manager.speak(response.text)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _ui.update {
                    it.copy(
                        busy = false,
                        status = "Stopped",
                    )
                }
                throw cancelled
            } catch (error: ProviderError) {
                _ui.update {
                    it.copy(
                        busy = false,
                        status = null,
                        error = error.message,
                    )
                }
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        busy = false,
                        status = null,
                        error = error.message ?: "Voice request failed.",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        manager.shutdown()
        super.onCleared()
    }

    private companion object {
        const val VOICE_SYSTEM_PROMPT =
            "You are Universal AI Agent in a voice conversation. Answer concisely in plain language, " +
                "because the response will be spoken aloud. Avoid markdown tables and long code blocks. " +
                "You cannot perform Android actions from this voice response; if the user wants an action, " +
                "briefly confirm what they asked and tell them to use Agent mode in chat."
    }
}
