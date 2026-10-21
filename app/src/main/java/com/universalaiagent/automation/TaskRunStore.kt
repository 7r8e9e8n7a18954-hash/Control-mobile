package com.universalaiagent.automation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class StepStatus {
    RUNNING,
    SUCCESS,
    FAILED,
    WAITING,
    SKIPPED,
}

data class TaskStep(
    val title: String,
    val detail: String = "",
    val status: StepStatus = StepStatus.RUNNING,
    val timestamp: Long = System.currentTimeMillis(),
)

data class TaskRun(
    val id: String,
    val instruction: String,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val steps: List<TaskStep> = emptyList(),
    val running: Boolean = true,
    val report: String? = null,
    val success: Boolean? = null,
)

class TaskRunStore {
    private val _current = MutableStateFlow<TaskRun?>(null)
    val current: StateFlow<TaskRun?> = _current.asStateFlow()

    private val _history = MutableStateFlow<List<TaskRun>>(emptyList())
    val history: StateFlow<List<TaskRun>> = _history.asStateFlow()

    fun start(instruction: String): TaskRun {
        val run = TaskRun(
            id = UUID.randomUUID().toString(),
            instruction = instruction,
        )
        _current.value = run
        return run
    }

    fun addStep(
        title: String,
        detail: String = "",
        status: StepStatus = StepStatus.RUNNING,
    ) {
        _current.update { current ->
            current?.copy(steps = current.steps + TaskStep(title, detail, status))
        }
    }

    fun updateLastStep(status: StepStatus, detail: String? = null) {
        _current.update { current ->
            if (current == null || current.steps.isEmpty()) return@update current
            val steps = current.steps.toMutableList()
            val last = steps.last()
            steps[steps.lastIndex] = last.copy(
                status = status,
                detail = detail ?: last.detail,
            )
            current.copy(steps = steps)
        }
    }

    fun finish(report: String, success: Boolean) {
        val current = _current.value ?: return
        val finished = current.copy(
            finishedAt = System.currentTimeMillis(),
            running = false,
            report = report,
            success = success,
        )
        _current.value = finished
        _history.update { listOf(finished) + it.take(49) }
    }

    fun clearHistory() {
        _history.value = emptyList()
        _current.value = null
    }
}
