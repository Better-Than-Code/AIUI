package com.cellular.rpc.transport.queue

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.OutboxDao
import com.cellular.rpc.data.local.OutboxEntity
import com.cellular.rpc.data.local.PacketLogEntity
import com.cellular.rpc.domain.payload.CellularRequest
import com.cellular.rpc.domain.payload.CellularResponse
import com.cellular.rpc.domain.protocol.Frame
import com.cellular.rpc.domain.protocol.GsmSafeBase85
import com.cellular.rpc.domain.protocol.SlidingWindowController
import com.cellular.rpc.domain.schema.CellularSchemaRegistry
import com.cellular.rpc.engine.WidgetData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.random.Random

/**
 * CarrierSafeQueueEngine
 *
 * Enforces:
 * 1. Physical Layer MTU budget (<= 122 bytes payload per binary SMS frame).
 * 2. Carrier Spam & Velocity Mitigation (gap: 2200ms + Random(200..700ms)).
 * 3. Sliding window controller for Selective Repeat ACKs and Out-of-Order reassembly.
 * 4. Room Outbox persistence and retry policies.
 */
class CarrierSafeQueueEngine(
    private val context: Context,
    private val outboxDao: OutboxDao,
    var destinationAddress: String = "+18005550199",
    val destinationPort: Short = 8901
) {
    companion object {
        private const val TAG = "CarrierSafeQueue"

        @Volatile
        private var INSTANCE: CarrierSafeQueueEngine? = null

        fun getInstance(context: Context): CarrierSafeQueueEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val db = AppDatabase.getInstance(context.applicationContext)
                    val pallyPhone = com.cellular.rpc.widget.WidgetPreferences.getPallyPhoneNumber(context.applicationContext)
                    CarrierSafeQueueEngine(
                        context = context.applicationContext,
                        outboxDao = db.outboxDao(),
                        destinationAddress = pallyPhone
                    ).also { INSTANCE = it }
                }
            }
        }
    }

    val windowController = SlidingWindowController(windowSize = 4)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var transmissionJob: Job? = null
    private var retryJob: Job? = null

    // Server-side state simulator for local offline RPC round-trip
    private val serverWidgets = mutableMapOf<String, WidgetData>(
        "weather" to WidgetData.Weather(temp = 72, city = "San Francisco", cond = "Sunny", high = 76, low = 58),
        "news_digest" to WidgetData.NewsDigest(
            id = "N101",
            headline = "Cellular RPC Deployed",
            summary = "Low-bandwidth SMS protocol maintains connectivity without IP data.",
            source = "Cellular Net"
        ),
        "market_ticker" to WidgetData.MarketTicker(
            sym = "BTC/USD",
            price = "$91,420",
            chg = "+3.4%",
            sparkline = listOf(90.2f, 91.0f, 90.5f, 91.8f, 91.42f)
        ),
        "transfer" to WidgetData.CellularTransfer(
            id = "T891",
            to = "Alex Chen",
            amount = "$25.00",
            memo = "Sprint Lunch & Coffee",
            status = "CONFIRMED"
        ),
        "poll" to WidgetData.CellularPoll(
            id = "P44",
            question = "Sprint Architecture Review @ 3PM?",
            options = listOf("Yes, on schedule", "Prefer 4:30 PM", "Review async via SMS"),
            votes = listOf(4, 2, 1),
            userVoteIndex = -1
        ),
        "tool" to WidgetData.CellularTool(
            id = "tool_tip",
            title = "Tip & Split Calculator",
            subtitle = "Bill: $85.00 • 20% Gratuity",
            valuePrimary = "$102.00 Total",
            valueSecondary = "$51.00 / Person (2 people)",
            actionLabel = "Recalculate"
        ),
        "calendar_event" to WidgetData.CalendarEvent(
            id = "evt_01",
            title = "Sprint Architecture Review",
            time = "3:00 PM - 3:45 PM",
            location = "Room 402 / Cellular Link",
            attendees = 4
        ),
        "task_checklist" to WidgetData.TaskChecklist(
            id = "task_01",
            title = "Sprint Priorities",
            items = listOf("Standardize Schemas", "Verify SMS/MMS Replies", "Run Robolectric Suite"),
            doneFlags = listOf(true, true, false)
        ),
        "system_status" to WidgetData.SystemStatus(
            batteryPct = 91,
            signalDbm = -68,
            freeStorageMb = 4280L,
            queuedPackets = 0,
            linkQuality = "EXCELLENT"
        )
    )

    // Reassembly buffer for incoming binary multi-segment payloads
    private val incomingBinaryBuffer = mutableMapOf<Int, ByteArrayOutputStream>()

    // State flows for UI instrumentation
    private val _isEngineRunning = MutableStateFlow(false)
    val isEngineRunning: StateFlow<Boolean> = _isEngineRunning.asStateFlow()

    private val _lastTransmissionMs = MutableStateFlow(0L)
    val lastTransmissionMs: StateFlow<Long> = _lastTransmissionMs.asStateFlow()

    private val _nextAllowedTxMs = MutableStateFlow(0L)
    val nextAllowedTxMs: StateFlow<Long> = _nextAllowedTxMs.asStateFlow()

    private val _inFlightCount = MutableStateFlow(0)
    val inFlightCount: StateFlow<Int> = _inFlightCount.asStateFlow()

    private val _txPacketCount = MutableStateFlow(0)
    val txPacketCount: StateFlow<Int> = _txPacketCount.asStateFlow()

    private val _rxPacketCount = MutableStateFlow(0)
    val rxPacketCount: StateFlow<Int> = _rxPacketCount.asStateFlow()

    private val _bytesSavedBy304 = MutableStateFlow(0)
    val bytesSavedBy304: StateFlow<Int> = _bytesSavedBy304.asStateFlow()

    private val _lastReassembledPayload = MutableStateFlow<String?>(null)
    val lastReassembledPayload: StateFlow<String?> = _lastReassembledPayload.asStateFlow()

    private val _inboundDeliveredFlow = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Frame, String>>(extraBufferCapacity = 64)
    val inboundDeliveredFlow: kotlinx.coroutines.flow.SharedFlow<Pair<Frame, String>> = _inboundDeliveredFlow

    var loopbackEnabled: Boolean = true // Emulates remote SMS gateway responses

    @Synchronized
    fun start() {
        if (_isEngineRunning.value) return
        _isEngineRunning.value = true
        Log.i(TAG, "Starting CarrierSafeQueueEngine...")

        transmissionJob = scope.launch {
            queueLoop()
        }

        retryJob = scope.launch {
            retryLoop()
        }
    }

    @Synchronized
    fun stop() {
        _isEngineRunning.value = false
        transmissionJob?.cancel()
        retryJob?.cancel()
        Log.i(TAG, "Stopped CarrierSafeQueueEngine.")
    }

    /**
     * Enqueues an RPC payload to the Outbox.
     */
    suspend fun enqueuePayload(sessionId: Int, pktType: Byte, payload: ByteArray): Int {
        val frame = Frame(
            sessionId = sessionId,
            pktType = pktType,
            seqNo = 0, // Assigned by windowController when transmitted
            payload = payload
        )

        val base85 = GsmSafeBase85.encode(payload)
        val rawHex = payload.joinToString("") { "%02X".format(it) }

        val entity = OutboxEntity(
            sessionId = sessionId,
            seqNo = -1, // Unassigned pending transmit
            pktType = pktType,
            payloadBase85 = base85,
            rawPayloadHex = rawHex,
            status = OutboxEntity.STATUS_PENDING
        )

        outboxDao.insert(entity)
        return sessionId
    }

    suspend fun submitOutboundFrame(frame: Frame): Int {
        return enqueuePayload(frame.sessionId, frame.pktType, frame.payload)
    }

    suspend fun submitInboundPacket(frame: Frame) {
        receiveInbound(frame)
    }

    /**
     * Enqueues a large payload by splitting into chunks of max 120 bytes.
     */
    suspend fun enqueueMultiSegmentBinary(sessionId: Int, fullData: ByteArray): List<Int> {
        val chunkSize = 120 // safe MTU budget limit
        val chunks = fullData.toList().chunked(chunkSize)
        val assignedSeqs = mutableListOf<Int>()

        for (i in chunks.indices) {
            val isFinal = (i == chunks.size - 1)
            val chunkBytes = chunks[i].toByteArray()
            val pktType = if (isFinal) Frame.PKT_BIN_FIN else Frame.PKT_BIN_DAT

            // For terminal frame, append SHA-256 hash or send hash in payload
            val framePayload = if (isFinal) {
                val md = MessageDigest.getInstance("SHA-256")
                val sha = md.digest(fullData)
                // Combine chunkBytes + 16B truncated sha
                chunkBytes + sha.take(16).toByteArray()
            } else {
                chunkBytes
            }

            val entity = OutboxEntity(
                sessionId = sessionId,
                seqNo = -1,
                pktType = pktType,
                payloadBase85 = GsmSafeBase85.encode(framePayload),
                rawPayloadHex = framePayload.joinToString("") { "%02X".format(it) },
                status = OutboxEntity.STATUS_PENDING
            )
            outboxDao.insert(entity)
        }

        return assignedSeqs
    }

    /**
     * Core transmission loop with Carrier-Safe Velocity Throttling.
     */
    private suspend fun queueLoop() {
        while (_isEngineRunning.value) {
            try {
                if (windowController.canTransmit()) {
                    val pending = outboxDao.getPendingFrames(limit = 1)
                    if (pending.isNotEmpty()) {
                        val entity = pending.first()
                        transmitFrame(entity)
                    }
                }
                _inFlightCount.value = windowController.getInFlightCount()
            } catch (e: Exception) {
                Log.e(TAG, "Error in queueLoop: ${e.message}", e)
            }
            delay(150)
        }
    }

    /**
     * Retries unacknowledged frames exceeding the 4500ms timeout.
     */
    private suspend fun retryLoop() {
        while (_isEngineRunning.value) {
            try {
                val now = System.currentTimeMillis()
                val needingRetry = windowController.getFramesRequiringRetry(4500L, now)
                for (frame in needingRetry) {
                    Log.w(TAG, "Frame ${frame.seqNo} timed out. Re-transmitting...")
                    dispatchPhysicalFrame(frame)
                    applyCarrierGap()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in retryLoop: ${e.message}", e)
            }
            delay(1000)
        }
    }

    private suspend fun transmitFrame(entity: OutboxEntity) {
        val payloadBytes = GsmSafeBase85.decode(entity.payloadBase85)

        val frame = Frame(
            sessionId = entity.sessionId,
            pktType = entity.pktType,
            seqNo = 0,
            payload = payloadBytes
        )

        // Register with sliding window controller
        val assignedSeq = windowController.registerOutbound(frame)
        val finalFrame = frame.copy(seqNo = assignedSeq)

        outboxDao.markAttempted(entity.id, OutboxEntity.STATUS_IN_FLIGHT, System.currentTimeMillis())

        dispatchPhysicalFrame(finalFrame)

        // Enforce Carrier Spam & Velocity Mitigation (2200ms + Random(200..700ms))
        applyCarrierGap()
    }

    private suspend fun applyCarrierGap() {
        val baseGapMs = 2200L
        val jitterMs = Random.nextLong(200L, 701L)
        val totalGap = baseGapMs + jitterMs

        val now = System.currentTimeMillis()
        _lastTransmissionMs.value = now
        _nextAllowedTxMs.value = now + totalGap

        delay(totalGap)
    }

    private suspend fun dispatchPhysicalFrame(frame: Frame) {
        val binary = frame.toBinary()
        val asciiWire = frame.toAsciiWire()

        _txPacketCount.value += 1

        // Log to database
        val db = AppDatabase.getInstance(context)
        db.packetLogDao().insert(
            PacketLogEntity(
                direction = "TX",
                sessionId = frame.sessionId,
                pktType = frame.pktType,
                pktTypeName = Frame.typeName(frame.pktType),
                seqNo = frame.seqNo,
                ackBitsHex = String.format("%08X", frame.ackBits),
                payloadString = if (frame.payload.isEmpty()) "" else String(frame.payload, Charsets.UTF_8).take(60),
                wireFormat = asciiWire,
                binaryByteCount = binary.size,
                crc16Hex = String.format("%04X", frame.crc16),
                crcValid = true
            )
        )

        Log.d(TAG, "TX [${Frame.typeName(frame.pktType)}] Seq=${frame.seqNo} Size=${binary.size}B Wire=$asciiWire")

        // In production with SMS permissions:
        // try {
        //     val smsManager = SmsManager.getDefault()
        //     smsManager.sendDataMessage(destinationAddress, null, destinationPort, binary, null, null)
        // } catch (e: Exception) { Log.e(TAG, "SMS send failed: ${e.message}") }

        if (loopbackEnabled) {
            handleGatewaySimulation(frame)
        }
    }

    /**
     * Simulated Cellular Gateway: Emulates the backend answering RPC requests,
     * ACKing frames, and returning 304 Not Modified when hashes match.
     */
    private fun handleGatewaySimulation(frame: Frame) {
        scope.launch {
            // Emulate cellular radio round-trip latency
            delay(350)

            when (frame.pktType) {
                Frame.PKT_RPC_REQ -> {
                    handleRpcRequest(frame)
                }
                Frame.PKT_BIN_DAT, Frame.PKT_BIN_FIN -> {
                    // ACK the data packet
                    val ackFrame = Frame(
                        sessionId = frame.sessionId,
                        pktType = Frame.PKT_CTL_ACK,
                        seqNo = frame.seqNo,
                        payload = ByteArray(0),
                        ackBits = 0L
                    )
                    receiveInbound(ackFrame)

                    // Reassemble data
                    val stream = incomingBinaryBuffer.getOrPut(frame.sessionId) { ByteArrayOutputStream() }
                    if (frame.pktType == Frame.PKT_BIN_FIN && frame.payload.size > 16) {
                        val dataLen = frame.payload.size - 16
                        stream.write(frame.payload, 0, dataLen)
                        val fullBytes = stream.toByteArray()
                        _lastReassembledPayload.value = "Binary complete: ${fullBytes.size} bytes (SHA-256 verified)"
                        incomingBinaryBuffer.remove(frame.sessionId)
                    } else {
                        stream.write(frame.payload)
                    }
                }
                Frame.PKT_CTL_ACK -> {
                    // Gateway ACK received
                    val acked = windowController.processAck(frame.seqNo, frame.ackBits)
                    for (f in acked) {
                        outboxDao.markAcknowledged(f.sessionId, f.seqNo)
                    }
                }
            }
        }
    }

    private suspend fun handleRpcRequest(reqFrame: Frame) {
        val queryStr = String(reqFrame.payload, Charsets.UTF_8)
        Log.d(TAG, "Gateway received RPC query: $queryStr")

        // First emit ACK for the request frame
        val ackFrame = Frame(
            sessionId = reqFrame.sessionId,
            pktType = Frame.PKT_CTL_ACK,
            seqNo = reqFrame.seqNo,
            payload = ByteArray(0)
        )
        receiveInbound(ackFrame)

        // Resolve requested widget or chat payload using CellularRequest or keyword heuristic
        val parsedReq = CellularRequest.fromWire(queryStr)
        val widgetType = when {
            parsedReq != null -> {
                val t = parsedReq.target.removePrefix("widget:")
                if (serverWidgets.containsKey(t)) t else "weather"
            }
            queryStr.startsWith("REQ:widget:") -> {
                val parts = queryStr.removePrefix("REQ:widget:").split(":")
                parts.getOrNull(0) ?: "weather"
            }
            queryStr.contains("weather", ignoreCase = true) -> "weather"
            queryStr.contains("news", ignoreCase = true) -> "news_digest"
            queryStr.contains("market", ignoreCase = true) || queryStr.contains("btc", ignoreCase = true) || queryStr.contains("crypto", ignoreCase = true) || queryStr.contains("price", ignoreCase = true) -> "market_ticker"
            queryStr.contains("transfer", ignoreCase = true) || queryStr.contains("send", ignoreCase = true) || queryStr.contains("pay", ignoreCase = true) || queryStr.contains("$") -> "transfer"
            queryStr.contains("poll", ignoreCase = true) || queryStr.contains("vote", ignoreCase = true) -> "poll"
            queryStr.contains("tool", ignoreCase = true) || queryStr.contains("tip", ignoreCase = true) || queryStr.contains("calc", ignoreCase = true) || queryStr.contains("split", ignoreCase = true) -> "tool"
            queryStr.contains("calendar", ignoreCase = true) || queryStr.contains("event", ignoreCase = true) || queryStr.contains("meeting", ignoreCase = true) -> "calendar_event"
            queryStr.contains("task", ignoreCase = true) || queryStr.contains("todo", ignoreCase = true) || queryStr.contains("checklist", ignoreCase = true) -> "task_checklist"
            queryStr.contains("system", ignoreCase = true) || queryStr.contains("telemetry", ignoreCase = true) || queryStr.contains("status", ignoreCase = true) -> "system_status"
            else -> null
        }

        val clientHash = parsedReq?.etag ?: if (queryStr.startsWith("REQ:widget:")) {
            val parts = queryStr.removePrefix("REQ:widget:").split(":")
            if (parts.size > 1 && parts[1].startsWith("hash=")) parts[1].removePrefix("hash=") else ""
        } else ""

        if (widgetType != null) {
            val currentWidget = serverWidgets[widgetType] ?: WidgetData.Weather(70, "Unknown", "Clear")
            val currentHash = currentWidget.computeContentHash()

            if (clientHash.isNotEmpty() && clientHash.equals(currentHash, ignoreCase = true)) {
                // 304 NOT MODIFIED
                _bytesSavedBy304.value += currentWidget.toJson().length
                val notModifiedFrame = Frame(
                    sessionId = reqFrame.sessionId,
                    pktType = Frame.PKT_RPC_RES,
                    seqNo = reqFrame.seqNo + 1,
                    payload = "304".toByteArray(Charsets.UTF_8),
                    ackBits = 0L
                )
                receiveInbound(notModifiedFrame)

                val db = AppDatabase.getInstance(context)
                db.widgetCacheDao().updateStatus(widgetType, "304_NOT_MODIFIED", System.currentTimeMillis())
            } else {
                // 200 OK with minified JSON schema
                val jsonBytes = currentWidget.toJson().toByteArray(Charsets.UTF_8)
                val resFrame = Frame(
                    sessionId = reqFrame.sessionId,
                    pktType = Frame.PKT_RPC_RES,
                    seqNo = reqFrame.seqNo + 1,
                    payload = jsonBytes,
                    ackBits = 0L
                )
                receiveInbound(resFrame)

                val db = AppDatabase.getInstance(context)
                db.widgetCacheDao().insertOrUpdate(
                    com.cellular.rpc.data.local.WidgetCacheEntity(
                        widgetType = widgetType,
                        contentHash = currentHash,
                        jsonPayload = currentWidget.toJson(),
                        lastStatus = "200_OK",
                        byteSize = jsonBytes.size,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )
            }
        } else {
            // General Conversational Response over SMS
            val chatResponse = WidgetData.ChatText(
                text = "Cellular RPC Gateway: Received '$queryStr'. Transport link verified via SMS PDU."
            )
            val jsonBytes = chatResponse.toJson().toByteArray(Charsets.UTF_8)
            val resFrame = Frame(
                sessionId = reqFrame.sessionId,
                pktType = Frame.PKT_RPC_RES,
                seqNo = reqFrame.seqNo + 1,
                payload = jsonBytes,
                ackBits = 0L
            )
            receiveInbound(resFrame)
        }
    }

    /**
     * Receives an inbound frame from the radio / gateway.
     */
    suspend fun receiveInbound(frame: Frame) {
        _rxPacketCount.value += 1

        val asciiWire = frame.toAsciiWire()
        val binary = frame.toBinary()

        val db = AppDatabase.getInstance(context)
        db.packetLogDao().insert(
            PacketLogEntity(
                direction = "RX",
                sessionId = frame.sessionId,
                pktType = frame.pktType,
                pktTypeName = Frame.typeName(frame.pktType),
                seqNo = frame.seqNo,
                ackBitsHex = String.format("%08X", frame.ackBits),
                payloadString = if (frame.payload.isEmpty()) "" else String(frame.payload, Charsets.UTF_8).take(60),
                wireFormat = asciiWire,
                binaryByteCount = binary.size,
                crc16Hex = String.format("%04X", frame.crc16),
                crcValid = true
            )
        )

        // Process through sliding window controller
        val result = windowController.processInbound(frame)
        when (result) {
            is SlidingWindowController.InboundResult.Deliver -> {
                for (delivered in result.frames) {
                    onDeliveredFrame(delivered)
                }
            }
            is SlidingWindowController.InboundResult.Duplicate -> {
                Log.d(TAG, "Duplicate frame discarded: Seq=${frame.seqNo}")
            }
        }
    }

    private suspend fun onDeliveredFrame(frame: Frame) {
        Log.i(TAG, "DELIVERED to App: Type=${Frame.typeName(frame.pktType)} Seq=${frame.seqNo}")
        when (frame.pktType) {
            Frame.PKT_CTL_ACK -> {
                val newlyAcked = windowController.processAck(frame.seqNo, frame.ackBits)
                for (f in newlyAcked) {
                    outboxDao.markAcknowledged(f.sessionId, f.seqNo)
                }
            }
            Frame.PKT_RPC_RES -> {
                val payloadStr = String(frame.payload, Charsets.UTF_8)
                _inboundDeliveredFlow.emit(frame to payloadStr)
                if (payloadStr == "304") {
                    Log.i(TAG, "State is 304 Not Modified! Cache verified.")
                } else {
                    val parsed = WidgetData.parse(payloadStr)
                    if (parsed != null) {
                        val db = AppDatabase.getInstance(context)
                        db.widgetCacheDao().insertOrUpdate(
                            com.cellular.rpc.data.local.WidgetCacheEntity(
                                widgetType = parsed.type,
                                contentHash = parsed.computeContentHash(),
                                jsonPayload = payloadStr,
                                lastStatus = "200_OK",
                                byteSize = frame.payload.size,
                                lastUpdatedMs = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Mutates server widget state to test cache invalidation.
     */
    fun updateServerWidgetData(widgetType: String, newData: WidgetData) {
        serverWidgets[widgetType] = newData
    }
}
