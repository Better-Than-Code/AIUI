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
import com.cellular.rpc.ui.chat.CellularChatTab
import com.cellular.rpc.ui.settings.PallySettingsBottomSheet
import com.cellular.rpc.ui.settings.TelemetryStrip
import com.cellular.rpc.ui.widget.WidgetsAndRpcTab
import com.cellular.rpc.ui.diagnostics.PacketInspectorTab
import com.cellular.rpc.ui.diagnostics.E2ETestRunnerTab
import com.cellular.rpc.ui.diagnostics.OutboxTab

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()
    private val diagnosticsViewModel: DiagnosticsViewModel by viewModels()
    private val miniAppViewModel: MiniAppViewModel by viewModels()
    private val serviceViewModel: ServiceViewModel by viewModels()
    private val widgetViewModel: WidgetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Register SMS and MMS ContentObservers to monitor incoming messages
        com.cellular.rpc.transport.receiver.PallySmsObserver.register(applicationContext)
        com.cellular.rpc.transport.service.HardenedTelephonyObserverService.start(applicationContext)
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            MyApplicationTheme(darkTheme = isDarkTheme) {
                CellularRpcScreen(chatViewModel, diagnosticsViewModel, miniAppViewModel, serviceViewModel, widgetViewModel, isDarkTheme = isDarkTheme, onToggleTheme = { isDarkTheme = !isDarkTheme })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Epic 10: Delta reconciliation onResume to capture any messages received while in background
        com.cellular.rpc.transport.service.HardenedTelephonyObserverService.reconcileMissedMessages(applicationContext)
        com.cellular.rpc.transport.receiver.PallySmsObserver.checkInboxNow(applicationContext)
    }

    override fun onPause() {
        super.onPause()
        // Epic 12: Flush active mini-app state checkpoints to Room to survive LMK process terminations
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.cellular.rpc.domain.miniapp.AtomicStateMutex.flushCheckpointsToDisk(applicationContext)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CellularRpcScreen(
    chatViewModel: ChatViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
    miniAppViewModel: MiniAppViewModel,
    serviceViewModel: ServiceViewModel,
    widgetViewModel: WidgetViewModel,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {}
) {
    // 0 = Chat (Initial Default Tab), 1 = Widgets, 2 = Inspector, 3 = E2E Tests, 4 = Outbox
    var selectedTab by remember { mutableIntStateOf(0) }
    var isDiagnosticsEnabled by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showThreadDrawer by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var pendingApprovalLog by remember { mutableStateOf<com.cellular.rpc.data.local.MutationLogEntity?>(null) }
    LaunchedEffect(Unit) {
        com.cellular.rpc.engine.AdminApprovalState.pendingApprovals.collect { log ->
            pendingApprovalLog = log
        }
    }
    val chatMessages by chatViewModel.chatMessages.collectAsStateWithLifecycle()
    val conversationThreads by chatViewModel.conversationThreads.collectAsStateWithLifecycle()
    val openTabThreadIds by chatViewModel.openTabThreadIds.collectAsStateWithLifecycle()
    val activeThreadId by chatViewModel.activeThreadId.collectAsStateWithLifecycle()
    val activeThread by chatViewModel.activeThread.collectAsStateWithLifecycle()
    val widgetCache by widgetViewModel.widgetCache.collectAsStateWithLifecycle()
    val packetLogs by diagnosticsViewModel.packetLogs.collectAsStateWithLifecycle()
    val outboxItems by diagnosticsViewModel.outboxItems.collectAsStateWithLifecycle()
    val isServiceActive by serviceViewModel.isServiceActive.collectAsStateWithLifecycle()
    val isEngineRunning by diagnosticsViewModel.isEngineRunning.collectAsStateWithLifecycle()
    val inFlightCount by diagnosticsViewModel.inFlightCount.collectAsStateWithLifecycle()
    val txCount by diagnosticsViewModel.txCount.collectAsStateWithLifecycle()
    val rxCount by diagnosticsViewModel.rxCount.collectAsStateWithLifecycle()
    val bytesSaved by diagnosticsViewModel.bytesSaved304.collectAsStateWithLifecycle()
    val nextTxMs by diagnosticsViewModel.nextAllowedTxMs.collectAsStateWithLifecycle()
    val lastBinaryPayload by diagnosticsViewModel.lastReassembledPayload.collectAsStateWithLifecycle()
    val testResults by diagnosticsViewModel.testResults.collectAsStateWithLifecycle()
    val isTesting by diagnosticsViewModel.isTesting.collectAsStateWithLifecycle()
    val pallyPhoneNumber by serviceViewModel.pallyPhoneNumber.collectAsStateWithLifecycle()
    val activeService by serviceViewModel.activeServiceProfile.collectAsStateWithLifecycle()
    val availableServices by serviceViewModel.availableServices.collectAsStateWithLifecycle()
    val isLoopbackSimulation by serviceViewModel.isLoopbackSimulation.collectAsStateWithLifecycle()
    val isAdminApprovalMode by serviceViewModel.isAdminApprovalMode.collectAsStateWithLifecycle()
    val isMcpSynced by diagnosticsViewModel.isMcpSynced.collectAsStateWithLifecycle()
    val mcpCatalogHash by diagnosticsViewModel.mcpCatalogHash.collectAsStateWithLifecycle()
    val handshakeStatus by diagnosticsViewModel.handshakeStatus.collectAsStateWithLifecycle()
    val pendingTransactions by diagnosticsViewModel.pendingTransactions.collectAsStateWithLifecycle()
    val activeIntervention by diagnosticsViewModel.activeIntervention.collectAsStateWithLifecycle()
    val dynamicFeatures by miniAppViewModel.dynamicFeatures.collectAsStateWithLifecycle()
    val selectedFeature by miniAppViewModel.selectedFeature.collectAsStateWithLifecycle()
    val installedMiniApps by miniAppViewModel.installedMiniApps.collectAsStateWithLifecycle()
    val selectedMiniApp by miniAppViewModel.selectedMiniApp.collectAsStateWithLifecycle()
    val customActions by chatViewModel.customActions.collectAsStateWithLifecycle()
    var showCreateActionDialog by remember { mutableStateOf(false) }

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
            com.cellular.rpc.transport.service.HardenedTelephonyObserverService.start(context)
            com.cellular.rpc.transport.receiver.PallySmsObserver.checkInboxNow(context)
        }
    }

    LaunchedEffect(hasSmsPermissions) {
        if (!hasSmsPermissions) {
            permissionLauncher.launch(requiredPermissions)
        } else {
            com.cellular.rpc.transport.service.HardenedTelephonyObserverService.start(context)
            com.cellular.rpc.transport.receiver.PallySmsObserver.checkInboxNow(context)
        }
    }

    LaunchedEffect(selectedTab) {
        if (hasSmsPermissions) {
            com.cellular.rpc.transport.service.HardenedTelephonyObserverService.reconcileMissedMessages(context)
            com.cellular.rpc.transport.receiver.PallySmsObserver.checkInboxNow(context)
        }
    }

    LaunchedEffect(isDiagnosticsEnabled) {
        if (!isDiagnosticsEnabled && selectedTab in listOf(2, 3, 4)) {
            selectedTab = 0
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
                    openTabIds = openTabThreadIds,
                    allThreads = conversationThreads,
                    activeThreadId = activeThreadId,
                    onSelectTab = { chatViewModel.openTab(it) },
                    onCloseTab = { chatViewModel.closeTab(it) },
                    onNewChatClick = { chatViewModel.createNewThread() },
                    onOpenHistoryClick = { showThreadDrawer = true },
                    customActions = customActions,
                    onCreateCustomAction = { showCreateActionDialog = true },
                    onDeleteCustomAction = { chatViewModel.deleteCustomAction(it) },
                    pallyPhone = pallyPhoneNumber,
                    assistantName = activeService.name,
                    isLoopbackSimulation = isLoopbackSimulation,
                    hasSmsPermissions = hasSmsPermissions,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions) },
                    onToggleLoopback = { serviceViewModel.toggleLoopbackSimulation() },
                    onSendMessage = { text, atts -> chatViewModel.sendChatMessage(text, atts) },
                    onSelectTemplate = { template -> chatViewModel.sendTemplateRequest(template) },
                    onVote = { pollId, opt -> chatViewModel.castVote(pollId, opt) },
                    onConfirmTransfer = { chatViewModel.confirmTransfer(it) },
                    onRefreshWidget = { chatViewModel.queryWidget(it) },
                    onDeleteMessage = { chatViewModel.deleteChatMessage(it) }
                )
                1 -> WidgetsAndRpcTab(
                    widgetCache = widgetCache,
                    onQueryWidget = { chatViewModel.queryWidget(it) },
                    onMutateServer = { widgetViewModel.simulateServerDataChange(it) },
                    onSendBinary = { widgetViewModel.sendBinaryDemo(260) },
                    lastBinaryPayload = lastBinaryPayload
                )
                2 -> PacketInspectorTab(
                    packetLogs = packetLogs,
                    onClearLogs = { diagnosticsViewModel.clearLogs() },
                    isMcpSynced = isMcpSynced,
                    mcpCatalogHash = mcpCatalogHash,
                    onPushMcpGenesis = { diagnosticsViewModel.pushGenesisMcpManifest() },
                    handshakeStatus = handshakeStatus,
                    pendingTransactions = pendingTransactions,
                    onProbeHandshake = { diagnosticsViewModel.probeHandshake() },
                    onResolveIntervention = { reqId, action -> diagnosticsViewModel.resolveIntervention(reqId, action) }
                )
                3 -> E2ETestRunnerTab(
                    testResults = testResults,
                    isTesting = isTesting,
                    onRunTests = { diagnosticsViewModel.runEndToEndTestPlan() }
                )
                4 -> OutboxTab(
                    outboxItems = outboxItems,
                    onClearOutbox = { diagnosticsViewModel.clearOutbox() }
                )
                5 -> com.cellular.rpc.ui.miniapp.UniversalAppsDeckTab(
                    installedMiniApps = installedMiniApps,
                    selectedMiniApp = selectedMiniApp,
                    onSelectMiniApp = { miniAppViewModel.selectMiniApp(it) },
                    onUninstallMiniApp = { miniAppViewModel.uninstallMiniApp(it) },
                    onUpdateMiniAppState = { id, state -> miniAppViewModel.updateMiniAppState(id, state) },
                    onSeedSampleMiniApps = { miniAppViewModel.seedSampleMiniApps() },
                    dynamicFeatures = dynamicFeatures,
                    selectedFeature = selectedFeature,
                    onSelectFeature = { miniAppViewModel.selectDynamicFeature(it) },
                    onDeleteFeature = { miniAppViewModel.deleteDynamicFeature(it) },
                    onDeploySampleFeature = { miniAppViewModel.deploySampleFeature(it) }
                )
                6 -> com.cellular.rpc.ui.diagnostics.MutationLogTab(
                    viewModel = diagnosticsViewModel
                )
            }
        }
    }

    // High-Priority User Intervention Dialog for Awaiting/Stalled Transactions
    activeIntervention?.let { interventionTx ->
        UserInterventionDialog(
            transaction = interventionTx,
            onResolve = { action ->
                diagnosticsViewModel.resolveIntervention(interventionTx.reqId, action)
            }
        )
    }

    // Admin Approval Mode Dialog
    pendingApprovalLog?.let { log ->
        AlertDialog(
            onDismissRequest = { /* Require explicit action */ },
            title = { Text("Admin Approval Required") },
            text = { Text("Incoming SDUI mutation intercepted:\n${log.triggerEvent}\n\nDo you want to approve and deploy this update?") },
            confirmButton = {
                Button(onClick = {
                    val currentLog = pendingApprovalLog
                    pendingApprovalLog = null
                    if (currentLog != null) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            com.cellular.rpc.domain.dynamic.DynamicFeatureManager.approveAndInstall(context, currentLog)
                        }
                    }
                }) {
                    Text("Approve")
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(onClick = {
                        val currentLog = pendingApprovalLog
                        pendingApprovalLog = null
                        if (currentLog != null) {
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                com.cellular.rpc.domain.dynamic.DynamicFeatureManager.rejectAndDiscard(context, currentLog)
                            }
                        }
                    }) {
                        Text("Reject")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        // Just dismiss the dialog, keep in queue
                        pendingApprovalLog = null
                    }) {
                        Text("Ignore")
                    }
                }
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
            isAdminApproval = isAdminApprovalMode,
            isDiagnostics = isDiagnosticsEnabled,
            onSelectService = { serviceViewModel.selectService(it) },
            onUpdatePhoneNumber = { phone, name -> serviceViewModel.updatePallyPhoneNumber(phone, name) },
            onRenameService = { id, name -> serviceViewModel.renameService(id, name) },
            onDeleteCustomService = { serviceViewModel.deleteCustomService(it) },
            onToggleForegroundService = { serviceViewModel.toggleForegroundService() },
            onToggleLoopback = { serviceViewModel.toggleLoopbackSimulation() },
            onToggleAdminApproval = { serviceViewModel.toggleAdminApprovalMode() },
            onToggleDiagnostics = { isDiagnosticsEnabled = !isDiagnosticsEnabled },
            onOpenWidgetConfig = {
                context.startActivity(Intent(context, com.cellular.rpc.widget.WidgetConfigurationActivity::class.java))
            },
            onOpenInspector = {
                selectedTab = 2
                showSettingsSheet = false
            },
            onOpenTests = {
                selectedTab = 3
                showSettingsSheet = false
            },
            onOpenOutbox = {
                selectedTab = 4
                showSettingsSheet = false
            },
            onOpenMutationTracker = {
                selectedTab = 6
                showSettingsSheet = false
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
                chatViewModel.selectThread(threadId)
                showThreadDrawer = false
            },
            onCreateThread = { title ->
                chatViewModel.createNewThread(title)
                showThreadDrawer = false
            },
            onRenameThread = { threadId, newTitle ->
                chatViewModel.renameThread(threadId, newTitle)
            },
            onTogglePin = { threadId, pinned ->
                chatViewModel.togglePinThread(threadId, pinned)
            },
            onDeleteThread = { threadId ->
                chatViewModel.deleteThread(threadId)
            },
            onDismiss = { showThreadDrawer = false }
        )
    }

    // Custom Cellular AI Action Creator Dialog
    if (showCreateActionDialog) {
        com.cellular.rpc.ui.chat.CustomActionDialog(
            onDismiss = { showCreateActionDialog = false },
            onSave = { newAction ->
                chatViewModel.saveCustomAction(newAction)
            }
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

