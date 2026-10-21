package com.universalaiagent.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        connected.value = false
        super.onDestroy()
    }

    fun snapshot(): UiSnapshot? {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return null
        val packageName = root.packageName?.toString().orEmpty()
        val label = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        val elements = mutableListOf<UiElement>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0
        while (queue.isNotEmpty() && elements.size < MAX_ELEMENTS && visited < MAX_VISITED) {
            val (node, depth) = queue.removeFirst()
            visited++
            if (depth > MAX_DEPTH) continue
            val isPassword = runCatching { node.isPassword }.getOrDefault(false)
            val text = if (isPassword) "" else node.text?.toString()?.trim().orEmpty().take(200)
            val description = if (isPassword) "" else {
                node.contentDescription?.toString()?.trim().orEmpty().take(200)
            }
            val viewId = node.viewIdResourceName
            val className = node.className?.toString().orEmpty()
            if (text.isNotBlank() || description.isNotBlank() || node.isClickable || node.isEditable) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                elements.add(
                    UiElement(
                        index = elements.size,
                        text = text,
                        description = description,
                        viewId = viewId,
                        className = className,
                        bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
                        clickable = node.isClickable,
                        editable = node.isEditable,
                        scrollable = node.isScrollable,
                        enabled = node.isEnabled,
                    ),
                )
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.add(it to depth + 1) }
            }
        }
        return UiSnapshot(packageName = packageName, appLabel = label, elements = elements)
    }

    companion object {
        @Volatile
        private var instance: AgentAccessibilityService? = null
        private val connected = MutableStateFlow(false)
        val connectedState: StateFlow<Boolean> = connected.asStateFlow()

        fun current(): AgentAccessibilityService? = instance
        fun isConnected(): Boolean = instance != null

        private const val MAX_ELEMENTS = 250
        private const val MAX_VISITED = 1200
        private const val MAX_DEPTH = 30
    }
}
