package com.cellular.rpc.ui.diagnostics

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

