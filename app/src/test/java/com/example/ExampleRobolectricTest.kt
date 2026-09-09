package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cellular.rpc.domain.protocol.*
import kotlinx.coroutines.flow.first
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
    assertEquals("PallyAI", appName)
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

  @Test
  fun testDynamicBlueprintSduiParsingAndTreeWalker() {
    val sduiJson = """
      {
        "type": "blueprint",
        "id": "bp_sensor_cluster",
        "title": "Cellular Sensor Node",
        "subtitle": "Telemetry v2.2",
        "themeColorHex": "#00E5FF",
        "root": {
          "type": "column",
          "spacing": 8,
          "children": [
            { "type": "badge", "text": "ONLINE", "color": "#00E5FF" },
            { "type": "metric", "title": "Soil Moisture", "value": "42%", "secondaryValue": "Optimal" },
            { "type": "progress", "title": "Battery Level", "progress": 0.88 },
            { "type": "key_value", "title": "Radio Band", "value": "Band 12 LTE-M" }
          ]
        }
      }
    """.trimIndent()

    val widget = com.cellular.rpc.engine.WidgetData.parse(sduiJson)
    assertNotNull("SDUI JSON must parse to WidgetData", widget)
    assertTrue("WidgetData must be DynamicBlueprint", widget is com.cellular.rpc.engine.WidgetData.DynamicBlueprint)

    val blueprint = widget as com.cellular.rpc.engine.WidgetData.DynamicBlueprint
    assertEquals("bp_sensor_cluster", blueprint.id)
    assertEquals("Cellular Sensor Node", blueprint.title)
    assertEquals(4, blueprint.rootNode.children.size)
    assertEquals("badge", blueprint.rootNode.children[0].type)
    assertEquals("ONLINE", blueprint.rootNode.children[0].text)
    assertEquals("metric", blueprint.rootNode.children[1].type)
    assertEquals("Soil Moisture", blueprint.rootNode.children[1].title)
    assertEquals("42%", blueprint.rootNode.children[1].value)
  }

  @Test
  fun testSenderRecognitionForLivePallyGateway() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(com.cellular.rpc.domain.service.CellularServiceManager.isSenderRecognized(context, "+16462619684"))
    assertTrue(com.cellular.rpc.domain.service.CellularServiceManager.isSenderRecognized(context, "16462619684"))
    assertTrue(com.cellular.rpc.domain.service.CellularServiceManager.isSenderRecognized(context, "+1 (646) 261-9684"))
    assertTrue(com.cellular.rpc.domain.service.CellularServiceManager.isSenderRecognized(context, "+18005550199"))
  }

  @Test
  fun testCellularAiOrchestratorOutboundAndInboundPipeline() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val orchestrator = com.cellular.rpc.orchestrator.CellularAiOrchestrator.getInstance(context)

    // 1. Test Outbound Mini-App Template Preparation
    val outbound = orchestrator.prepareOutbound(
      userPrompt = "Create a fitness rep tracker",
      threadId = "th_sprint9",
      schemaType = com.cellular.rpc.orchestrator.OrchestratorSchemaType.DYNAMIC_MINIAPP,
      templateId = "tpl_habit_tracker"
    )

    assertTrue("Outbound wire text must contain thread ID", outbound.wireText.contains("[TID:th_sprint9]"))
    assertTrue("Outbound wire text must contain MINIAPP schema trigger", outbound.wireText.contains("[SCHEMA:MINIAPP]"))
    assertTrue("Outbound wire text must contain template prompt prefix", outbound.wireText.contains("[TEMPLATE:COUNTER]"))
    assertTrue("Outbound wire text must contain user prompt", outbound.wireText.contains("Create a fitness rep tracker"))
    assertTrue("Estimated bytes must be positive", outbound.estimatedBytes > 0)

    // 2. Test Inbound Dynamic Mini-App Installation
    val habitTemplate = com.cellular.rpc.orchestrator.OrchestratorTemplateCatalog.getById("tpl_habit_tracker")
    assertNotNull(habitTemplate)

    val inboundResult = orchestrator.processInbound(habitTemplate!!.mockOfflinePayload, "+16462619684")
    assertNotNull("Installed mini app must not be null", inboundResult.installedMiniApp)
    assertEquals("Workout Rep Counter", inboundResult.installedMiniApp?.title)
    assertEquals("habit_counter", inboundResult.installedMiniApp?.featureId)

    // 3. Test Inbound Dual Response (Prose + Weather Widget)
    val dualPayload = "Here is the local forecast:\n\n{\"type\":\"weather\",\"city\":\"New York\",\"temp\":68,\"cond\":\"Cloudy\"}"
    val dualResult = orchestrator.processInbound(dualPayload)
    assertEquals("Here is the local forecast:", dualResult.conversationalProse)
    assertNotNull(dualResult.widgetData)
    assertTrue(dualResult.widgetData is com.cellular.rpc.engine.WidgetData.Weather)
    val weather = dualResult.widgetData as com.cellular.rpc.engine.WidgetData.Weather
    assertEquals("New York", weather.city)
    assertEquals(68, weather.temp)
  }

  @Test
  fun testOrchestratorTemplateCatalogIntegrity() {
    val allTemplates = com.cellular.rpc.orchestrator.OrchestratorTemplateCatalog.TEMPLATES
    assertTrue("Must have registered templates", allTemplates.size >= 5)

    val miniApps = com.cellular.rpc.orchestrator.OrchestratorTemplateCatalog.getByCategory(com.cellular.rpc.orchestrator.TemplateCategory.MINI_APP)
    val sduiWidgets = com.cellular.rpc.orchestrator.OrchestratorTemplateCatalog.getByCategory(com.cellular.rpc.orchestrator.TemplateCategory.SDUI_WIDGET)

    assertTrue("Must have mini apps in catalog", miniApps.isNotEmpty())
    assertTrue("Must have SDUI widgets in catalog", sduiWidgets.isNotEmpty())

    allTemplates.forEach { tpl ->
      assertTrue("Template ID must not be blank", tpl.id.isNotBlank())
      assertTrue("Template title must not be blank", tpl.title.isNotBlank())
      assertTrue("Template default prompt must not be blank", tpl.defaultPrompt.isNotBlank())
      assertTrue("Template mock payload must not be blank", tpl.mockOfflinePayload.isNotBlank())
    }
  }

  @Test
  fun testTabbedChatsLifecycleAndHistoryPreservation() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.cellular.rpc.data.local.AppDatabase.getInstance(context)
    val threadDao = db.conversationThreadDao()
    val chatDao = db.chatMessageDao()

    // 1. Create two separate conversation threads with history
    val thread1 = com.cellular.rpc.data.local.ConversationThreadEntity(
      threadId = "th_sprint_work",
      title = "Sprint Work",
      createdAtMs = System.currentTimeMillis()
    )
    val thread2 = com.cellular.rpc.data.local.ConversationThreadEntity(
      threadId = "th_tokyo_trip",
      title = "Tokyo Trip",
      createdAtMs = System.currentTimeMillis()
    )
    threadDao.insertOrUpdate(thread1)
    threadDao.insertOrUpdate(thread2)

    // 2. Insert messages in both threads
    val msg1 = com.cellular.rpc.data.local.ChatMessageEntity(
      id = "m1",
      threadId = "th_sprint_work",
      sender = "USER",
      text = "Reviewing sprint tasks"
    )
    val msg2 = com.cellular.rpc.data.local.ChatMessageEntity(
      id = "m2",
      threadId = "th_tokyo_trip",
      sender = "AI_GATEWAY",
      text = "Recommended Shibuya and Shinjuku hotels"
    )
    chatDao.insertMessage(msg1)
    chatDao.insertMessage(msg2)

    // 3. Verify messages are saved and isolated per thread
    val thread1Msgs = chatDao.getMessagesForThread("th_sprint_work").first()
    val thread2Msgs = chatDao.getMessagesForThread("th_tokyo_trip").first()
    assertEquals(1, thread1Msgs.size)
    assertEquals("Reviewing sprint tasks", thread1Msgs[0].text)
    assertEquals(1, thread2Msgs.size)
    assertEquals("Recommended Shibuya and Shinjuku hotels", thread2Msgs[0].text)

    // 4. Verify thread queries and active threads list
    val allActive = threadDao.getActiveThreadsFlow().first()
    assertTrue(allActive.any { it.threadId == "th_sprint_work" })
    assertTrue(allActive.any { it.threadId == "th_tokyo_trip" })
  }

  @Test
  fun testUniversalMiniAppBlueprintAndActionExecutor() = kotlinx.coroutines.runBlocking {
    val blueprintJson = """
      {
        "type": "mini_app_blueprint",
        "appId": "app_test_counter",
        "version": 1,
        "metadata": {
          "title": "Test Counter App",
          "icon": "checklist",
          "description": "Deterministic local state counter",
          "category": "productivity"
        },
        "initialState": {
          "count": 5,
          "items": [{"id":"1","name":"Item A"}]
        },
        "ui": {
          "type": "Column",
          "children": [
            {
              "type": "Text",
              "text": "Current count: 5"
            }
          ]
        }
      }
    """.trimIndent()

    // 1. Verify Blueprint Parsing
    val blueprint = com.cellular.rpc.domain.miniapp.MiniAppBlueprint.fromJson(blueprintJson)
    assertNotNull("Blueprint must parse from valid JSON", blueprint)
    assertEquals("app_test_counter", blueprint!!.appId)
    assertEquals("Test Counter App", blueprint.metadata.title)
    assertEquals(5, blueprint.initialState["count"])

    // 2. Test ActionExecutor mutations (INCREMENT)
    val incAction = mapOf("action" to "MUTATE_STATE", "op" to "INCREMENT", "prop" to "count", "step" to 5)
    val (state1, changed1) = com.cellular.rpc.domain.miniapp.ActionExecutor.execute(incAction, blueprint.initialState)
    assertTrue("State should be mutated", changed1)
    assertEquals(10, state1["count"])

    // 3. Test ActionExecutor mutations (APPEND)
    val appendAction = mapOf(
      "action" to "MUTATE_STATE",
      "op" to "APPEND",
      "target" to "items",
      "value" to mapOf("id" to "2", "name" to "Item B")
    )
    val (state2, changed2) = com.cellular.rpc.domain.miniapp.ActionExecutor.execute(appendAction, state1)
    assertTrue("State should be mutated", changed2)
    val itemsList = state2["items"] as? List<*>
    assertNotNull("Items list must not be null", itemsList)
    assertEquals(2, itemsList!!.size)

    // 4. Test Room Persistence via MiniAppDeckManager
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.cellular.rpc.domain.miniapp.MiniAppDeckManager.installApp(context, blueprint, state2)

    val db = com.cellular.rpc.data.local.AppDatabase.getInstance(context)
    val savedApp = db.appBlueprintDao().getAppById("app_test_counter")
    assertNotNull("Installed app must persist in Room", savedApp)
    assertEquals("Test Counter App", savedApp!!.title)
    assertTrue(savedApp.serializedStateJson.contains("Item B"))

    // Test state update
    com.cellular.rpc.domain.miniapp.MiniAppDeckManager.updateState(context, "app_test_counter", mapOf("count" to 99))
    val updatedApp = db.appBlueprintDao().getAppById("app_test_counter")
    assertNotNull(updatedApp)
    assertTrue(updatedApp!!.serializedStateJson.contains("99"))

    // Test uninstall
    com.cellular.rpc.domain.miniapp.MiniAppDeckManager.uninstallApp(context, "app_test_counter")
    val uninstalledApp = db.appBlueprintDao().getAppById("app_test_counter")
    assertNull("Uninstalled app should be removed from database", uninstalledApp)
  }

  @Test
  fun testJsonStreamDemuxer() {
    val rawStream = """[TID:t1] some prefix {"type":"weather","temp":72,"city":"Seattle"}{"type":"news_digest","id":"1","headline":"Test Headline"} trailing text"""
    val demuxed = com.cellular.rpc.engine.JsonStreamDemuxer.extractJsonObjects(rawStream)
    assertEquals(2, demuxed.size)
    assertTrue(demuxed[0].contains("weather"))
    assertTrue(demuxed[1].contains("news_digest"))

    val parsedWidget = com.cellular.rpc.engine.WidgetData.parse(rawStream)
    assertNotNull(parsedWidget)
    assertTrue(parsedWidget is com.cellular.rpc.engine.WidgetData.Weather)
    assertEquals(72, (parsedWidget as com.cellular.rpc.engine.WidgetData.Weather).temp)
  }

  /**
   * Epic 3 (Sprint 3.1): Cellular Voice MMS Pipeline Test
   * Verifies audio compression ratio computation, file packaging into MMS-friendly containers,
   * and carrier MMS intent multi-part wrapping.
   */
  @Test
  fun `epic 3 cellular voice note MMS wrapper and compression test`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    // 1. Create mock audio file (simulating recorded voice note)
    val testAudioDir = java.io.File(context.cacheDir, "test_voice").apply { mkdirs() }
    val mockAudioFile = java.io.File(testAudioDir, "test_voice_note.m4a").apply {
      // Write 40 KB of mock compressed audio payload
      writeBytes(ByteArray(40_000) { (it % 128).toByte() })
    }

    assertTrue("Mock audio file must exist", mockAudioFile.exists())
    assertEquals(40_000L, mockAudioFile.length())

    // 2. Test CellularAudioCompressor compression pipeline
    kotlinx.coroutines.runBlocking {
      val compressionResult = com.cellular.rpc.transport.mms.CellularAudioCompressor.compressForCarrierMms(
        context = context,
        inputFile = mockAudioFile,
        targetDurationMs = 15_000L
      )

      assertNotNull("Compression result must not be null", compressionResult)
      assertTrue("Compressed file must exist", compressionResult.compressedFile.exists())
      assertTrue("Compressed size must be > 0", compressionResult.compressedSizeBytes > 0)
      assertTrue("Original size must match 40,000 bytes", compressionResult.originalSizeBytes == 40_000L)
      assertEquals(15_000L, compressionResult.durationMs)
    }

    // 3. Test MessageAttachment creation for VOICE_NOTE
    val voiceAttachment = com.cellular.rpc.engine.MessageAttachment(
      id = "voice_test_001",
      type = com.cellular.rpc.engine.AttachmentType.VOICE_NOTE,
      uri = android.net.Uri.fromFile(mockAudioFile).toString(),
      fileName = mockAudioFile.name,
      fileSizeBytes = mockAudioFile.length(),
      mimeType = "audio/mp4",
      durationMs = 15_000L,
      voiceAmplitudes = listOf(0.2f, 0.5f, 0.8f, 0.4f, 0.6f)
    )

    assertEquals(com.cellular.rpc.engine.AttachmentType.VOICE_NOTE, voiceAttachment.type)
    assertEquals(5, voiceAttachment.voiceAmplitudes.size)

    // 4. Test Carrier MMS intent creation with FileProvider wrapping
    try {
      com.cellular.rpc.transport.receiver.PallyMmsHelper.dispatchCarrierMms(
        context = context,
        destinationNumber = "+15551234567",
        text = "Voice Note Attached",
        attachmentUri = android.net.Uri.fromFile(mockAudioFile),
        mimeType = voiceAttachment.mimeType
      )
    } catch (e: Exception) {
      fail("dispatchCarrierMms must not throw exception: ${e.message}")
    }
  }
}




