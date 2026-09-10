package com.cellular.rpc.ui.chat

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
fun CellularChatTab(
    messages: List<ChatMessage>,
    openTabIds: List<String> = listOf("th_main"),
    allThreads: List<com.cellular.rpc.data.local.ConversationThreadEntity> = emptyList(),
    activeThreadId: String = "th_main",
    onSelectTab: (String) -> Unit = {},
    onCloseTab: (String) -> Unit = {},
    onNewChatClick: () -> Unit = {},
    onOpenHistoryClick: () -> Unit = {},
    customActions: List<com.cellular.rpc.data.local.CustomActionEntity> = emptyList(),
    onCreateCustomAction: () -> Unit = {},
    onDeleteCustomAction: ((String) -> Unit)? = null,
    pallyPhone: String,
    assistantName: String = "AI Assistant",
    isLoopbackSimulation: Boolean = false,
    hasSmsPermissions: Boolean = true,
    onRequestPermissions: () -> Unit = {},
    onToggleLoopback: () -> Unit = {},
    onSendMessage: (String, List<com.cellular.rpc.engine.MessageAttachment>) -> Unit,
    onSelectTemplate: (com.cellular.rpc.orchestrator.AiTemplate) -> Unit = {},
    onVote: (String, Int) -> Unit,
    onConfirmTransfer: (String) -> Unit,
    onRefreshWidget: (String) -> Unit,
    onDeleteMessage: (String) -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val pendingAttachments = remember { mutableStateListOf<com.cellular.rpc.engine.MessageAttachment>() }
    val audioPlayer = remember { com.cellular.rpc.engine.AudioPlayerManager(context) }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.release()
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to latest message on receive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tabbed Chat Bar (Active open tabs, New chat '+', History drawer)
        com.cellular.rpc.ui.chat.ChatTabBar(
            openTabIds = openTabIds,
            allThreads = allThreads,
            activeThreadId = activeThreadId,
            onSelectTab = onSelectTab,
            onCloseTab = onCloseTab,
            onNewChatClick = onNewChatClick,
            onOpenHistoryClick = onOpenHistoryClick
        )

        // Chat Stream (iMessage/RCS Asymmetric bubbles with Markdown, Attachments & Widget Cards)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                com.cellular.rpc.ui.chat.NextGenChatMessageItem(
                    message = msg,
                    assistantName = assistantName,
                    onVote = onVote,
                    onConfirmTransfer = onConfirmTransfer,
                    onRefreshWidget = onRefreshWidget,
                    audioPlayerManager = audioPlayer,
                    onReply = {
                        replyingToMessage = it
                    },
                    onResend = {
                        onSendMessage(it.widgetData?.toJson() ?: it.text, emptyList())
                    },
                    onDelete = {
                        onDeleteMessage(it.id)
                    }
                )
            }
        }

        // Full Native Compose Bar with Photo Picker, Documents, Voice Note Recorder & Tools
        com.cellular.rpc.ui.chat.FullNativeChatInputBar(
            inputText = inputText,
            onInputTextChange = { inputText = it },
            pendingAttachments = pendingAttachments.toList(),
            onAddAttachment = { att -> pendingAttachments.add(att) },
            onRemoveAttachment = { id -> pendingAttachments.removeAll { it.id == id } },
            replyingToText = replyingToMessage?.let { "Replying: \"${it.text.take(45)}\"" },
            onCancelReply = { replyingToMessage = null },
            onSendMessage = { text, atts ->
                val fullMessage = if (replyingToMessage != null) {
                    val quote = "> ${replyingToMessage?.text?.take(40)}...\n$text"
                    replyingToMessage = null
                    quote
                } else {
                    text
                }
                onSendMessage(fullMessage, atts)
                inputText = ""
                pendingAttachments.clear()
                coroutineScope.launch {
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                }
            },
            onSelectQuickPrompt = { prompt, type ->
                onSendMessage(prompt, emptyList())
                onRefreshWidget(type)
                coroutineScope.launch {
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                }
            },
            customActions = customActions,
            onCreateCustomAction = onCreateCustomAction,
            onDeleteCustomAction = onDeleteCustomAction,
            onSelectTemplate = onSelectTemplate,
            destinationPhone = pallyPhone,
            isLoopback = isLoopbackSimulation,
            hasSmsPermissions = hasSmsPermissions,
            onRequestPermissions = onRequestPermissions
        )
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onVote: (String, Int) -> Unit,
    onConfirmTransfer: (String) -> Unit,
    onRefreshWidget: (String) -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    var showWireInspector by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User Sent Bubble
            Surface(
                color = CyanPrimaryDark,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${message.byteSize}B • ${message.pduCount} SMS PDU",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Delivered",
                    tint = SignalGreen,
                    modifier = Modifier.size(12.dp)
                )
            }
        } else {
            // AI Gateway Response: Rich Rendered Feature Card (No code/JSON shown!)
            Column(modifier = Modifier.widthIn(max = 330.dp)) {
                if (message.widgetData != null) {
                    when (val widget = message.widgetData) {
                        is WidgetData.Weather -> WeatherChatCard(
                            weather = widget,
                            is304 = message.is304NotModified,
                            onRefresh = { onRefreshWidget("weather") }
                        )
                        is WidgetData.NewsDigest -> NewsChatCard(news = widget)
                        is WidgetData.MarketTicker -> MarketChatCard(ticker = widget)
                        is WidgetData.CellularTransfer -> TransferChatCard(
                            transfer = widget,
                            onConfirm = { onConfirmTransfer(widget.id) }
                        )
                        is WidgetData.CellularPoll -> PollChatCard(
                            poll = widget,
                            onVote = { onVote(widget.id, it) }
                        )
                        is WidgetData.CellularTool -> ToolChatCard(tool = widget)
                        is WidgetData.CalendarEvent -> CalendarChatCard(event = widget)
                        is WidgetData.TaskChecklist -> TaskChecklistChatCard(checklist = widget)
                        is WidgetData.SystemStatus -> SystemStatusChatCard(status = widget)
                        is WidgetData.DynamicBlueprint -> DynamicBlueprintChatCard(blueprint = widget)
                        is WidgetData.MiniAppPreview -> {
                            val blueprint = com.cellular.rpc.domain.miniapp.MiniAppBlueprint.fromJson(widget.rawBlueprintJson)
                            if (blueprint != null) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
                                com.cellular.rpc.ui.miniapp.DynamicAppHost(
                                    blueprint = blueprint,
                                    isPreviewMode = true,
                                    onInstallToDeck = { bp, st ->
                                        coroutineScope.launch {
                                            com.cellular.rpc.domain.miniapp.MiniAppDeckManager.installApp(context, bp, st)
                                        }
                                    }
                                )
                            } else {
                                PlainChatBubble(text = widget.rawBlueprintJson)
                            }
                        }
                        is WidgetData.ChatText -> PlainChatBubble(text = widget.text)
                        else -> PlainChatBubble(text = widget.toJson())
                    }
                } else if (message.text.isNotEmpty()) {
                    PlainChatBubble(text = message.text)
                }

                // Discreet Cellular Wire Inspector Toggle
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clickable { showWireInspector = !showWireInspector }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showWireInspector) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Inspect Wire",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (message.is304NotModified) "304 ETag Cache (3B) • Tap to Inspect" else "${message.byteSize}B SMS Frame • CRC16 Valid",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (message.is304NotModified) SignalGreen else MaterialTheme.colorScheme.primary
                    )
                }

                // Collapsible Wire Packet Drawer
                AnimatedVisibility(visible = showWireInspector) {
                    Surface(
                        color = DarkNavySurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "RAW CELLULAR WIRE (ASCII Fallback):",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = message.wirePacket ?: "~1A2F:04:0001:00000000:PAYLOAD:CRC#",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyanPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlainChatBubble(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
        border = BorderStroke(1.dp, DarkNavyBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// -----------------------------------------------------------------------------
// RICH INTERACTIVE CHAT CARDS (LIKE iMESSAGE / RCS APPS)
// -----------------------------------------------------------------------------

/**
 * Weather Interactive Card:
 * Renders atmospheric gradient, high/low, temperature, and 304 revalidation action.
 */
@Composable
fun WeatherChatCard(
    weather: WidgetData.Weather,
    is304: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, if (is304) SignalGreen.copy(alpha = 0.5f) else DarkNavyBorder)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F3057), Color(0xFF00587A))
                    )
                )
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Weather",
                            tint = SignalAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = weather.city,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (is304) {
                        Surface(
                            color = SignalGreen.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "304 UNCHANGED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = SignalGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "${weather.temp}°F",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = weather.cond,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "H: ${weather.high}°  L: ${weather.low}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FilledTonalButton(
                            onClick = onRefresh,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Recheck SMS", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * News Digest Interactive Card:
 * Renders verified news brief with publication badge and read actions.
 */
@Composable
fun NewsChatCard(news: WidgetData.NewsDigest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DarkNavyBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = news.source.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "ID: ${news.id}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = news.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = news.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { /* Bookmark action */ },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Offline", fontSize = 10.sp)
                }
            }
        }
    }
}

/**
 * Market Ticker Interactive Card:
 * Renders cryptocurrency/stock price, delta chip, and a custom native Canvas sparkline!
 */
@Composable
fun MarketChatCard(ticker: WidgetData.MarketTicker) {
    val isPositive = ticker.chg.startsWith("+")
    val trendColor = if (isPositive) SignalGreen else SignalRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DarkNavyBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("₿", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CyanPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ticker.sym,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = trendColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = ticker.chg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = trendColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = ticker.price,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Native Canvas Sparkline
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val points = ticker.sparkline
                if (points.size >= 2) {
                    val min = points.minOrNull() ?: 0f
                    val max = points.maxOrNull() ?: 1f
                    val range = if (max - min == 0f) 1f else max - min

                    val path = Path()
                    points.forEachIndexed { index, value ->
                        val x = (index.toFloat() / (points.size - 1)) * size.width
                        val y = size.height - ((value - min) / range) * size.height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = trendColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

/**
 * Cellular Cash Transfer Card (like Apple Cash in iMessage):
 * Renders recipient, bold dollar amount, memo, and one-tap SMS authorization button!
 */
@Composable
fun TransferChatCard(
    transfer: WidgetData.CellularTransfer,
    onConfirm: () -> Unit
) {
    val isConfirmed = transfer.status.contains("AUTH") || transfer.status == "CONFIRMED"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF003820), Color(0xFF001F12))
                    )
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(SignalGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachMoney,
                                contentDescription = null,
                                tint = SignalGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cellular Cash",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SignalGreen
                        )
                    }

                    Surface(
                        color = SignalGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isConfirmed) "COMPLETED" else "READY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignalGreen,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = transfer.amount,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )

                Text(
                    text = "To: ${transfer.to} • \"${transfer.memo}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onConfirm,
                    enabled = !isConfirmed,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (isConfirmed) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConfirmed) "Authorized via Cellular SMS" else "Authorize via Cellular SMS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Cellular Poll Card (like iMessage Interactive Poll extension):
 * Tappable voting choices with live percentage fill bars and checkmarks!
 */
@Composable
fun PollChatCard(
    poll: WidgetData.CellularPoll,
    onVote: (Int) -> Unit
) {
    val totalVotes = poll.votes.sum().coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, DarkNavyBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Poll, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cellular Poll",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                }
                Text(
                    text = "${poll.votes.sum()} votes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = poll.question,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            poll.options.forEachIndexed { index, option ->
                val voteCount = poll.votes.getOrElse(index) { 0 }
                val percent = (voteCount.toFloat() / totalVotes.toFloat())
                val isSelected = poll.userVoteIndex == index

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onVote(index) }
                ) {
                    // Percentage Fill Bar
                    if (percent > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percent.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(
                                    if (isSelected) CyanPrimary.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "${(percent * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolChatCard(tool: WidgetData.CellularTool) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Calculate,
                            contentDescription = "Tool",
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = SignalGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "CELLULAR TOOL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = tool.valuePrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = CyanPrimary
            )
            if (tool.valueSecondary.isNotEmpty()) {
                Text(
                    text = tool.valueSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CalendarChatCard(event: WidgetData.CalendarEvent) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Calendar Event",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                }
                Surface(
                    color = CyanPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${event.attendees} Attendees",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "🕒 ${event.time}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "📍 ${event.location}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TaskChecklistChatCard(checklist: WidgetData.TaskChecklist) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, SignalAmber.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Task Checklist",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SignalAmber
                    )
                }
                Surface(
                    color = SignalAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    val doneCount = checklist.doneFlags.count { it }
                    Text(
                        text = "$doneCount/${checklist.items.size} DONE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalAmber,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = checklist.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            checklist.items.forEachIndexed { idx, itemText ->
                val isDone = checklist.doneFlags.getOrNull(idx) ?: false
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isDone) SignalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
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
    }
}

@Composable
fun SystemStatusChatCard(status: WidgetData.SystemStatus) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = SignalGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "System Telemetry",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SignalGreen
                    )
                }
                Surface(
                    color = SignalGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = status.linkQuality,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Signal: ${status.signalDbm} dBm",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Storage: ${status.freeStorageMb} MB Free",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${status.batteryPct}%",
                        style = MaterialTheme.typography.titleLarge,
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
        }
    }
}

@Composable
fun DynamicBlueprintChatCard(blueprint: WidgetData.DynamicBlueprint) {
    val themeColor = try {
        Color(android.graphics.Color.parseColor(blueprint.themeColorHex))
    } catch (e: Exception) {
        CyanPrimary
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, themeColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = themeColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (blueprint.icon.lowercase()) {
                                    "memory", "cpu", "chip" -> Icons.Default.Memory
                                    "bolt", "flash", "power" -> Icons.Default.FlashOn
                                    "analytics", "chart" -> Icons.Default.Analytics
                                    "settings", "gear" -> Icons.Default.Settings
                                    "cloud" -> Icons.Default.Cloud
                                    else -> Icons.Default.Widgets
                                },
                                contentDescription = null,
                                tint = themeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = blueprint.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (blueprint.subtitle.isNotBlank()) {
                            Text(
                                text = blueprint.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Surface(
                    color = themeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "SDUI",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Generic Dynamic SDUI Tree Walker
            DynamicSduiNodeView(node = blueprint.rootNode, themeColor = themeColor)
        }
    }
}

@Composable
fun DynamicSduiNodeView(
    node: WidgetData.DynamicSduiNode,
    themeColor: Color = CyanPrimary,
    modifier: Modifier = Modifier
) {
    val nodeColor = node.colorHex?.let {
        try { Color(android.graphics.Color.parseColor(it)) } catch (e: Exception) { null }
    } ?: themeColor

    when (node.type.lowercase()) {
        "row" -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(node.padding.dp),
                horizontalArrangement = Arrangement.spacedBy(node.spacing.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                node.children.forEach { child ->
                    DynamicSduiNodeView(
                        node = child,
                        themeColor = themeColor,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
        "card", "box" -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(node.padding.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(node.spacing.dp)
                ) {
                    if (node.title.isNotBlank()) {
                        Text(
                            text = node.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = nodeColor
                        )
                    }
                    node.children.forEach { child ->
                        DynamicSduiNodeView(node = child, themeColor = themeColor)
                    }
                }
            }
        }
        "metric" -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = node.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (node.subtitle.isNotBlank()) {
                        Text(
                            text = node.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = node.value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = nodeColor
                    )
                    if (node.secondaryValue.isNotBlank()) {
                        Text(
                            text = node.secondaryValue,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        "progress" -> {
            Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = node.title.ifBlank { "Progress" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(node.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = nodeColor
                    )
                }
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { node.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = nodeColor,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }
        "key_value" -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.title.ifBlank { node.text },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = node.value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        "badge" -> {
            Surface(
                color = nodeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                modifier = modifier
            ) {
                Text(
                    text = node.text,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = nodeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        "chip" -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = modifier
            ) {
                Text(
                    text = node.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        "button" -> {
            Button(
                onClick = { /* Action triggered via SDUI */ },
                colors = ButtonDefaults.buttonColors(containerColor = nodeColor),
                shape = RoundedCornerShape(8.dp),
                modifier = modifier.fillMaxWidth()
            ) {
                Text(
                    text = node.text.ifBlank { "Action" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        "divider" -> {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = modifier.padding(vertical = 4.dp)
            )
        }
        "canvas", "canvas_view", "draw_canvas", "sketch", "paint" -> {
            com.cellular.rpc.ui.miniapp.CanvasLeafView(
                modifier = modifier,
                canvasHeightDp = 220,
                initialColorHex = node.colorHex
            )
        }
        "markdown", "reader", "article" -> {
            RichMarkdownText(
                text = node.text,
                textColor = MaterialTheme.colorScheme.onSurface,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(node.padding.dp)
            )
        }
        else -> {
            // Default "column" or "text" container
            if (node.children.isNotEmpty()) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(node.padding.dp),
                    verticalArrangement = Arrangement.spacedBy(node.spacing.dp)
                ) {
                    if (node.title.isNotBlank()) {
                        Text(
                            text = node.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    node.children.forEach { child ->
                        DynamicSduiNodeView(node = child, themeColor = themeColor)
                    }
                }
            } else {
                val textStyle = when (node.style) {
                    "headline" -> MaterialTheme.typography.headlineSmall
                    "title" -> MaterialTheme.typography.titleMedium
                    "label" -> MaterialTheme.typography.labelMedium
                    "caption" -> MaterialTheme.typography.bodySmall
                    else -> MaterialTheme.typography.bodyMedium
                }
                Text(
                    text = node.text,
                    style = textStyle,
                    fontWeight = if (node.isBold) FontWeight.Bold else FontWeight.Normal,
                    color = if (node.colorHex != null) nodeColor else MaterialTheme.colorScheme.onSurface,
                    modifier = modifier
                )
            }
        }
    }
}
