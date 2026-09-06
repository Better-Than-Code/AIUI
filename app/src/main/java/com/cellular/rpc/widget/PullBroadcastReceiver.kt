package com.cellular.rpc.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.domain.protocol.Frame
import com.cellular.rpc.engine.WidgetData
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PullBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "PullBroadcastReceiver"
        const val ACTION_WIDGET_MANUAL_REFRESH = "com.cellular.rpc.ACTION_WIDGET_MANUAL_REFRESH"
        const val ACTION_PERIODIC_PULL_TICK = "com.cellular.rpc.ACTION_PERIODIC_PULL_TICK"

        const val EXTRA_WIDGET_TYPE = "extra_widget_type" // "weather" or "news"
        const val EXTRA_APP_WIDGET_ID = "extra_app_widget_id"

        fun schedulePeriodicPull(context: Context, intervalMinutes: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, PullBroadcastReceiver::class.java).apply {
                action = ACTION_PERIODIC_PULL_TICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (intervalMinutes <= 0) {
                alarmManager.cancel(pendingIntent)
                Log.d(TAG, "Periodic pull cancelled (Manual mode)")
                return
            }

            val intervalMs = intervalMinutes * 60 * 1000L
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + intervalMs,
                intervalMs,
                pendingIntent
            )
            Log.d(TAG, "Scheduled periodic pull every $intervalMinutes minutes")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received action: $action")

        val widgetType = intent.getStringExtra(EXTRA_WIDGET_TYPE) ?: "weather"
        val widgetId = intent.getIntExtra(EXTRA_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        CoroutineScope(Dispatchers.IO).launch {
            if (widgetType == "custom") {
                CellularCustomAppWidgetProvider.updateAllWidgets(context)
            } else {
                triggerPull(context.applicationContext, widgetType, widgetId)
            }
        }
    }

    private suspend fun triggerPull(context: Context, widgetType: String, widgetId: Int) {
        val db = AppDatabase.getInstance(context)
        val schemaTarget = if (widgetType == "news") "news_digest" else "weather"
        val cached = db.widgetCacheDao().getWidgetByType(schemaTarget)
        val currentHash = cached?.contentHash ?: "00000000"

        val pallyNumber = WidgetPreferences.getPallyPhoneNumber(context)
        val zip = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetPreferences.getWeatherZip(context, widgetId)
        } else "94102"

        val cellularReq = com.cellular.rpc.domain.payload.CellularRequest(
            action = com.cellular.rpc.domain.payload.CellularAction.PULL,
            target = "widget:$schemaTarget",
            etag = if (currentHash != "00000000") currentHash else null,
            params = if (widgetType == "news") emptyMap() else mapOf("zip" to zip)
        )
        val pullPayload = cellularReq.toCompactWire()

        Log.d(TAG, "Dispatching Cellular Pull via Pally SMS to $pallyNumber: $pullPayload")

        val outFrame = Frame(
            sessionId = 0x1A2F,
            pktType = Frame.PKT_RPC_REQ,
            seqNo = 0,
            payload = pullPayload.toByteArray(Charsets.UTF_8)
        )

        // Queue frame to outbox via CarrierSafeQueueEngine
        val queueEngine = CarrierSafeQueueEngine.getInstance(context)
        queueEngine.submitOutboundFrame(outFrame)

        // If loopback simulation is enabled (for testing/development on devices without active SMS)
        if (WidgetPreferences.isLoopbackSimulationEnabled(context)) {
            handleSimulatedPallyResponse(context, widgetType, currentHash)
        }
    }

    private suspend fun handleSimulatedPallyResponse(context: Context, widgetType: String, currentHash: String) {
        kotlinx.coroutines.delay(1200) // Realistic SMS radio round-trip delay
        val schemaTarget = if (widgetType == "news") "news_digest" else "weather"
        val pallyNumber = WidgetPreferences.getPallyPhoneNumber(context)

        val response = if (widgetType == "news") {
            val sampleNews = WidgetData.NewsDigest(
                id = "N" + (System.currentTimeMillis() % 1000),
                headline = "Offline Cellular RPC 2026 Engine Synced via Pally SMS",
                summary = "Zero-data operating layer delivers live home-screen widget updates in dead zones.",
                source = "PALLY NET"
            )
            val newHash = sampleNews.computeContentHash()
            if (newHash == currentHash) {
                com.cellular.rpc.domain.payload.CellularResponse.notModified(
                    reqId = null,
                    schemaId = schemaTarget,
                    etag = currentHash
                )
            } else {
                com.cellular.rpc.domain.payload.CellularResponse.success(
                    reqId = null,
                    schemaId = schemaTarget,
                    data = sampleNews,
                    json = sampleNews.toJson(),
                    etag = newHash
                )
            }
        } else {
            val tempVariation = (70 + (System.currentTimeMillis() % 6)).toInt()
            val sampleWeather = WidgetData.Weather(
                temp = tempVariation,
                city = "San Francisco",
                cond = "Sunny",
                high = 76,
                low = 58
            )
            val newHash = sampleWeather.computeContentHash()
            if (newHash == currentHash) {
                com.cellular.rpc.domain.payload.CellularResponse.notModified(
                    reqId = null,
                    schemaId = schemaTarget,
                    etag = currentHash
                )
            } else {
                com.cellular.rpc.domain.payload.CellularResponse.success(
                    reqId = null,
                    schemaId = schemaTarget,
                    data = sampleWeather,
                    json = sampleWeather.toJson(),
                    etag = newHash
                )
            }
        }

        val wirePayload = response.toCompactWire()
        val inbound = com.cellular.rpc.transport.handler.InboundCellularMessage(
            transportType = com.cellular.rpc.transport.handler.CellularTransportType.LOOPBACK_SIMULATION,
            senderAddress = pallyNumber,
            rawText = wirePayload,
            timestampMs = System.currentTimeMillis()
        )
        com.cellular.rpc.transport.handler.CellularMessageDispatcher.dispatchInbound(context, inbound)
    }
}
