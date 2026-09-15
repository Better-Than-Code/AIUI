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
import com.cellular.rpc.engine.DualParsedResponse
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

        // Live Chat UI Theming over Cellular
        val themeConsumer: SchemaPayloadConsumer = { response, context ->
            try {
                if (response.payload.isNotBlank()) {
                    com.cellular.rpc.ui.chat.theme.ChatThemeManager.updateTheme(context, response.payload, isStaged = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply live chat theme: ${e.message}")
            }
        }
        registerConsumer("chat_theme", themeConsumer)
        registerConsumer("theme", themeConsumer)
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

        // INC-26: Thread isolation guard. Reject messages from non-active senders
        if (message.senderAddress.isNotBlank() &&
            message.senderAddress != "MMS_GATEWAY" &&
            message.senderAddress != "PALLY_SYSTEM" &&
            message.senderAddress != "LOCAL_TEST" &&
            message.senderAddress != "PALLY_AI"
        ) {
            if (!com.cellular.rpc.domain.service.CellularServiceManager.isSenderRecognized(context, message.senderAddress)) {
                Log.w(TAG, "CellularMessageDispatcher: Dropping inbound message from unrecognized/non-active sender '${message.senderAddress}' to maintain thread isolation.")
                return CellularResponse(status = CellularStatusCode.FORBIDDEN_403, schemaId = "thread_isolation", payload = "")
            }
        }

        // 1. Submit low-level frame to queue engine for sliding window ACK / reassembly
        if (message.frame != null) {
            try {
                CarrierSafeQueueEngine.getInstance(context).submitInboundPacket(message.frame)
            } catch (e: Exception) {
                Log.w(TAG, "Frame reassembly error: ${e.message}")
            }
            if (message.frame.pktType == Frame.PKT_CTL_ACK) {
                Log.d(TAG, "CellularMessageDispatcher: Intercepted ACK control packet (seq=${message.frame.seqNo}), skipping UI chat hydration.")
                return CellularResponse(status = 200, schemaId = "ack", payload = "")
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
        val dedupeKey = if (message.attachment != null) {
            "${message.senderAddress.filter { it.isDigit() || it == '+' }}:att:${message.attachment.id}:${message.attachment.fileSizeBytes}"
        } else {
            "${message.senderAddress.filter { it.isDigit() || it == '+' }}:${payloadStr.trim()}"
        }
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
        } else if ((payloadStr.isNotBlank() || message.attachment != null) && !isHandshakeHandled) {
            // 5. Parse Dual Response (Conversational Text + Structured Native Widget Data)
            val dual = if (payloadStr.isNotBlank()) {
                try {
                    DualResponseParser.parse(payloadStr)
                } catch (e: Exception) {
                    Log.e(TAG, "INC-12: Fenced Wire Envelope parser fault, recovering stream...", e)
                    DualParsedResponse(
                        conversationalText = "⚠️ Payload Parse Error. Raw: ${payloadStr.take(50)}...",
                        widgetData = null,
                        threadId = null
                    )
                }
            } else {
                DualParsedResponse(
                    conversationalText = "",
                    widgetData = null,
                    threadId = null
                )
            }
            val targetThreadId = dual.threadId?.ifBlank { null } ?: activeThreadId.ifBlank { "th_main" }
            var isSilent = false

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
            var effectiveWidgetData = dual.widgetData
            var calculatedRevision = 1
            val newMsgId = "msg_${now}_${(1000..9999).random()}"

            if (effectiveWidgetData != null) {
                // If this is a mini app patch, apply delta patch to SQLite blueprint and project updated preview
                if (effectiveWidgetData is WidgetData.MiniAppPatch) {
                    val app = db.appBlueprintDao().getAppById(effectiveWidgetData.appId)
                    if (app != null) {
                        val currentBlueprint = com.cellular.rpc.domain.miniapp.MiniAppBlueprint.fromJson(app.rawBlueprintJson)
                        if (currentBlueprint != null) {
                            val patchResult = com.cellular.rpc.domain.miniapp.BlueprintPatcher.applyDeltaPatch(
                                currentBlueprint,
                                emptyMap(),
                                effectiveWidgetData.patchJsonStr
                            )
                            if (patchResult.success && patchResult.patchedBlueprint != null) {
                                val patchedApp = app.copy(
                                    rawBlueprintJson = patchResult.patchedBlueprint.rawJson,
                                    lastUpdated = now
                                )
                                db.appBlueprintDao().installOrUpdate(patchedApp)
                                android.util.Log.i(TAG, "Successfully hot-patched mini app: ${app.appId}")
                                // Project updated preview to tail
                                effectiveWidgetData = WidgetData.MiniAppPreview(
                                    appId = patchResult.patchedBlueprint.appId,
                                    version = patchResult.patchedBlueprint.version,
                                    title = patchResult.patchedBlueprint.metadata.title,
                                    icon = patchResult.patchedBlueprint.metadata.icon,
                                    description = patchResult.patchedBlueprint.metadata.description,
                                    category = patchResult.patchedBlueprint.metadata.category,
                                    rawBlueprintJson = patchResult.patchedBlueprint.rawJson
                                )
                            } else {
                                android.util.Log.e(TAG, "Hot-patching failed for ${app.appId}: ${patchResult.errorMessage}")
                            }
                        }
                    }
                }

                val widgetType = effectiveWidgetData.type
                db.widgetCacheDao().insertOrUpdate(
                    WidgetCacheEntity(
                        widgetType = widgetType,
                        contentHash = effectiveWidgetData.computeContentHash(),
                        jsonPayload = effectiveWidgetData.toJson(),
                        lastStatus = "200_OK",
                        byteSize = effectiveWidgetData.toJson().toByteArray(Charsets.UTF_8).size,
                        lastUpdatedMs = now
                    )
                )
                when (widgetType) {
                    "weather" -> CellularWeatherAppWidgetProvider.updateAllWidgets(context)
                    "news_digest" -> CellularNewsAppWidgetProvider.updateAllWidgets(context)
                    else -> CellularCustomAppWidgetProvider.updateAllWidgets(context)
                }

                // FEAT-06 Feed-Tail Projection & Revisioning:
                // Find existing historical cards for this widget ID or type
                val wid = effectiveWidgetData.widgetId
                val idPattern = if (!wid.isNullOrBlank()) "%\"id\":\"$wid\"%" else "%\"type\":\"$widgetType\"%"
                val typePattern = "%\"type\":\"$widgetType\"%"

                val existingMatches = db.chatMessageDao().findMatchingWidgetMessages(targetThreadId, idPattern, typePattern)
                if (existingMatches.isNotEmpty()) {
                    val highestRev = existingMatches.maxOfOrNull { it.revision } ?: 1
                    calculatedRevision = highestRev + 1
                    // De-emphasize older historical upstream card(s) as superseded
                    db.chatMessageDao().markMatchingWidgetsSuperseded(targetThreadId, idPattern, typePattern, newMsgId)
                }

                // Delete any pending skeleton loading placeholders in this thread
                db.chatMessageDao().deleteSkeletonMessages(targetThreadId)

                // Epic 14.2: Natural language chat creation inserts into local SQLite
                if (effectiveWidgetData is WidgetData.TaskChecklist) {
                    effectiveWidgetData.items.forEachIndexed { index, itemText ->
                        val isDone = effectiveWidgetData.doneFlags.getOrElse(index) { false }
                        db.taskDao().insertTask(
                            com.cellular.rpc.data.local.TaskEntity(
                                id = "task_${now}_$index",
                                title = itemText,
                                listId = "inbox",
                                isCompleted = isDone
                            )
                        )
                    }
                } else if (effectiveWidgetData is WidgetData.CalendarEvent) {
                    db.calendarEventDao().insertEvent(
                        com.cellular.rpc.data.local.CalendarEventEntity(
                            id = effectiveWidgetData.id,
                            title = effectiveWidgetData.title,
                            description = effectiveWidgetData.time,
                            location = effectiveWidgetData.location,
                            startTimeMs = now,
                            endTimeMs = now + 3600000
                        )
                    )
                } else if (effectiveWidgetData is WidgetData.RpcControlFrame) {
                    val rpc = effectiveWidgetData
                    CarrierSafeQueueEngine.getInstance(context).onAckReceived(
                        hash = rpc.hash,
                        chunks = rpc.chunks,
                        rawWire = message.rawText
                    )
                    isSilent = true
                }
            }

            // Persist Inbound Chat Message directly in Room
            val displayText = dual.conversationalText.ifEmpty {
                if (effectiveWidgetData != null) "" else payloadStr
            }

            if (!isSilent && (displayText.isNotEmpty() || effectiveWidgetData != null || message.attachment != null)) {
                val chatMsg = ChatMessageEntity(
                    id = newMsgId,
                    threadId = targetThreadId,
                    sender = "AI_GATEWAY",
                    text = displayText,
                    widgetDataJson = effectiveWidgetData?.toJson(),
                    is304NotModified = false,
                    wirePacket = message.rawText.ifBlank { response.toCompactWire() },
                    byteSize = payloadStr.toByteArray(Charsets.UTF_8).size,
                    pduCount = ((payloadStr.toByteArray(Charsets.UTF_8).size + 139) / 140).coerceAtLeast(1),
                    deliveryStatus = "DELIVERED",
                    timestampMs = now,
                    revision = calculatedRevision,
                    isSuperseded = false,
                    supersededByMessageId = null,
                    attachmentId = message.attachment?.id,
                    attachmentType = message.attachment?.type?.name,
                    attachmentUri = message.attachment?.uri,
                    attachmentFileName = message.attachment?.fileName,
                    attachmentSizeBytes = message.attachment?.fileSizeBytes ?: 0,
                    attachmentMimeType = message.attachment?.mimeType,
                    attachmentDurationMs = message.attachment?.durationMs ?: 0L,
                    attachmentAmplitudes = message.attachment?.voiceAmplitudes?.takeIf { it.isNotEmpty() }?.joinToString(",")
                )
                db.chatMessageDao().insertMessage(chatMsg)

                val snippet = when {
                    displayText.isNotBlank() -> displayText.take(60)
                    effectiveWidgetData != null -> "[Widget: ${effectiveWidgetData.type}]"
                    message.attachment?.type == com.cellular.rpc.engine.AttachmentType.VOICE_NOTE -> "🎤 Voice Note (${(message.attachment.durationMs / 1000).coerceAtLeast(1)}s)"
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
