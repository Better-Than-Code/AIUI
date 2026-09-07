package com.cellular.rpc.ui.miniapp

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.data.local.AppBlueprintEntity
import com.cellular.rpc.data.local.DynamicFeatureEntity
import com.cellular.rpc.domain.miniapp.MiniAppBlueprint
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalAmber
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Universal Apps Deck & Dynamic Runtime Screen.
 * Lists installed Mini Apps from Room persistence and dynamic micro-apps.
 * Allows users to run standalone interactive sessions with instant in-memory state mutations.
 */
@Composable
fun UniversalAppsDeckTab(
    installedMiniApps: List<AppBlueprintEntity>,
    selectedMiniApp: AppBlueprintEntity?,
    onSelectMiniApp: (AppBlueprintEntity?) -> Unit,
    onUninstallMiniApp: (String) -> Unit,
    onUpdateMiniAppState: (String, Map<String, Any?>) -> Unit,
    onSeedSampleMiniApps: () -> Unit,
    dynamicFeatures: List<DynamicFeatureEntity>,
    selectedFeature: DynamicFeatureEntity?,
    onSelectFeature: (DynamicFeatureEntity?) -> Unit,
    onDeleteFeature: (String) -> Unit,
    onDeploySampleFeature: (String) -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) } // 0: Mini Apps Deck, 1: Dynamic Features & Sandbox
    var inspectingAppJson by remember { mutableStateOf<AppBlueprintEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("universal_apps_deck_tab")
    ) {
        // Active Mini App Runner
        if (selectedMiniApp != null) {
            val blueprint = remember(selectedMiniApp.appId, selectedMiniApp.rawBlueprintJson) {
                MiniAppBlueprint.fromJson(selectedMiniApp.rawBlueprintJson)
            }
            val initialStateOverride = remember(selectedMiniApp.appId, selectedMiniApp.serializedStateJson) {
                try {
                    val obj = JSONObject(selectedMiniApp.serializedStateJson)
                    MiniAppBlueprint.jsonObjectToMap(obj)
                } catch (e: Exception) {
                    null
                }
            }

            if (blueprint != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Running: ${blueprint.metadata.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { onSelectMiniApp(null) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Done", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        DynamicAppHost(
                            blueprint = blueprint,
                            initialStateOverride = initialStateOverride,
                            isPreviewMode = false,
                            onStateChanged = { newState ->
                                onUpdateMiniAppState(blueprint.appId, newState)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Active Dynamic Feature Runner
        if (selectedFeature != null) {
            com.cellular.rpc.ui.dynamic.DynamicScreenHost(
                feature = selectedFeature,
                onClose = { onSelectFeature(null) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Sub-Tab Switcher (Deck vs V8 Sandboxed Extensions)
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.clip(RoundedCornerShape(12.dp)),
            divider = {}
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = {
                    Text(
                        "Installed Mini Apps (${installedMiniApps.size})",
                        fontWeight = if (activeSubTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = {
                    Text(
                        "V8 Script Extensions (${dynamicFeatures.size})",
                        fontWeight = if (activeSubTab == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (activeSubTab == 0) {
            // MINI APPS DECK VIEW
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
                                        Icons.Default.Widgets,
                                        contentDescription = null,
                                        tint = CyanPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Installed Mini Apps Deck",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Deterministic SDUI • Zero Latency • Room Persistence",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Button(
                            onClick = onSeedSampleMiniApps,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sample", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (installedMiniApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No mini-apps in your deck yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onSeedSampleMiniApps) {
                            Text("Seed Sprint Checklist App")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(installedMiniApps, key = { it.appId }) { app ->
                        InstalledMiniAppCard(
                            app = app,
                            isSelected = selectedMiniApp?.appId == app.appId,
                            onLaunch = { onSelectMiniApp(app) },
                            onInspect = { inspectingAppJson = app },
                            onUninstall = { onUninstallMiniApp(app.appId) }
                        )
                    }
                }
            }
        } else {
            // DYNAMIC FEATURES TAB DELEGATE
            com.cellular.rpc.ui.dynamic.DynamicFeaturesTab(
                dynamicFeatures = dynamicFeatures,
                selectedFeature = selectedFeature,
                onSelectFeature = onSelectFeature,
                onDeleteFeature = onDeleteFeature,
                onDeploySample = onDeploySampleFeature
            )
        }
    }

    if (inspectingAppJson != null) {
        val app = inspectingAppJson!!
        val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(app.lastUpdated))
        AlertDialog(
            onDismissRequest = { inspectingAppJson = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${app.title} (v${app.version})", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Updated: $dateStr • Category: ${app.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "State JSON:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = app.serializedStateJson,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Blueprint Layout:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = app.rawBlueprintJson,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingAppJson = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun InstalledMiniAppCard(
    app: AppBlueprintEntity,
    isSelected: Boolean,
    onLaunch: () -> Unit,
    onInspect: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isSelected) CyanPrimary else MaterialTheme.colorScheme.outlineVariant)
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getMiniAppIcon(app.icon),
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = app.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (app.description.isNotBlank()) {
                            Text(
                                text = app.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    color = SignalGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "DECK v${app.version}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onInspect,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Inspect", fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = onUninstall,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Uninstall",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSelected) "Close" else "Launch", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}
