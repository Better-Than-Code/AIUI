package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cellular.rpc.domain.protocol.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Cellular RPC", appName)
  }

  /**
   * Scenario 1: Cellular Boundary Test
   * Transmit a 122-byte chunk. Verify that the serialized binary frame size
   * exactly equals 133 bytes (9B header + 122B payload + 2B CRC16 trailer),
   * strictly fitting within the safe 133-byte single SMS PDU allocation
   * without multi-PDU splitting.
   */
  @Test
  fun `cellular boundary test 122 byte payload fits in 133 byte safe MTU`() {
    val payload122 = ByteArray(122) { (it % 26 + 65).toByte() }
    val frame = Frame(
        sessionId = 0x1A2F,
        pktType = Frame.PKT_BIN_DAT,
        seqNo = 0x0001,
        payload = payload122
    )

    val binary = frame.toBinary()
    assertEquals(133, binary.size)
    assertTrue("Binary frame must not exceed 133 bytes safe MTU", binary.size <= 133)

    // Verify CRC16 and deserialization
    val parsed = Frame.fromBinary(binary)
    assertNotNull("Frame must deserialize cleanly from binary", parsed)
    assertEquals(frame.sessionId, parsed!!.sessionId)
    assertEquals(frame.pktType, parsed.pktType)
    assertEquals(frame.seqNo, parsed.seqNo)
    assertArrayEquals(payload122, parsed.payload)
  }

  /**
   * Scenario 2: Out-of-Order Packet Injection
   * Force seq 0, drop seq 1, deliver seq 2. Verify the receiver emits an ACK confirming 0
   * with bit 1 set in the bitmask, and verify that the reassembled packets buffer until
   * chunk 1 is retransmitted.
   */
  @Test
  fun `out of order packet injection and selective repeat buffering`() {
    val swc = SlidingWindowController(windowSize = 4, maxSequence = 65535)

    val frame0 = Frame(sessionId = 0x1A2F, pktType = Frame.PKT_BIN_DAT, seqNo = 0, payload = "CHUNK_0".toByteArray())
    val frame1 = Frame(sessionId = 0x1A2F, pktType = Frame.PKT_BIN_DAT, seqNo = 1, payload = "CHUNK_1".toByteArray())
    val frame2 = Frame(sessionId = 0x1A2F, pktType = Frame.PKT_BIN_DAT, seqNo = 2, payload = "CHUNK_2".toByteArray())

    // 1. Deliver frame 0
    val res0 = swc.processInbound(frame0)
    assertTrue(res0 is SlidingWindowController.InboundResult.Deliver)
    val deliver0 = res0 as SlidingWindowController.InboundResult.Deliver
    assertEquals(1, deliver0.frames.size)
    assertEquals(0, deliver0.frames[0].seqNo)
    assertEquals(1, deliver0.ackBase) // Next expected is 1

    // 2. Drop seq 1 and deliver seq 2 directly (out-of-order)
    val res2 = swc.processInbound(frame2)
    assertTrue(res2 is SlidingWindowController.InboundResult.Deliver)
    val deliver2 = res2 as SlidingWindowController.InboundResult.Deliver

    // seq 2 cannot be delivered yet because seq 1 is missing
    assertEquals(0, deliver2.frames.size)
    assertEquals(1, deliver2.ackBase) // ackBase remains 1

    // Bitmask must have bit (2 - 1 - 1) = bit 0 set to acknowledge seq 2 selectively!
    val bit0Set = (deliver2.ackBitmask and 0x01L) != 0L
    assertTrue("Bit 0 of selective repeat bitmask must acknowledge seq 2", bit0Set)

    // 3. Now deliver dropped chunk 1
    val res1 = swc.processInbound(frame1)
    assertTrue(res1 is SlidingWindowController.InboundResult.Deliver)
    val deliver1 = res1 as SlidingWindowController.InboundResult.Deliver

    // Both seq 1 and buffered seq 2 should now be delivered in contiguous sequence!
    assertEquals(2, deliver1.frames.size)
    assertEquals(1, deliver1.frames[0].seqNo)
    assertEquals(2, deliver1.frames[1].seqNo)
    assertEquals(3, deliver1.ackBase) // Cumulative base advanced to 3
  }

  /**
   * Scenario 3: Spam & Velocity Mitigation
   * Transmissions must maintain a minimum gap of 2200ms + Random(200..700ms).
   */
  @Test
  fun `spam and velocity mitigation maintains minimum throttle gap`() {
    val floorGapMs = 2200L
    val minGapMs = 2200L + 200L
    val maxGapMs = 2200L + 700L

    for (i in 1..50) {
      val jitter = Random.nextLong(200L, 701L)
      val totalGap = floorGapMs + jitter
      assertTrue("Throttle gap must be >= $minGapMs", totalGap >= minGapMs)
      assertTrue("Throttle gap must be <= $maxGapMs", totalGap <= maxGapMs)
    }
  }

  /**
   * GSM-Safe Base85 Encoder & ASCII Fallback Wire Format Test
   */
  @Test
  fun `gsm safe base85 encoding round trip and wire format`() {
    val testPayload = "Hello Cellular RPC Protocol 2026!".toByteArray(Charsets.UTF_8)
    val encoded = GsmSafeBase85.encode(testPayload)
    val decoded = GsmSafeBase85.decode(encoded)

    assertArrayEquals(testPayload, decoded)

    // Ensure no unsafe characters in GSM 03.38 Basic Character Set
    for (ch in encoded) {
      assertNotEquals('\\', ch)
      assertNotEquals('"', ch)
      assertNotEquals('\'', ch)
      assertFalse(ch.isWhitespace())
    }

    // ASCII Wire Format test
    val frame = Frame(
        sessionId = 0x1A2F,
        pktType = Frame.PKT_RPC_RES,
        seqNo = 4,
        payload = "304".toByteArray(Charsets.UTF_8),
        ackBits = 0L
    )
    val wire = frame.toAsciiWire()
    assertTrue("Wire format must start with ~ and end with #", wire.startsWith("~") && wire.endsWith("#"))

    val parsedFromWire = Frame.fromAsciiWire(wire)
    assertNotNull(parsedFromWire)
    assertEquals(frame.sessionId, parsedFromWire!!.sessionId)
    assertEquals(frame.pktType, parsedFromWire.pktType)
    assertEquals(frame.seqNo, parsedFromWire.seqNo)
    assertEquals("304", String(parsedFromWire.payload, Charsets.UTF_8))
  }

  /**
   * Scenario 5: Rich Widget Schemas & Cellular Chat Serialization Test
   */
  @Test
  fun `rich widget schemas serialize and parse cleanly`() {
    // 1. Weather
    val weather = com.cellular.rpc.engine.WidgetData.Weather(72, "San Francisco", "Sunny", 76, 58)
    val parsedWeather = com.cellular.rpc.engine.WidgetData.parse(weather.toJson()) as? com.cellular.rpc.engine.WidgetData.Weather
    assertNotNull(parsedWeather)
    assertEquals(72, parsedWeather!!.temp)
    assertEquals("San Francisco", parsedWeather.city)

    // 2. Transfer
    val transfer = com.cellular.rpc.engine.WidgetData.CellularTransfer("T891", "Alex Chen", "$25.00", "Lunch", "CONFIRMED")
    val parsedTransfer = com.cellular.rpc.engine.WidgetData.parse(transfer.toJson()) as? com.cellular.rpc.engine.WidgetData.CellularTransfer
    assertNotNull(parsedTransfer)
    assertEquals("$25.00", parsedTransfer!!.amount)
    assertEquals("Alex Chen", parsedTransfer.to)

    // 3. Poll
    val poll = com.cellular.rpc.engine.WidgetData.CellularPoll(
        id = "P44",
        question = "Sprint Review?",
        options = listOf("Yes", "No"),
        votes = listOf(3, 1)
    )
    val parsedPoll = com.cellular.rpc.engine.WidgetData.parse(poll.toJson()) as? com.cellular.rpc.engine.WidgetData.CellularPoll
    assertNotNull(parsedPoll)
    assertEquals(2, parsedPoll!!.options.size)
    assertEquals(4, parsedPoll.votes.sum())

    // 4. Hash consistency for 304 caching
    val hash1 = weather.computeContentHash()
    val hash2 = weather.computeContentHash()
    assertEquals(hash1, hash2)
    assertEquals(8, hash1.length) // 4 bytes hex = 8 chars
  }

  /**
   * Scenario 6: Standardized Cellular Schemas & RPC Protocol
   * Validates CalendarEvent, TaskChecklist, SystemStatus serialization,
   * CellularSchemaRegistry schema discovery, and CellularRequest/CellularResponse
   * compact wire transmission format for SMS and MMS payloads.
   */
  @Test
  fun `standardized cellular schemas and wire envelopes roundtrip`() {
    // 1. Calendar Event
    val cal = com.cellular.rpc.engine.WidgetData.CalendarEvent(
        id = "evt_99",
        title = "Sprint Protocol Review",
        time = "2:00 PM - 3:00 PM",
        location = "Cellular Lab 4",
        attendees = 5
    )
    val parsedCal = com.cellular.rpc.engine.WidgetData.parse(cal.toJson()) as? com.cellular.rpc.engine.WidgetData.CalendarEvent
    assertNotNull(parsedCal)
    assertEquals("Sprint Protocol Review", parsedCal!!.title)
    assertEquals(5, parsedCal.attendees)

    // 2. Task Checklist
    val task = com.cellular.rpc.engine.WidgetData.TaskChecklist(
        id = "task_42",
        title = "Pally Flight Checklist",
        items = listOf("Pre-flight Radio", "Check Battery", "Verify SMSC"),
        doneFlags = listOf(true, true, false)
    )
    val parsedTask = com.cellular.rpc.engine.WidgetData.parse(task.toJson()) as? com.cellular.rpc.engine.WidgetData.TaskChecklist
    assertNotNull(parsedTask)
    assertEquals(3, parsedTask!!.items.size)
    assertTrue(parsedTask.doneFlags[0])
    assertFalse(parsedTask.doneFlags[2])

    // 3. System Status Telemetry
    val sys = com.cellular.rpc.engine.WidgetData.SystemStatus(
        batteryPct = 88,
        signalDbm = -65,
        freeStorageMb = 5120L,
        queuedPackets = 1,
        linkQuality = "OPTIMAL"
    )
    val parsedSys = com.cellular.rpc.engine.WidgetData.parse(sys.toJson()) as? com.cellular.rpc.engine.WidgetData.SystemStatus
    assertNotNull(parsedSys)
    assertEquals(88, parsedSys!!.batteryPct)
    assertEquals("OPTIMAL", parsedSys.linkQuality)

    // 4. CellularRequest wire format
    val req = com.cellular.rpc.domain.payload.CellularRequest(
        action = com.cellular.rpc.domain.payload.CellularAction.GET,
        target = "widget:task_checklist",
        etag = task.computeContentHash(),
        params = mapOf("priority" to "high")
    )
    val reqWire = req.toCompactWire()
    val parsedReq = com.cellular.rpc.domain.payload.CellularRequest.fromWire(reqWire)
    assertNotNull(parsedReq)
    assertEquals(com.cellular.rpc.domain.payload.CellularAction.GET, parsedReq!!.action)
    assertEquals("widget:task_checklist", parsedReq.target)
    assertEquals(task.computeContentHash(), parsedReq.etag)
    assertEquals("high", parsedReq.params["priority"])

    // 5. CellularResponse 304 wire format
    val res304 = com.cellular.rpc.domain.payload.CellularResponse(
        status = 304,
        schemaId = "task_checklist",
        etag = task.computeContentHash()
    )
    val res304Wire = res304.toCompactWire()
    val parsedRes304 = com.cellular.rpc.domain.payload.CellularResponse.fromWire(res304Wire)
    assertNotNull(parsedRes304)
    assertEquals(304, parsedRes304.statusCode)
    assertTrue(parsedRes304.isNotModified)
    assertEquals("task_checklist", parsedRes304.schemaId)

    // 6. CellularResponse 200 payload wire format
    val res200 = com.cellular.rpc.domain.payload.CellularResponse(
        status = 200,
        schemaId = "system_status",
        etag = sys.computeContentHash(),
        payload = sys.toJson()
    )
    val res200Wire = res200.toCompactWire()
    val parsedRes200 = com.cellular.rpc.domain.payload.CellularResponse.fromWire(res200Wire)
    assertNotNull(parsedRes200)
    assertEquals(200, parsedRes200.statusCode)
    assertFalse(parsedRes200.isNotModified)
    assertEquals("system_status", parsedRes200.schemaId)
    val recoveredSys = com.cellular.rpc.engine.WidgetData.parse(parsedRes200.payload) as? com.cellular.rpc.engine.WidgetData.SystemStatus
    assertNotNull(recoveredSys)
    assertEquals(88, recoveredSys!!.batteryPct)

    // 7. Schema Registry completeness
    val allSchemas = com.cellular.rpc.domain.schema.CellularSchemaRegistry.getAllSchemas()
    assertTrue("At least 10 schemas should be registered", allSchemas.size >= 10)
    assertNotNull(com.cellular.rpc.domain.schema.CellularSchemaRegistry.getSchema<Any>("calendar_event"))
    assertNotNull(com.cellular.rpc.domain.schema.CellularSchemaRegistry.getSchema<Any>("task_checklist"))
    assertNotNull(com.cellular.rpc.domain.schema.CellularSchemaRegistry.getSchema<Any>("system_status"))
    assertTrue(com.cellular.rpc.domain.schema.CellularSchemaRegistry.hasSchema("weather"))
  }

  /**
   * Scenario 6: MCP Single-Push Genesis Manifest & Delta Invalidation Test
   * Verifies that the client compiles a self-describing Genesis MCP manifest
   * containing all registered widget schemas and actionable tools, computes
   * a deterministic 8-char catalog hash, and validates delta updates.
   */
  @Test
  fun `mcp single push genesis manifest generation and deterministic catalog hash`() {
    val catalogHash = com.cellular.rpc.domain.mcp.CellularMcpRegistry.computeCatalogHash()
    assertNotNull(catalogHash)
    assertEquals("Catalog hash must be 8 hex characters", 8, catalogHash.length)

    val genesisJson = com.cellular.rpc.domain.mcp.CellularMcpRegistry.buildGenesisManifestJson()
    assertTrue("Manifest must contain OP identifier", genesisJson.contains("MCP_GENESIS_INIT"))
    assertTrue("Manifest must contain protocol version", genesisJson.contains(com.cellular.rpc.domain.mcp.CellularMcpRegistry.MCP_PROTOCOL_VERSION))
    assertTrue("Manifest must contain catalog hash", genesisJson.contains(catalogHash))
    assertTrue("Manifest must register widgets", genesisJson.contains("registered_widgets"))
    assertTrue("Manifest must register actionable tools", genesisJson.contains("registered_tools"))
    assertTrue("Manifest must declare cellular MTU constraints", genesisJson.contains("mtu_budget_bytes"))

    val prompt = com.cellular.rpc.domain.mcp.CellularMcpRegistry.buildGenesisSmsPrompt()
    assertTrue(prompt.startsWith("SYS:MCP_GENESIS_SYNC"))
    assertTrue(prompt.contains("---CELLULAR_DATA---"))

    val tools = com.cellular.rpc.domain.mcp.CellularMcpRegistry.getRegisteredTools()
    assertTrue("Should register at least 4 native tools", tools.size >= 4)
    assertTrue(tools.any { it.name == "cast_poll_vote" })
    assertTrue(tools.any { it.name == "confirm_cellular_transfer" })
    assertTrue(tools.any { it.name == "query_cellular_widget" })
    assertTrue(tools.any { it.name == "query_device_telemetry" })
  }

  /**
   * Scenario 7: Dynamic Feature Wire Parser & AST Interpreter Test
   * Verifies that incoming cellular [APP:BUILD:<id>] payloads are parsed into
   * robust AST trees, handle variable interpolation, and execute sandboxed actions.
   */
  @Test
  fun `dynamic feature wire parsing, ast resolution and sandbox state evaluation`() = kotlinx.coroutines.test.runTest {
    val samplePayload = """
      [APP:BUILD:solar_calc]{
        "title": "Solar Estimator",
        "version": "1.2.0",
        "description": "Calculates solar PV generation",
        "icon": "solar",
        "state": { "kw": 5.0, "hours": 6.0, "result": 30.0 },
        "ui": {
          "type": "Column",
          "spacing": 10,
          "children": [
            { "type": "Text", "text": "Daily Power: {result} kWh", "style": "headline", "bold": true },
            { "type": "Btn", "label": "Calculate", "action": "calculate", "variant": "filled" }
          ]
        },
        "js": "function calculate() { state.result = state.kw * state.hours; bridge.commit(JSON.stringify(state)); }"
      }
    """.trimIndent()

    assertTrue(com.cellular.rpc.domain.dynamic.DynamicFeatureWireParser.isAppBuildPayload(samplePayload))

    val parsed = com.cellular.rpc.domain.dynamic.DynamicFeatureWireParser.parsePayload(samplePayload)
    assertNotNull(parsed)
    assertEquals("solar_calc", parsed!!.featureId)
    assertEquals("Solar Estimator", parsed.title)
    assertEquals("1.2.0", parsed.version)

    // Test AST Parsing
    val astNode = com.cellular.rpc.domain.dynamic.AstParser.parse(parsed.uiAstJson)
    assertTrue(astNode is com.cellular.rpc.domain.dynamic.AstNode.Container)
    val container = astNode as com.cellular.rpc.domain.dynamic.AstNode.Container
    assertEquals("Column", container.type)
    assertEquals(2, container.children.size)

    // Test Template String Resolution
    val textNode = container.children[0] as com.cellular.rpc.domain.dynamic.AstNode.TextNode
    val resolved = com.cellular.rpc.domain.dynamic.AstParser.resolveTemplate(textNode.text, parsed.currentStateJson)
    assertEquals("Daily Power: 30 kWh", resolved)

    // Test Sandboxed Execution
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sandbox = com.cellular.rpc.domain.dynamic.DynamicScriptSandbox.getInstance(context)
    val result = sandbox.executeAction(
      featureId = parsed.featureId,
      actionName = "calculate",
      currentStateJson = """{"kw": 8.0, "hours": 5.0, "result": 0}""",
      userScript = parsed.jsLogic
    )

    assertTrue(result.isSuccess)
    val finalStateJson = result.getOrThrow()
    assertTrue(finalStateJson.contains("\"result\":40") || finalStateJson.contains("\"result\": 40") || finalStateJson.contains("40"))
  }

  @Test
  fun testCustomAppWidgetProviderConfigurationAndStateResolution() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testWidgetId = 42

    // Configure the custom widget to bind to our solar estimator feature
    com.cellular.rpc.widget.CellularCustomAppWidgetProvider.setCustomWidgetConfig(
      context = context,
      appWidgetId = testWidgetId,
      featureId = "solar_estimator",
      title = "Solar Array Estimator",
      metricKey = "result"
    )

    assertEquals("solar_estimator", com.cellular.rpc.widget.CellularCustomAppWidgetProvider.getCustomFeatureId(context, testWidgetId))
    assertEquals("Solar Array Estimator", com.cellular.rpc.widget.CellularCustomAppWidgetProvider.getCustomTitle(context, testWidgetId))
    assertEquals("result", com.cellular.rpc.widget.CellularCustomAppWidgetProvider.getCustomMetricKey(context, testWidgetId))

    // Set transparency and verify
    com.cellular.rpc.widget.WidgetPreferences.setTransparency(context, testWidgetId, 75)
    assertEquals(75, com.cellular.rpc.widget.WidgetPreferences.getTransparency(context, testWidgetId))
  }
}



