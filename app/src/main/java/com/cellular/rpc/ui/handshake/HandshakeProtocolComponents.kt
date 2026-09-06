package com.cellular.rpc.ui.handshake

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.domain.handshake.AwaitingState
import com.cellular.rpc.domain.handshake.CellularHandshakeEngine
import com.cellular.rpc.domain.handshake.HandshakeStatus
import com.cellular.rpc.domain.handshake.PendingTransaction
import com.cellular.rpc.domain.handshake.TargetChannel
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handshake and Session State Card displayed inside the Protocol Inspector.
 */
@Composable
fun HandshakeSessionCard(
    handshakeStatus: HandshakeStatus,
    catalogHash: String,
    onProbeHandshake: () -> Unit,
    onPushGenesis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (handshakeStatus) {
            HandshakeStatus.SESSION_READY -> SignalGreen
            HandshakeStatus.PROBING_AI, HandshakeStatus.AWAITING_GENESIS -> SignalAmber
            HandshakeStatus.DESYNC_DETECTED -> SignalRed
            HandshakeStatus.UNINITIALIZED -> MaterialTheme.colorScheme.primary
        },
        label = "handshake_status_color"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (handshakeStatus) {
                            HandshakeStatus.SESSION_READY -> Icons.Default.CheckCircle
                            HandshakeStatus.PROBING_AI -> Icons.Default.Sync
                            HandshakeStatus.AWAITING_GENESIS -> Icons.Default.CloudUpload
                            HandshakeStatus.DESYNC_DETECTED -> Icons.Default.Warning
                            HandshakeStatus.UNINITIALIZED -> Icons.Default.CellTower
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Handshake & State Router",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = handshakeStatus.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when (handshakeStatus) {
                    HandshakeStatus.SESSION_READY -> "AI Gateway is synchronized. Handshake confirmed local schemas in AI context."
                    HandshakeStatus.PROBING_AI -> "Sent HELLO:$catalogHash probe over cellular SMS. Awaiting AI capability response..."
                    HandshakeStatus.AWAITING_GENESIS -> "Remote AI requested full MCP Genesis Manifest. Dispatched automatically."
                    HandshakeStatus.DESYNC_DETECTED -> "Schema mismatch or uninterpretable packet detected. Auto-recovery active."
                    HandshakeStatus.UNINITIALIZED -> "App has not probed AI yet. Send a zero-bandwidth HELLO probe to verify schemas."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Schema Hash: $catalogHash",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )

                Row {
                    OutlinedButton(
                        onClick = onProbeHandshake,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("probe_handshake_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Probe HELLO", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = onPushGenesis,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("push_genesis_button")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Push Genesis", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Section displaying all active outgoing requests and their waiting/nudge status.
 */
@Composable
fun AwaitingTransactionsList(
    pendingTransactions: List<PendingTransaction>,
    onResolveIntervention: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = SignalAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Awaiting AI Responses (${pendingTransactions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (pendingTransactions.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = SignalGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No pending cellular transactions. All channels idle & synchronized.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pendingTransactions.forEach { tx ->
                    PendingTransactionCard(
                        transaction = tx,
                        onResolve = { action -> onResolveIntervention(tx.reqId, action) }
                    )
                }
            }
        }
    }
}

@Composable
fun PendingTransactionCard(
    transaction: PendingTransaction,
    onResolve: (String) -> Unit
) {
    val stateColor = when (transaction.awaitingState) {
        AwaitingState.AWAITING_RESPONSE -> CyanPrimary
        AwaitingState.AUTO_NUDGING -> SignalAmber
        AwaitingState.USER_INTERVENTION -> SignalRed
        AwaitingState.COMPLETED -> SignalGreen
        AwaitingState.FAILED -> SignalRed
    }

    val elapsedSec = (System.currentTimeMillis() - transaction.sentTimestampMs) / 1000

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, stateColor.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (transaction.targetChannel) {
                            TargetChannel.CHAT -> MaterialTheme.colorScheme.primaryContainer
                            TargetChannel.WIDGET -> SignalGreen.copy(alpha = 0.15f)
                            TargetChannel.DYNAMIC_APP -> CyanPrimary.copy(alpha = 0.15f)
                            TargetChannel.TOOL_EXECUTION -> SignalAmber.copy(alpha = 0.15f)
                            TargetChannel.SYSTEM -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = transaction.targetChannel.name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "REQ: ${transaction.reqId.take(12)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = stateColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${transaction.awaitingState.name} (${elapsedSec}s)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = stateColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = transaction.displayPrompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (transaction.awaitingState == AwaitingState.USER_INTERVENTION || transaction.awaitingState == AwaitingState.AUTO_NUDGING) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onResolve("CANCEL") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Cancel", fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { onResolve("RETRY") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/**
 * High-priority User Intervention Dialog when an AI transaction stalls or returns uninterpretable data.
 */
@Composable
fun UserInterventionDialog(
    transaction: PendingTransaction,
    onResolve: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onResolve("DISMISS") },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = SignalRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cellular AI Intervention Needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "A cellular transaction on channel [${transaction.targetChannel.name}] did not receive a valid AI response after auto-nudging.",
                    style = MaterialTheme.typography.bodySmall
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Prompt: ${transaction.displayPrompt}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reason: ${transaction.lastErrorReason ?: "Carrier timeout (90s exceeded)"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalRed
                        )
                    }
                }
                Text(
                    text = "Would you like to re-send this request, force a schema re-probe, or cancel?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onResolve("RETRY") },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color.Black)
            ) {
                Text("Retry Send")
            }
        },
        dismissButton = {
            TextButton(onClick = { onResolve("CANCEL") }) {
                Text("Cancel Transaction")
            }
        }
    )
}
