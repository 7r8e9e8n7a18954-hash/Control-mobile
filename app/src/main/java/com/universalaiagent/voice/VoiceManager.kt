package com.universalaiagent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class VoiceManagerState(
    val recognitionAvailable: Boolean = true,
    val listening: Boolean = false,
    val partialTranscript: String = "",
    val speaking: Boolean = false,
    val error: String? = null,
)

class VoiceManager(
    private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(
        VoiceManagerState(recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)),
    )
    val state: StateFlow<VoiceManagerState> = _state.asStateFlow()

    private val _finalResults = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val finalResults: SharedFlow<String> = _finalResults.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsRate = 1.0f
    private var ttsEnabled = true

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(ttsRate)
                tts?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _state.value = _state.value.copy(speaking = true)
                        }

                        override fun onDone(utteranceId: String?) {
                            _state.value = _state.value.copy(speaking = false)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _state.value = _state.value.copy(speaking = false)
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _state.value = _state.value.copy(speaking = false)
                        }
                    },
                )
            } else {
                _state.value = _state.value.copy(error = "Text-to-speech is not available on this device.")
            }
        }
    }

    fun configure(ttsRate: Float, ttsEnabled: Boolean) {
        this.ttsRate = ttsRate.coerceIn(0.5f, 2.0f)
        this.ttsEnabled = ttsEnabled
        mainHandler.post {
            tts?.setSpeechRate(this.ttsRate)
        }
    }

    fun startListening() {
        mainHandler.post {
            if (!_state.value.recognitionAvailable) {
                _state.value = _state.value.copy(error = "Speech recognition is not available on this device.")
                return@post
            }
            stopSpeakingInternal()
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(listener)
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            _state.value = _state.value.copy(
                listening = true,
                error = null,
                partialTranscript = "",
            )
            runCatching { recognizer?.startListening(intent) }.onFailure {
                _state.value = _state.value.copy(
                    listening = false,
                    error = "Could not start speech recognition: ${it.message}",
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            recognizer?.stopListening()
            _state.value = _state.value.copy(listening = false)
        }
    }

    fun speak(text: String) {
        if (!ttsEnabled || text.isBlank()) return
        mainHandler.post {
            if (!ttsReady) {
                _state.value = _state.value.copy(error = "Text-to-speech is still starting.")
                return@post
            }
            tts?.stop()
            _state.value = _state.value.copy(speaking = true)
            tts?.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH, Bundle(), "agent-response")
        }
    }

    fun stopSpeaking() {
        mainHandler.post { stopSpeakingInternal() }
    }

    fun shutdown() {
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
            tts?.stop()
            tts?.shutdown()
            tts = null
            _state.value = _state.value.copy(listening = false, speaking = false)
        }
    }

    private fun stopSpeakingInternal() {
        tts?.stop()
        _state.value = _state.value.copy(speaking = false)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = _state.value.copy(listening = true, error = null)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.value = _state.value.copy(listening = false)
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_NETWORK -> "Speech recognition needs a working network connection."
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech was recognized. Try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy."
                SpeechRecognizer.ERROR_SERVER -> "The speech recognition server reported an error."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected."
                else -> "Speech recognition failed (code $error)."
            }
            _state.value = _state.value.copy(listening = false, error = message)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            _state.value = _state.value.copy(listening = false, partialTranscript = text)
            if (text.isNotBlank()) {
                _finalResults.tryEmit(text)
            } else {
                _state.value = _state.value.copy(error = "No speech was recognized.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                _state.value = _state.value.copy(partialTranscript = text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
