package com.universalaiagent.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalaiagent.AppContainer
import com.universalaiagent.data.local.AppSettingsData
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.TaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutingUiState(
    val task: TaskType = TaskType.GENERAL_CHAT,
    val included: List<ApiConfig> = emptyList(),
    val excluded: List<ApiConfig> = emptyList(),
) {
    val allEmpty: Boolean get() = included.isEmpty() && excluded.isEmpty()
}

class RoutingViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val selectedTask = MutableStateFlow(TaskType.GENERAL_CHAT)
    val task: StateFlow<TaskType> = selectedTask

    val configs: StateFlow<List<ApiConfig>> = container.providers.configs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    private val settings: StateFlow<AppSettingsData> = container.settings.data.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettingsData(),
    )

    val state: StateFlow<RoutingUiState> = combine(configs, settings, selectedTask) { configs, settings, task ->
        val defaultOrder = configs.sortedWith(compareBy({ it.priority }, { it.createdAt })).map { it.id }
        val route = settings.routeFor(task).ifEmpty { defaultOrder }
        val included = route.mapNotNull { id -> configs.firstOrNull { it.id == id } }
        val excluded = configs.filterNot { config -> included.any { it.id == config.id } }
        RoutingUiState(task, included, excluded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutingUiState())

    fun selectTask(task: TaskType) {
        selectedTask.value = task
    }

    fun move(id: String, direction: Int) {
        viewModelScope.launch {
            val current = state.value
            val ids = current.included.map { it.id }.toMutableList()
            val index = ids.indexOf(id)
            if (index < 0) return@launch
            val target = (index + direction).coerceIn(0, ids.lastIndex)
            if (target == index) return@launch
            val value = ids.removeAt(index)
            ids.add(target, value)
            container.settings.setRoute(current.task, ids)
        }
    }

    fun moveTo(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val current = state.value
            val ids = current.included.map { it.id }.toMutableList()
            if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) return@launch
            val value = ids.removeAt(fromIndex)
            ids.add(toIndex, value)
            container.settings.setRoute(current.task, ids)
        }
    }

    fun setIncluded(id: String, included: Boolean) {
        viewModelScope.launch {
            val current = state.value
            val ids = current.included.map { it.id }.toMutableList()
            if (included) {
                if (id !in ids) ids.add(id)
            } else {
                ids.remove(id)
            }
            container.settings.setRoute(current.task, ids)
        }
    }

    fun resetToPriority() {
        viewModelScope.launch {
            val current = state.value
            val ids = configs.value.sortedWith(compareBy({ it.priority }, { it.createdAt })).map { it.id }
            container.settings.setRoute(current.task, ids)
        }
    }
}
