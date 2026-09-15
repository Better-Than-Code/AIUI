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
    val now = 1000000000000L
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

  @Test
  fun testCellularAckParser_allFormats() {
    // 1. Colon format
    val colonAck = com.cellular.rpc.domain.protocol.CellularAckParser.parse("ACK:2.1.0:5B212384:1,2")
    assertNotNull(colonAck)
    assertEquals("5B212384", colonAck?.hash)
    assertEquals(listOf(1, 2), colonAck?.chunks)

    // 2. MCP format
    val mcpAck = com.cellular.rpc.domain.protocol.CellularAckParser.parse("[MCP_ACK 1/67 hash=5B212384]")
    assertNotNull(mcpAck)
    assertEquals("5B212384", mcpAck?.hash)
    assertEquals(listOf(1), mcpAck?.chunks)

    // 3. Natural language format
    val nlAck = com.cellular.rpc.domain.protocol.CellularAckParser.parse("ack chunks 1-2 of 67 (hash 5b212384). receiving stream.")
    assertNotNull(nlAck)
    assertEquals("5b212384", nlAck?.hash)
    assertEquals(listOf(1, 2), nlAck?.chunks)

    // 4. JSON format (embedded after delimiter)
    val jsonPayload = "Receiving stream\n---CELLULAR_DATA---\n{\"version\":\"2.1.0\",\"rpc\":{\"type\":\"ack\",\"hash\":\"5B212384\",\"chunks\":[1,2],\"status\":\"OK\"}}"
    val jsonAck = com.cellular.rpc.domain.protocol.CellularAckParser.parse(jsonPayload)
    assertNotNull(jsonAck)
    assertEquals("5B212384", jsonAck?.hash)
    assertEquals(listOf(1, 2), jsonAck?.chunks)

    // 5. Non-ACK payload should return null
    val normalMessage = "What is the weather today?"
    val noAck = com.cellular.rpc.domain.protocol.CellularAckParser.parse(normalMessage)
    assertNull(noAck)
  }

  @Test
  fun testSlidingWindowController_permitManagementAndDeadlockRecovery() {
    val window = com.cellular.rpc.domain.protocol.SlidingWindowController(windowSize = 4)
    assertEquals(0, window.getInFlightCount())
    assertTrue(window.canTransmit())

    // Transmit 4 frames
    val f0 = com.cellular.rpc.domain.protocol.Frame(sessionId = 1, pktType = 1, seqNo = 0, payload = ByteArray(10))
    val f1 = com.cellular.rpc.domain.protocol.Frame(sessionId = 1, pktType = 1, seqNo = 0, payload = ByteArray(10))
    val f2 = com.cellular.rpc.domain.protocol.Frame(sessionId = 1, pktType = 1, seqNo = 0, payload = ByteArray(10))
    val f3 = com.cellular.rpc.domain.protocol.Frame(sessionId = 1, pktType = 1, seqNo = 0, payload = ByteArray(10))

    val s0 = window.registerOutbound(f0)
    val s1 = window.registerOutbound(f1)
    val s2 = window.registerOutbound(f2)
    val s3 = window.registerOutbound(f3)

    assertEquals(4, window.getInFlightCount())
    assertFalse(window.canTransmit())

    // Acknowledge one frame
    window.markFrameAcknowledged(s0)
    assertEquals(3, window.getInFlightCount())
    assertTrue(window.canTransmit())

    // Out of order ack (s2 before s1)
    window.markFrameAcknowledged(s2)
    assertEquals(2, window.getInFlightCount())

    // Evict remaining stalled frames
    val evicted = window.evictStalledFrames(timeoutMs = 0L, currentTimeMs = System.currentTimeMillis() + 50000L)
    assertEquals(2, evicted)
    assertEquals(0, window.getInFlightCount())
    assertTrue(window.canTransmit())

    // Test explicit resetInFlight
    window.registerOutbound(f0)
    window.registerOutbound(f1)
    assertEquals(2, window.getInFlightCount())
    window.resetInFlight()
    assertEquals(0, window.getInFlightCount())
    assertTrue(window.canTransmit())
  }

  @Test
  fun testNamedSkeletonModel_serializationAndDefaults() {
    val skeleton = com.cellular.rpc.engine.WidgetData.Skeleton(
      label = "Loading Market Ticker...",
      targetType = "market_ticker",
      iconEmoji = "📈"
    )
    val jsonStr = skeleton.toJson()
    assertTrue(jsonStr.contains("\"type\":\"skeleton\""))
    assertTrue(jsonStr.contains("\"label\":\"Loading Market Ticker...\""))
    assertTrue(jsonStr.contains("\"target_type\":\"market_ticker\""))
    assertTrue(jsonStr.contains("\"icon\":\"📈\""))

    val parsed = com.cellular.rpc.engine.WidgetData.parse(jsonStr)
    assertTrue(parsed is com.cellular.rpc.engine.WidgetData.Skeleton)
    val parsedSkeleton = parsed as com.cellular.rpc.engine.WidgetData.Skeleton
    assertEquals("Loading Market Ticker...", parsedSkeleton.label)
    assertEquals("market_ticker", parsedSkeleton.targetType)
    assertEquals("📈", parsedSkeleton.iconEmoji)

    // Verify backward compatibility when optional fields are omitted in legacy JSON
    val legacyJson = org.json.JSONObject().apply {
      put("type", "skeleton")
      put("label", "Loading Blueprint...")
    }
    val parsedLegacy = com.cellular.rpc.engine.WidgetData.Skeleton.fromJson(legacyJson)
    assertEquals("Loading Blueprint...", parsedLegacy.label)
    assertEquals("blueprint", parsedLegacy.targetType)
    assertEquals("✨", parsedLegacy.iconEmoji)
  }

  @Test
  fun testChatMessage_revisionAndSupersededProperties() {
    val initialMsg = com.cellular.rpc.engine.ChatMessage(
      sender = com.cellular.rpc.engine.MessageSender.AI_GATEWAY,
      text = "Initial",
      revision = 1,
      isSuperseded = false,
      supersededByMessageId = null
    )
    assertEquals(1, initialMsg.revision)
    assertFalse(initialMsg.isSuperseded)
    assertNull(initialMsg.supersededByMessageId)

    val updatedMsg = initialMsg.copy(
      revision = 2,
      isSuperseded = true,
      supersededByMessageId = "msg_next_gen_123"
    )
    assertEquals(2, updatedMsg.revision)
    assertTrue(updatedMsg.isSuperseded)
    assertEquals("msg_next_gen_123", updatedMsg.supersededByMessageId)
  }

  @Test
  fun testWidgetModels_consistentWidgetIdExposure() {
    val weather = com.cellular.rpc.engine.WidgetData.Weather(72, "San Francisco", "Sunny")
    assertEquals("weather_san_francisco", weather.widgetId)

    val news = com.cellular.rpc.engine.WidgetData.NewsDigest("news_42", "Solar Flare Detected", "No disruption")
    assertEquals("news_news_42", news.widgetId)

    val ticker = com.cellular.rpc.engine.WidgetData.MarketTicker("ticker_btc", "BTC", "64,200", "+3.2%")
    assertEquals("ticker_btc", ticker.widgetId)

    val transfer = com.cellular.rpc.engine.WidgetData.CellularTransfer("tx_99", "Alice", "$50", "Lunch", "CONFIRMED")
    assertEquals("tx_99", transfer.widgetId)

    val tasks = com.cellular.rpc.engine.WidgetData.TaskChecklist("todo_list", "My Tasks", listOf("Item 1"), listOf(false))
    assertEquals("todo_list", tasks.widgetId)

    val miniApp = com.cellular.rpc.engine.WidgetData.MiniAppPreview(
      appId = "calc_app",
      title = "Calculator",
      rawBlueprintJson = "{}"
    )
    assertEquals("calc_app", miniApp.widgetId)

    val patch = com.cellular.rpc.engine.WidgetData.MiniAppPatch(
      appId = "calc_app",
      patchJsonStr = "[]"
    )
    assertEquals("calc_app", patch.widgetId)
  }

  /**
   * FEAT-09: Unit test CarrierApnResolver fallback parsing.
   */
  @Test
  fun testCarrierApnResolverProfiles() {
    val tmobile = com.cellular.rpc.transport.apn.CarrierNetworkProfile(
      carrierName = "T-Mobile",
      simOperator = "310260",
      mcc = "310",
      mnc = "260",
      activeMmscUrl = "http://mms.msg.eng.t-mobile.com/mms/wapenc",
      mmsProxy = null,
      mmsPort = null,
      isApnResolvedFromSystem = false,
      subId = 1
    )
    assertEquals("310", tmobile.mcc)
    assertEquals("260", tmobile.mnc)
    assertTrue(tmobile.activeMmscUrl.contains("t-mobile.com"))

    val att = com.cellular.rpc.transport.apn.CarrierNetworkProfile(
      carrierName = "AT&T",
      simOperator = "310410",
      mcc = "310",
      mnc = "410",
      activeMmscUrl = "http://mmsc.mobile.att.net",
      mmsProxy = "proxy.mobile.att.net",
      mmsPort = 80,
      isApnResolvedFromSystem = false,
      subId = 2
    )
    assertEquals("310410", att.simOperator)
    assertEquals("proxy.mobile.att.net", att.mmsProxy)
    assertEquals(80, att.mmsPort)
  }

  /**
   * FEAT-11: Test voice note seek math and speed toggling.
   */
  @Test
  fun testVoiceNoteScrubbingAndSpeedToggles() {
    val durationMs = 45000L // 45 seconds
    val scrubFraction = 0.40f // 40%
    val calculatedSeekMs = (scrubFraction * durationMs).toLong()
    assertEquals(18000L, calculatedSeekMs)

    var currentSpeed = 1.0f
    val speeds = listOf(1.0f, 1.5f, 2.0f)
    fun cycleSpeed(spd: Float): Float = when (spd) {
      1.0f -> 1.5f
      1.5f -> 2.0f
      else -> 1.0f
    }

    currentSpeed = cycleSpeed(currentSpeed)
    assertEquals(1.5f, currentSpeed, 0.01f)
    currentSpeed = cycleSpeed(currentSpeed)
    assertEquals(2.0f, currentSpeed, 0.01f)
    currentSpeed = cycleSpeed(currentSpeed)
    assertEquals(1.0f, currentSpeed, 0.01f)
  }

  /**
   * FEAT-12: Test SDUI Playground AST blueprint parsing and linter.
   */
  @Test
  fun testSduiPlaygroundTemplateParsingAndLinting() {
    val telemetryJson = """{
      "appId": "app_telemetry_studio",
      "version": 1,
      "metadata": {
        "title": "Dual Telemetry Lab",
        "description": "Comparative time-series sensor graph with interactive scrubber",
        "icon": "trending_up"
      },
      "initialState": {
        "primary_stream": [18.2, 22.4, 25.1, 23.8],
        "secondary_stream": [14.0, 16.5, 19.8, 22.0],
        "status": "Telemetry Online"
      },
      "uiRoot": {
        "type": "column",
        "children": [
          {
            "type": "text",
            "text": "Comparative Inverter Telemetry",
            "fontSize": 16,
            "fontWeight": "bold"
          },
          {
            "type": "sparkline",
            "bind": "primary_stream",
            "secondary_bind": "secondary_stream",
            "color": "#00E5FF",
            "secondary_color": "#00E676",
            "height": 140
          }
        ]
      }
    }"""

    val blueprint = com.cellular.rpc.domain.miniapp.MiniAppBlueprint.fromJson(telemetryJson)
    assertNotNull(blueprint)
    assertEquals("app_telemetry_studio", blueprint?.appId)
    assertEquals(2, blueprint?.uiRoot?.children?.size)

    val lintResult = com.cellular.rpc.domain.miniapp.BlueprintLinter.lint(blueprint!!)
    assertTrue(lintResult.isValid)
    assertEquals(0, lintResult.errors.size)
  }

  /**
   * Test packet inspector filter logic and JSON timeline serialization.
   */
  @Test
  fun testPacketInspectorFiltersAndExportSerialization() {
    val sampleLogs = listOf(
      com.cellular.rpc.data.local.PacketLogEntity(
        id = 1,
        direction = "TX",
        sessionId = 101,
        pktType = 0x01,
        pktTypeName = "RPC_REQ",
        seqNo = 1,
        ackBitsHex = "00",
        payloadString = "weather:NYC",
        wireFormat = "~101:01:01:00:weather:NYC:F2A1#",
        binaryByteCount = 28,
        crc16Hex = "F2A1",
        crcValid = true
      ),
      com.cellular.rpc.data.local.PacketLogEntity(
        id = 2,
        direction = "RX",
        sessionId = 101,
        pktType = 0x02,
        pktTypeName = "MMS_PDU",
        seqNo = 1,
        ackBitsHex = "01",
        payloadString = "DATA:GZ:H4sIC...",
        wireFormat = "~101:02:01:01:DATA:GZ:H4sIC...:A4B2#",
        binaryByteCount = 240,
        crc16Hex = "A4B2",
        crcValid = true
      )
    )

    // Verify TX filter
    val txOnly = sampleLogs.filter { it.direction == "TX" }
    assertEquals(1, txOnly.size)
    assertEquals("RPC_REQ", txOnly.first().pktTypeName)

    // Verify MMS filter
    val mmsOnly = sampleLogs.filter { it.pktTypeName.contains("MMS") || it.wireFormat.contains("MMS") }
    assertEquals(1, mmsOnly.size)
    assertEquals("MMS_PDU", mmsOnly.first().pktTypeName)

    // Verify GZIP filter
    val gzipOnly = sampleLogs.filter { it.payloadString.contains("DATA:GZ") || it.wireFormat.contains("GZ") }
    assertEquals(1, gzipOnly.size)

    // Verify JSON Serialization format
    val jsonArray = org.json.JSONArray()
    sampleLogs.forEach { log ->
      val obj = org.json.JSONObject().apply {
        put("id", log.id)
        put("direction", log.direction)
        put("sessionId", log.sessionId)
        put("pktType", log.pktTypeName)
        put("seqNo", log.seqNo)
        put("bytes", log.binaryByteCount)
        put("crc", log.crc16Hex)
      }
      jsonArray.put(obj)
    }
    assertEquals(2, jsonArray.length())
    assertEquals("TX", jsonArray.getJSONObject(0).getString("direction"))
    assertEquals("RX", jsonArray.getJSONObject(1).getString("direction"))
  }

  /**
   * Doc Section 2.12 / 2.14: Test pluggable animation preset configs and serialization.
   */
  @Test
  fun testPluggableAnimationPresetConfigsAndSerialization() {
    val cloud = com.cellular.rpc.ui.chat.animation.AnimationPresetConfig.fromPresetId("cloud")
    assertEquals("cloud", cloud.presetId)
    assertEquals("vapor_condense", cloud.shaderType)

    val neon = com.cellular.rpc.ui.chat.animation.AnimationPresetConfig.fromPresetId("neon_strike")
    assertEquals("neon_strike", neon.presetId)
    assertEquals("glow_pulse", neon.shaderType)

    val origami = com.cellular.rpc.ui.chat.animation.AnimationPresetConfig.fromPresetId("origami_fold")
    assertEquals("origami_fold", origami.presetId)
    assertEquals("crease_snap", origami.shaderType)

    val spring = com.cellular.rpc.ui.chat.animation.AnimationPresetConfig.fromPresetId("spring_detent")
    assertEquals("spring_detent", spring.presetId)

    // Test JSON schema contract serialization
    val jsonStr = neon.toJson()
    val jsonObj = org.json.JSONObject(jsonStr)
    assertEquals("neon_strike", jsonObj.getString("preset_id"))
    assertEquals(300, jsonObj.getInt("duration_ms"))
    assertEquals("glow_pulse", jsonObj.getJSONObject("shader_effect").getString("type"))

    // Test ThemeConfig binding
    val themeConfig = com.cellular.rpc.ui.chat.theme.ChatThemeConfig(animationStyle = "origami_fold")
    assertEquals("origami_fold", themeConfig.animationPreset.presetId)
  }

  /**
   * Doc Section 2.13: Test Thread Forking logic and message cloning semantics.
   */
  @Test
  fun testThreadForkingAndMessageCloning() {
    val origThreadId = "th_main"
    val msg1 = com.cellular.rpc.engine.ChatMessage(
        id = "msg_001",
        threadId = origThreadId,
        sender = com.cellular.rpc.engine.MessageSender.USER,
        text = "Analyze Q3 portfolio performance",
        timestampMs = 1000L
    )
    val msg2 = com.cellular.rpc.engine.ChatMessage(
        id = "msg_002",
        threadId = origThreadId,
        sender = com.cellular.rpc.engine.MessageSender.AI_GATEWAY,
        text = "Portfolio is up 14.2% driven by tech sector rally.",
        timestampMs = 2000L
    )
    val msg3 = com.cellular.rpc.engine.ChatMessage(
        id = "msg_003",
        threadId = origThreadId,
        sender = com.cellular.rpc.engine.MessageSender.USER,
        text = "Unrelated question about weather",
        timestampMs = 3000L
    )

    val allMessages = listOf(msg1, msg2, msg3)
    val targetMessage = msg2

    // Filter messages up to cutoff timestamp
    val messagesToClone = allMessages.filter { it.timestampMs <= targetMessage.timestampMs || it.id == targetMessage.id }
    assertEquals(2, messagesToClone.size)
    assertEquals("msg_001", messagesToClone[0].id)
    assertEquals("msg_002", messagesToClone[1].id)

    val newBranchThreadId = "th_fork_ab12cd"
    val clonedMessages = messagesToClone.map { orig ->
        orig.copy(
            id = "msg_clone_" + orig.id,
            threadId = newBranchThreadId
        )
    }

    assertEquals(2, clonedMessages.size)
    assertEquals(newBranchThreadId, clonedMessages[0].threadId)
    assertEquals(newBranchThreadId, clonedMessages[1].threadId)
    assertEquals("Analyze Q3 portfolio performance", clonedMessages[0].text)
    assertEquals("Portfolio is up 14.2% driven by tech sector rally.", clonedMessages[1].text)
    assertFalse(clonedMessages[0].id == msg1.id)
  }

  /**
   * Doc Section 2.13: Test Edit Prompt PDU calculation and branch title formatting.
   */
  @Test
  fun testEditPromptPduCalculationAndBranchTitle() {
    val shortPrompt = "Current weather in SF"
    val shortBytes = shortPrompt.toByteArray(Charsets.UTF_8).size
    val shortPdu = ((shortBytes + 139) / 140).coerceAtLeast(1)
    assertEquals(1, shortPdu)

    val longPrompt = "A".repeat(300)
    val longBytes = longPrompt.toByteArray(Charsets.UTF_8).size
    val longPdu = ((longBytes + 139) / 140).coerceAtLeast(1)
    assertEquals(3, longPdu)

    val customPrompt = "Calculate 15% tip on $120 total bill"
    val branchTitle = "Branch: " + customPrompt.trim().take(22)
    assertEquals("Branch: Calculate 15% tip on $", branchTitle)
  }

  /**
   * Doc Section 2.10: Test 2D MiniGame Physics Engine Vector Mathematics & Symplectic Euler Integration.
   */
  @Test
  fun testMiniGamePhysicsEngine_tiltAccelerationAndWallCollisions() {
    val engine = com.cellular.rpc.domain.game.MiniGamePhysicsEngine(
      arenaWidth = 300f,
      arenaHeight = 300f,
      gravityFactor = 200f,
      damping = 0.95f,
      restitution = 0.5f
    )

    var state = com.cellular.rpc.domain.game.GameEngineState(
      ball = com.cellular.rpc.domain.game.BallState(
        pos = com.cellular.rpc.domain.game.Vector2D(150f, 150f),
        radius = 10f
      ),
      targets = listOf(
        com.cellular.rpc.domain.game.TargetRing("t1", com.cellular.rpc.domain.game.Vector2D(180f, 150f), radius = 15f, points = 10)
      ),
      obstacles = listOf(
        com.cellular.rpc.domain.game.Obstacle("hazard_1", 200f, 100f, 220f, 200f, isHazard = true)
      ),
      lives = 3,
      timeRemainingSec = 30f
    )

    // Simulate tilt right (tiltX = -5f => ax > 0)
    var collected = false
    var collisionCount = 0
    for (i in 0 until 10) {
      state = engine.update(
        state = state,
        tiltX = -5f,
        tiltY = 0f,
        dtSec = 0.05f,
        onCollision = { isHazard -> collisionCount++ },
        onCollectTarget = { target -> collected = true }
      )
    }

    // Ball should have moved to the right (x > 150)
    assertTrue("Ball should move right on negative tiltX", state.ball.pos.x > 150f)
    assertTrue("Target t1 should be collected", collected)
    assertEquals(10, state.score)
    assertTrue("Target should be marked collected in state", state.targets[0].isCollected)
  }

  /**
   * Doc Section 2.10: Test Hardware Sensor Engine Simulation Tilt & Blueprint Linter whitelisting.
   */
  @Test
  fun testSensorGameBlueprintLintingAndSimulation() {
    val gameBlueprintJson = """
    {
      "type": "mini_app_blueprint",
      "appId": "sensor_maze_test",
      "version": 1,
      "metadata": {
        "title": "Sensor Maze Test",
        "icon": "sports_esports"
      },
      "initialState": {
        "score": 0,
        "lives": 3
      },
      "ui": {
        "type": "sensor_game",
        "bind": "score",
        "modifier": {
          "height": 280,
          "lives": 3,
          "timeLimit": 45
        }
      }
    }
    """.trimIndent()

    val blueprint = com.cellular.rpc.domain.miniapp.MiniAppBlueprint.fromJson(gameBlueprintJson)
    assertNotNull(blueprint)
    val lintResult = com.cellular.rpc.domain.miniapp.BlueprintLinter.lint(blueprint!!)
    assertTrue(lintResult.isValid)
    assertEquals(0, lintResult.errors.size)
  }

  /**
   * Doc Section 2.11: Test Live Voice Recorder State, Max Duration & Waveform Normalization.
   */
  @Test
  fun testLiveVoiceRecorderAmplitudesAndDurationFormatting() {
    // Amplitude normalization test
    val rawAmplitudes = listOf(100, 3276, 16384, 32767, 50000)
    val normalized = rawAmplitudes.map { (it / 32767f).coerceIn(0.06f, 1.0f) }

    assertEquals(0.06f, normalized[0], 0.01f)
    assertEquals(0.10f, normalized[1], 0.01f)
    assertEquals(0.50f, normalized[2], 0.01f)
    assertEquals(1.00f, normalized[3], 0.01f)
    assertEquals(1.00f, normalized[4], 0.01f) // Clamped to 1.0f

    // Duration formatting test
    val durationMs = 65_000L // 1 min 5 sec
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / 1000) / 60
    val formatted = String.format("%02d:%02d", minutes, seconds)
    assertEquals("01:05", formatted)
  }

  /**
   * Doc Section 2.16: Test Procedural 2D Maze Generator Determinism, DFS Layout & Obstacle Scaling.
   */
  @Test
  fun testProceduralMazeGeneratorDeterministicDFSAndTargets() {
    val seed = 42L
    val maze1 = com.cellular.rpc.domain.game.ProceduralMazeGenerator.generateMaze(
      arenaWidth = 600f,
      arenaHeight = 600f,
      difficulty = com.cellular.rpc.domain.game.ProceduralMazeGenerator.MazeDifficulty.INTERMEDIATE,
      customSeed = seed
    )

    val maze2 = com.cellular.rpc.domain.game.ProceduralMazeGenerator.generateMaze(
      arenaWidth = 600f,
      arenaHeight = 600f,
      difficulty = com.cellular.rpc.domain.game.ProceduralMazeGenerator.MazeDifficulty.INTERMEDIATE,
      customSeed = seed
    )

    // Verify determinism from same seed
    assertEquals(seed, maze1.seed)
    assertEquals(maze1.gridSize, maze2.gridSize)
    assertEquals(maze1.obstacles.size, maze2.obstacles.size)
    assertEquals(maze1.targets.size, maze2.targets.size)
    assertEquals(maze1.ballSpawn.x, maze2.ballSpawn.x, 0.001f)
    assertEquals(maze1.ballSpawn.y, maze2.ballSpawn.y, 0.001f)

    // Verify target rings are created and exit portal is present
    assertTrue("Targets should be non-empty", maze1.targets.isNotEmpty())
    assertNotNull("Exit portal should be created", maze1.exitPortal)
    assertTrue("Obstacles should contain walls and hazards", maze1.obstacles.any { it.isHazard })

    // Test difficulty tier scaling
    val expertMaze = com.cellular.rpc.domain.game.ProceduralMazeGenerator.generateMaze(
      arenaWidth = 600f,
      arenaHeight = 600f,
      difficulty = com.cellular.rpc.domain.game.ProceduralMazeGenerator.MazeDifficulty.EXPERT,
      customSeed = 999L
    )
    assertEquals(8, expertMaze.gridSize)
    assertTrue(expertMaze.targets.size >= 8)
  }

  /**
   * INC-26: Test strict number matching and sender thread isolation
   */
  @Test
  fun testStrictNumberMatchingAndIsolation() {
    // 1. E.164 NANP 10-digit matching (with or without country code / formatting)
    assertTrue(com.cellular.rpc.domain.service.CellularServiceManager.isNumberMatch("+16462619684", "6462619684"))
    assertTrue(com.cellular.rpc.domain.service.CellularServiceManager.isNumberMatch("16462619684", "+1 (646) 261-9684"))
    assertTrue(com.cellular.rpc.domain.service.CellularServiceManager.isNumberMatch("+18005550199", "8005550199"))

    // 2. Strict rejection of third-party contacts sharing last 4 or 7 digits
    assertFalse("Must not match different area code with same suffix",
      com.cellular.rpc.domain.service.CellularServiceManager.isNumberMatch("+12125550199", "+18005550199"))
    assertFalse("Must not match random third-party number",
      com.cellular.rpc.domain.service.CellularServiceManager.isNumberMatch("+15559876543", "+16462619684"))
    assertFalse("Must not match 4-digit suffix collision",
      com.cellular.rpc.domain.service.CellularServiceManager.isNumberMatch("+19998889684", "+16462619684"))
  }

  /**
   * INC-27: Test PDU UDH concatenation and text sequence parsing
   */
  @Test
  fun testPduReassemblyBufferSequenceParsing() {
    // 1. Text header parsing: [1/3]
    val parsedBracket = com.cellular.rpc.transport.receiver.PduReassemblyBuffer.parseTextSequenceHeader("[1/3] Part one text")
    assertNotNull(parsedBracket)
    assertEquals(1, parsedBracket!!.first.partSeq)
    assertEquals(3, parsedBracket.first.totalParts)
    assertEquals("Part one text", parsedBracket.second)

    // 2. Text header parsing: (2/3)
    val parsedParen = com.cellular.rpc.transport.receiver.PduReassemblyBuffer.parseTextSequenceHeader("(2/3) Part two text")
    assertNotNull(parsedParen)
    assertEquals(2, parsedParen!!.first.partSeq)
    assertEquals(3, parsedParen.first.totalParts)
    assertEquals("Part two text", parsedParen.second)

    // 3. Single-part text with no prefix
    val singlePart = com.cellular.rpc.transport.receiver.PduReassemblyBuffer.parseTextSequenceHeader("Hello world, no sequence")
    assertNull(singlePart)

    // 4. Synthetic 3GPP SMS-DELIVER PDU with UDH (IEI 0x00: ref 77, total 2, seq 1)
    // Layout:
    // SMSC Len: 0x00 (no SMSC)
    // First Octet: 0x44 (SMS-DELIVER, TP-UDHI bit 6 = 1)
    // OA: 0x0A (10 digits), Type: 0x81 (unknown), OA bytes: 5 bytes (0x12, 0x34, 0x56, 0x78, 0x90)
    // TP-PID: 0x00
    // TP-DCS: 0x00
    // TP-SCTS: 7 bytes (0x26, 0x09, 0x14, 0x12, 0x00, 0x00, 0x00)
    // TP-UDL: 0x0A (10 septets/bytes)
    // UDHL: 0x05 (5 bytes header)
    // IEI: 0x00 (concatenated 8-bit), IE-Len: 0x03, Ref: 0x4D (77), Total: 0x02, Seq: 0x01
    val syntheticPdu = byteArrayOf(
      0x00, // SMSC Len
      0x44, // First octet (TP-UDHI=1)
      0x0A, 0x81.toByte(), 0x12, 0x34, 0x56, 0x78, 0x90.toByte(), // OA
      0x00, // PID
      0x00, // DCS
      0x26, 0x09, 0x14, 0x12, 0x00, 0x00, 0x00, // SCTS
      0x0A, // UDL
      0x05, // UDHL
      0x00, 0x03, 0x4D, 0x02, 0x01, // IEI=0x00, Len=3, Ref=77, Total=2, Seq=1
      0x41, 0x42, 0x43 // User text "ABC"
    )

    val udh = com.cellular.rpc.transport.receiver.PduReassemblyBuffer.parseUdhConcatHeader(syntheticPdu)
    assertNotNull("UDH header must be parsed from synthetic PDU", udh)
    assertEquals(77, udh!!.refNumber)
    assertEquals(2, udh.totalParts)
    assertEquals(1, udh.partSeq)
  }
}

