package com.cellular.rpc.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class WidgetConfigurationActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONFIG_TYPE = "extra_config_type" // "weather", "news", or "custom"
        const val EXTRA_WIDGET_TYPE = "extra_widget_type"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set result to CANCELED initially so if user backs out, launcher cancels widget addition
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        val configTypeExtra = intent.getStringExtra(EXTRA_CONFIG_TYPE)
            ?: intent.getStringExtra(EXTRA_WIDGET_TYPE)
            ?: "weather"

        setContent {
            MyApplicationTheme {
                WidgetConfigScreen(
                    appWidgetId = appWidgetId,
                    initialType = configTypeExtra,
                    onSave = { type, city, zip, unit, topic, featureId, title, metricKey, transparency, intervalMin ->
                        saveAndFinish(type, city, zip, unit, topic, featureId, title, metricKey, transparency, intervalMin)
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun saveAndFinish(
        type: String,
        city: String,
        zip: String,
        unit: String,
        topic: String,
        featureId: String,
        customTitle: String,
        metricKey: String,
        transparency: Int,
        intervalMin: Int
    ) {
        val targetId = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetId else 0

        // 1. Save per-widget preferences
        when (type) {
            "weather" -> WidgetPreferences.setWeatherConfig(this, targetId, city, zip, unit)
            "news" -> WidgetPreferences.setNewsConfig(this, targetId, topic)
            "custom" -> CellularCustomAppWidgetProvider.setCustomWidgetConfig(this, targetId, featureId, customTitle, metricKey)
        }
        WidgetPreferences.setTransparency(this, targetId, transparency)
        WidgetPreferences.setIntervalMinutes(this, targetId, intervalMin)

        // 2. Schedule pull worker if interval > 0
        PullBroadcastReceiver.schedulePeriodicPull(this, intervalMin)

        // 3. Request immediate AppWidget update
        when (type) {
            "weather" -> CellularWeatherAppWidgetProvider.updateAllWidgets(this)
            "news" -> CellularNewsAppWidgetProvider.updateAllWidgets(this)
            "custom" -> CellularCustomAppWidgetProvider.updateAllWidgets(this)
        }

        // 4. Return RESULT_OK with appWidgetId
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    appWidgetId: Int,
    initialType: String,
    onSave: (type: String, city: String, zip: String, unit: String, topic: String, featureId: String, title: String, metricKey: String, transparency: Int, interval: Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val targetId = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetId else 0

    var selectedType by remember { mutableStateOf(initialType) }
    var city by remember { mutableStateOf(WidgetPreferences.getWeatherCity(context, targetId)) }
    var zip by remember { mutableStateOf(WidgetPreferences.getWeatherZip(context, targetId)) }
    var unit by remember { mutableStateOf(WidgetPreferences.getWeatherUnit(context, targetId)) }
    var topic by remember { mutableStateOf(WidgetPreferences.getNewsTopic(context, targetId)) }
    var customFeatureId by remember { mutableStateOf(CellularCustomAppWidgetProvider.getCustomFeatureId(context, targetId)) }
    var customTitle by remember { mutableStateOf(CellularCustomAppWidgetProvider.getCustomTitle(context, targetId)) }
    var customMetricKey by remember { mutableStateOf(CellularCustomAppWidgetProvider.getCustomMetricKey(context, targetId)) }
    var transparencyPercent by remember { mutableStateOf(WidgetPreferences.getTransparency(context, targetId).toFloat()) }
    var selectedInterval by remember { mutableStateOf(WidgetPreferences.getIntervalMinutes(context, targetId)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Widget Customization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) "Configuring Widget #$appWidgetId" else "Home Screen Launcher Settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            onSave(
                                selectedType,
                                city,
                                zip,
                                unit,
                                topic,
                                customFeatureId,
                                customTitle,
                                customMetricKey,
                                transparencyPercent.toInt(),
                                selectedInterval
                            )
                        },
                        modifier = Modifier.testTag("widget_save_button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Widget Type Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                listOf(
                    "weather" to "Weather",
                    "news" to "News",
                    "custom" to "Custom AI"
                ).forEach { (typeKey, label) ->
                    val isSelected = selectedType == typeKey
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        onClick = { selectedType = typeKey }
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // Live Preview Card with Transparency Slider
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Live Home Screen Preview",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Simulated wallpaper background behind widget
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(Color(0xFF0D253F), Color(0xFF1B4965), Color(0xFF2B2D42))
                                )
                            )
                            .padding(12.dp)
                    ) {
                        // The previewed widget container
                        val alphaFloat = (transparencyPercent / 100f).coerceIn(0.1f, 1.0f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF101823).copy(alpha = alphaFloat))
                                .border(1.dp, Color(0xFF40566A).copy(alpha = alphaFloat), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            if (selectedType == "weather") {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(city, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Pally SMS", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64B5F6))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (unit == "C") "22°" else "72°", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Sunny", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(if (unit == "C") "H: 24° L: 14°" else "H: 76° L: 58°", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8FA3B8))
                                        }
                                    }
                                }
                            } else if (selectedType == "news") {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(topic, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                        Text("Pally SMS", style = MaterialTheme.typography.labelSmall, color = Color(0xFF64B5F6))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Cellular RPC 2026 Engine Live", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                    Text("Zero-data operating layer syncing via Pally SMS.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0BEC5), maxLines = 1)
                                }
                            } else {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(customTitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                        Text("AI EXTENSION", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8FA3B8))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("⚡ 30 kWh / Day", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Linked to dynamic feature [$customFeatureId]", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0BEC5))
                                }
                            }
                        }
                    }
                }
            }

            // Transparency / Opacity Slider
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Background Opacity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${transparencyPercent.toInt()}%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Slide to adjust transparency on your home screen wallpaper.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = transparencyPercent,
                        onValueChange = { transparencyPercent = it },
                        valueRange = 10f..100f,
                        steps = 18,
                        modifier = Modifier.testTag("transparency_slider")
                    )
                }
            }

            // Type-Specific Configuration Controls
            if (selectedType == "weather") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Weather Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("City Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = zip,
                            onValueChange = { zip = it },
                            label = { Text("Postal / Zip Code (Cellular Query)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("Temperature Scale", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = unit == "F",
                                onClick = { unit = "F" },
                                label = { Text("Fahrenheit (°F)") }
                            )
                            FilterChip(
                                selected = unit == "C",
                                onClick = { unit = "C" },
                                label = { Text("Celsius (°C)") }
                            )
                        }
                    }
                }
            } else if (selectedType == "news") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("News Topics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Select news category requested over Pally SMS:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        val topics = listOf("TECH & WORLD", "FINANCE & CRYPTO", "GLOBAL NEWS", "SCIENCE & AI")
                        topics.forEach { t ->
                            FilterChip(
                                selected = topic == t,
                                onClick = { topic = t },
                                label = { Text(t) }
                            )
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Custom AI Extension Binding", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Link this home screen widget to any dynamic micro-app or form received over cellular:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it },
                            label = { Text("Widget Display Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = customFeatureId,
                            onValueChange = { customFeatureId = it },
                            label = { Text("Dynamic Feature ID (e.g. solar_estimator)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = customMetricKey,
                            onValueChange = { customMetricKey = it },
                            label = { Text("Primary State Key to Display (e.g. result)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("Quick Presets:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = customFeatureId == "solar_estimator",
                                onClick = {
                                    customFeatureId = "solar_estimator"
                                    customTitle = "Solar Array Estimator"
                                    customMetricKey = "result"
                                },
                                label = { Text("Solar Estimator", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = customFeatureId == "inventory_counter",
                                onClick = {
                                    customFeatureId = "inventory_counter"
                                    customTitle = "Field Inventory Counter"
                                    customMetricKey = "count"
                                },
                                label = { Text("Inventory Count", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Sync Pull Interval (WorkManager / AlarmManager)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cellular Pull Interval", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Interval for background ETag pulls via Pally AI SMS. Uses 304 Not Modified caching (0 bytes payload if unchanged).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val intervals = listOf(
                        0 to "Manual Only",
                        15 to "15 Min",
                        30 to "30 Min",
                        60 to "1 Hour",
                        180 to "3 Hours",
                        360 to "6 Hours"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        intervals.chunked(3).forEach { rowIntervals ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowIntervals.forEach { (mins, label) ->
                                    FilterChip(
                                        selected = selectedInterval == mins,
                                        onClick = { selectedInterval = mins },
                                        label = { Text(label, fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
