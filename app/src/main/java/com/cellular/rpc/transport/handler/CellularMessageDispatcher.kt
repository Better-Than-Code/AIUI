package com.cellular.rpc.transport.handler

import android.content.Context
import android.util.Log
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.WidgetCacheEntity
import com.cellular.rpc.domain.payload.CellularRequest
import com.cellular.rpc.domain.payload.CellularResponse
import com.cellular.rpc.domain.payload.CellularStatusCode
import com.cellular.rpc.domain.protocol.Frame
import com.cellular.rpc.domain.schema.CellularSchemaRegistry
import com.cellular.rpc.engine.WidgetData
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import com.cellular.rpc.widget.CellularNewsAppWidgetProvider
import com.cellular.rpc.widget.CellularWeatherAppWidgetProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val timestampMs: Long = System.currentTimeMillis()
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
 * 2. Pluggable schema consumers so new widgets and features can be plugged in seamlessly.
 * 3. Standardized reply generation (ACK, 304, data responses, tool computations).
 * 4. Automatic database caching and AppWidget updates.
 */
object CellularMessageDispatcher {

    private const val TAG = "CellularDispatcher"

    // Consumers registered for specific schema IDs
    private val schemaConsumers = ConcurrentHashMap<String, CopyOnWriteArrayList<SchemaPayloadConsumer>>()

    // Global event stream for UI and ViewModel
    private val _inboundEvents = MutableSharedFlow<CellularResponse>(extraBufferCapacity = 64)
    val inboundEvents: SharedFlow<CellularResponse> = _inboundEvents.asSharedFlow()

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

        // 2b. Intercept Dynamic Feature Deployment payloads [APP:BUILD:<feature_id>]
        if (com.cellular.rpc.domain.dynamic.DynamicFeatureManager.handleInboundPayload(context, payloadStr, message.senderAddress)) {
            Log.i(TAG, "Processed dynamic feature deployment payload over cellular transport.")
        }

        // 3. Parse into standardized CellularResponse
        val response = CellularResponse.fromWire(payloadStr)
        Log.d(TAG, "Parsed response: status=${response.status}, schema=${response.schemaId}, etag=${response.etag}")

        // 4. Handle 304 Not Modified across relevant providers
        if (response.isNotModified) {
            val db = AppDatabase.getInstance(context)
            if (response.schemaId != "unknown" && response.schemaId.isNotEmpty()) {
                db.widgetCacheDao().updateStatus(response.schemaId, "304_NOT_MODIFIED", System.currentTimeMillis())
                if (response.schemaId == "weather") {
                    CellularWeatherAppWidgetProvider.updateAllWidgets(context)
                } else if (response.schemaId == "news_digest" || response.schemaId == "news") {
                    CellularNewsAppWidgetProvider.updateAllWidgets(context)
                }
            } else {
                db.widgetCacheDao().updateStatus("weather", "304_NOT_MODIFIED", System.currentTimeMillis())
                db.widgetCacheDao().updateStatus("news_digest", "304_NOT_MODIFIED", System.currentTimeMillis())
                CellularWeatherAppWidgetProvider.updateAllWidgets(context)
                CellularNewsAppWidgetProvider.updateAllWidgets(context)
            }
        }

        // 5. Invoke registered consumers
        val consumers = schemaConsumers[response.schemaId]
        consumers?.forEach { consumer ->
            try {
                consumer.invoke(response, context)
            } catch (e: Exception) {
                Log.e(TAG, "Error in consumer for schema ${response.schemaId}: ${e.message}", e)
            }
        }

        // 6. Broadcast event to UI
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
