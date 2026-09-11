package com.example

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
}
