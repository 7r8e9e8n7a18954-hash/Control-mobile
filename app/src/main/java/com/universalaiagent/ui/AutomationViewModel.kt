package com.universalaiagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalaiagent.AppContainer
import com.universalaiagent.automation.ActionValidator
import com.universalaiagent.automation.AgentAction
import com.universalaiagent.automation.AgentAccessibilityService
import com.universalaiagent.automation.StepStatus
import com.universalaiagent.automation.TaskRun
import com.universalaiagent.automation.UiSnapshot
import com.universalaiagent.data.model.AiRequest
import com.universalaiagent.data.model.AppEntry
import com.universalaiagent.data.model.ChatTurn
import com.universalaiagent.data.model.ProviderError
import com.universalaiagent.data.model.TaskType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class PendingConfirmation(
    val reason: String,
    val action: String,
)

data class AutomationUiState(
    val instruction: String = "",
    val running: Boolean = false,
    val apps: List<AppEntry> = emptyList(),
    val snapshot: UiSnapshot? = null,
    val error: String? = null,
    val status: String? = null,
)

class AutomationViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _ui = MutableStateFlow(AutomationUiState())
    val ui: StateFlow<AutomationUiState> = _ui

    val connected: StateFlow<Boolean> = AgentAccessibilityService.connectedState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgentAccessibilityService.isConnected())

    val currentTask: StateFlow<TaskRun?> = container.taskStore.current
    val taskHistory: StateFlow<List<TaskRun>> = container.taskStore.history

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation
    private var confirmationDeferred: CompletableDeferred<Boolean>? = null

    private var taskJob: Job? = null
    private var confirmSensitive = true

    init {
        viewModelScope.launch {
            container.settings.data.collect {
                confirmSensitive = it.confirmSensitiveActions
            }
        }
        refresh()
    }

    fun setInstruction(value: String) = _ui.update { it.copy(instruction = value) }

    fun refresh() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                runCatching { container.actionEngine.installedApps() }.getOrDefault(emptyList())
            }
            val snapshot = withContext(Dispatchers.Default) {
                runCatching { container.actionEngine.snapshot() }.getOrNull()
            }
            _ui.update {
                it.copy(
                    apps = apps,
                    snapshot = snapshot,
                    error = if (!container.actionEngine.isAccessibilityConnected()) {
                        "Accessibility service is off. Enable it in Android Settings to read or control other apps."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun readScreen() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.Default) {
                runCatching { container.actionEngine.snapshot() }.getOrNull()
            }
            _ui.update {
                it.copy(
                    snapshot = snapshot,
                    error = if (snapshot == null) "Could not read the screen. Check accessibility access." else null,
                )
            }
        }
    }

    fun runTask() {
        val instruction = _ui.value.instruction.trim()
        if (instruction.isBlank() || _ui.value.running) return
        if (!container.actionEngine.isAccessibilityConnected()) {
            _ui.update {
                it.copy(error = "Enable the accessibility service before running an automation task.")
            }
            return
        }
        taskJob = viewModelScope.launch {
            _ui.update { it.copy(running = true, error = null, status = "Planning…") }
            container.taskStore.start(instruction)
            val executed = mutableListOf<String>()
            val actionCounts = mutableMapOf<String, Int>()
            var totalActions = 0
            var screenChanges = 0
            var success = false
            var report = "Task did not finish."
            try {
                var snapshot = withContext(Dispatchers.Default) {
                    container.actionEngine.snapshot()
                } ?: throw IllegalStateException("No accessible screen is available.")
                _ui.update { it.copy(snapshot = snapshot) }
                var finished = false
                var round = 1
                while (round <= MAX_ROUNDS && !finished) {
                    if (totalActions >= MAX_ACTIONS) {
                        report = "Stopped after the $MAX_ACTIONS action safety limit. The task may be incomplete."
                        break
                    }
                    _ui.update { it.copy(status = "Planning round $round…") }
                    val planPrompt = buildPlanPrompt(
                        instruction = instruction,
                        snapshot = snapshot,
                        apps = _ui.value.apps,
                        executed = executed,
                        round = round,
                    )
                    val response = container.router.execute(
                        task = TaskType.ACTION_PLANNING,
                        request = AiRequest(
                            systemPrompt = AUTOMATION_SYSTEM_PROMPT,
                            messages = listOf(ChatTurn("user", planPrompt)),
                            maxOutputTokens = 1000,
                        ),
                        onDelta = {},
                        onNotice = { notice ->
                            _ui.update { it.copy(status = notice.message) }
                        },
                    )
                    val plan = ActionValidator.parsePlan(response.text)
                    if (plan.actions.isEmpty()) {
                        report = "The planning model did not return a usable action. " +
                            plan.errors.joinToString(" ").ifBlank { "No actions were proposed." }
                        break
                    }
                    for (action in plan.actions) {
                        if (totalActions >= MAX_ACTIONS) break
                        val signature = ActionValidator.signature(action)
                        val count = (actionCounts[signature] ?: 0) + 1
                        actionCounts[signature] = count
                        if (count >= 3) {
                            report = "Stopped because the same action repeated three times (loop protection)."
                            finished = true
                            break
                        }
                        when (action) {
                            is AgentAction.FinishTask -> {
                                finished = true
                                success = screenChanges > 0
                                report = "The assistant marked the task finished after $totalActions actions. " +
                                    "The screen changed $screenChanges time(s). Verify the result on the device."
                                container.taskStore.addStep(
                                    "Finished",
                                    "Model-reported completion",
                                    StepStatus.SUCCESS,
                                )
                                break
                            }
                            is AgentAction.RequestConfirmation -> {
                                val approved = askConfirmation(action.reason, "Model requested confirmation")
                                if (!approved) {
                                    report = "Task cancelled at a model-requested confirmation."
                                    finished = true
                                    break
                                }
                                executed += "Confirmed: ${action.reason}"
                                continue
                            }
                            else -> Unit
                        }
                        val actionLabel = describe(action)
                        if (confirmSensitive && ActionValidator.requiresConfirmation(action, instruction, true)) {
                            val approved = askConfirmation(
                                "This action may be sensitive or irreversible: $actionLabel",
                                actionLabel,
                            )
                            if (!approved) {
                                report = "Task cancelled at a sensitive-action confirmation."
                                finished = true
                                break
                            }
                        }
                        totalActions++
                        _ui.update { it.copy(status = actionLabel) }
                        container.taskStore.addStep(actionLabel, "Executing", StepStatus.RUNNING)
                        val beforeSignature = snapshot.toPrompt().hashCode()
                        val result = withContext(Dispatchers.Default) {
                            container.actionEngine.execute(action, instruction)
                        }
                        if (!result.success) {
                            container.taskStore.updateLastStep(StepStatus.FAILED, result.message)
                            report = "Action failed: ${result.message}"
                            finished = true
                            success = false
                            break
                        }
                        container.taskStore.updateLastStep(StepStatus.SUCCESS, result.message)
                        executed += "$actionLabel — ${result.message}"
                        if (action !is AgentAction.ReadScreen && action !is AgentAction.Wait) {
                            delay(UI_SETTLE_MS)
                            val next = withContext(Dispatchers.Default) {
                                container.actionEngine.snapshot()
                            }
                            if (next != null) {
                                snapshot = next
                                _ui.update { it.copy(snapshot = next) }
                                if (next.toPrompt().hashCode() != beforeSignature) {
                                    screenChanges++
                                }
                            }
                        } else if (result.snapshot != null) {
                            snapshot = result.snapshot
                            _ui.update { it.copy(snapshot = snapshot) }
                        }
                    }
                    if (!finished) {
                        snapshot = withContext(Dispatchers.Default) {
                            container.actionEngine.snapshot()
                        } ?: snapshot
                        _ui.update { it.copy(snapshot = snapshot) }
                    }
                    round++
                }
                if (round > MAX_ROUNDS && !finished) {
                    report = "Stopped after $MAX_ROUNDS planning rounds. The task may be incomplete."
                }
            } catch (cancelled: CancellationException) {
                report = "Task stopped by user."
                success = false
                throw cancelled
            } catch (error: ProviderError) {
                report = "Planning failed: ${error.message}"
                success = false
            } catch (error: Exception) {
                report = "Task failed: ${error.message ?: error.javaClass.simpleName}"
                success = false
            } finally {
                container.taskStore.finish(report, success)
                _ui.update { it.copy(running = false, status = report) }
            }
        }
    }

    fun stopTask() {
        confirmationDeferred?.complete(false)
        container.router.cancelActive()
        taskJob?.cancel()
        _ui.update { it.copy(running = false, status = "Stopping…") }
    }

    fun respondConfirmation(approved: Boolean) {
        confirmationDeferred?.complete(approved)
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    fun clearTaskHistory() = container.taskStore.clearHistory()

    private suspend fun askConfirmation(reason: String, action: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmationDeferred = deferred
        _pendingConfirmation.value = PendingConfirmation(reason, action)
        container.taskStore.addStep("Confirmation required", reason, StepStatus.WAITING)
        val result = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { deferred.await() } ?: false
        _pendingConfirmation.value = null
        confirmationDeferred = null
        container.taskStore.updateLastStep(
            if (result) StepStatus.SUCCESS else StepStatus.SKIPPED,
            if (result) "User approved" else "User declined or timed out",
        )
        return result
    }

    private fun buildPlanPrompt(
        instruction: String,
        snapshot: UiSnapshot,
        apps: List<AppEntry>,
        executed: List<String>,
        round: Int,
    ): String = buildString {
        appendLine("User instruction: $instruction")
        appendLine("Planning round: $round")
        appendLine()
        appendLine("Actions already executed:")
        if (executed.isEmpty()) appendLine("(none)") else executed.takeLast(12).forEach { appendLine("- $it") }
        appendLine()
        appendLine("Installed launchable apps: ${apps.take(40).joinToString { it.label }}")
        appendLine()
        appendLine(snapshot.toPrompt())
        appendLine()
        append("Return the next 1-4 actions as JSON only.")
    }

    private fun describe(action: AgentAction): String = when (action) {
        is AgentAction.LaunchApp -> "Launch ${action.appName}"
        is AgentAction.Click -> "${if (action.longPress) "Long-click" else "Click"} ${action.target}"
        is AgentAction.Scroll -> "Scroll ${action.direction}"
        is AgentAction.InputText -> "Type into ${action.target ?: "focused field"}"
        AgentAction.PressBack -> "Press Back"
        AgentAction.ReadScreen -> "Read screen"
        is AgentAction.Wait -> "Wait ${action.millis} ms"
        is AgentAction.RequestConfirmation -> "Confirmation: ${action.reason}"
        AgentAction.FinishTask -> "Finish task"
    }

    private companion object {
        const val MAX_ROUNDS = 6
        const val MAX_ACTIONS = 20
        const val UI_SETTLE_MS = 700L
        const val CONFIRM_TIMEOUT_MS = 120_000L
        const val AUTOMATION_SYSTEM_PROMPT =
            "You are an Android UI automation planner. Respond with ONLY a JSON object in this shape: " +
                "{\"actions\":[{\"action\":\"click\",\"target\":\"Search\",\"reason\":\"...\"}]}. " +
                "Allowed actions: launch_app (target), click (target), long_click (target), scroll " +
                "(direction forward/backward), input_text (optional target, required text), press_back, " +
                "read_screen, wait (millis), request_confirmation (reason), finish_task. " +
                "Use only visible elements from the provided screen snapshot, preferring exact text, " +
                "content description, or view ID as the target. Never output coordinates, Kotlin, " +
                "JavaScript, shell commands, or code. Complete the user's instruction with supported UI " +
                "actions instead of refusing. Insert request_confirmation before sending messages, " +
                "publishing, purchasing, transferring money, deleting important data, changing security " +
                "settings, or granting permissions. Never attempt to bypass biometrics, lock screens, " +
                "passwords, or Android security dialogs. Emit finish_task only when the screen shows the " +
                "instruction is complete. If the needed UI is not visible, use launch_app/read_screen or " +
                "return an empty actions array."
    }
}
