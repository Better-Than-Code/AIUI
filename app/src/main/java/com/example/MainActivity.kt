package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cellular.rpc.data.local.PacketLogEntity
import com.cellular.rpc.data.local.WidgetCacheEntity
import com.cellular.rpc.engine.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: CellularRpcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CellularRpcScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellularRpcScreen(viewModel: CellularRpcViewModel) {
    // 0 = Chat (Initial Default Tab), 1 = Widgets, 2 = Inspector, 3 = E2E Tests, 4 = Outbox
    var selectedTab by remember { mutableIntStateOf(0) }
    var isDiagnosticsEnabled by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val widgetCache by viewModel.widgetCache.collectAsStateWithLifecycle()
    val packetLogs by viewModel.packetLogs.collectAsStateWithLifecycle()
    val outboxItems by viewModel.outboxItems.collectAsStateWithLifecycle()
    val isServiceActive by viewModel.isServiceActive.collectAsStateWithLifecycle()
    val isEngineRunning by viewModel.isEngineRunning.collectAsStateWithLifecycle()
    val inFlightCount by viewModel.inFlightCount.collectAsStateWithLifecycle()
    val txCount by viewModel.txCount.collectAsStateWithLifecycle()
    val rxCount by viewModel.rxCount.collectAsStateWithLifecycle()
    val bytesSaved by viewModel.bytesSaved304.collectAsStateWithLifecycle()
    val nextTxMs by viewModel.nextAllowedTxMs.collectAsStateWithLifecycle()
    val lastBinaryPayload by viewModel.lastReassembledPayload.collectAsStateWithLifecycle()
    val testResults by viewModel.testResults.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val pallyPhoneNumber by viewModel.pallyPhoneNumber.collectAsStateWithLifecycle()
    val isLoopbackSimulation by viewModel.isLoopbackSimulation.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cellular_rpc_scaffold"),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = "Pally AI Cellular Gateway",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Pally AI",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.3.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = SignalGreen.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "CELLULAR RPC",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SignalGreen,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "$pallyPhoneNumber • Port 8901",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Action Buttons: Pull Sync, Settings & Service Toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.triggerPullSync() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("top_pull_sync_button")
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = "Pull Sync via SMS",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("top_settings_button")
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Settings & Widgets",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Foreground Service Toggle Pill
                            ElevatedFilterChip(
                                selected = isServiceActive,
                                onClick = { viewModel.toggleForegroundService() },
                                label = {
                                    Text(
                                        if (isServiceActive) "ON" else "OFF",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isServiceActive) SignalGreen else SignalAmber)
                                    )
                                },
                                modifier = Modifier.testTag("service_toggle_button")
                            )
                        }
                    }

                    // Sliding Window & Telemetry Strip (Shown if Diagnostics is toggled)
                    if (isDiagnosticsEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TelemetryStrip(
                            inFlightCount = inFlightCount,
                            windowSize = 4,
                            txCount = txCount,
                            rxCount = rxCount,
                            bytesSaved = bytesSaved,
                            nextTxMs = nextTxMs
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (isDiagnosticsEnabled) {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("main_navigation_bar"),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Chat Window") },
                        label = { Text("Chat") },
                        modifier = Modifier.testTag("nav_chat")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Widgets, contentDescription = "Widgets") },
                        label = { Text("Widgets") },
                        modifier = Modifier.testTag("nav_widgets")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Dns, contentDescription = "Inspector") },
                        label = { Text("Inspector") },
                        modifier = Modifier.testTag("nav_inspector")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.FactCheck, contentDescription = "Tests") },
                        label = { Text("E2E Tests") },
                        modifier = Modifier.testTag("nav_tests")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Outbox, contentDescription = "Outbox") },
                        label = { Text("Outbox") },
                        modifier = Modifier.testTag("nav_outbox")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> CellularChatTab(
                    messages = chatMessages,
                    pallyPhone = pallyPhoneNumber,
                    onSendMessage = { viewModel.sendChatMessage(it) },
                    onVote = { pollId, opt -> viewModel.castVote(pollId, opt) },
                    onConfirmTransfer = { viewModel.confirmTransfer(it) },
                    onRefreshWidget = { viewModel.queryWidget(it) }
                )
                1 -> WidgetsAndRpcTab(
                    widgetCache = widgetCache,
                    onQueryWidget = { viewModel.queryWidget(it) },
                    onMutateServer = { viewModel.simulateServerDataChange(it) },
                    onSendBinary = { viewModel.sendBinaryDemo(260) },
                    lastBinaryPayload = lastBinaryPayload
                )
                2 -> PacketInspectorTab(
                    packetLogs = packetLogs,
                    onClearLogs = { viewModel.clearLogs() }
                )
                3 -> E2ETestRunnerTab(
                    testResults = testResults,
                    isTesting = isTesting,
                    onRunTests = { viewModel.runEndToEndTestPlan() }
                )
                4 -> OutboxTab(
                    outboxItems = outboxItems,
                    onClearOutbox = { viewModel.clearOutbox() }
                )
            }
        }
    }

    // Settings BottomSheet Dialog
    if (showSettingsSheet) {
        PallySettingsBottomSheet(
            currentPhoneNumber = pallyPhoneNumber,
            isLoopback = isLoopbackSimulation,
            isDiagnostics = isDiagnosticsEnabled,
            onUpdatePhoneNumber = { viewModel.updatePallyPhoneNumber(it) },
            onToggleLoopback = { viewModel.toggleLoopbackSimulation() },
            onToggleDiagnostics = { isDiagnosticsEnabled = !isDiagnosticsEnabled },
            onOpenWidgetConfig = {
                context.startActivity(Intent(context, com.cellular.rpc.widget.WidgetConfigurationActivity::class.java))
            },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

// -----------------------------------------------------------------------------
// CHAT TAB (THE INITIAL RICH INTERACTIVE UI)
// -----------------------------------------------------------------------------

@Composable
fun CellularChatTab(
    messages: List<ChatMessage>,
    pallyPhone: String,
    onSendMessage: (String) -> Unit,
    onVote: (String, Int) -> Unit,
    onConfirmTransfer: (String) -> Unit,
    onRefreshWidget: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to latest message on receive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val quickPrompts = listOf(
        "🌤️ SF Weather" to "What is the weather in San Francisco?",
        "📈 BTC Ticker" to "Get latest BTC price and market ticker",
        "📰 News Digest" to "Summarize the latest top stories",
        "💸 Send $25" to "Send $25 to Alex Chen for sprint lunch",
        "📊 Team Poll" to "Create a quick poll: Sprint Architecture Review @ 3PM?",
        "🧮 Tip & Split" to "Calculate tip and split on an $85 bill for 2 people",
        "🔄 304 ETag Pull" to "REQ:widget:weather"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Chat Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(
                    message = msg,
                    onVote = onVote,
                    onConfirmTransfer = onConfirmTransfer,
                    onRefreshWidget = onRefreshWidget
                )
            }
        }

        // Quick Suggestion Chips Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { (label, prompt) ->
                SuggestionChip(
                    onClick = {
                        onSendMessage(prompt)
                        coroutineScope.launch {
                            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                        }
                    },
                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = DarkNavyBorder)
                )
            }
        }

        // iMessage-Style Input Bar with Live PDU Counter
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        placeholder = {
                            Text(
                                "Message via Cellular SMS...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(22.dp),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val canSend = inputText.isNotBlank()
                    FilledIconButton(
                        onClick = {
                            if (canSend) {
                                onSendMessage(inputText)
                                inputText = ""
                                coroutineScope.launch {
                                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("chat_send_button"),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (canSend) CyanPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (canSend) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send SMS"
                        )
                    }
                }

                // SMS PDU & Byte Counter
                val byteCount = inputText.toByteArray(Charsets.UTF_8).size
                val pduCount = if (byteCount == 0) 1 else ((byteCount + 139) / 140)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SMS Port: 8901  •  Dest: $pallyPhone",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$byteCount/140B • $pduCount SMS PDU",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (byteCount <= 140) SignalGreen else SignalAmber
                    )
                }
            }
        }
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percent)
                            .matchParentSize()
                            .background(
                                if (isSelected) CyanPrimary.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                    )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PallySettingsBottomSheet(
    currentPhoneNumber: String,
    isLoopback: Boolean,
    isDiagnostics: Boolean,
    onUpdatePhoneNumber: (String) -> Unit,
    onToggleLoopback: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onOpenWidgetConfig: () -> Unit,
    onDismiss: () -> Unit
) {
    var phoneNumberInput by remember { mutableStateOf(currentPhoneNumber) }
    var selectedInterval by remember { mutableStateOf("15 min") }
    val intervals = listOf("Manual Only", "15 min", "30 min", "1 hour", "3 hours")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pally AI & Widgets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close Settings")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Dedicated Pally AI Phone Number
            Text(
                text = "Dedicated Carrier Phone Number",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CyanPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "All cellular RPC packets and background pull requests are routed to this number.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = phoneNumberInput,
                    onValueChange = { phoneNumberInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("+18005550199") },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onUpdatePhoneNumber(phoneNumberInput.trim())
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
                ) {
                    Text("Save")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Pull Sync Interval
            Text(
                text = "Cellular Pull Interval (WorkManager / Alarms)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CyanPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Controls how often the app queries Pally via SMS for new weather and news updates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(intervals) { interval ->
                    FilterChip(
                        selected = selectedInterval == interval,
                        onClick = { selectedInterval = interval },
                        label = { Text(interval, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Android Home Screen Widgets
            Text(
                text = "Home Screen Widgets",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CyanPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pally Weather & News Widgets",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "To place on your home screen: exit to the Android launcher, long-press empty space, select Widgets, and choose Pally Weather or Pally News.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenWidgetConfig,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Configure Widget Opacity & Topics")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: Loopback & Protocol Diagnostics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Loopback Simulation",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Emulates remote SMS gateway responses locally for zero-carrier testing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isLoopback,
                    onCheckedChange = { onToggleLoopback() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Protocol Diagnostics",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Reveals telemetry bar, Outbox queue, Packet inspector & E2E tests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isDiagnostics,
                    onCheckedChange = { onToggleDiagnostics() }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// TELEMETRY STRIP
// -----------------------------------------------------------------------------

@Composable
fun TelemetryStrip(
    inFlightCount: Int,
    windowSize: Int,
    txCount: Int,
    rxCount: Int,
    bytesSaved: Int,
    nextTxMs: Long
) {
    val now = System.currentTimeMillis()
    val isThrottled = nextTxMs > now
    val remainingThrottleMs = if (isThrottled) nextTxMs - now else 0L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sliding window in-flight slots indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "WINDOW:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    for (i in 0 until windowSize) {
                        val isSlotOccupied = i < inFlightCount
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSlotOccupied) SignalAmber else DarkNavyBorder)
                                .border(1.dp, if (isSlotOccupied) SignalAmber else Color.Transparent, RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$inFlightCount/$windowSize IN-FLIGHT",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (inFlightCount >= windowSize) SignalAmber else MaterialTheme.colorScheme.primary
                    )
                }

                // Throttle status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isThrottled) "THROTTLED (~${remainingThrottleMs / 1000}s)" else "READY (2.2s+Jitter)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isThrottled) SignalAmber else SignalGreen,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TX: $txCount pkts  |  RX: $rxCount pkts",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "SAVED: ${bytesSaved}B (304)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SignalGreen,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// WIDGETS TAB (EXPANDED DASHBOARD VIEW)
// -----------------------------------------------------------------------------

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

@Composable
fun PacketInspectorTab(
    packetLogs: List<PacketLogEntity>,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Live Cellular Protocol Inspector",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Exact wire format: ~<SES>:<TYPE>:<SEQ>:<ACK>:<PAYLOAD>:<CRC>#",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onClearLogs,
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (packetLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Podcasts, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No cellular packets captured yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Dispatch an RPC query to observe wire framing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(packetLogs, key = { it.id }) { log ->
                    PacketLogCard(log)
                }
            }
        }
    }
}

@Composable
fun PacketLogCard(log: PacketLogEntity) {
    val isTx = log.direction == "TX"
    val badgeColor = if (isTx) CyanPrimary else SignalGreen

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = log.direction,
                            color = badgeColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.pktTypeName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SEQ: ${log.seqNo}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${log.binaryByteCount}B",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (log.binaryByteCount <= 133) SignalGreen else SignalRed
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = SignalGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "CRC ${log.crc16Hex}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SignalGreen,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = log.wireFormat,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(6.dp)
                )
            }

            if (log.payloadString.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Payload: ${log.payloadString}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// E2E TESTS TAB
// -----------------------------------------------------------------------------

@Composable
fun E2ETestRunnerTab(
    testResults: List<TestResult>,
    isTesting: Boolean,
    onRunTests: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Section 5: End-to-End Test Plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Executes the 3 blueprint test scenarios:\n1. Cellular Boundary Test (122B in 133B safe MTU)\n2. Out-of-Order Packet Injection & Selective Repeat Bitmask\n3. Spam & Velocity Mitigation (2200ms + Random gap)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onRunTests,
                    enabled = !isTesting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_tests_button")
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying Wire Protocols...")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run E2E Test Scenarios")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (testResults.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Press 'Run E2E Test Scenarios' to execute tests.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(testResults) { test ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (test.passed) SignalGreen.copy(alpha = 0.08f) else SignalRed.copy(alpha = 0.08f)
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = test.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (test.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (test.passed) SignalGreen else SignalRed
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = test.message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (test.passed) SignalGreen else SignalRed
                            )
                            if (test.details.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = test.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// OUTBOX PERSISTENCE TAB
// -----------------------------------------------------------------------------

@Composable
fun OutboxTab(
    outboxItems: List<com.cellular.rpc.data.local.OutboxEntity>,
    onClearOutbox: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Outbox Persistence Queue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Room-backed persistent queue for offline SMS queuing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onClearOutbox,
                modifier = Modifier.testTag("clear_outbox_button")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Outbox")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (outboxItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Outbox is empty. All frames dispatched.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(outboxItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ID: ${item.id} • ${com.cellular.rpc.domain.protocol.Frame.typeName(item.pktType)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                val statusColor = when (item.status) {
                                    "ACKNOWLEDGED" -> SignalGreen
                                    "IN_FLIGHT" -> SignalAmber
                                    else -> CyanPrimary
                                }
                                Surface(
                                    color = statusColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = item.status,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Base85: ${item.payloadBase85.take(40)}...",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Cellular RPC: $name", modifier = modifier)
}
