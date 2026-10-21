package com.universalaiagent.data.routing

import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.Capability
import com.universalaiagent.data.model.TaskType

object RoutePlanner {
    fun candidates(
        configs: List<ApiConfig>,
        routeIds: List<String>,
        task: TaskType,
        manualConfigId: String? = null,
        manualMode: Boolean = false,
        manualAllowFallback: Boolean = false,
    ): List<ApiConfig> {
        val eligible = configs
            .filter { it.enabled }
            .filter { supportsTask(it, task) }

        if (manualMode) {
            val manual = manualConfigId?.let { id -> eligible.firstOrNull { it.id == id } }
                ?: return emptyList()
            if (!manualAllowFallback) return listOf(manual)
            val orderedIds = buildList {
                add(manual.id)
                routeIds.filterNot { it == manual.id }.forEach { add(it) }
            }
            return orderBy(eligible, orderedIds)
        }

        return if (routeIds.isEmpty()) {
            eligible.sortedWith(compareBy({ it.priority }, { it.createdAt }))
        } else {
            orderBy(eligible, routeIds)
        }
    }

    fun supportsTask(config: ApiConfig, task: TaskType): Boolean {
        if (!task.requiresVision) return true
        if (config.supportsVision) return true
        val model = config.discoveredModels.firstOrNull { it.id == config.model }
        return model?.capabilities?.contains(Capability.VISION) == true
    }

    private fun orderBy(configs: List<ApiConfig>, ids: List<String>): List<ApiConfig> {
        val byId = configs.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }
}
