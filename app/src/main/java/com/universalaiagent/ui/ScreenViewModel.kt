package com.universalaiagent.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalaiagent.AppContainer
import com.universalaiagent.data.model.AiRequest
import com.universalaiagent.data.model.ChatTurn
import com.universalaiagent.data.model.ImageAttachment
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class ScreenUiState(
    val sharingActive: Boolean = false,
    val cloudEnabled: Boolean = true,
    val busy: Boolean = false,
    val preview: Bitmap? = null,
    val analysis: String = "",
    val prompt: String = "",
    val status: String? = null,
    val error: String? = null,
)

class ScreenViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _ui = MutableStateFlow(ScreenUiState())
    val ui: StateFlow<ScreenUiState> = _ui

    init {
        viewModelScope.launch {
            container.screenCapture.active.collect { active ->
                _ui.update { it.copy(sharingActive = active) }
            }
        }
        viewModelScope.launch {
            container.screenCapture.error.collect { error ->
                if (error != null) _ui.update { it.copy(error = error) }
            }
        }
        viewModelScope.launch {
            container.settings.data.collect { settings ->
                _ui.update { it.copy(cloudEnabled = settings.cloudImageProcessing) }
            }
        }
    }

    fun setPrompt(value: String) = _ui.update { it.copy(prompt = value) }
    fun clearError() = _ui.update { it.copy(error = null) }

    fun setCloudEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settings.setCloudImageProcessing(enabled) }
    }

    fun stopSharing() {
        container.screenCapture.stop(container.appContext)
        val preview = _ui.value.preview
        preview?.recycle()
        _ui.update {
            it.copy(
                sharingActive = false,
                preview = null,
                analysis = "",
                status = "Screen sharing stopped.",
            )
        }
    }

    fun capturePreview() {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null, status = "Capturing a frame…") }
            runCatching { container.screenCapture.capture() }
                .onSuccess { bitmap ->
                    val old = _ui.value.preview
                    if (old !== bitmap) old?.recycle()
                    _ui.update {
                        it.copy(
                            preview = bitmap,
                            busy = false,
                            status = "Frame captured locally. Nothing was uploaded.",
                        )
                    }
                }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            busy = false,
                            status = null,
                            error = error.message ?: "Screen capture failed.",
                        )
                    }
                }
        }
    }

    fun captureAndAnalyze() {
        if (_ui.value.busy) return
        val state = _ui.value
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    busy = true,
                    error = null,
                    analysis = "",
                    status = "Capturing a frame…",
                )
            }
            val bitmap = runCatching { container.screenCapture.capture() }
                .onFailure { error ->
                    _ui.update {
                        it.copy(
                            busy = false,
                            status = null,
                            error = error.message ?: "Screen capture failed.",
                        )
                    }
                }
                .getOrNull() ?: return@launch
            val old = _ui.value.preview
            if (old !== bitmap) old?.recycle()
            _ui.update { it.copy(preview = bitmap) }

            if (!_ui.value.cloudEnabled) {
                _ui.update {
                    it.copy(
                        busy = false,
                        status = "Screen processing is disabled. The frame stayed on this device.",
                    )
                }
                return@launch
            }

            _ui.update { it.copy(status = "Sending the frame to the selected vision provider…") }
            val attachment = withContext(Dispatchers.Default) { toAttachment(bitmap) }
                ?: run {
                    _ui.update {
                        it.copy(busy = false, status = null, error = "Could not encode the screenshot.")
                    }
                    return@launch
                }
            val question = _ui.value.prompt.ifBlank {
                "Describe what is on this screen. Identify the app, important UI elements, and suggest the next useful action."
            }
            val request = AiRequest(
                systemPrompt = SCREEN_SYSTEM_PROMPT,
                messages = listOf(ChatTurn("user", question)),
                images = listOf(attachment),
                maxOutputTokens = 1200,
            )
            try {
                val response = container.router.execute(
                    task = TaskType.SCREEN_ANALYSIS,
                    request = request,
                    onDelta = { delta ->
                        _ui.update { it.copy(analysis = it.analysis + delta, status = null) }
                    },
                    onNotice = { notice ->
                        _ui.update { it.copy(status = notice.message) }
                    },
                )
                _ui.update {
                    it.copy(
                        busy = false,
                        analysis = response.text,
                        status = "Analyzed with ${response.providerLabel} · ${response.model}",
                    )
                }
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
                        error = error.message ?: "Screen analysis failed.",
                    )
                }
            }
        }
    }

    private fun toAttachment(bitmap: Bitmap): ImageAttachment? {
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > MAX_SIDE) {
            val ratio = MAX_SIDE.toFloat() / largest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val output = ByteArrayOutputStream()
        val ok = scaled.compress(Bitmap.CompressFormat.JPEG, 75, output)
        if (scaled !== bitmap) scaled.recycle()
        if (!ok) return null
        return ImageAttachment(output.toByteArray(), "image/jpeg")
    }

    override fun onCleared() {
        _ui.value.preview?.recycle()
        super.onCleared()
    }

    private companion object {
        const val MAX_SIDE = 1280
        const val SCREEN_SYSTEM_PROMPT =
            "You are analyzing a screenshot the user explicitly captured. Describe only what is visible. " +
                "Do not claim to have clicked or changed anything. If the screen is black or protected, say " +
                "that it may be a secure window. Suggest concrete next steps using accessible UI elements."
    }
}
