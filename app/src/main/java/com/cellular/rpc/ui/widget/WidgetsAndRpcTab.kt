package com.cellular.rpc.ui.widget

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.data.local.PacketLogEntity
import com.cellular.rpc.data.local.WidgetCacheEntity
import com.cellular.rpc.domain.service.CellularServiceProfile
import com.cellular.rpc.domain.service.ServiceProtocolMode
import com.cellular.rpc.engine.*
import com.cellular.rpc.ui.handshake.AwaitingTransactionsList
import com.cellular.rpc.ui.handshake.HandshakeSessionCard
import com.cellular.rpc.ui.handshake.UserInterventionDialog
import com.cellular.rpc.update.AppUpdateManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*




@Composable
fun WidgetsAndRpcTab(
    widgetCache: List<WidgetCacheEntity>,
    onQueryWidget: (String) -> Unit,
    onMutateServer: (String) -> Unit,
    onSendBinary: () -> Unit,
    lastBinaryPayload: String?
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Offline-First Dynamic Widgets",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text("Content-Hash ETag", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Queries send cached hash over cellular SMS. If unmutated, gateway returns 304 Not Modified (~0 data bytes).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Weather Widget
        item {
            val weatherCache = widgetCache.find { it.widgetType == "weather" }
            val parsedWeather = weatherCache?.let { WidgetData.parse(it.jsonPayload) as? WidgetData.Weather }
            WidgetCard(
                title = "Weather Widget",
                icon = Icons.Default.WbSunny,
                iconColor = SignalAmber,
                status = weatherCache?.lastStatus ?: "NO CACHE",
                hash = weatherCache?.contentHash ?: "NONE",
                content = {
                    if (parsedWeather != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = parsedWeather.city,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = parsedWeather.cond,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${parsedWeather.temp}°F",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyanPrimary
                            )
                        }
                    } else {
                        Text("No cached weather data. Trigger RPC Query below.", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                onQuery = { onQueryWidget("weather") },
                onMutate = { onMutateServer("weather") },
                queryTag = "query_weather_button",
                mutateTag = "mutate_weather_button"
            )
        }

        // News Digest Widget
        item {
            val newsCache = widgetCache.find { it.widgetType == "news_digest" }
            val parsedNews = newsCache?.let { WidgetData.parse(it.jsonPayload) as? WidgetData.NewsDigest }
            WidgetCard(
                title = "News Digest Widget",
                icon = Icons.Default.Newspaper,
                iconColor = CyanPrimary,
                status = newsCache?.lastStatus ?: "NO CACHE",
                hash = newsCache?.contentHash ?: "NONE",
                content = {
                    if (parsedNews != null) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(parsedNews.id, fontSize = 10.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = parsedNews.headline,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = parsedNews.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text("No cached news digest. Trigger RPC Query below.", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                onQuery = { onQueryWidget("news_digest") },
                onMutate = { onMutateServer("news_digest") },
                queryTag = "query_news_button",
                mutateTag = "mutate_news_button"
            )
        }

        // Markets Ticker Widget
        item {
            val marketCache = widgetCache.find { it.widgetType == "market_ticker" }
            val parsedMarket = marketCache?.let { WidgetData.parse(it.jsonPayload) as? WidgetData.MarketTicker }
            WidgetCard(
                title = "Market Ticker Widget",
                icon = Icons.Default.TrendingUp,
                iconColor = SignalGreen,
                status = marketCache?.lastStatus ?: "NO CACHE",
                hash = marketCache?.contentHash ?: "NONE",
                content = {
                    if (parsedMarket != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = parsedMarket.sym,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Cellular Live Ticker",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = parsedMarket.price,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = parsedMarket.chg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (parsedMarket.chg.startsWith("+")) SignalGreen else SignalRed
                                )
                            }
                        }
                    } else {
                        Text("No cached market ticker. Trigger RPC Query below.", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                onQuery = { onQueryWidget("market_ticker") },
                onMutate = { onMutateServer("market_ticker") },
                queryTag = "query_market_button",
                mutateTag = "mutate_market_button"
            )
        }

        // Calendar Event Widget
        item {
            val calCache = widgetCache.find { it.widgetType == "calendar_event" }
            val parsedCal = calCache?.let { WidgetData.parse(it.jsonPayload) as? WidgetData.CalendarEvent }
            WidgetCard(
                title = "Calendar Event Widget",
                icon = Icons.Default.DateRange,
                iconColor = CyanPrimary,
                status = calCache?.lastStatus ?: "NO CACHE",
                hash = calCache?.contentHash ?: "NONE",
                content = {
                    if (parsedCal != null) {
                        Column {
                            Text(
                                text = parsedCal.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🕒 ${parsedCal.time} • 📍 ${parsedCal.location}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${parsedCal.attendees} attendees confirmed via cellular PDU",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanPrimary
                            )
                        }
                    } else {
                        Text("No cached calendar event. Trigger RPC Query below.", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                onQuery = { onQueryWidget("calendar_event") },
                onMutate = { onMutateServer("calendar_event") },
                queryTag = "query_calendar_button",
                mutateTag = "mutate_calendar_button"
            )
        }

        // Task Checklist Widget
        item {
            val taskCache = widgetCache.find { it.widgetType == "task_checklist" }
            val parsedTasks = taskCache?.let { WidgetData.parse(it.jsonPayload) as? WidgetData.TaskChecklist }
            WidgetCard(
                title = "Task Checklist Widget",
                icon = Icons.Default.CheckCircle,
                iconColor = SignalAmber,
                status = taskCache?.lastStatus ?: "NO CACHE",
                hash = taskCache?.contentHash ?: "NONE",
                content = {
                    if (parsedTasks != null) {
                        Column {
                            Text(
                                text = parsedTasks.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            parsedTasks.items.forEachIndexed { idx, itemText ->
                                val isDone = parsedTasks.doneFlags.getOrNull(idx) ?: false
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isDone) SignalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = itemText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        Text("No cached task checklist. Trigger RPC Query below.", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                onQuery = { onQueryWidget("task_checklist") },
                onMutate = { onMutateServer("task_checklist") },
                queryTag = "query_task_button",
                mutateTag = "mutate_task_button"
            )
        }

        // System Telemetry Status Widget
        item {
            val sysCache = widgetCache.find { it.widgetType == "system_status" }
            val parsedSys = sysCache?.let { WidgetData.parse(it.jsonPayload) as? WidgetData.SystemStatus }
            WidgetCard(
                title = "System Telemetry Widget",
                icon = Icons.Default.SignalCellularAlt,
                iconColor = SignalGreen,
                status = sysCache?.lastStatus ?: "NO CACHE",
                hash = sysCache?.contentHash ?: "NONE",
                content = {
                    if (parsedSys != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cellular Link: ${parsedSys.linkQuality}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SignalGreen
                                )
                                Text(
                                    text = "RSSI: ${parsedSys.signalDbm} dBm • Free: ${parsedSys.freeStorageMb} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${parsedSys.batteryPct}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanPrimary
                                )
                                Text(
                                    text = "Battery",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text("No cached telemetry status. Trigger RPC Query below.", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                onQuery = { onQueryWidget("system_status") },
                onMutate = { onMutateServer("system_status") },
                queryTag = "query_system_button",
                mutateTag = "mutate_system_button"
            )
        }

        // Multi-Segment Binary Demo
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Multi-Segment Binary Chunking",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Transfers large payloads (>122B) by partitioning into 120-byte BIN_DAT (05) chunks with sequential SEQ numbers, followed by a terminal BIN_FIN (06) frame with SHA-256 integrity hash.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (lastBinaryPayload != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = lastBinaryPayload,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = SignalGreen
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Button(
                        onClick = onSendBinary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_binary_demo_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Transmit 260B Binary Chunk (Multi-PDU Demo)", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    status: String,
    hash: String,
    content: @Composable () -> Unit,
    onQuery: () -> Unit,
    onMutate: () -> Unit,
    queryTag: String,
    mutateTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                val is304 = status.contains("304")
                Surface(
                    color = if (is304) SignalGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (is304) "304 NOT MODIFIED" else status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (is304) SignalGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            content()

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ETag Hash: $hash",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )

                Row {
                    OutlinedButton(
                        onClick = onMutate,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag(mutateTag)
                    ) {
                        Text("Mutate", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onQuery,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag(queryTag)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RPC Query", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// PACKET INSPECTOR TAB
// -----------------------------------------------------------------------------

