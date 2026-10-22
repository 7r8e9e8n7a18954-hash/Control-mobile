package com.universalaiagent.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.universalaiagent.data.model.AppEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ActionResult(
    val success: Boolean,
    val message: String,
    val snapshot: UiSnapshot? = null,
)

class ActionEngine(
    private val context: Context,
) {
    fun isAccessibilityConnected(): Boolean = AgentAccessibilityService.isConnected()

    fun snapshot(): UiSnapshot? = AgentAccessibilityService.current()?.snapshot()

    fun showOverlay() = AgentAccessibilityService.current()?.showAgentOverlay()

    fun hideOverlay() = AgentAccessibilityService.current()?.hideAgentOverlay()

    @Suppress("DEPRECATION")
    fun installedApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val manager = context.packageManager
        return manager.queryIntentActivities(intent, 0)
            .map { info ->
                AppEntry(
                    label = info.loadLabel(manager).toString(),
                    packageName = info.activityInfo.packageName,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun execute(action: AgentAction, instruction: String): ActionResult = when (action) {
        is AgentAction.LaunchApp -> launchApp(action.appName)
        is AgentAction.Click -> withService { service -> click(service, action) }
        is AgentAction.Scroll -> withService { service -> scroll(service, action.direction) }
        is AgentAction.InputText -> withService { service -> inputText(service, action) }
        AgentAction.PressBack -> withService { service ->
            val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            ActionResult(ok, if (ok) "Pressed Back." else "Android refused the Back action.")
        }
        AgentAction.ReadScreen -> {
            val snapshot = snapshot()
            if (snapshot == null) ActionResult(false, "Could not read the screen.")
            else ActionResult(true, "Read ${snapshot.elements.size} UI elements.", snapshot)
        }
        is AgentAction.Wait -> {
            delay(action.millis)
            ActionResult(true, "Waited ${action.millis} ms.")
        }
        is AgentAction.RequestConfirmation -> ActionResult(true, "Confirmation requested: ${action.reason}")
        AgentAction.FinishTask -> ActionResult(true, "Task marked finished.")
    }

    private suspend fun withService(
        block: suspend (AgentAccessibilityService) -> ActionResult,
    ): ActionResult {
        val service = AgentAccessibilityService.current()
            ?: return ActionResult(
                false,
                "Accessibility service is not enabled. Open Android Settings and enable Universal AI Agent.",
            )
        return withContext(Dispatchers.Main) {
            runCatching { block(service) }.getOrElse {
                ActionResult(false, "Action failed: ${it.message?.take(300) ?: it.javaClass.simpleName}")
            }
        }
    }

    private fun launchApp(name: String): ActionResult {
        val apps = installedApps()
        val exact = apps.filter { it.label.equals(name, ignoreCase = true) }
        val matches = if (exact.isNotEmpty()) {
            exact
        } else {
            apps.filter {
                it.label.contains(name, ignoreCase = true) ||
                    it.packageName.contains(name, ignoreCase = true)
            }
        }
        if (matches.isEmpty()) {
            return ActionResult(false, "No installed app matches \"$name\". Installed apps: " +
                apps.take(20).joinToString { it.label })
        }
        if (matches.size > 1) {
            return ActionResult(
                false,
                "Multiple apps match \"$name\": ${matches.take(6).joinToString { it.label }}. Be more specific.",
            )
        }
        val target = matches.first()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
            ?: return ActionResult(false, "${target.label} does not expose a launchable activity.")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            val metrics = context.resources.displayMetrics
            AgentAccessibilityService.current()?.showAgentTap(
                metrics.widthPixels / 2f,
                metrics.heightPixels / 2f,
            )
            ActionResult(true, "Launched ${target.label}.")
        }.getOrElse {
            ActionResult(false, "Android blocked launching ${target.label}: ${it.message}")
        }
    }

    private suspend fun click(service: AgentAccessibilityService, action: AgentAction.Click): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult(false, "No active window is available.")
        val node = findNode(root, action.target)
            ?: return ActionResult(
                false,
                "Could not find \"${action.target}\" on the current screen. Read the screen and try again.",
            )
        val clickable = findClickable(node)
        if (clickable == null) {
            return ActionResult(false, "\"${action.target}\" is visible but not clickable.")
        }
        val rect = Rect()
        clickable.getBoundsInScreen(rect)
        val x = rect.exactCenterX()
        val y = rect.exactCenterY()
        val performed = clickable.performAction(
            if (action.longPress) AccessibilityNodeInfo.ACTION_LONG_CLICK
            else AccessibilityNodeInfo.ACTION_CLICK,
        )
        if (performed) {
            service.showAgentTap(x, y)
            return ActionResult(true, "${if (action.longPress) "Long-clicked" else "Clicked"} \"${action.target}\".")
        }
        val duration = if (action.longPress) 650L else 60L
        val fallback = dispatchTap(service, x, y, duration)
        return if (fallback) {
            service.showAgentTap(x, y)
            ActionResult(
                true,
                "${if (action.longPress) "Long-pressed" else "Tapped"} \"${action.target}\" with a touch gesture.",
            )
        } else {
            ActionResult(false, "The app rejected the click on \"${action.target}\".")
        }
    }

    private suspend fun dispatchTap(
        service: AgentAccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long,
    ): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        val result = CompletableDeferred<Boolean>()
        service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    result.complete(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    result.complete(false)
                }
            },
            null,
        )
        return withTimeoutOrNull(3000) { result.await() } ?: false
    }

    private suspend fun scroll(service: AgentAccessibilityService, direction: String): ActionResult {
        val root = service.rootInActiveWindow
        val scrollable = findScrollable(root)
        if (scrollable != null) {
            val ok = scrollable.performAction(
                if (direction == "forward") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
            )
            if (ok) {
                val rect = Rect()
                scrollable.getBoundsInScreen(rect)
                service.showAgentTap(rect.exactCenterX(), rect.exactCenterY())
                return ActionResult(true, "Scrolled $direction.")
            }
        }
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels / 2f
        val height = metrics.heightPixels / 2f
        val startY = if (direction == "forward") height * 1.35f else height * 0.65f
        val endY = if (direction == "forward") height * 0.65f else height * 1.35f
        val path = Path().apply {
            moveTo(width, startY)
            lineTo(width, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        val result = CompletableDeferred<Boolean>()
        service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    result.complete(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    result.complete(false)
                }
            },
            null,
        )
        val completed = withTimeoutOrNull(3000) { result.await() } ?: false
        if (completed) {
            service.showAgentTap(width, (startY + endY) / 2f)
        }
        return ActionResult(
            completed,
            if (completed) "Scrolled $direction using a gesture fallback." else "Could not scroll this screen.",
        )
    }

    private suspend fun inputText(service: AgentAccessibilityService, action: AgentInput): ActionResult {
        val root = service.rootInActiveWindow
            ?: return ActionResult(false, "No active window is available.")
        val targetNode = if (action.target.isNullOrBlank()) {
            findFirstEditable(root) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } else {
            findNode(root, action.target)
        }
        if (targetNode == null) {
            return ActionResult(false, "Could not find an editable field${action.target?.let { " for \"$it\"" } ?: ""}.")
        }
        val editable = findEditable(targetNode)
            ?: return ActionResult(false, "The selected element is not an editable text field.")
        editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text)
        }
        val ok = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return if (ok) {
            val rect = Rect()
            editable.getBoundsInScreen(rect)
            service.showAgentTap(rect.exactCenterX(), rect.exactCenterY())
            ActionResult(true, "Entered ${action.text.length} characters.")
        } else {
            ActionResult(false, "The app rejected setting text in this field.")
        }
    }

    private suspend fun inputText(service: AgentAccessibilityService, action: AgentAction.InputText): ActionResult =
        inputText(service, AgentInput(action.target, action.text))

    private data class AgentInput(val target: String?, val text: String)

    private fun findNode(root: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val indexMatch = Regex("#(\\d+)").matchEntire(target.trim())
        if (indexMatch != null) {
            val index = indexMatch.groupValues[1].toIntOrNull() ?: return null
            return nodeAt(root, index)
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var containsMatch: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty() && visited < 1500) {
            val node = queue.removeFirst()
            visited++
            val values = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.viewIdResourceName?.substringAfterLast('/'),
                node.className?.toString()?.substringAfterLast('.'),
            )
            if (values.any { it.trim().equals(target.trim(), ignoreCase = true) }) return node
            if (containsMatch == null && values.any {
                    it.contains(target.trim(), ignoreCase = true) && it.trim().length <= 200
                }
            ) {
                containsMatch = node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.add(it) }
            }
        }
        return containsMatch
    }

    private fun nodeAt(root: AccessibilityNodeInfo, targetIndex: Int): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = -1
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.text?.isNotBlank() == true || node.contentDescription?.isNotBlank() == true ||
                node.isClickable || node.isEditable
            ) {
                count++
                if (count == targetIndex) return node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 4) {
            if (current.isEditable) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun findFirstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 800) {
            val node = queue.removeFirst()
            visited++
            if (node.isEditable && node.isEnabled) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 800) {
            val node = queue.removeFirst()
            visited++
            if (node.isScrollable) return node
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.add(it) }
            }
        }
        return null
    }
}
