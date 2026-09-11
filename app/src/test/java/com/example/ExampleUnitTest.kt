package com.example

import androidx.compose.ui.unit.dp
import com.cellular.rpc.domain.miniapp.ActionExecutor
import com.cellular.rpc.domain.miniapp.SimpleMathParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSimpleMathParser_orderOfOperations() {
    val result = SimpleMathParser.parse("100 * 0.18")
    assertEquals(18.0, result, 0.001)

    val complex = SimpleMathParser.parse("50 + 100 * 0.20")
    assertEquals(70.0, complex, 0.001)

    val parenthesized = SimpleMathParser.parse("(50 + 10) * 0.15")
    assertEquals(9.0, parenthesized, 0.001)
  }

  @Test
  fun testActionExecutor_calculateTipAndTotal() {
    val initialState = mapOf<String, Any?>(
      "bill" to "50.00",
      "tip_pct" to 20,
      "tip" to 0.0,
      "total" to 0.0
    )

    val calcTipAction = mapOf<String, Any?>(
      "action" to "CALCULATE",
      "formula" to "bill * (tip_pct / 100)",
      "target" to "tip"
    )

    val (stateWithTip, changed1) = ActionExecutor.execute(calcTipAction, initialState, null)
    assertTrue(changed1)
    assertEquals(10.0, (stateWithTip["tip"] as Number).toDouble(), 0.001)

    val calcTotalAction = mapOf<String, Any?>(
      "action" to "CALCULATE",
      "formula" to "bill + tip",
      "target" to "total"
    )

    val (finalState, changed2) = ActionExecutor.execute(calcTotalAction, stateWithTip, null)
    assertTrue(changed2)
    assertEquals(60.0, (finalState["total"] as Number).toDouble(), 0.001)
  }

  @Test
  fun testSmsRow_multiPartAssemblyLogic() {
    val now = System.currentTimeMillis()
    val part1 = com.cellular.rpc.transport.service.SmsRow(101L, "+16462619684", "{\"type\":\"sdui\",", now)
    val part2 = com.cellular.rpc.transport.service.SmsRow(102L, "+16462619684", "\"title\":\"Weather\",", now + 100)
    val part3 = com.cellular.rpc.transport.service.SmsRow(103L, "+16462619684", "\"temp\":72}", now + 200)

    val unorderedList = listOf(part2, part1, part3)
    val clusters = unorderedList.groupBy { "${it.address.filter { c -> c.isDigit() || c == '+' }}:${it.date / 4000L}" }
    
    assertEquals(1, clusters.size)
    val segments = clusters.values.first()
    val ordered = segments.sortedBy { it.id }
    val fullBody = ordered.joinToString("") { it.body }

    assertEquals("{\"type\":\"sdui\",\"title\":\"Weather\",\"temp\":72}", fullBody)
  }

  @Test
  fun testDualResponseParser_multiPartPayloadExtraction() {
    val payload = "Hello user!\n---CELLULAR_DATA---\n{\"type\":\"weather\",\"city\":\"New York\",\"temp\":72}\n---PAYLOAD---"
    val parsed = com.cellular.rpc.engine.DualResponseParser.parse(payload)
    assertEquals("Hello user!", parsed.conversationalText.trim())
    assertNotNull(parsed.widgetData)
    assertEquals("weather", parsed.widgetData?.type)
  }

  @Test
  fun testBlueprintLinter_validAndInvalidBlueprints() {
    val validJson = """
      {
        "type": "mini_app_blueprint",
        "appId": "tip_calc_1",
        "metadata": { "title": "Tip Calculator", "icon": "calculator" },
        "initialState": { "bill": "50.00", "tip_pct": 20, "tip": 0.0, "total": 0.0 },
        "ui": {
          "type": "Column",
          "children": [
            { "type": "Input", "hint": "Bill Amount", "bind": "bill" },
            { "type": "Select", "bind": "tip_pct", "options": [15, 18, 20, 25] },
            { "type": "Button", "text": "Calculate", "onClick": { "action": "CALCULATE", "formula": "bill * (tip_pct / 100)", "target": "tip" } }
          ]
        }
      }
    """.trimIndent()

    val blueprint = com.cellular.rpc.domain.miniapp.MiniAppBlueprint.fromJson(validJson)
    assertNotNull(blueprint)
    val lintResult = com.cellular.rpc.domain.miniapp.BlueprintLinter.lint(blueprint!!)
    assertTrue(lintResult.isValid)
    assertEquals(0, lintResult.errors.size)

    // Test missing appId
    val invalidJson = """
      {
        "type": "mini_app_blueprint",
        "appId": "",
        "metadata": { "title": "Broken App" },
        "initialState": {},
        "ui": { "type": "Column" }
      }
    """.trimIndent()
    val brokenBlueprint = com.cellular.rpc.domain.miniapp.MiniAppBlueprint.fromJson(invalidJson)
    assertNotNull(brokenBlueprint)
    val brokenLintResult = com.cellular.rpc.domain.miniapp.BlueprintLinter.lint(brokenBlueprint!!)
    assertFalse(brokenLintResult.isValid)
    assertTrue(brokenLintResult.errors.any { it.contains("appId") })
  }

  @Test
  fun testBlueprintFuzzer_syntheticBoundaryExecution() {
    val blueprintJson = """
      {
        "type": "mini_app_blueprint",
        "appId": "counter_fuzz",
        "metadata": { "title": "Counter App" },
        "initialState": { "count": 10, "text": "Hello" },
        "ui": {
          "type": "Column",
          "children": [
            { "type": "Button", "text": "+", "onClick": { "action": "MUTATE_STATE", "op": "INCREMENT", "prop": "count", "step": 1 } },
            { "type": "Button", "text": "-", "onClick": { "action": "MUTATE_STATE", "op": "DECREMENT", "prop": "count", "step": 1 } }
          ]
        }
      }
    """.trimIndent()

    val blueprint = com.cellular.rpc.domain.miniapp.MiniAppBlueprint.fromJson(blueprintJson)
    assertNotNull(blueprint)
    val fuzzResult = com.cellular.rpc.domain.miniapp.BlueprintFuzzer.fuzz(blueprint!!)
    assertTrue(fuzzResult.passed)
    assertTrue(fuzzResult.actionsTested > 0)
    assertTrue(fuzzResult.issues.isEmpty())
  }

  @Test
  fun testBlueprintPatcher_statePreservingDeltaUpdate() {
    val baseJson = """
      {
        "type": "mini_app_blueprint",
        "appId": "todo_app",
        "version": 1,
        "metadata": { "title": "Todo List", "icon": "checklist" },
        "initialState": { "input_task": "", "tasks": [] },
        "ui": {
          "type": "Column",
          "children": [
            { "type": "Text", "text": "My Tasks" }
          ]
        }
      }
    """.trimIndent()

    val blueprint = com.cellular.rpc.domain.miniapp.MiniAppBlueprint.fromJson(baseJson)
    assertNotNull(blueprint)

    val activeRuntimeState = mapOf<String, Any?>(
      "input_task" to "Buy groceries",
      "tasks" to listOf(mapOf("id" to "1", "name" to "Walk dog", "done" to true))
    )

    // RFC 6902 patch updating title
    val patchJson = """
      [
        { "op": "replace", "path": "/metadata/title", "value": "Super Todos" }
      ]
    """.trimIndent()

    val patchResult = com.cellular.rpc.domain.miniapp.BlueprintPatcher.applyDeltaPatch(
      currentBlueprint = blueprint!!,
      currentRuntimeState = activeRuntimeState,
      patchJsonStr = patchJson
    )

    assertTrue(patchResult.success)
    assertNotNull(patchResult.patchedBlueprint)
    assertEquals("Super Todos", patchResult.patchedBlueprint?.metadata?.title)
    // Verify runtime user inputs were preserved!
    assertEquals("Buy groceries", patchResult.preservedState["input_task"])
  }

  @Test
  fun testSemanticDesignTokens_tokenResolutions() {
    assertEquals(0.dp, com.cellular.rpc.domain.miniapp.SemanticDesignTokens.resolveSpacing("none"))
    assertEquals(8.dp, com.cellular.rpc.domain.miniapp.SemanticDesignTokens.resolveSpacing("sm"))
    assertEquals(16.dp, com.cellular.rpc.domain.miniapp.SemanticDesignTokens.resolveSpacing("lg"))

    assertEquals(999.dp, com.cellular.rpc.domain.miniapp.SemanticDesignTokens.resolveCornerRadius("pill"))
    assertEquals(12.dp, com.cellular.rpc.domain.miniapp.SemanticDesignTokens.resolveCornerRadius("md"))

    val searchIcon = com.cellular.rpc.domain.miniapp.SemanticDesignTokens.resolveIcon("search")
    assertNotNull(searchIcon)
  }
}
