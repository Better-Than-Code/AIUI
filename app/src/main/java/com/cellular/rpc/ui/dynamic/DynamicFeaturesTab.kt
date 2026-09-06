package com.cellular.rpc.ui.dynamic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.data.local.DynamicFeatureEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CyanPrimary = Color(0xFF00E5FF)
private val SignalGreen = Color(0xFF00E676)
private val SignalAmber = Color(0xFFFFD600)

/**
 * Dynamic Features Management & Sandbox Runtime Tab.
 * Lists all installed dynamic cellular micro-apps, lets users launch interactive sessions,
 * inspect the AST layout JSON, view isolated V8 sandbox execution logs, and simulate SMS deployments.
 */
@Composable
fun DynamicFeaturesTab(
    dynamicFeatures: List<DynamicFeatureEntity>,
    selectedFeature: DynamicFeatureEntity?,
    onSelectFeature: (DynamicFeatureEntity?) -> Unit,
    onDeleteFeature: (String) -> Unit,
    onDeploySample: (String) -> Unit
) {
    var inspectingFeatureJson by remember { mutableStateOf<DynamicFeatureEntity?>(null) }
    var showDeployDialog by remember { mutableStateOf(false) }
    var showBoundaryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("dynamic_features_tab")
    ) {
        // Active feature runner screen if a feature is selected
        if (selectedFeature != null) {
            DynamicScreenHost(
                feature = selectedFeature,
                onClose = { onSelectFeature(null) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Header / Architecture Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CyanPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Extension,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Autonomous Offline Extensions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dynamic UI AST • Isolated V8 Sandbox • Zero-DEX",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { showBoundaryDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Guardrails", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = { showDeployDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deploy", fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI gateways deploy full interactive forms, calculators, and offline workflows via [APP:BUILD:<id>] SMS text payloads without updating the APK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Features list header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Installed Micro-Apps (${dynamicFeatures.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = SignalGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "OFFLINE READY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SignalGreen,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (dynamicFeatures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No dynamic extensions installed yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { onDeploySample("solar") }) {
                        Text("Seed Sample Extensions")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(dynamicFeatures, key = { it.featureId }) { feature ->
                    DynamicFeatureCard(
                        feature = feature,
                        isSelected = selectedFeature?.featureId == feature.featureId,
                        onLaunch = { onSelectFeature(feature) },
                        onInspect = { inspectingFeatureJson = feature },
                        onDelete = { onDeleteFeature(feature.featureId) }
                    )
                }
            }
        }
    }

    // Deploy / Seed Dialog
    if (showDeployDialog) {
        AlertDialog(
            onDismissRequest = { showDeployDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deploy Micro-App over Cellular", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Simulate receiving an [APP:BUILD:<id>] payload over SMS from an AI gateway.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            onDeploySample("solar")
                            showDeployDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Deploy Solar Array Estimator")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            onDeploySample("tally")
                            showDeployDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Deploy Field Inventory Counter")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeployDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Inspector Dialog
    if (inspectingFeatureJson != null) {
        val f = inspectingFeatureJson!!
        val dateStr = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(f.lastUpdatedMs))

        AlertDialog(
            onDismissRequest = { inspectingFeatureJson = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${f.title} (v${f.version})", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Updated: $dateStr • Executions: ${f.executionCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Jetpack Compose AST Graph:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            item {
                                Text(
                                    text = f.uiAstJson,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sandboxed JS Logic:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            item {
                                Text(
                                    text = f.jsLogic,
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
                    onSelectFeature(f)
                    inspectingFeatureJson = null
                }) {
                    Text("Launch Tool")
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectingFeatureJson = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Guardrails & Architectural Boundary Dialog
    if (showBoundaryDialog) {
        AlertDialog(
            onDismissRequest = { showBoundaryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("System Boundary & Guardrails", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text(
                            text = "Non-Destructive Execution Policy:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SignalGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dynamic UI AST & V8 sandboxed scripts cannot tamper with base cellular protocols, Android permissions, or primary database queues.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "⚡ In-App Dynamic Safe (`[APP:BUILD]`):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "• Interactive UI AST layouts (Columns, Rows, Cards, Inputs, Sliders)\n• Sandboxed V8 arithmetic, logic, and state mutations\n• Scoped key-value persistence (`dynamic_features` table)\n• Audited haptics & native notifications",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = " Full App Rebuild Required (APK / Native):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "• Baseband AT commands & SMS PDU framing\n• Carrier safety limits (5 msgs/min rate engine)\n• Android Manifest & runtime permissions\n• Core Room tables (`outbox_queue`, `chat_messages`)\n• Root navigation shell & Keystore cryptographic keys",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBoundaryDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }
}

@Composable
fun DynamicFeatureCard(
    feature: DynamicFeatureEntity,
    isSelected: Boolean,
    onLaunch: () -> Unit,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLaunch() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = getFeatureIcon(feature.iconName),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = feature.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: ${feature.featureId} • v${feature.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onInspect,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Inspect", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onLaunch,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSelected) "Active" else "Run", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Feature",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (feature.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Current State: ${feature.currentStateJson}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${feature.executionCount} runs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
