package com.universalaiagent.screen

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenCaptureController {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile
    private var service: ScreenCaptureService? = null

    fun start(context: Context, resultCode: Int, data: Intent) {
        _error.value = null
        val intent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, ScreenCaptureService::class.java))
    }

    suspend fun capture(): Bitmap {
        val current = service ?: throw IllegalStateException("Screen sharing is not active.")
        return current.captureFrame()
    }

    internal fun attach(service: ScreenCaptureService) {
        this.service = service
        _active.value = true
    }

    internal fun detach() {
        service = null
        _active.value = false
    }

    internal fun reportError(message: String?) {
        _error.value = message
    }
}
