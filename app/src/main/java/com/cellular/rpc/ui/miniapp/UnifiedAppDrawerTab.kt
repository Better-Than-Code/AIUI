package com.cellular.rpc.ui.miniapp

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.data.local.AppBlueprintEntity
import com.cellular.rpc.data.local.DynamicFeatureEntity
import com.cellular.rpc.domain.miniapp.MiniAppBlueprint
import java.util.Locale
import com.cellular.rpc.ui.agenda.AgendaScreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalGreen
import org.json.JSONObject

/**
 * Sprint 3 / INC-19: Unified App Drawer & 2-Tab Navigation Architecture.
 * Combines:
 * 1. Top glanceable shelf (compact carousel for active SDUI cards: Agenda, Weather, Market).
 * 2. 4-column Mini-App Grid (First-party apps + installed dynamic mini-apps).
 * 3. Long-press context menu (Inspect JSON, Reset State, Uninstall).
 * 4. Settings & Gateway quick access.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnifiedAppDrawerTab(
    installedMiniApps: List<AppBlueprintEntity>,
    selectedMiniApp: AppBlueprintEntity?,
    onSelectMiniApp: (AppBlueprintEntity?) -> Unit,
    onUninstallMiniApp: (String) -> Unit,
    onUpdateMiniAppState: (String, Map<String, Any?>) -> Unit,
    onSeedSampleMiniApps: () -> Unit,
    onInstallBlueprint: ((MiniAppBlueprint, Map<String, Any?>) -> Unit)? = null,
    dynamicFeatures: List<DynamicFeatureEntity>,
    selectedFeature: DynamicFeatureEntity?,
    onSelectFeature: (DynamicFeatureEntity?) -> Unit,
    onDeleteFeature: (String) -> Unit,
    onDeploySampleFeature: (String) -> Unit,
    onLaunchQuickPrompt: (String) -> Unit,
    onOpenSettings: () -> Unit,
    gatewayName: String,
    modifier: Modifier = Modifier
) {
    var isAgendaOpen by remember { mutableStateOf(false) }
    var isPlaygroundOpen by remember { mutableStateOf(false) }
    var contextMenuApp by remember { mutableStateOf<AppBlueprintEntity?>(null) }
    var inspectingAppJson by remember { mutableStateOf<AppBlueprintEntity?>(null) }

    // If SDUI Playground is open, render full SduiPlaygroundScreen
    if (isPlaygroundOpen) {
        SduiPlaygroundScreen(
            onNavigateBack = { isPlaygroundOpen = false },
            onInstallBlueprint = { blueprint, state ->
                onInstallBlueprint?.invoke(blueprint, state)
            }
        )
        return
    }

    // If Agenda is open, render full AgendaScreen
    if (isAgendaOpen) {
        AgendaScreen(
            onNavigateBack = { isAgendaOpen = false }
        )
        return
    }

    // If a Mini-App is actively running, render its interactive host
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

        Scaffold(
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onSelectMiniApp(null) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Apps")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = blueprint?.metadata?.title ?: selectedMiniApp.appId,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Edge Mini-App Session",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanPrimary
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = { onSelectMiniApp(null) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Done", fontSize = 12.sp)
                        }
                    }
                }
            }
        ) { innerPad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPad)
                    .padding(16.dp)
            ) {
                if (blueprint != null) {
                    DynamicAppHost(
                        blueprint = blueprint,
                        initialStateOverride = initialStateOverride,
                        isPreviewMode = false,
                        onStateChanged = { newState ->
                            onUpdateMiniAppState(selectedMiniApp.appId, newState)
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error parsing blueprint JSON for ${selectedMiniApp.appId}")
                    }
                }
            }
        }
        return
    }

    // Main App Drawer View
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("unified_app_drawer_tab")
    ) {
        // Top Glanceable Shelf Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Glanceable Shelf",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onOpenSettings,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Settings", fontSize = 12.sp)
            }
        }

        // Glanceable Shelf Horizontal Carousel (~130dp)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Agenda Glanceable Preview
            item {
                GlanceableCard(
                    title = "Agenda Hub",
                    subtitle = "Schedule & Tasks",
                    statusText = "Open Calendar",
                    statusColor = CyanPrimary,
                    icon = Icons.Default.EventNote,
                    onClick = { isAgendaOpen = true }
                )
            }

            // 2. Weather Glanceable Card
            item {
                GlanceableCard(
                    title = "Live Weather",
                    subtitle = "Tap to update forecast",
                    statusText = "Local Radar",
                    statusColor = SignalAmber,
                    icon = Icons.Default.WbSunny,
                    onClick = { onLaunchQuickPrompt("What is the current weather forecast?") }
                )
            }

            // 3. Market Ticker Glanceable Card
            item {
                GlanceableCard(
                    title = "Market Ticker",
                    subtitle = "Crypto & Indices",
                    statusText = "Quotes & Rates",
                    statusColor = SignalGreen,
                    icon = Icons.Default.TrendingUp,
                    onClick = { onLaunchQuickPrompt("Market ticker quotes for BTC and ETH") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Universal Apps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (installedMiniApps.isEmpty()) {
                TextButton(
                    onClick = onSeedSampleMiniApps,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Seed Mini-Apps", fontSize = 11.sp, color = CyanPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4-Column Apps Grid
        val firstPartyApps = listOf(
            AppGridItem(
                id = "agenda_app",
                title = "Agenda",
                icon = Icons.Default.EventNote,
                tint = CyanPrimary,
                onClick = { isAgendaOpen = true }
            ),
            AppGridItem(
                id = "weather_app",
                title = "Weather",
                icon = Icons.Default.WbSunny,
                tint = SignalAmber,
                onClick = { onLaunchQuickPrompt("Weather forecast") }
            ),
            AppGridItem(
                id = "news_app",
                title = "News",
                icon = Icons.Default.Article,
                tint = Color(0xFF81C784),
                onClick = { onLaunchQuickPrompt("Give me today's news digest") }
            ),
            AppGridItem(
                id = "tools_app",
                title = "Calculator",
                icon = Icons.Default.Calculate,
                tint = Color(0xFFBA68C8),
                onClick = { onLaunchQuickPrompt("Tip calculator tool") }
            ),
            AppGridItem(
                id = "market_app",
                title = "Market",
                icon = Icons.Default.TrendingUp,
                tint = SignalGreen,
                onClick = { onLaunchQuickPrompt("Show market ticker") }
            ),
            AppGridItem(
                id = "sdui_studio_app",
                title = "SDUI Studio",
                icon = Icons.Default.Code,
                tint = CyanPrimary,
                onClick = { isPlaygroundOpen = true }
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // First party apps
            items(firstPartyApps) { app ->
                AppIconItem(
                    title = app.title,
                    icon = app.icon,
                    iconTint = app.tint,
                    onClick = app.onClick,
                    onLongClick = null
                )
            }

            // Installed Dynamic Mini-Apps from Room
            items(installedMiniApps) { miniApp ->
                val title = miniApp.appId.replace("app_", "").replace("_", " ").capitalize()
                AppIconItem(
                    title = title,
                    icon = Icons.Default.Extension,
                    iconTint = CyanPrimary,
                    onClick = { onSelectMiniApp(miniApp) },
                    onLongClick = { contextMenuApp = miniApp }
                )
            }
        }
    }

    // Long-Press Context Menu Bottom Sheet
    if (contextMenuApp != null) {
        val app = contextMenuApp!!
        AlertDialog(
            onDismissRequest = { contextMenuApp = null },
            title = {
                Text(
                    text = "Manage: ${app.appId}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            inspectingAppJson = app
                            contextMenuApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inspect Blueprint JSON")
                        }
                    }

                    TextButton(
                        onClick = {
                            onUpdateMiniAppState(app.appId, emptyMap())
                            contextMenuApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset App State")
                        }
                    }

                    TextButton(
                        onClick = {
                            onUninstallMiniApp(app.appId)
                            contextMenuApp = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uninstall App")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { contextMenuApp = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Blueprint JSON Inspector Dialog
    if (inspectingAppJson != null) {
        val app = inspectingAppJson!!
        AlertDialog(
            onDismissRequest = { inspectingAppJson = null },
            title = {
                Text("Blueprint: ${app.appId}", fontWeight = FontWeight.Bold)
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = app.rawBlueprintJson,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingAppJson = null }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun GlanceableCard(
    title: String,
    subtitle: String,
    statusText: String,
    statusColor: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .width(170.dp)
            .height(115.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIconItem(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            iconTint.copy(alpha = 0.25f),
                            iconTint.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    BorderStroke(1.dp, iconTint.copy(alpha = 0.3f)),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class AppGridItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

private fun String.capitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
