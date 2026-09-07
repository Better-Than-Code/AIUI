package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.cellular.rpc.update.AppUpdateManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
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
import com.cellular.rpc.domain.handshake.CellularHandshakeEngine
import com.cellular.rpc.domain.service.CellularServiceProfile
import com.cellular.rpc.domain.service.ServiceProtocolMode
import com.cellular.rpc.engine.*
import com.cellular.rpc.ui.handshake.AwaitingTransactionsList
import com.cellular.rpc.ui.handshake.HandshakeSessionCard
import com.cellular.rpc.ui.handshake.UserInterventionDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: CellularRpcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Register SMS and MMS ContentObservers to monitor incoming messages
        com.cellular.rpc.transport.receiver.PallySmsObserver.register(applicationContext)
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            MyApplicationTheme(darkTheme = isDarkTheme) {
                CellularRpcScreen(viewModel, isDarkTheme = isDarkTheme, onToggleTheme = { isDarkTheme = !isDarkTheme })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellularRpcScreen(
    viewModel: CellularRpcViewModel,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    // 0 = Chat (Initial Default Tab), 1 = Widgets, 2 = Inspector, 3 = E2E Tests, 4 = Outbox
    var selectedTab by remember { mutableIntStateOf(0) }
    var isDiagnosticsEnabled by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showThreadDrawer by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val conversationThreads by viewModel.conversationThreads.collectAsStateWithLifecycle()
    val activeThreadId by viewModel.activeThreadId.collectAsStateWithLifecycle()
    val activeThread by viewModel.activeThread.collectAsStateWithLifecycle()
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
    val activeService by viewModel.activeServiceProfile.collectAsStateWithLifecycle()
    val availableServices by viewModel.availableServices.collectAsStateWithLifecycle()
    val isLoopbackSimulation by viewModel.isLoopbackSimulation.collectAsStateWithLifecycle()
    val isMcpSynced by viewModel.isMcpSynced.collectAsStateWithLifecycle()
    val mcpCatalogHash by viewModel.mcpCatalogHash.collectAsStateWithLifecycle()
    val handshakeStatus by viewModel.handshakeStatus.collectAsStateWithLifecycle()
    val pendingTransactions by viewModel.pendingTransactions.collectAsStateWithLifecycle()
    val activeIntervention by viewModel.activeIntervention.collectAsStateWithLifecycle()
    val dynamicFeatures by viewModel.dynamicFeatures.collectAsStateWithLifecycle()
    val selectedFeature by viewModel.selectedFeature.collectAsStateWithLifecycle()

    val requiredPermissions = remember {
        val list = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_WAP_PUSH
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    var hasSmsPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasSmsPermissions = results[Manifest.permission.SEND_SMS] == true &&
                            results[Manifest.permission.RECEIVE_SMS] == true &&
                            results[Manifest.permission.READ_SMS] == true
        if (hasSmsPermissions) {
            com.cellular.rpc.transport.receiver.PallySmsObserver.checkInboxNow(context)
        }
    }

    LaunchedEffect(hasSmsPermissions) {
        if (!hasSmsPermissions) {
            permissionLauncher.launch(requiredPermissions)
        } else {
            com.cellular.rpc.transport.receiver.PallySmsObserver.checkInboxNow(context)
        }
    }

    LaunchedEffect(selectedTab) {
        if (hasSmsPermissions) {
            com.cellular.rpc.transport.receiver.PallySmsObserver.checkInboxNow(context)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cellular_rpc_scaffold"),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clean Conversational Identity & Drawer Launcher
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showThreadDrawer = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(CyanPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✨",
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = activeThread?.title ?: activeService.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isLoopbackSimulation) SignalAmber else SignalGreen)
                                    )
                                    Text(
                                        text = if (isLoopbackSimulation) "Simulation Mode" else "Cellular Gateway",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Streamlined Action Buttons (Theme Toggle & Settings Sheet)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { showThreadDrawer = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("top_thread_drawer_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (conversationThreads.size > 1) {
                                            Badge(
                                                containerColor = CyanPrimary,
                                                contentColor = Color.Black
                                            ) {
                                                Text(
                                                    text = conversationThreads.size.toString(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Forum,
                                        contentDescription = "Conversation Threads",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onToggleTheme() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Theme",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("top_settings_button")
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Settings & Gateway",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // SMS Permission Warning if in live mode but permissions missing
                    if (!isLoopbackSimulation && !hasSmsPermissions) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SMS Permission Needed",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Grant SMS access to send & receive messages via carrier radio.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                    )
                                }
                                Button(
                                    onClick = { permissionLauncher.launch(requiredPermissions) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Sliding Window & Telemetry Strip (Only shown if Protocol Diagnostics is explicitly enabled in Settings)
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
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5 },
                        icon = { Icon(Icons.Default.Extension, contentDescription = "Extensions") },
                        label = { Text("Apps") },
                        modifier = Modifier.testTag("nav_dynamic_apps")
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
                    assistantName = activeService.name,
                    isLoopbackSimulation = isLoopbackSimulation,
                    hasSmsPermissions = hasSmsPermissions,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions) },
                    onToggleLoopback = { viewModel.toggleLoopbackSimulation() },
                    onSendMessage = { text, atts -> viewModel.sendChatMessage(text, atts) },
                    onSelectTemplate = { template -> viewModel.sendTemplateRequest(template) },
                    onVote = { pollId, opt -> viewModel.castVote(pollId, opt) },
                    onConfirmTransfer = { viewModel.confirmTransfer(it) },
                    onRefreshWidget = { viewModel.queryWidget(it) },
                    onDeleteMessage = { viewModel.deleteChatMessage(it) }
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
                    onClearLogs = { viewModel.clearLogs() },
                    isMcpSynced = isMcpSynced,
                    mcpCatalogHash = mcpCatalogHash,
                    onPushMcpGenesis = { viewModel.pushGenesisMcpManifest() },
                    handshakeStatus = handshakeStatus,
                    pendingTransactions = pendingTransactions,
                    onProbeHandshake = { viewModel.probeHandshake() },
                    onResolveIntervention = { reqId, action -> viewModel.resolveIntervention(reqId, action) }
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
                5 -> com.cellular.rpc.ui.dynamic.DynamicFeaturesTab(
                    dynamicFeatures = dynamicFeatures,
                    selectedFeature = selectedFeature,
                    onSelectFeature = { viewModel.selectDynamicFeature(it) },
                    onDeleteFeature = { viewModel.deleteDynamicFeature(it) },
                    onDeploySample = { viewModel.deploySampleFeature(it) }
                )
            }
        }
    }

    // High-Priority User Intervention Dialog for Awaiting/Stalled Transactions
    activeIntervention?.let { interventionTx ->
        UserInterventionDialog(
            transaction = interventionTx,
            onResolve = { action ->
                viewModel.resolveIntervention(interventionTx.reqId, action)
            }
        )
    }

    // Settings BottomSheet Dialog
    if (showSettingsSheet) {
        PallySettingsBottomSheet(
            context = context,
            currentPhoneNumber = pallyPhoneNumber,
            activeService = activeService,
            availableServices = availableServices,
            isServiceActive = isServiceActive,
            isLoopback = isLoopbackSimulation,
            isDiagnostics = isDiagnosticsEnabled,
            onSelectService = { viewModel.selectService(it) },
            onUpdatePhoneNumber = { phone, name -> viewModel.updatePallyPhoneNumber(phone, name) },
            onRenameService = { id, name -> viewModel.renameService(id, name) },
            onDeleteCustomService = { viewModel.deleteCustomService(it) },
            onToggleForegroundService = { viewModel.toggleForegroundService() },
            onToggleLoopback = { viewModel.toggleLoopbackSimulation() },
            onToggleDiagnostics = { isDiagnosticsEnabled = !isDiagnosticsEnabled },
            onOpenWidgetConfig = {
                context.startActivity(Intent(context, com.cellular.rpc.widget.WidgetConfigurationActivity::class.java))
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    // Conversation Threads Drawer BottomSheet
    if (showThreadDrawer) {
        com.cellular.rpc.ui.chat.ThreadDrawerSheet(
            threads = conversationThreads,
            activeThreadId = activeThreadId,
            onSelectThread = { threadId ->
                viewModel.selectThread(threadId)
                showThreadDrawer = false
            },
            onCreateThread = { title ->
                viewModel.createNewThread(title)
                showThreadDrawer = false
            },
            onRenameThread = { threadId, newTitle ->
                viewModel.renameThread(threadId, newTitle)
            },
            onTogglePin = { threadId, pinned ->
                viewModel.togglePinThread(threadId, pinned)
            },
            onDeleteThread = { threadId ->
                viewModel.deleteThread(threadId)
            },
            onDismiss = { showThreadDrawer = false }
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
                onRefreshWidget(type)
                coroutineScope.launch {
                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
                }
            },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PallySettingsBottomSheet(
    context: android.content.Context,
    currentPhoneNumber: String,
    activeService: CellularServiceProfile,
    availableServices: List<CellularServiceProfile>,
    isServiceActive: Boolean,
    isLoopback: Boolean,
    isDiagnostics: Boolean,
    onSelectService: (CellularServiceProfile) -> Unit,
    onUpdatePhoneNumber: (String, String?) -> Unit,
    onRenameService: (String, String) -> Unit,
    onDeleteCustomService: (String) -> Unit,
    onToggleForegroundService: () -> Unit,
    onToggleLoopback: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onOpenWidgetConfig: () -> Unit,
    onDismiss: () -> Unit
) {
    var phoneNumberInput by remember { mutableStateOf(currentPhoneNumber) }
    var nameInput by remember { mutableStateOf(activeService.name) }
    var renamingProfile by remember { mutableStateOf<CellularServiceProfile?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }

    var selectedInterval by remember { mutableStateOf("15 min") }
    val intervals = listOf("Manual Only", "15 min", "30 min", "1 hour", "3 hours")

    LaunchedEffect(activeService) {
        phoneNumberInput = activeService.phoneNumber
        nameInput = activeService.name
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AI SMS Number & Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Connect any AI SMS provider to power widgets and chat",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Settings")
                    }
                }
            }

            // Section 1: Enter AI Provider SMS Number & Apply
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "AI Provider's SMS Number",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                        Text(
                            text = "Enter your AI provider's phone number. Outgoing messages, schema queries, and interactive widget actions route directly to this cellular number.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = phoneNumberInput,
                            onValueChange = { phoneNumberInput = it },
                            label = { Text("AI SMS Phone Number") },
                            placeholder = { Text("+16462619684") },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Provider Label (Optional)") },
                            placeholder = { Text("e.g. Pally AI, Claude, Office Assistant") },
                            leadingIcon = {
                                Icon(Icons.Default.Label, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (phoneNumberInput.isNotBlank()) {
                                        onUpdatePhoneNumber(phoneNumberInput.trim(), nameInput.trim().ifBlank { null })
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply Number", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    showAddDialog = true
                                    newName = ""
                                    newPhone = ""
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New")
                            }
                        }
                    }
                }
            }

            // Section 2: Saved AI Numbers List (Select, Rename, Delete)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved AI Numbers (${availableServices.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }
                    Text(
                        text = "Tap any saved number to switch to it. Use the edit icon to rename or the trash icon to delete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    availableServices.forEach { service ->
                        val isSelected = service.id == activeService.id ||
                                service.phoneNumber == activeService.phoneNumber

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectService(service)
                                    phoneNumberInput = service.phoneNumber
                                    nameInput = service.name
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) CyanPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (isSelected) BorderStroke(1.5.dp, CyanPrimary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Active indicator icon
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) SignalGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = if (isSelected) SignalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = service.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = SignalGreen,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.Black,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = service.phoneNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Action buttons: Rename and Delete
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            renamingProfile = service
                                            renameText = service.name
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Rename ${service.name}",
                                            tint = CyanPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteCustomService(service.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete ${service.name}",
                                            tint = SignalRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Cellular Pull Interval
            item {
                Column {
                    Text(
                        text = "Cellular Pull Interval (WorkManager / Alarms)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Controls how often the app queries the active AI SMS service for weather and news updates.",
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
                }
            }

            // Section 4: Home Screen Widgets
            item {
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
                                text = "Cellular Weather & News Widgets",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To place on your home screen: exit to the Android launcher, long-press empty space, select Widgets, and choose Weather or News.",
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
            }

            // Section 5: Cellular Transport & Diagnostics
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Transport & Diagnostics",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background Cellular Service",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isServiceActive) "Active • Processing packets with screen locked" else "Inactive • Wakes on incoming SMS only",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isServiceActive) SignalGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isServiceActive,
                            onCheckedChange = { onToggleForegroundService() },
                            modifier = Modifier.testTag("service_toggle_button")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Loopback Simulation Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isLoopback) "Currently simulating locally (no actual SMS sent)" else "Sending real SMS to carrier network",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isLoopback) SignalAmber else SignalGreen
                            )
                        }
                        Switch(
                            checked = isLoopback,
                            onCheckedChange = { onToggleLoopback() },
                            modifier = Modifier.testTag("mode_toggle_pill")
                        )
                    }

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

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))

                    // App Update Section (GitHub APK & Releases Catalog)
                    var isCheckingUpdate by remember { mutableStateOf(false) }
                    var updateMessage by remember { mutableStateOf<String?>(null) }
                    var downloadingFileName by remember { mutableStateOf<String?>(null) }
                    var downloadProgress by remember { mutableFloatStateOf(0f) }
                    var releasesList by remember { mutableStateOf<List<com.cellular.rpc.update.AppUpdateManager.ReleaseItem>>(emptyList()) }
                    var currentVerInfo by remember {
                        mutableStateOf(com.cellular.rpc.update.AppUpdateManager.getCurrentVersion(context))
                    }
                    val scope = rememberCoroutineScope()

                    // Auto-load recent releases and active version on initial render
                    LaunchedEffect(Unit) {
                        currentVerInfo = com.cellular.rpc.update.AppUpdateManager.getCurrentVersion(context)
                        try {
                            val result = com.cellular.rpc.update.AppUpdateManager.fetchRecentReleases(context)
                            releasesList = result.recentReleases
                        } catch (e: Exception) {
                            android.util.Log.d("SettingsScreen", "Initial releases load: ${e.message}")
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "App Updates & Versioning",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Current Version: ${currentVerInfo.first} (v${currentVerInfo.second})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyanPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Button(
                                onClick = {
                                    isCheckingUpdate = true
                                    updateMessage = null
                                    val localContext = context
                                    scope.launch {
                                        try {
                                            currentVerInfo = com.cellular.rpc.update.AppUpdateManager.getCurrentVersion(localContext)
                                            val result = com.cellular.rpc.update.AppUpdateManager.fetchRecentReleases(localContext)
                                            releasesList = result.recentReleases
                                            updateMessage = result.message

                                            if (result.latestUpdate != null) {
                                                val update = result.latestUpdate
                                                downloadingFileName = update.fileName
                                                downloadProgress = 0f
                                                updateMessage = "Downloading update ${update.versionName} (${update.fileName})..."
                                                com.cellular.rpc.update.AppUpdateManager.downloadAndInstallApk(localContext, update.apkUrl) { progress ->
                                                    downloadProgress = progress
                                                    updateMessage = "Downloading ${update.fileName}: ${(progress * 100).toInt()}%"
                                                }
                                                downloadingFileName = null
                                            }
                                        } catch (e: Exception) {
                                            updateMessage = "Update check failed: ${e.localizedMessage}"
                                            downloadingFileName = null
                                        } finally {
                                            isCheckingUpdate = false
                                        }
                                    }
                                },
                                enabled = !isCheckingUpdate && downloadingFileName == null,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Checking...")
                                } else {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Check for Update")
                                }
                            }
                        }

                        if (updateMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = updateMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (downloadingFileName != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // Recent 5 Releases from GitHub releases folder
                        if (releasesList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Available Releases (Latest 5)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                releasesList.forEach { rel ->
                                    val isCurrent = rel.isCurrent
                                    val isNewer = rel.isNewer
                                    val isDownloadingThis = downloadingFileName == rel.fileName

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrent) {
                                            CyanPrimary.copy(alpha = 0.12f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrent) CyanPrimary.copy(alpha = 0.5f) else Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = rel.fileName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isCurrent) CyanPrimary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (isCurrent) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = CyanPrimary,
                                                            contentColor = Color.Black
                                                        ) {
                                                            Text(
                                                                text = "CURRENT",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    } else if (isNewer) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = Color(0xFF4CAF50),
                                                            contentColor = Color.White
                                                        ) {
                                                            Text(
                                                                text = "NEW",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "Build v${rel.versionCode} • ${rel.versionName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    val localContext = context
                                                    downloadingFileName = rel.fileName
                                                    downloadProgress = 0f
                                                    updateMessage = "Downloading ${rel.fileName}..."
                                                    scope.launch {
                                                        try {
                                                            com.cellular.rpc.update.AppUpdateManager.downloadAndInstallApk(
                                                                localContext,
                                                                rel.apkUrl
                                                            ) { p ->
                                                                downloadProgress = p
                                                                updateMessage = "Downloading ${rel.fileName}: ${(p * 100).toInt()}%"
                                                            }
                                                        } catch (e: Exception) {
                                                            updateMessage = "Download failed: ${e.localizedMessage}"
                                                        } finally {
                                                            downloadingFileName = null
                                                        }
                                                    }
                                                },
                                                enabled = downloadingFileName == null && !isCheckingUpdate,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                if (isDownloadingThis) {
                                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Installing", fontSize = 11.sp)
                                                } else {
                                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(if (isCurrent) "Reinstall" else "Install", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (renamingProfile != null) {
        AlertDialog(
            onDismissRequest = { renamingProfile = null },
            title = {
                Text(
                    text = "Rename AI Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Phone: ${renamingProfile?.phoneNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Assistant Name") },
                        placeholder = { Text("e.g. Pally AI, Claude SMS") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = renamingProfile
                        if (current != null && renameText.isNotBlank()) {
                            onRenameService(current.id, renameText.trim())
                        }
                        renamingProfile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
                ) {
                    Text("Save Name")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingProfile = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add New Number Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "Add AI SMS Number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name / Label") },
                        placeholder = { Text("e.g. Claude SMS, Work AI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+16462619684") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPhone.isNotBlank()) {
                            onUpdatePhoneNumber(newPhone.trim(), newName.trim().ifBlank { null })
                            showAddDialog = false
                            newName = ""
                            newPhone = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
                ) {
                    Text("Add & Activate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
    onClearLogs: () -> Unit,
    isMcpSynced: Boolean = false,
    mcpCatalogHash: String = "",
    onPushMcpGenesis: () -> Unit = {},
    handshakeStatus: com.cellular.rpc.domain.handshake.HandshakeStatus = com.cellular.rpc.domain.handshake.HandshakeStatus.UNINITIALIZED,
    pendingTransactions: List<com.cellular.rpc.domain.handshake.PendingTransaction> = emptyList(),
    onProbeHandshake: () -> Unit = {},
    onResolveIntervention: (String, String) -> Unit = { _, _ -> }
) {
    var showMcpManifestDialog by remember { mutableStateOf(false) }
    val genesisManifestJson = remember { com.cellular.rpc.domain.mcp.CellularMcpRegistry.buildGenesisManifestJson() }

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

        // Handshake & State Router Card
        HandshakeSessionCard(
            handshakeStatus = handshakeStatus,
            catalogHash = mcpCatalogHash,
            onProbeHandshake = onProbeHandshake,
            onPushGenesis = onPushMcpGenesis
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Awaiting AI Responses List
        AwaitingTransactionsList(
            pendingTransactions = pendingTransactions,
            onResolveIntervention = onResolveIntervention
        )

        Spacer(modifier = Modifier.height(10.dp))

        // MCP (Model Context Protocol) Single-Push Genesis Sync Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, if (isMcpSynced) SignalGreen.copy(alpha = 0.4f) else SignalAmber.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = if (isMcpSynced) SignalGreen else SignalAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MCP Genesis Capability Discovery",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = if (isMcpSynced) SignalGreen.copy(alpha = 0.15f) else SignalAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isMcpSynced) "SYNCED" else "UNSYNCED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMcpSynced) SignalGreen else SignalAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Self-describing MCP manifest provides AI agents with all registered native widgets (10) and tools (4) upon first start with zero continuous polling overhead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manifest Hash: $mcpCatalogHash (v2.1.0)",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row {
                        OutlinedButton(
                            onClick = { showMcpManifestDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("View JSON", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = onPushMcpGenesis,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Push Genesis", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (showMcpManifestDialog) {
            AlertDialog(
                onDismissRequest = { showMcpManifestDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = CyanPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MCP Genesis Discovery Manifest", style = MaterialTheme.typography.titleMedium)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Deterministic JSON pushed to the AI Gateway. Stored in AI persistent memory for zero-overhead tool calling.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(8.dp)) {
                                item {
                                    Text(
                                        text = genesisManifestJson,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onPushMcpGenesis()
                        showMcpManifestDialog = false
                    }) {
                        Text("Push to AI")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMcpManifestDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

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
