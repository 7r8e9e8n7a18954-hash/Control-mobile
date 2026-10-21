package com.universalaiagent.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionValidatorTest {

    @Test
    fun `parses fenced json plan into structured actions`() {
        val raw = """
            Here is the plan:
            ```json
            {"actions":[
              {"action":"click","target":"Search"},
              {"action":"input_text","target":"Search box","text":"weather"},
              {"action":"finish_task"}
            ]}
            ```
        """.trimIndent()

        val result = ActionValidator.parsePlan(raw)

        assertEquals(
            listOf(
                AgentAction.Click("Search"),
                AgentAction.InputText("Search box", "weather"),
                AgentAction.FinishTask,
            ),
            result.actions,
        )
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `rejects unsupported executable actions`() {
        val result = ActionValidator.parsePlan("""{"action":"shell","command":"rm -rf /"}""")

        assertTrue(result.actions.isEmpty())
        assertTrue(result.errors.single().contains("Unsupported action"))
    }

    @Test
    fun `reports malformed json`() {
        val result = ActionValidator.parsePlan("not json at all")

        assertTrue(result.actions.isEmpty())
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `requires a target for click`() {
        val (action, error) = ActionValidator.validate(
            kotlinx.serialization.json.Json.parseToJsonElement("""{"action":"click"}""")
                .let { it as kotlinx.serialization.json.JsonObject },
        )

        assertNull(action)
        assertNotNull(error)
    }

    @Test
    fun `clamps wait duration to safe range`() {
        val tooShort = ActionValidator.parsePlan("""{"action":"wait","millis":1}""")
        val tooLong = ActionValidator.parsePlan("""{"action":"wait","millis":999999}""")

        assertEquals(AgentAction.Wait(100), tooShort.actions.single())
        assertEquals(AgentAction.Wait(5000), tooLong.actions.single())
    }

    @Test
    fun `rejects oversized input text`() {
        val text = "a".repeat(4001)
        val result = ActionValidator.parsePlan("""{"action":"input_text","text":"$text"}""")

        assertTrue(result.actions.isEmpty())
        assertTrue(result.errors.single().contains("4000"))
    }

    @Test
    fun `normalizes scroll aliases`() {
        val down = ActionValidator.parsePlan("""{"action":"scroll","direction":"down"}""")
        val up = ActionValidator.parsePlan("""{"action":"scroll","direction":"up"}""")

        assertEquals(AgentAction.Scroll("forward"), down.actions.single())
        assertEquals(AgentAction.Scroll("backward"), up.actions.single())
    }

    @Test
    fun `flags sensitive click target for confirmation`() {
        val required = ActionValidator.requiresConfirmation(
            AgentAction.Click("Send message"),
            instruction = "Open the app",
            confirmSetting = true,
        )

        assertTrue(required)
    }

    @Test
    fun `does not flag benign click`() {
        val required = ActionValidator.requiresConfirmation(
            AgentAction.Click("Search"),
            instruction = "Search for a recipe",
            confirmSetting = true,
        )

        assertFalse(required)
    }

    @Test
    fun `confirmation setting can disable prompts`() {
        val required = ActionValidator.requiresConfirmation(
            AgentAction.Click("Delete forever"),
            instruction = "delete it",
            confirmSetting = false,
        )

        assertFalse(required)
    }

    @Test
    fun `input text in payment instruction needs confirmation`() {
        val required = ActionValidator.requiresConfirmation(
            AgentAction.InputText("Amount", "500"),
            instruction = "transfer 500 to my friend",
            confirmSetting = true,
        )

        assertTrue(required)
    }

    @Test
    fun `signature is case insensitive and distinguishes long press`() {
        val click = ActionValidator.signature(AgentAction.Click("Search"))
        val longClick = ActionValidator.signature(AgentAction.Click("Search", longPress = true))

        assertEquals("click:false:search", click)
        assertEquals("click:true:search", longClick)
    }

    @Test
    fun `extracts json embedded in prose with braces in strings`() {
        val raw = """The result is {"actions":[{"action":"input_text","text":"use {braces}"}]} thanks."""

        val extracted = ActionValidator.extractJson(raw)

        assertNotNull(extracted)
        assertEquals("""{"actions":[{"action":"input_text","text":"use {braces}"}]}""", extracted)
    }
}
