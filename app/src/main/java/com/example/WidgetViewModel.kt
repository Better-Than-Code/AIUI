package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.WidgetCacheEntity
import com.cellular.rpc.engine.WidgetData
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import com.cellular.rpc.transport.service.CellularRpcForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

class WidgetViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val widgetCacheDao = db.widgetCacheDao()

    private val queueEngine: CarrierSafeQueueEngine
        get() = CellularRpcForegroundService.activeEngine ?: CellularRpcApp.instance.queueEngine

    val widgetCache: StateFlow<List<WidgetCacheEntity>> = widgetCacheDao.getAllWidgetsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Preload default widgets in cache if empty
        viewModelScope.launch(Dispatchers.IO) {
            val existing = widgetCacheDao.getWidgetByType("weather")
            if (existing == null) {
                val defaultWeather = WidgetData.Weather(72, "San Francisco", "Sunny")
                widgetCacheDao.insertOrUpdate(
                    WidgetCacheEntity(
                        widgetType = "weather",
                        contentHash = defaultWeather.computeContentHash(),
                        jsonPayload = defaultWeather.toJson(),
                        lastStatus = "CACHED_LOCAL",
                        byteSize = defaultWeather.toJson().length,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )

                val defaultNews = WidgetData.NewsDigest(
                    id = "N101",
                    headline = "Cellular RPC Deployed",
                    summary = "Low-bandwidth SMS protocol maintains connectivity without IP data."
                )
                widgetCacheDao.insertOrUpdate(
                    WidgetCacheEntity(
                        widgetType = "news_digest",
                        contentHash = defaultNews.computeContentHash(),
                        jsonPayload = defaultNews.toJson(),
                        lastStatus = "CACHED_LOCAL",
                        byteSize = defaultNews.toJson().length,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )

                val defaultMarkets = WidgetData.MarketTicker("BTC/USD", "$91,420", "+3.4%")
                widgetCacheDao.insertOrUpdate(
                    WidgetCacheEntity(
                        widgetType = "market_ticker",
                        contentHash = defaultMarkets.computeContentHash(),
                        jsonPayload = defaultMarkets.toJson(),
                        lastStatus = "CACHED_LOCAL",
                        byteSize = defaultMarkets.toJson().length,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )

                val defaultCalendar = WidgetData.CalendarEvent(
                    id = "evt_01",
                    title = "Sprint Architecture Review",
                    time = "3:00 PM - 3:45 PM",
                    location = "Room 402 / Cellular Link",
                    attendees = 4
                )
                widgetCacheDao.insertOrUpdate(
                    WidgetCacheEntity(
                        widgetType = "calendar_event",
                        contentHash = defaultCalendar.computeContentHash(),
                        jsonPayload = defaultCalendar.toJson(),
                        lastStatus = "CACHED_LOCAL",
                        byteSize = defaultCalendar.toJson().length,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )

                val defaultTasks = WidgetData.TaskChecklist(
                    id = "task_01",
                    title = "Sprint Priorities",
                    items = listOf("Standardize Schemas", "Verify SMS/MMS Replies", "Run Robolectric Suite"),
                    doneFlags = listOf(true, true, false)
                )
                widgetCacheDao.insertOrUpdate(
                    WidgetCacheEntity(
                        widgetType = "task_checklist",
                        contentHash = defaultTasks.computeContentHash(),
                        jsonPayload = defaultTasks.toJson(),
                        lastStatus = "CACHED_LOCAL",
                        byteSize = defaultTasks.toJson().length,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )

                val defaultStatus = WidgetData.SystemStatus(
                    batteryPct = 89,
                    signalDbm = -68,
                    freeStorageMb = 4280L,
                    queuedPackets = 0,
                    linkQuality = "EXCELLENT"
                )
                widgetCacheDao.insertOrUpdate(
                    WidgetCacheEntity(
                        widgetType = "system_status",
                        contentHash = defaultStatus.computeContentHash(),
                        jsonPayload = defaultStatus.toJson(),
                        lastStatus = "CACHED_LOCAL",
                        byteSize = defaultStatus.toJson().length,
                        lastUpdatedMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * Mutates the simulated backend data to test cache invalidation.
     */
    fun simulateServerDataChange(type: String) {
        when (type) {
            "weather" -> {
                val temps = listOf(68, 75, 82, 59, 71)
                val conds = listOf("Partly Cloudy", "Thunderstorms", "Clear", "Breezy", "Sunny")
                val newWeather = WidgetData.Weather(
                    temp = temps.random(),
                    city = "San Francisco",
                    cond = conds.random()
                )
                queueEngine.updateServerWidgetData("weather", newWeather)
            }
            "news_digest" -> {
                val id = "N" + Random.nextInt(102, 999)
                val headlines = listOf(
                    "Lunar Gateway Mesh Online",
                    "Solar Flare Absorbed By Faraday Shield",
                    "Global Cellular SMS RPC Standard Approved",
                    "Sat-Link Bandwidth Compressed by 85%"
                )
                val newNews = WidgetData.NewsDigest(
                    id = id,
                    headline = headlines.random(),
                    summary = "Autonomous micro-satellites report zero packet loss with selective repeat bitmask ACKs."
                )
                queueEngine.updateServerWidgetData("news_digest", newNews)
            }
            "market_ticker" -> {
                val price = "$" + String.format("%,d", Random.nextInt(89000, 96000))
                val chg = (if (Random.nextBoolean()) "+" else "-") + String.format("%.1f%%", Random.nextDouble(0.5, 6.0))
                val newTicker = WidgetData.MarketTicker("BTC/USD", price, chg)
                queueEngine.updateServerWidgetData("market_ticker", newTicker)
            }
            "calendar_event" -> {
                val titles = listOf("Sprint Demo & Architecture", "Cellular Mesh Sync", "Security & Protocol Audit")
                val times = listOf("10:00 AM - 11:00 AM", "2:00 PM - 2:30 PM", "4:15 PM - 5:00 PM")
                val newEvent = WidgetData.CalendarEvent(
                    id = "evt_" + Random.nextInt(10, 99),
                    title = titles.random(),
                    time = times.random(),
                    location = "Cellular Link 8901",
                    attendees = Random.nextInt(3, 8)
                )
                queueEngine.updateServerWidgetData("calendar_event", newEvent)
            }
            "task_checklist" -> {
                val lists = listOf(
                    listOf("Verify SMS PDU Envelope", "Audit Cellular Registry", "Deploy Standalone APK"),
                    listOf("Carrier Throttle Passed", "Sliding Window Verified", "304 ETag Benchmarked")
                )
                val newTasks = WidgetData.TaskChecklist(
                    id = "task_" + Random.nextInt(10, 99),
                    title = "Cellular Tasks",
                    items = lists.random(),
                    doneFlags = listOf(true, true, Random.nextBoolean())
                )
                queueEngine.updateServerWidgetData("task_checklist", newTasks)
            }
            "system_status" -> {
                val newStatus = WidgetData.SystemStatus(
                    batteryPct = Random.nextInt(75, 100),
                    signalDbm = Random.nextInt(-95, -55),
                    freeStorageMb = Random.nextLong(3000L, 8000L),
                    queuedPackets = Random.nextInt(0, 3),
                    linkQuality = listOf("EXCELLENT", "GOOD", "OPTIMAL").random()
                )
                queueEngine.updateServerWidgetData("system_status", newStatus)
            }
        }
    }

    fun sendBinaryDemo(sizeBytes: Int = 260) {
        viewModelScope.launch(Dispatchers.IO) {
            val randomBytes = ByteArray(sizeBytes) { it.toByte() }
            queueEngine.enqueueMultiSegmentBinary(0x1A2F, randomBytes)
        }
    }
}
