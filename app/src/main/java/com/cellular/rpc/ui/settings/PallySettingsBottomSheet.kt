package com.cellular.rpc.ui.settings

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PallySettingsBottomSheet(
    context: android.content.Context,
    currentPhoneNumber: String,
    activeService: CellularServiceProfile,
    availableServices: List<CellularServiceProfile>,
    isServiceActive: Boolean,
    isLoopback: Boolean,
    isAdminApproval: Boolean,
    isDiagnostics: Boolean,
    onSelectService: (CellularServiceProfile) -> Unit,
    onUpdatePhoneNumber: (String, String?) -> Unit,
    onRenameService: (String, String) -> Unit,
    onDeleteCustomService: (String) -> Unit,
    onToggleForegroundService: () -> Unit,
    onToggleLoopback: () -> Unit,
    onToggleAdminApproval: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onOpenWidgetConfig: () -> Unit,
    onOpenInspector: () -> Unit,
    onOpenTests: () -> Unit,
    onOpenOutbox: () -> Unit,
    onOpenMutationTracker: () -> Unit,
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

                    if (isDiagnostics) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Admin Approval Mode",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = if (isAdminApproval) "SDUI mutations require manual confirmation" else "SDUI mutations apply instantly",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Switch(
                                        checked = isAdminApproval,
                                        onCheckedChange = { onToggleAdminApproval() }
                                    )
                                }
                            }

                            // Epic 4: Carrier 15-Minute Cooldown Circuit Breaker Card
                            val circuitBreaker = remember { com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.getInstance(context) }
                            val cbState by circuitBreaker.state.collectAsStateWithLifecycle()
                            val cbRemainingMs by circuitBreaker.cooldownRemainingMs.collectAsStateWithLifecycle()
                            val cbFailures by circuitBreaker.consecutiveFailures.collectAsStateWithLifecycle()
                            val cbReason by circuitBreaker.lastFailureReason.collectAsStateWithLifecycle()

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = when (cbState) {
                                        com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.OPEN ->
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                        com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.HALF_OPEN ->
                                            SignalAmber.copy(alpha = 0.2f)
                                        com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.CLOSED ->
                                            MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = when (cbState) {
                                                    com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.OPEN -> MaterialTheme.colorScheme.error
                                                    com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.HALF_OPEN -> SignalAmber
                                                    com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.CLOSED -> SignalGreen
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Carrier Circuit Breaker",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (cbState) {
                                                com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.OPEN -> MaterialTheme.colorScheme.error
                                                com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.HALF_OPEN -> SignalAmber
                                                com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.CLOSED -> SignalGreen.copy(alpha = 0.2f)
                                            }
                                        ) {
                                            Text(
                                                text = cbState.name,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (cbState == com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.OPEN)
                                                    MaterialTheme.colorScheme.onError
                                                else if (cbState == com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.HALF_OPEN)
                                                    Color.Black
                                                else SignalGreen
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    if (cbState == com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.OPEN) {
                                        val totalSecs = cbRemainingMs / 1000
                                        val mins = totalSecs / 60
                                        val secs = totalSecs % 60
                                        Text(
                                            text = "15-min cooldown active to protect SIM. Resumes in ${String.format("%02d:%02d", mins, secs)}.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        if (!cbReason.isNullOrBlank()) {
                                            Text(
                                                text = "Reason: $cbReason",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    } else if (cbState == com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.HALF_OPEN) {
                                        Text(
                                            text = "Cooldown elapsed. Line clearance canary probe active (1 test message permitted).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "Normal operation. Outbound cellular transmissions live (Failures: $cbFailures/3).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (cbState != com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.CLOSED) {
                                            OutlinedButton(
                                                onClick = { circuitBreaker.forceReset() },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Reset Breaker", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                        if (cbState == com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.OPEN) {
                                            OutlinedButton(
                                                onClick = { circuitBreaker.forceProbe() },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Probe Canary", style = MaterialTheme.typography.labelMedium)
                                            }
                                        } else if (cbState == com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.CircuitState.CLOSED) {
                                            OutlinedButton(
                                                onClick = { circuitBreaker.tripBreaker("Manual Test Trip", resultCode = 5) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Test 15m Trip", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenInspector,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Protocol Inspector")
                            }
                            OutlinedButton(
                                onClick = onOpenTests,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("E2E Test Runner")
                            }
                            OutlinedButton(
                                onClick = onOpenOutbox,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Outbox, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Outbox Manager")
                            }
                            OutlinedButton(
                                onClick = onOpenMutationTracker,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mutation Tracker")
                            }
                        }
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

