package com.cellular.rpc.transport.handler

import android.content.Context
import android.util.Log
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.ChatMessageEntity
import com.cellular.rpc.data.local.PacketLogEntity
import com.cellular.rpc.data.local.WidgetCacheEntity
import com.cellular.rpc.domain.payload.CellularRequest
import com.cellular.rpc.domain.payload.CellularResponse
import com.cellular.rpc.domain.payload.CellularStatusCode
import com.cellular.rpc.domain.protocol.Frame
import com.cellular.rpc.domain.schema.CellularSchemaRegistry
import com.cellular.rpc.engine.DualResponseParser
import com.cellular.rpc.engine.WidgetData
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import com.cellular.rpc.widget.CellularCustomAppWidgetProvider
import com.cellular.rpc.widget.CellularNewsAppWidgetProvider
import com.cellular.rpc.widget.CellularWeatherAppWidgetProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Transport medium of an inbound cellular message.
 */
enum class CellularTransportType {
    SMS_DATA_PORT_8901,
    SMS_TEXT_WIRE,
    MMS_WAP_PUSH,
    LOOPBACK_SIMULATION
}

/**
 * Standardized inbound message model agnostic of whether received over SMS, MMS, or Loopback.
 */
data class InboundCellularMessage(
    val transportType: CellularTransportType,
    val senderAddress: String,
    val rawText: String,
    val rawBytes: ByteArray? = null,
    val frame: Frame? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val attachment: com.cellular.rpc.engine.MessageAttachment? = null
)

/**
 * Consumer callback for handling specific schema payloads.
 */
typealias SchemaPayloadConsumer = suspend (response: CellularResponse, context: Context) -> Unit

/**
 * Central standardized dispatcher and reply engine for all SMS and MMS traffic.
 *
 * Provides:
 * 1. Unified inbound decoding (Frame + CellularResponse + Schema detection).
 * 2. Real-time background SQLite persistence (Room ChatMessageEntity & PacketLogEntity).
 * 3. Pluggable schema consumers for instant widget and dynamic feature updates.
 * 4. Deduplication across BroadcastReceivers and ContentObservers.
 */
object CellularMessageDispatcher {

    private const val TAG = "CellularDispatcher"

    // Consumers registered for specific schema IDs
    private val schemaConsumers = ConcurrentHashMap<String, CopyOnWriteArrayList<SchemaPayloadConsumer>>()

    // Inbound deduplication cache: Message hash -> TimestampMs
    private val recentProcessedHashes = Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > 100
            }
        }
    )

    // Global event stream for UI and ViewModel
    private val _inboundEvents = MutableSharedFlow<CellularResponse>(extraBufferCapacity = 64)
    val inboundEvents: SharedFlow<CellularResponse> = _inboundEvents.asSharedFlow()

    // Currently focused/active thread ID in UI (defaults to "th_main")
    @Volatile
    var activeThreadId: String = "th_main"

    init {
        // Register default consumers for built-in widgets
        registerConsumer("weather") { response, context ->
            val db = AppDatabase.getInstance(context)
            if (response.isNotModified) {
                db.widgetCacheDao().updateStatus("weather", "304_NOT_MODIFIED", System.currentTimeMillis())
            } else {
                db.widgetCacheDao().insertOrUpdate(
                    WidgetCacheEntity(
                        widgetType = "weather",
                        contentHash = response.etag,
                        jsonPayload = response.payload,
                        lastStatus = "200_OK",
                        byteSize = response.payload.toByteArray().size,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )
            }
            CellularWeatherAppWidgetProvider.updateAllWidgets(context)
        }

        registerConsumer("news_digest") { response, context ->
            val db = AppDatabase.getInstance(context)
            if (response.isNotModified) {
                db.widgetCacheDao().updateStatus("news_digest", "304_NOT_MODIFIED", System.currentTimeMillis())
            } else {
                db.widgetCacheDao().insertOrUpdate(
                    WidgetCacheEntity(
                        widgetType = "news_digest",
                        contentHash = response.etag,
                        jsonPayload = response.payload,
                        lastStatus = "200_OK",
                        byteSize = response.payload.toByteArray().size,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )
            }
            CellularNewsAppWidgetProvider.updateAllWidgets(context)
        }

        val autoCachedSchemas = listOf(
            "market_ticker",
            "transfer",
            "poll",
            "tool",
            "calendar_event",
            "task_checklist",
            "system_status"
        )
        for (schema in autoCachedSchemas) {
            registerConsumer(schema) { response, context ->
                val db = AppDatabase.getInstance(context)
                if (response.isNotModified) {
                    db.widgetCacheDao().updateStatus(schema, "304_NOT_MODIFIED", System.currentTimeMillis())
                } else if (response.payload.isNotEmpty()) {
                    db.widgetCacheDao().insertOrUpdate(
                        WidgetCacheEntity(
                            widgetType = schema,
                            contentHash = response.etag,
                            jsonPayload = response.payload,
                            lastStatus = "200_OK",
                            byteSize = response.payload.toByteArray().size,
                            lastUpdatedMs = System.currentTimeMillis()
                        )
                    )
                }
                if (schema == "market_ticker") {
                    CellularCustomAppWidgetProvider.updateAllWidgets(context)
                } else if (schema == "transfer") {
                    CellularCustomAppWidgetProvider.updateAllWidgets(context)
                }
            }
        }
    }

    /**
     * Registers a consumer for a specific schema ID. Allows new widgets/tools to easily subscribe.
     */
    fun registerConsumer(schemaId: String, consumer: SchemaPayloadConsumer) {
        val list = schemaConsumers.computeIfAbsent(schemaId) { CopyOnWriteArrayList() }
        list.add(consumer)
    }

    /**
     * Removes a consumer.
     */
    fun unregisterConsumer(schemaId: String, consumer: SchemaPayloadConsumer) {
        schemaConsumers[schemaId]?.remove(consumer)
    }

    /**
     * Main entry point: Processes any inbound SMS, MMS, or loopback message.
     */
    suspend fun dispatchInbound(context: Context, message: InboundCellularMessage): CellularResponse {
        Log.d(TAG, "Dispatching inbound message via ${message.transportType} from ${message.senderAddress}")

        // 1. Submit low-level frame to queue engine for sliding window ACK / reassembly
        if (message.frame != null) {
            try {
                CarrierSafeQueueEngine.getInstance(context).submitInboundPacket(message.frame)
            } catch (e: Exception) {
                Log.w(TAG, "Frame reassembly error: ${e.message}")
            }
        }

        // 2. Extract payload string
        val payloadStr = when {
            message.frame != null -> String(message.frame.payload, Charsets.UTF_8)
            message.rawBytes != null -> String(message.rawBytes, Charsets.UTF_8)
            else -> message.rawText
        }

        // Filter out corrupted binary text (unless accompanied by a media attachment)
        if (com.cellular.rpc.transport.receiver.PallySmsTracker.isCorruptedOrBinaryText(payloadStr) && message.attachment == null) {
            Log.w(TAG, "Rejecting corrupted/binary payload from chat view: $payloadStr")
            return CellularResponse.fromWire(payloadStr)
        }

        // Deduplication Check (within 30-second window)
        val dedupeKey = "${message.senderAddress.filter { it.isDigit() || it == '+' }}:${payloadStr.trim()}"
        val now = System.currentTimeMillis()
        val lastSeen = recentProcessedHashes[dedupeKey]
        if (lastSeen != null && (now - lastSeen) < 30_000L) {
            Log.d(TAG, "Skipping duplicate inbound SMS packet received within ${now - lastSeen}ms.")
            return CellularResponse.fromWire(payloadStr)
        }
        recentProcessedHashes[dedupeKey] = now

        val db = AppDatabase.getInstance(context)

        // Deduplication Check: Room Database (60-second window)
        if (payloadStr.isNotBlank() && message.attachment == null) {
            try {
                val existingCount = db.chatMessageDao().countRecentMatchingMessages(payloadStr.trim(), now - 60_000L)
                if (existingCount > 0) {
                    Log.d(TAG, "Skipping duplicate inbound message already present in Room database: ${payloadStr.take(30)}")
                    return CellularResponse.fromWire(payloadStr)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking duplicate count: ${e.message}")
            }
        }

        // 2a. Record Inbound Packet in Protocol Log DAO (Protocol Inspector)
        try {
            val frame = message.frame
            val pktLog = PacketLogEntity(
                direction = "RX",
                sessionId = frame?.sessionId ?: 1,
                pktType = frame?.pktType ?: Frame.PKT_RPC_RES,
                pktTypeName = if (frame != null) Frame.typeName(frame.pktType) else "SMS_TEXT_WIRE",
                seqNo = frame?.seqNo ?: 0,
                ackBitsHex = String.format("%08X", frame?.ackBits ?: 0L),
                payloadString = payloadStr,
                wireFormat = message.rawText.ifBlank { frame?.toAsciiWire() ?: payloadStr },
                binaryByteCount = message.rawBytes?.size ?: payloadStr.toByteArray(Charsets.UTF_8).size,
                crc16Hex = String.format("%04X", frame?.crc16 ?: 0),
                crcValid = true,
                timestampMs = now
            )
            db.packetLogDao().insert(pktLog)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log RX packet: ${e.message}")
        }

        // 2b. Intercept Dynamic Feature Deployment payloads [APP:BUILD:<feature_id>]
        val isFeatureHandled = com.cellular.rpc.domain.dynamic.DynamicFeatureManager.handleInboundPayload(
            context,
            payloadStr,
            message.senderAddress
        )
        if (isFeatureHandled) {
            Log.i(TAG, "Processed dynamic feature deployment payload over cellular transport.")
        }

        // 2c. Inspect Handshake signals (READY, PROBE_SCHEMA)
        val isHandshakeHandled = com.cellular.rpc.domain.handshake.CellularHandshakeEngine.inspectInboundHandshake(
            context,
            payloadStr
        )
        if (isHandshakeHandled) {
            Log.i(TAG, "Processed Handshake signal successfully.")
        }

        // 3. Parse into standardized CellularResponse
        val response = CellularResponse.fromWire(payloadStr)
        Log.d(TAG, "Parsed response: status=${response.status}, schema=${response.schemaId}, etag=${response.etag}")

        // 3b. Correlate with pending request in Handshake Engine
        val correlatedTx = com.cellular.rpc.domain.handshake.CellularHandshakeEngine.correlateResponse(response)
        if (correlatedTx != null) {
            Log.i(TAG, "Matched inbound response to pending transaction [${correlatedTx.reqId}] on channel ${correlatedTx.targetChannel}")
        }

        // 4. Handle 304 Not Modified across relevant providers
        if (response.isNotModified) {
            val schemaTarget = if (response.schemaId != "unknown" && response.schemaId.isNotEmpty()) response.schemaId else "weather"
            db.widgetCacheDao().updateStatus(schemaTarget, "304_NOT_MODIFIED", now)
            if (schemaTarget == "weather") {
                CellularWeatherAppWidgetProvider.updateAllWidgets(context)
            } else if (schemaTarget == "news_digest" || schemaTarget == "news") {
                CellularNewsAppWidgetProvider.updateAllWidgets(context)
            } else {
                CellularCustomAppWidgetProvider.updateAllWidgets(context)
            }

            // Persist 304 Cache Render message in Chat
            val cached = db.widgetCacheDao().getWidgetByType(schemaTarget)
            val currentThread = activeThreadId.ifBlank { "th_main" }
            val chatMsg = ChatMessageEntity(
                id = "msg_${now}_${(1000..9999).random()}",
                threadId = currentThread,
                sender = "AI_GATEWAY",
                text = "Resource unmodified (304 Not Modified). Rendered from local cache.",
                widgetDataJson = cached?.jsonPayload,
                is304NotModified = true,
                wirePacket = response.toCompactWire(),
                byteSize = response.toCompactWire().length,
                pduCount = 1,
                deliveryStatus = "DELIVERED",
                timestampMs = now
            )
            db.chatMessageDao().insertMessage(chatMsg)
            db.conversationThreadDao().updateLastMessage(currentThread, chatMsg.text.take(60), now)
        } else if (payloadStr.isNotBlank() && !isHandshakeHandled) {
            // 5. Parse Dual Response (Conversational Text + Structured Native Widget Data)
            val dual = DualResponseParser.parse(payloadStr)
            val targetThreadId = dual.threadId?.ifBlank { null } ?: activeThreadId.ifBlank { "th_main" }

            // Ensure the thread exists in the database
            val existingThread = db.conversationThreadDao().getThreadById(targetThreadId)
            if (existingThread == null) {
                db.conversationThreadDao().insertOrUpdate(
                    com.cellular.rpc.data.local.ConversationThreadEntity(
                        threadId = targetThreadId,
                        title = if (targetThreadId == "th_main") "General Chat" else "Conversation ${targetThreadId.takeLast(4)}",
                        createdAtMs = now,
                        lastMessageTimestamp = now,
                        lastSnippet = ""
                    )
                )
            }

            // Cache Widget Data if present
            if (dual.widgetData != null) {
                val widgetType = dual.widgetData.type
                db.widgetCacheDao().insertOrUpdate(
                    WidgetCacheEntity(
                        widgetType = widgetType,
                        contentHash = dual.widgetData.computeContentHash(),
                        jsonPayload = dual.widgetData.toJson(),
                        lastStatus = "200_OK",
                        byteSize = dual.widgetData.toJson().toByteArray(Charsets.UTF_8).size,
                        lastUpdatedMs = now
                    )
                )
                when (widgetType) {
                    "weather" -> CellularWeatherAppWidgetProvider.updateAllWidgets(context)
                    "news_digest" -> CellularNewsAppWidgetProvider.updateAllWidgets(context)
                    else -> CellularCustomAppWidgetProvider.updateAllWidgets(context)
                }
            }

            // Persist Inbound Chat Message directly in Room
            val displayText = dual.conversationalText.ifEmpty {
                if (dual.widgetData != null) "" else payloadStr
            }

            if (displayText.isNotEmpty() || dual.widgetData != null || message.attachment != null) {
                val chatMsg = ChatMessageEntity(
                    id = "msg_${now}_${(1000..9999).random()}",
                    threadId = targetThreadId,
                    sender = "AI_GATEWAY",
                    text = displayText,
                    widgetDataJson = dual.widgetData?.toJson(),
                    is304NotModified = false,
                    wirePacket = message.rawText.ifBlank { response.toCompactWire() },
                    byteSize = payloadStr.toByteArray(Charsets.UTF_8).size,
                    pduCount = ((payloadStr.toByteArray(Charsets.UTF_8).size + 139) / 140).coerceAtLeast(1),
                    deliveryStatus = "DELIVERED",
                    timestampMs = now,
                    attachmentId = message.attachment?.id,
                    attachmentType = message.attachment?.type?.name,
                    attachmentUri = message.attachment?.uri,
                    attachmentFileName = message.attachment?.fileName,
                    attachmentSizeBytes = message.attachment?.fileSizeBytes ?: 0,
                    attachmentMimeType = message.attachment?.mimeType
                )
                db.chatMessageDao().insertMessage(chatMsg)

                val snippet = when {
                    displayText.isNotBlank() -> displayText.take(60)
                    dual.widgetData != null -> "[Widget: ${dual.widgetData.type}]"
                    message.attachment != null -> "[Attachment: ${message.attachment.fileName}]"
                    else -> "New message"
                }
                db.conversationThreadDao().updateLastMessage(targetThreadId, snippet, now)
                if (targetThreadId != activeThreadId) {
                    db.conversationThreadDao().incrementUnread(targetThreadId)
                }
            }
        }

        // 6. Invoke registered consumers
        val consumers = schemaConsumers[response.schemaId]
        consumers?.forEach { consumer ->
            try {
                consumer.invoke(response, context)
            } catch (e: Exception) {
                Log.e(TAG, "Error in consumer for schema ${response.schemaId}: ${e.message}", e)
            }
        }

        // 7. Broadcast event to UI
        _inboundEvents.tryEmit(response)
        return response
    }

    /**
     * Generates a standardized outbound reply for a cellular request.
     * Evaluates ETag for 304 generation and packs into a compliant Frame or ASCII wire string.
     */
    fun createStandardReply(
        request: CellularRequest,
        currentData: Any,
        schemaId: String
    ): CellularResponse {
        val currentHash = CellularSchemaRegistry.computeHash(schemaId, currentData)

        // 304 Content Negotation: If client ETag matches server data hash, reply with 304 (0B payload)
        if (!request.etag.isNullOrBlank() && request.etag == currentHash) {
            Log.d(TAG, "ETag match ($currentHash). Generating 304 NOT MODIFIED reply.")
            return CellularResponse.notModified(request.reqId, schemaId, currentHash)
        }

        // 200 OK Fresh Data
        val schema = CellularSchemaRegistry.getSchema<Any>(schemaId)
        val json = schema?.serialize(currentData) ?: currentData.toString()
        return CellularResponse.success(request.reqId, schemaId, currentData, json, currentHash)
    }

    /**
     * Convenience method to send an outbound reply via CarrierSafeQueueEngine.
     */
    suspend fun sendOutboundReply(
        context: Context,
        response: CellularResponse,
        destinationAddress: String,
        sessionId: Int = 1
    ): Int {
        val wirePayload = response.toCompactWire().toByteArray(Charsets.UTF_8)
        val engine = CarrierSafeQueueEngine.getInstance(context)
        return engine.enqueuePayload(
            sessionId = sessionId,
            pktType = if (response.isNotModified) Frame.PKT_CTL_ACK else Frame.PKT_RPC_RES,
            payload = wirePayload
        )
    }
}
