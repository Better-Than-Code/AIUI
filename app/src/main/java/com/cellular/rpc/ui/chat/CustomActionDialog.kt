package com.cellular.rpc.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cellular.rpc.data.local.CustomActionEntity
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavySurface as DarkNavyCard
import com.example.ui.theme.SignalGreen

@Composable
fun CustomActionDialog(
    onDismiss: () -> Unit,
    onSave: (CustomActionEntity) -> Unit
) {
    CreateCustomActionDialog(
        onDismiss = onDismiss,
        onSaveAction = onSave
    )
}

object ActionIconMapper {
    fun getIcon(name: String): ImageVector {
        return when (name.lowercase()) {
            "weather", "sun", "wbsunny" -> Icons.Default.WbSunny
            "markets", "market", "trendingup", "stock", "crypto" -> Icons.Default.TrendingUp
            "news", "article" -> Icons.Default.Article
            "poll", "vote", "howtovote" -> Icons.Default.HowToVote
            "flight", "plane", "travel" -> Icons.Default.Flight
            "fitness", "workout", "gym" -> Icons.Default.FitnessCenter
            "code", "dev", "tech" -> Icons.Default.Code
            "calc", "tool", "calculate" -> Icons.Default.Calculate
            "task", "tasks", "checklist", "todo" -> Icons.Default.Checklist
            "pay", "payment", "transfer", "money" -> Icons.Default.Payments
            "analytics", "chart", "metrics" -> Icons.Default.Analytics
            "food", "recipe", "restaurant" -> Icons.Default.Restaurant
            "ai", "brain", "psychology" -> Icons.Default.Psychology
            "star", "favorite" -> Icons.Default.Star
            "idea", "lightbulb" -> Icons.Default.Lightbulb
            "hardware", "chip", "memory" -> Icons.Default.Memory
            "web", "globe", "language" -> Icons.Default.Language
            "json", "data", "dataobject" -> Icons.Default.DataObject
            else -> Icons.Default.Bolt
        }
    }
}

private val AVAILABLE_ICONS = listOf(
    "bolt" to Icons.Default.Bolt,
    "flight" to Icons.Default.Flight,
    "fitness" to Icons.Default.FitnessCenter,
    "code" to Icons.Default.Code,
    "calc" to Icons.Default.Calculate,
    "task" to Icons.Default.Checklist,
    "pay" to Icons.Default.Payments,
    "analytics" to Icons.Default.Analytics,
    "food" to Icons.Default.Restaurant,
    "ai" to Icons.Default.Psychology,
    "news" to Icons.Default.Article,
    "market" to Icons.Default.TrendingUp,
    "weather" to Icons.Default.WbSunny,
    "poll" to Icons.Default.HowToVote,
    "star" to Icons.Default.Star,
    "idea" to Icons.Default.Lightbulb
)

private val COLOR_PALETTE = listOf(
    "#00E5FF", // Cyan
    "#00E676", // Green
    "#FFB300", // Amber
    "#FF5252", // Red
    "#E040FB", // Purple
    "#7C4DFF", // Indigo
    "#FF4081", // Pink
    "#40C4FF"  // Sky
)

private val PRESET_TEMPLATES = listOf(
    Triple("Flight Status", "flight_tracker", "Please provide flight status for flight UA428 in JSON format: {\"type\":\"flight_tracker\",\"flight\":\"UA428\",\"status\":\"ON_TIME\",\"gate\":\"B12\",\"departure\":\"14:20\",\"arrival\":\"17:45\"}"),
    Triple("Workout Log", "workout_log", "Please provide today's workout plan in JSON format: {\"type\":\"workout_log\",\"title\":\"Upper Body Hypertrophy\",\"durationMin\":45,\"exercises\":[{\"name\":\"Pushups\",\"sets\":\"3x15\"},{\"name\":\"Pullups\",\"sets\":\"3x10\"}]}"),
    Triple("Recipe Brief", "recipe", "Please provide a quick 3-step high-protein recipe in JSON format: {\"type\":\"recipe\",\"title\":\"Avocado Egg Bowl\",\"prepTime\":\"10m\",\"ingredients\":[\"2 Eggs\",\"1 Avocado\",\"Salt & Pepper\"],\"steps\":[\"Poach eggs\",\"Slice avocado\",\"Combine and season\"]}"),
    Triple("Code Review", "code_review", "Please review this snippet for edge cases in JSON format: {\"type\":\"code_review\",\"verdict\":\"APPROVED\",\"findings\":[\"Clean null-safety\",\"Optimized memory allocation\"],\"score\":95}"),
    Triple("Daily Standup", "standup", "Please format my daily engineering standup in JSON format: {\"type\":\"standup\",\"completed\":[\"Fixed SMS parser\",\"Released v15 APK\"],\"inProgress\":[\"Custom Instant Actions UI\"],\"blockers\":[]}")
)

/**
 * Dialog to create and persist custom instant cellular AI actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateCustomActionDialog(
    onDismiss: () -> Unit,
    onSaveAction: (CustomActionEntity) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var schemaType by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("bolt") }
    var selectedColor by remember { mutableStateOf("#00E5FF") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavyCard),
            border = BorderStroke(1.dp, DarkNavyBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CyanPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Create Instant AI Action",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Define structured low-bandwidth cellular RPC prompt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkNavyBorder)
                Spacer(modifier = Modifier.height(14.dp))

                // Quick Preset Fillers
                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PRESET_TEMPLATES.forEach { (presetLabel, presetType, presetPrompt) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable {
                                label = presetLabel
                                schemaType = presetType
                                prompt = presetPrompt
                                isError = false
                            }
                        ) {
                            Text(
                                text = "+ $presetLabel",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = CyanPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Label
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        if (schemaType.isBlank()) {
                            schemaType = it.lowercase().replace(" ", "_").filter { c -> c.isLetterOrDigit() || c == '_' }
                        }
                    },
                    label = { Text("Action Name (e.g. Flight Status)") },
                    singleLine = true,
                    isError = isError && label.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = DarkNavyBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Schema Type Identifier
                OutlinedTextField(
                    value = schemaType,
                    onValueChange = { schemaType = it.lowercase().replace(" ", "_") },
                    label = { Text("Target Schema Type (e.g. flight_tracker)") },
                    singleLine = true,
                    isError = isError && schemaType.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = DarkNavyBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Full Cellular Prompt with JSON Directive
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Cellular AI Prompt with JSON Schema") },
                    minLines = 3,
                    maxLines = 5,
                    isError = isError && prompt.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = DarkNavyBorder
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))
                // Smart prompt JSON helper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            val cleanType = if (schemaType.isNotBlank()) schemaType else "custom_data"
                            prompt = "Please provide ${label.ifBlank { "update" }} in JSON format: {\"type\":\"$cleanType\",\"title\":\"${label.ifBlank { "Custom Data" }}\",\"status\":\"OK\"}"
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Format JSON Prompt", fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Icon Picker
                Text(
                    text = "Select Icon:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AVAILABLE_ICONS.forEach { (iconKey, vector) ->
                        val isSelected = selectedIcon == iconKey
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) CyanPrimary.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) CyanPrimary else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedIcon = iconKey },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = vector,
                                contentDescription = iconKey,
                                tint = if (isSelected) CyanPrimary else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color Theme Picker
                Text(
                    text = "Theme Color:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    COLOR_PALETTE.forEach { colorHex ->
                        val parsedColor = try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { CyanPrimary }
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parsedColor)
                                .border(
                                    2.dp,
                                    if (isSelected) Color.White else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Live Preview
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, DarkNavyBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val previewColor = try { Color(android.graphics.Color.parseColor(selectedColor)) } catch (e: Exception) { CyanPrimary }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(previewColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ActionIconMapper.getIcon(selectedIcon),
                                contentDescription = null,
                                tint = previewColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = label.ifBlank { "Custom Action Preview" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Type: ${schemaType.ifBlank { "custom" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (label.isBlank() || schemaType.isBlank() || prompt.isBlank()) {
                                isError = true
                            } else {
                                val action = CustomActionEntity(
                                    id = "act_${schemaType}_${System.currentTimeMillis() % 10000}",
                                    label = label.trim(),
                                    prompt = prompt.trim(),
                                    type = schemaType.trim(),
                                    iconName = selectedIcon,
                                    colorHex = selectedColor,
                                    isPreset = false
                                )
                                onSaveAction(action)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                    ) {
                        Text("Save Action", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
