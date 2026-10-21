package com.universalaiagent.routing

import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.Capability
import com.universalaiagent.data.model.ModelInfo
import com.universalaiagent.data.model.ProviderType
import com.universalaiagent.data.model.TaskType
import com.universalaiagent.data.routing.RoutePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {

    @Test
    fun `orders by priority then creation when no route is set`() {
        val late = config("late", priority = 1, createdAt = 1)
        val early = config("early", priority = 0, createdAt = 5)
        val first = config("first", priority = 0, createdAt = 2)

        val result = RoutePlanner.candidates(
            configs = listOf(late, early, first),
            routeIds = emptyList(),
            task = TaskType.GENERAL_CHAT,
        )

        assertEquals(listOf("first", "early", "late"), result.map { it.id })
    }

    @Test
    fun `explicit route wins over priority and ignores unknown ids`() {
        val a = config("a", priority = 0)
        val b = config("b", priority = 1)
        val c = config("c", priority = 2)

        val result = RoutePlanner.candidates(
            configs = listOf(a, b, c),
            routeIds = listOf("c", "missing", "a"),
            task = TaskType.GENERAL_CHAT,
        )

        assertEquals(listOf("c", "a"), result.map { it.id })
    }

    @Test
    fun `disabled configurations are never candidates`() {
        val enabled = config("enabled", enabled = true)
        val disabled = config("disabled", enabled = false)

        val result = RoutePlanner.candidates(
            configs = listOf(enabled, disabled),
            routeIds = listOf("disabled", "enabled"),
            task = TaskType.GENERAL_CHAT,
        )

        assertEquals(listOf("enabled"), result.map { it.id })
    }

    @Test
    fun `vision task filters configurations without image capability`() {
        val textOnly = config("text")
        val flaggedVision = config("flagged", supportsVision = true)
        val discoveredVision = config(
            "discovered",
            discoveredModels = listOf(
                ModelInfo(
                    id = "vision-model",
                    provider = ProviderType.OPENAI,
                    capabilities = setOf(Capability.TEXT, Capability.VISION),
                ),
            ),
            model = "vision-model",
        )

        val result = RoutePlanner.candidates(
            configs = listOf(textOnly, flaggedVision, discoveredVision),
            routeIds = listOf("text", "flagged", "discovered"),
            task = TaskType.IMAGE_UNDERSTANDING,
        )

        assertEquals(listOf("flagged", "discovered"), result.map { it.id })
    }

    @Test
    fun `non-vision task keeps text-only configurations`() {
        val textOnly = config("text")

        val result = RoutePlanner.candidates(
            configs = listOf(textOnly),
            routeIds = emptyList(),
            task = TaskType.GENERAL_CHAT,
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `manual mode returns only the selected configuration`() {
        val manual = config("manual")
        val other = config("other")

        val result = RoutePlanner.candidates(
            configs = listOf(manual, other),
            routeIds = listOf("other", "manual"),
            task = TaskType.GENERAL_CHAT,
            manualConfigId = "manual",
            manualMode = true,
            manualAllowFallback = false,
        )

        assertEquals(listOf("manual"), result.map { it.id })
    }

    @Test
    fun `manual mode with fallback starts with the manual configuration`() {
        val manual = config("manual")
        val other = config("other")
        val third = config("third")

        val result = RoutePlanner.candidates(
            configs = listOf(other, manual, third),
            routeIds = listOf("other", "third"),
            task = TaskType.GENERAL_CHAT,
            manualConfigId = "manual",
            manualMode = true,
            manualAllowFallback = true,
        )

        assertEquals(listOf("manual", "other", "third"), result.map { it.id })
    }

    @Test
    fun `manual mode with a missing or incompatible configuration returns empty`() {
        val result = RoutePlanner.candidates(
            configs = listOf(config("text")),
            routeIds = listOf("text"),
            task = TaskType.SCREEN_ANALYSIS,
            manualConfigId = "text",
            manualMode = true,
            manualAllowFallback = true,
        )

        assertTrue(result.isEmpty())
    }

    private fun config(
        id: String,
        priority: Int = 0,
        enabled: Boolean = true,
        createdAt: Long = 0,
        supportsVision: Boolean = false,
        discoveredModels: List<ModelInfo> = emptyList(),
        model: String = "model",
    ) = ApiConfig(
        id = id,
        provider = ProviderType.OPENAI,
        nickname = id,
        model = model,
        priority = priority,
        enabled = enabled,
        supportsVision = supportsVision,
        discoveredModels = discoveredModels,
        createdAt = createdAt,
    )
}
