package com.universalaiagent.automation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface AgentAction {
    data class LaunchApp(val appName: String) : AgentAction
    data class Click(val target: String, val longPress: Boolean = false) : AgentAction
    data class Scroll(val direction: String) : AgentAction
    data class InputText(val target: String?, val text: String) : AgentAction
    data object PressBack : AgentAction
    data object ReadScreen : AgentAction
    data class Wait(val millis: Long) : AgentAction
    data class RequestConfirmation(val reason: String) : AgentAction
    data object FinishTask : AgentAction
}

data class PlanResult(
    val actions: List<AgentAction>,
    val errors: List<String>,
)

object ActionValidator {
    fun parsePlan(raw: String): PlanResult {
        val payload = extractJson(raw)
            ?: return PlanResult(emptyList(), listOf("The model did not return a JSON action plan."))
        val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(payload) }
            .getOrElse { return PlanResult(emptyList(), listOf("The action plan is not valid JSON.")) }
        val actions = mutableListOf<AgentAction>()
        val errors = mutableListOf<String>()
        when (root) {
            is JsonObject -> {
                val list = root["actions"]
                if (list is JsonArray) {
                    list.forEachIndexed { index, item ->
                        val obj = item as? JsonObject
                        if (obj == null) errors += "Action $index is not an object." else {
                            validate(obj).also { result ->
                                if (result.first != null) actions += result.first!!
                                if (result.second != null) errors += result.second!!
                            }
                        }
                    }
                } else {
                    val result = validate(root)
                    if (result.first != null) actions += result.first!!
                    if (result.second != null) errors += result.second!!
                }
            }
            is JsonArray -> root.forEachIndexed { index, item ->
                val obj = item as? JsonObject
                if (obj == null) errors += "Action $index is not an object." else {
                    val result = validate(obj)
                    if (result.first != null) actions += result.first!!
                    if (result.second != null) errors += result.second!!
                }
            }
            else -> errors += "The action plan must be a JSON object or array."
        }
        if (actions.isEmpty() && errors.isEmpty()) errors += "The model returned an empty action plan."
        return PlanResult(actions, errors)
    }

    fun validate(obj: JsonObject): Pair<AgentAction?, String?> {
        val name = obj.string("action")?.lowercase()?.trim()
            ?: return null to "An action is missing the \"action\" field."
        return when (name) {
            "launch_app", "launch" -> {
                val target = obj.string("target") ?: obj.string("app") ?: obj.string("app_name")
                if (target.isNullOrBlank()) null to "launch_app requires an app name."
                else if (target.length > 200) null to "launch_app target is too long."
                else AgentAction.LaunchApp(target) to null
            }
            "click" -> {
                val target = obj.string("target") ?: obj.string("text") ?: obj.string("description")
                if (target.isNullOrBlank()) null to "click requires a target."
                else if (target.length > 200) null to "click target is too long."
                else AgentAction.Click(target) to null
            }
            "long_click", "longclick" -> {
                val target = obj.string("target") ?: obj.string("text") ?: obj.string("description")
                if (target.isNullOrBlank()) null to "long_click requires a target."
                else if (target.length > 200) null to "long_click target is too long."
                else AgentAction.Click(target, longPress = true) to null
            }
            "scroll" -> {
                val direction = obj.string("direction")?.lowercase()?.trim() ?: "forward"
                val normalized = when (direction) {
                    "forward", "down" -> "forward"
                    "backward", "back", "up" -> "backward"
                    else -> return null to "scroll direction must be forward or backward."
                }
                AgentAction.Scroll(normalized) to null
            }
            "input_text", "type", "set_text" -> {
                val text = obj.string("text") ?: obj.string("value")
                if (text == null) null to "input_text requires text."
                else if (text.length > 4000) null to "input_text is longer than 4000 characters."
                else AgentAction.InputText(obj.string("target"), text) to null
            }
            "press_back", "back" -> AgentAction.PressBack to null
            "read_screen" -> AgentAction.ReadScreen to null
            "wait" -> {
                val millis = obj.long("millis") ?: obj.long("duration_ms") ?: obj.long("ms") ?: 800
                AgentAction.Wait(millis.coerceIn(100, 5000)) to null
            }
            "request_confirmation", "confirm" -> {
                val reason = obj.string("reason") ?: obj.string("message")
                if (reason.isNullOrBlank()) null to "request_confirmation requires a reason."
                else AgentAction.RequestConfirmation(reason.take(300)) to null
            }
            "finish_task", "finish", "done" -> AgentAction.FinishTask to null
            else -> null to "Unsupported action \"$name\". Only structured UI actions are allowed."
        }
    }

    fun requiresConfirmation(action: AgentAction, instruction: String, confirmSetting: Boolean): Boolean {
        if (!confirmSetting) return false
        val actionText = when (action) {
            is AgentAction.Click -> action.target
            is AgentAction.InputText -> action.text
            is AgentAction.LaunchApp -> action.appName
            else -> ""
        }.lowercase()
        val instructionText = instruction.lowercase()
        val sensitiveTarget = SENSITIVE_TERMS.any { actionText.contains(it) }
        val sensitiveInstruction = SENSITIVE_TERMS.any { instructionText.contains(it) }
        return when (action) {
            is AgentAction.Click -> sensitiveTarget || (sensitiveInstruction && actionText.isNotBlank())
            is AgentAction.InputText -> sensitiveInstruction &&
                listOf("transfer", "payment", "password", "delete").any { instructionText.contains(it) }
            else -> false
        }
    }

    fun signature(action: AgentAction): String = when (action) {
        is AgentAction.LaunchApp -> "launch:${action.appName.lowercase()}"
        is AgentAction.Click -> "click:${action.longPress}:${action.target.lowercase()}"
        is AgentAction.Scroll -> "scroll:${action.direction}"
        is AgentAction.InputText -> "input:${action.target.orEmpty().lowercase()}:${action.text.lowercase()}"
        AgentAction.PressBack -> "back"
        AgentAction.ReadScreen -> "read"
        is AgentAction.Wait -> "wait:${action.millis}"
        is AgentAction.RequestConfirmation -> "confirm:${action.reason.lowercase()}"
        AgentAction.FinishTask -> "finish"
    }

    fun extractJson(raw: String): String? {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1)?.trim()
        val candidate = fenced?.takeIf { it.isNotEmpty() } ?: raw.trim()
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false
        for (index in candidate.indices) {
            val char = candidate[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{', '[' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}', ']' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        return candidate.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    private val SENSITIVE_TERMS = listOf(
        "send",
        "delete",
        "remove",
        "purchase",
        "buy",
        "pay",
        "transfer",
        "publish",
        "post",
        "confirm",
        "uninstall",
        "format",
        "reset",
        "grant",
        "allow",
        "subscribe",
        "order",
        "book",
        "call",
        "message",
    )
}
