package com.cellular.rpc.ui.miniapp

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.domain.miniapp.BlueprintLinter
import com.cellular.rpc.domain.miniapp.MiniAppBlueprint
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalGreen
import org.json.JSONObject

/**
 * FEAT-12: Zero-Data Offline SDUI Blueprint Code Editor & Playground Test Bench.
 * Allows on-device authoring, AST verification, live interactive rendering, and zero-data installation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SduiPlaygroundScreen(
    onNavigateBack: () -> Unit,
    onInstallBlueprint: (MiniAppBlueprint, Map<String, Any?>) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Code Editor, 1 = Live Preview, 2 = Diagnostics
    var jsonText by remember { mutableStateOf(SAMPLE_TEMPLATES[0].second) }
    var currentAppState by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var showInstallSuccessDialog by remember { mutableStateOf(false) }

    // Parse Blueprint AST
    val parsedBlueprint = remember(jsonText) {
        try {
            MiniAppBlueprint.fromJson(jsonText)
        } catch (e: Exception) {
            null
        }
    }

    // Lint Result
    val lintResult = remember(parsedBlueprint) {
        if (parsedBlueprint != null) {
            BlueprintLinter.lint(parsedBlueprint)
        } else {
            BlueprintLinter.LintResult(
                isValid = false,
                errors = listOf("Invalid JSON syntax or missing schema root object."),
                warnings = emptyList()
            )
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "SDUI Studio",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SignalGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "OFFLINE",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SignalGreen
                                            )
                                        )
                                    }
                                }
                                Text(
                                    text = "Zero-Data Declarative Test Bench",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Copy JSON Button
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(jsonText))
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy JSON", modifier = Modifier.size(20.dp))
                            }

                            // Install to OS Button
                            Button(
                                onClick = {
                                    if (parsedBlueprint != null && lintResult.isValid) {
                                        onInstallBlueprint(parsedBlueprint, currentAppState.ifEmpty { parsedBlueprint.initialState })
                                        showInstallSuccessDialog = true
                                    }
                                },
                                enabled = parsedBlueprint != null && lintResult.isValid,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Install", fontSize = 12.sp)
                            }
                        }
                    }

                    // Navigation Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = CyanPrimary
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Blueprint Code")
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Live Preview")
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val badgeColor = if (!lintResult.isValid) MaterialTheme.colorScheme.error else if (lintResult.warnings.isNotEmpty()) SignalAmber else SignalGreen
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(badgeColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Linter (${lintResult.errors.size + lintResult.warnings.size})")
                                }
                            }
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Template Selector Carousel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Presets:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SAMPLE_TEMPLATES.forEach { (name, json) ->
                    val isSelected = jsonText.trim() == json.trim()
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            jsonText = json
                            currentAppState = emptyMap()
                        },
                        label = { Text(name, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = CyanPrimary
                        )
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // Code Editor Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "JSON AST Definition",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = {
                                        try {
                                            val formatted = JSONObject(jsonText).toString(2)
                                            jsonText = formatted
                                        } catch (e: Exception) {
                                            // invalid json formatting
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Format", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = jsonText,
                            onValueChange = { jsonText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            placeholder = { Text("Paste or author SDUI JSON Blueprint here...") }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Linter mini-banner
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (lintResult.isValid) SignalGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (lintResult.isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (lintResult.isValid) SignalGreen else MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (lintResult.isValid) "AST Valid • ${lintResult.warnings.size} warnings" else "Syntax Error • ${lintResult.errors.size} errors",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (lintResult.isValid) SignalGreen else MaterialTheme.colorScheme.error
                                    )
                                }

                                TextButton(
                                    onClick = { selectedTab = 2 },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("View Details", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Live Preview Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        if (parsedBlueprint != null && lintResult.isValid) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                DynamicAppHost(
                                    blueprint = parsedBlueprint,
                                    initialStateOverride = currentAppState.ifEmpty { null },
                                    isPreviewMode = false,
                                    onStateChanged = { updatedState ->
                                        currentAppState = updatedState
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Dynamic Live State Inspector
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Runtime State Tree",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        TextButton(
                                            onClick = { currentAppState = parsedBlueprint.initialState },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reset", fontSize = 10.sp)
                                        }
                                    }
                                    Text(
                                        text = JSONObject(currentAppState.ifEmpty { parsedBlueprint.initialState }).toString(2),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState())
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.BugReport,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Cannot Render Preview",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Resolve JSON AST syntax errors in the Blueprint Code tab.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Diagnostics & Linter Tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Linter Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-time structural AST schema validation & accessibility heuristics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (lintResult.errors.isNotEmpty()) {
                            Text(
                                text = "Errors (${lintResult.errors.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            lintResult.errors.forEach { err ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(modifier = Modifier.padding(10.dp)) {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (lintResult.warnings.isNotEmpty()) {
                            Text(
                                text = "Warnings (${lintResult.warnings.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SignalAmber
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            lintResult.warnings.forEach { warn ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SignalAmber.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(modifier = Modifier.padding(10.dp)) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = SignalAmber, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(warn, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        if (lintResult.errors.isEmpty() && lintResult.warnings.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SignalGreen.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalGreen, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "All AST Checks Passed",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SignalGreen
                                        )
                                        Text(
                                            text = "Blueprint structure is fully compliant with edge SDUI runtime specification.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInstallSuccessDialog && parsedBlueprint != null) {
        AlertDialog(
            onDismissRequest = { showInstallSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("App Installed Locally")
                }
            },
            text = {
                Text(
                    text = "'${parsedBlueprint.metadata.title}' (${parsedBlueprint.appId}) has been successfully saved to your offline Universal Apps deck."
                )
            },
            confirmButton = {
                TextButton(onClick = { showInstallSuccessDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

// Pre-packaged curated SDUI playground templates
private val SAMPLE_TEMPLATES = listOf(
    "Dual Telemetry" to """{
  "appId": "app_telemetry_studio",
  "version": 1,
  "metadata": {
    "title": "Dual Telemetry Lab",
    "description": "Comparative time-series sensor graph with interactive scrubber",
    "icon": "trending_up"
  },
  "initialState": {
    "primary_stream": [18.2, 22.4, 25.1, 23.8, 31.2, 38.5, 42.0, 39.1, 44.3, 49.8],
    "secondary_stream": [14.0, 16.5, 19.8, 22.0, 24.5, 27.8, 32.0, 35.2, 38.0, 41.5],
    "status": "Telemetry Online"
  },
  "uiRoot": {
    "type": "column",
    "children": [
      {
        "type": "text",
        "text": "Comparative Inverter Telemetry",
        "fontSize": 16,
        "fontWeight": "bold"
      },
      {
        "type": "sparkline",
        "bind": "primary_stream",
        "secondary_bind": "secondary_stream",
        "color": "#00E5FF",
        "secondary_color": "#00E676",
        "height": 140
      },
      {
        "type": "text",
        "bind": "status",
        "fontSize": 12
      }
    ]
  }
}""",
    "Tip & Bill Splitter" to """{
  "appId": "app_calc_studio",
  "version": 1,
  "metadata": {
    "title": "Tip & Bill Splitter",
    "description": "Dynamic calculator with instant state mutations",
    "icon": "calculate"
  },
  "initialState": {
    "subtotal": 50.0,
    "tip_pct": 20,
    "tip_amount": 10.0,
    "total": 60.0
  },
  "uiRoot": {
    "type": "column",
    "children": [
      {
        "type": "card",
        "children": [
          {
            "type": "text",
            "text": "Total with Tip",
            "fontSize": 14
          },
          {
            "type": "text",
            "bind": "total",
            "fontSize": 28,
            "fontWeight": "bold"
          }
        ]
      },
      {
        "type": "row",
        "children": [
          {
            "type": "button",
            "text": "15% Tip",
            "action": {
              "type": "SET_STATE",
              "key": "tip_pct",
              "value": 15
            }
          },
          {
            "type": "button",
            "text": "20% Tip",
            "action": {
              "type": "SET_STATE",
              "key": "tip_pct",
              "value": 20
            }
          },
          {
            "type": "button",
            "text": "25% Tip",
            "action": {
              "type": "SET_STATE",
              "key": "tip_pct",
              "value": 25
            }
          }
        ]
      }
    ]
  }
}""",
    "Task Checklist" to """{
  "appId": "app_checklist_studio",
  "version": 1,
  "metadata": {
    "title": "Checklist Studio",
    "description": "Offline task checklist node",
    "icon": "checklist"
  },
  "initialState": {
    "task1": true,
    "task2": false,
    "task3": false,
    "summary": "1 / 3 Completed"
  },
  "uiRoot": {
    "type": "column",
    "children": [
      {
        "type": "checkbox",
        "text": "Verify carrier APN MMSC resolution",
        "bindChecked": "task1"
      },
      {
        "type": "checkbox",
        "text": "Test offline voice note scrubber",
        "bindChecked": "task2"
      },
      {
        "type": "checkbox",
        "text": "Deploy SDUI blueprint to OS",
        "bindChecked": "task3"
      }
    ]
  }
}""",
    "Sensor 2D Maze" to """{
  "appId": "app_sensor_maze_studio",
  "version": 1,
  "metadata": {
    "title": "Tilt Probe 2D Game",
    "description": "Hardware accelerometer 2D physics game with collision haptics",
    "icon": "sports_esports"
  },
  "initialState": {
    "game_score": 0,
    "game_lives": 3,
    "game_over": false,
    "game_complete": false
  },
  "uiRoot": {
    "type": "column",
    "children": [
      {
        "type": "text",
        "text": "Tilt Probe 2D Game Engine",
        "fontSize": 16,
        "fontWeight": "bold"
      },
      {
        "type": "sensor_game",
        "bind": "game_score",
        "modifier": {
          "height": 280,
          "lives": 3,
          "timeLimit": 60
        }
      }
    ]
  }
}"""
)
