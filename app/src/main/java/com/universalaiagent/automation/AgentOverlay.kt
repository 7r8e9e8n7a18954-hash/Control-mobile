package com.universalaiagent.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class AgentOverlay(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density

    private var rippleView: RippleView? = null
    private var panel: ControlPanel? = null
    private var screenVisible = true
    private var onToggleAudio: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null

    fun show(
        audioEnabled: Boolean,
        onToggleAudio: () -> Unit,
        onStop: () -> Unit,
    ) {
        this.onToggleAudio = onToggleAudio
        this.onStop = onStop
        screenVisible = true
        handler.post {
            if (!showRipple()) return@post
            showPanel(audioEnabled)
        }
    }

    fun hide() {
        handler.post {
            rippleView?.let { runCatching { windowManager.removeView(it) } }
            rippleView = null
            panel?.let { runCatching { windowManager.removeView(it) } }
            panel = null
            onToggleAudio = null
            onStop = null
        }
    }

    fun showTap(x: Float, y: Float) {
        handler.post { rippleView?.addTap(x, y) }
    }

    fun setAudioEnabled(enabled: Boolean) {
        handler.post { panel?.setAudioEnabled(enabled) }
    }

    private fun showRipple(): Boolean {
        if (rippleView != null) return true
        val view = RippleView(service)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        return runCatching {
            windowManager.addView(view, params)
            rippleView = view
            true
        }.getOrDefault(false)
    }

    private fun showPanel(audioEnabled: Boolean) {
        if (panel != null) {
            panel?.setAudioEnabled(audioEnabled)
            return
        }
        val view = ControlPanel(service).apply {
            setAudioEnabled(audioEnabled)
            onScreenAction = { toggleScreenIndicators() }
            onAudioAction = { onToggleAudio?.invoke() }
            onStopAction = { onStop?.invoke() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = dp(16)
        params.y = dp(96)
        runCatching {
            windowManager.addView(view, params)
            panel = view
            view.attach(windowManager, params)
            view.post {
                val metrics = service.resources.displayMetrics
                params.x = (metrics.widthPixels - view.width - dp(16)).coerceAtLeast(dp(8))
                runCatching { windowManager.updateViewLayout(view, params) }
            }
        }
    }

    private fun toggleScreenIndicators() {
        screenVisible = !screenVisible
        rippleView?.visibility = if (screenVisible) View.VISIBLE else View.INVISIBLE
        panel?.setScreenVisible(screenVisible)
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private class RippleView(context: Context) : View(context) {
        private data class Ripple(val x: Float, val y: Float, val startedAt: Long)

        private val ripples = mutableListOf<Ripple>()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(124, 124, 255)
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.resources.displayMetrics.density * 2.4f
            color = Color.rgb(165, 180, 252)
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(124, 124, 255)
        }
        private val density = context.resources.displayMetrics.density

        fun addTap(x: Float, y: Float) {
            ripples += Ripple(x, y, SystemClock.uptimeMillis())
            if (ripples.size > 12) ripples.removeAt(0)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = SystemClock.uptimeMillis()
            val iterator = ripples.iterator()
            while (iterator.hasNext()) {
                val ripple = iterator.next()
                val age = now - ripple.startedAt
                if (age > DURATION_MS) {
                    iterator.remove()
                    continue
                }
                val progress = age / DURATION_MS.toFloat()
                val eased = 1f - (1f - progress) * (1f - progress)
                val base = density * 9f
                val ringRadius = base + eased * density * 52f

                glow.alpha = ((1f - progress) * 42f).toInt().coerceIn(0, 255)
                canvas.drawCircle(ripple.x, ripple.y, ringRadius * 0.9f, glow)

                ring.alpha = ((1f - progress) * 190f).toInt().coerceIn(0, 255)
                canvas.drawCircle(ripple.x, ripple.y, ringRadius, ring)

                fill.alpha = ((1f - progress * 0.6f) * 225f).toInt().coerceIn(0, 255)
                canvas.drawCircle(ripple.x, ripple.y, base * (1f - progress * 0.35f), fill)

                val cross = density * 2.2f
                ring.alpha = ((1f - progress) * 140f).toInt().coerceIn(0, 255)
                canvas.drawLine(ripple.x - cross, ripple.y, ripple.x + cross, ripple.y, ring)
                canvas.drawLine(ripple.x, ripple.y - cross, ripple.x, ripple.y + cross, ring)
            }
            if (ripples.isNotEmpty()) postInvalidateOnAnimation()
        }

        private companion object {
            const val DURATION_MS = 900L
        }
    }

    private class ControlPanel(context: Context) : LinearLayout(context) {
        var onScreenAction: (() -> Unit)? = null
        var onAudioAction: (() -> Unit)? = null
        var onStopAction: (() -> Unit)? = null

        private val audioLabel: TextView
        private val screenLabel: TextView
        private val density = context.resources.displayMetrics.density
        private var windowManager: WindowManager? = null
        private var params: WindowManager.LayoutParams? = null
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var audioEnabled = true
        private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(Color.argb(238, 18, 18, 28))
                setStroke(dp(1), Color.argb(64, 255, 255, 255))
            }
            elevation = dp(12).toFloat()
            setPadding(dp(4), dp(4), dp(4), dp(4))

            screenLabel = button("Screen")
            addView(screenLabel)
            audioLabel = button("Sound")
            addView(audioLabel)
            addView(button("Stop", Color.rgb(255, 138, 128)))

            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startX = params?.x ?: 0
                        startY = params?.y ?: 0
                        dragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downRawX
                        val dy = event.rawY - downRawY
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) dragging = true
                        val layoutParams = params
                        val manager = windowManager
                        if (dragging && layoutParams != null && manager != null) {
                            val metrics = resources.displayMetrics
                            layoutParams.x = (startX + dx.toInt())
                                .coerceIn(0, (metrics.widthPixels - width).coerceAtLeast(0))
                            layoutParams.y = (startY + dy.toInt())
                                .coerceIn(0, (metrics.heightPixels - height).coerceAtLeast(0))
                            runCatching { manager.updateViewLayout(this, layoutParams) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragging) {
                            val cell = width / 3f
                            when ((event.x / cell).toInt().coerceIn(0, 2)) {
                                0 -> onScreenAction?.invoke()
                                1 -> onAudioAction?.invoke()
                                else -> onStopAction?.invoke()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        fun attach(manager: WindowManager, layoutParams: WindowManager.LayoutParams) {
            windowManager = manager
            params = layoutParams
        }

        fun setAudioEnabled(enabled: Boolean) {
            audioEnabled = enabled
            audioLabel.text = if (enabled) "Sound" else "Muted"
            audioLabel.alpha = if (enabled) 1f else 0.6f
        }

        fun setScreenVisible(visible: Boolean) {
            screenLabel.text = if (visible) "Screen" else "Hidden"
            screenLabel.alpha = if (visible) 1f else 0.6f
        }

        private fun button(label: String, color: Int = Color.WHITE): TextView =
            TextView(context).apply {
                text = label
                setTextColor(color)
                textSize = 12f
                gravity = Gravity.CENTER
                isSingleLine = true
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

        private fun dp(value: Int): Int = (value * density).toInt()
    }
}
