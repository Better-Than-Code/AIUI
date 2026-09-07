package com.cellular.rpc.ui.miniapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.domain.miniapp.ActionExecutor
import com.cellular.rpc.domain.miniapp.MiniAppBlueprint
import com.cellular.rpc.domain.miniapp.MiniAppUiNode
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalAmber
import kotlinx.coroutines.launch

/**
 * Universal DynamicAppHost Composable.
 * Recursively traverses the UI tree of a MiniAppBlueprint and instantiates native Jetpack Compose Material 3 composables.
 * Manages an isolated MutableStateFlow<Map<String, Any?>> / remember state for instant local state mutations.
 */
@Composable
fun DynamicAppHost(
    blueprint: MiniAppBlueprint,
    modifier: Modifier = Modifier,
    initialStateOverride: Map<String, Any?>? = null,
    isPreviewMode: Boolean = true,
    onInstallToDeck: ((MiniAppBlueprint, Map<String, Any?>) -> Unit)? = null,
    onDiscardPreview: ((String) -> Unit)? = null,
    onStateChanged: ((Map<String, Any?>) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var appState by remember(blueprint.appId, initialStateOverride) {
        mutableStateOf(initialStateOverride ?: blueprint.initialState)
    }

    val onPerformAction: (Map<String, Any?>?, Map<String, Any?>?) -> Unit = { actionMap, itemContext ->
        if (actionMap != null) {
            val (newState, changed) = ActionExecutor.execute(actionMap, appState, itemContext)
            if (changed) {
                appState = newState
                onStateChanged?.invoke(newState)
            }

            val actionName = actionMap["action"]?.toString()
            if (actionName == "INSTALL_APP") {
                onInstallToDeck?.invoke(blueprint, appState)
            } else if (actionName == "DISCARD_PREVIEW") {
                onDiscardPreview?.invoke(blueprint.appId)
            }
        }
    }

    val onDirectStateMutation: (String, Any?) -> Unit = { key, value ->
        val updated = appState.toMutableMap()
        updated[key] = value
        appState = updated
        onStateChanged?.invoke(updated)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getMiniAppIcon(blueprint.metadata.icon),
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = blueprint.metadata.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (blueprint.metadata.description.isNotBlank()) {
                            Text(
                                text = blueprint.metadata.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    color = if (isPreviewMode) SignalAmber.copy(alpha = 0.15f) else SignalGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isPreviewMode) "IN-FEED PREVIEW" else "INSTALLED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPreviewMode) SignalAmber else SignalGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // Render Declarative Node Tree
            RenderNode(
                node = blueprint.uiRoot,
                appState = appState,
                itemContext = null,
                isPreviewMode = isPreviewMode,
                onPerformAction = onPerformAction,
                onDirectStateMutation = onDirectStateMutation,
                onInstallToDeck = { onInstallToDeck?.invoke(blueprint, appState) },
                onDiscardPreview = { onDiscardPreview?.invoke(blueprint.appId) }
            )
        }
    }
}

@Composable
private fun RenderNode(
    node: MiniAppUiNode,
    appState: Map<String, Any?>,
    itemContext: Map<String, Any?>?,
    isPreviewMode: Boolean,
    onPerformAction: (Map<String, Any?>?, Map<String, Any?>?) -> Unit,
    onDirectStateMutation: (String, Any?) -> Unit,
    onInstallToDeck: () -> Unit,
    onDiscardPreview: () -> Unit
) {
    if (node.showInPreviewOnly && !isPreviewMode) {
        return
    }

    when (node.type.lowercase()) {
        "card" -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding((node.padding).coerceAtLeast(0).dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(node.spacing.dp)
                ) {
                    node.children.forEach { child ->
                        RenderNode(
                            node = child,
                            appState = appState,
                            itemContext = itemContext,
                            isPreviewMode = isPreviewMode,
                            onPerformAction = onPerformAction,
                            onDirectStateMutation = onDirectStateMutation,
                            onInstallToDeck = onInstallToDeck,
                            onDiscardPreview = onDiscardPreview
                        )
                    }
                }
            }
        }

        "row" -> {
            val horizontalArrangement = when (node.align.lowercase()) {
                "spacebetween", "space_between" -> Arrangement.SpaceBetween
                "center" -> Arrangement.Center
                "end", "right" -> Arrangement.End
                else -> Arrangement.spacedBy(node.spacing.dp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = (node.padding / 2).dp),
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = Alignment.CenterVertically
            ) {
                node.children.forEach { child ->
                    val weight = (child.modifier["weight"] as? Number)?.toFloat()
                    val childMod = if (weight != null && weight > 0f) Modifier.weight(weight) else Modifier
                    Box(modifier = childMod) {
                        RenderNode(
                            node = child,
                            appState = appState,
                            itemContext = itemContext,
                            isPreviewMode = isPreviewMode,
                            onPerformAction = onPerformAction,
                            onDirectStateMutation = onDirectStateMutation,
                            onInstallToDeck = onInstallToDeck,
                            onDiscardPreview = onDiscardPreview
                        )
                    }
                }
            }
        }

        "column" -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = (node.padding / 2).dp),
                verticalArrangement = Arrangement.spacedBy(node.spacing.dp)
            ) {
                node.children.forEach { child ->
                    RenderNode(
                        node = child,
                        appState = appState,
                        itemContext = itemContext,
                        isPreviewMode = isPreviewMode,
                        onPerformAction = onPerformAction,
                        onDirectStateMutation = onDirectStateMutation,
                        onInstallToDeck = onInstallToDeck,
                        onDiscardPreview = onDiscardPreview
                    )
                }
            }
        }

        "text" -> {
            val rawText = if (node.bind.isNotBlank()) {
                resolveBinding(node.bind, appState, itemContext)
            } else {
                node.text
            }

            val isStrikethrough = if (node.strikethroughWhen.isNotBlank()) {
                resolveBooleanBinding(node.strikethroughWhen, appState, itemContext)
            } else false

            val textStyle = when (node.style.lowercase()) {
                "titlelarge" -> MaterialTheme.typography.titleLarge
                "titlemedium" -> MaterialTheme.typography.titleMedium
                "titlesmall" -> MaterialTheme.typography.titleSmall
                "bodymedium" -> MaterialTheme.typography.bodyMedium
                "bodysmall" -> MaterialTheme.typography.bodySmall
                "labellarge" -> MaterialTheme.typography.labelLarge
                "labelsmall" -> MaterialTheme.typography.labelSmall
                else -> MaterialTheme.typography.bodyMedium
            }

            Text(
                text = rawText,
                style = textStyle,
                fontWeight = if (node.style.contains("Title", ignoreCase = true)) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (isStrikethrough) TextDecoration.LineThrough else TextDecoration.None,
                color = if (isStrikethrough) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
            )
        }

        "badge" -> {
            Surface(
                color = CyanPrimary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = node.text,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        "divider" -> {
            Spacer(modifier = Modifier.height(node.spacing.dp.coerceAtLeast(4.dp)))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(node.spacing.dp.coerceAtLeast(4.dp)))
        }

        "checkbox" -> {
            val isChecked = if (node.bindChecked.isNotBlank()) {
                resolveBooleanBinding(node.bindChecked, appState, itemContext)
            } else false

            Checkbox(
                checked = isChecked,
                onCheckedChange = {
                    onPerformAction(node.onToggle, itemContext)
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = CyanPrimary,
                    checkmarkColor = Color.Black
                )
            )
        }

        "textfield", "input" -> {
            val currentValue = if (node.bindValue.isNotBlank()) {
                appState[node.bindValue]?.toString() ?: ""
            } else ""

            OutlinedTextField(
                value = currentValue,
                onValueChange = { newValue ->
                    if (node.bindValue.isNotBlank()) {
                        onDirectStateMutation(node.bindValue, newValue)
                    }
                },
                placeholder = { Text(node.hint, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        "button" -> {
            Button(
                onClick = { onPerformAction(node.onClick, itemContext) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (node.icon.isNotBlank()) {
                    Icon(
                        imageVector = getMiniAppIcon(node.icon),
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(node.text, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        "iconbutton" -> {
            IconButton(
                onClick = { onPerformAction(node.onClick, itemContext) }
            ) {
                Icon(
                    imageVector = getMiniAppIcon(node.icon),
                    contentDescription = null,
                    tint = CyanPrimary
                )
            }
        }

        "list" -> {
            val itemsList = (appState[node.bindItems] as? List<*>) ?: emptyList<Any>()
            val template = node.itemTemplate
            if (template != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsList.forEach { item ->
                        val itemMap = item as? Map<String, Any?> ?: emptyMap()
                        RenderNode(
                            node = template,
                            appState = appState,
                            itemContext = itemMap,
                            isPreviewMode = isPreviewMode,
                            onPerformAction = onPerformAction,
                            onDirectStateMutation = onDirectStateMutation,
                            onInstallToDeck = onInstallToDeck,
                            onDiscardPreview = onDiscardPreview
                        )
                    }
                }
            }
        }

        "installfooter" -> {
            if (isPreviewMode) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDiscardPreview,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discard", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onInstallToDeck,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install to Deck", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun resolveBinding(bindExpr: String, appState: Map<String, Any?>, itemContext: Map<String, Any?>?): String {
    return when {
        bindExpr.startsWith("\$item.") -> {
            val key = bindExpr.removePrefix("\$item.")
            itemContext?.get(key)?.toString() ?: ""
        }
        bindExpr.startsWith("\$state.") -> {
            val key = bindExpr.removePrefix("\$state.")
            appState[key]?.toString() ?: ""
        }
        else -> appState[bindExpr]?.toString() ?: ""
    }
}

private fun resolveBooleanBinding(bindExpr: String, appState: Map<String, Any?>, itemContext: Map<String, Any?>?): Boolean {
    val value = when {
        bindExpr.startsWith("\$item.") -> {
            val key = bindExpr.removePrefix("\$item.")
            itemContext?.get(key)
        }
        bindExpr.startsWith("\$state.") -> {
            val key = bindExpr.removePrefix("\$state.")
            appState[key]
        }
        else -> appState[bindExpr]
    }
    return when (value) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        is Number -> value.toInt() != 0
        else -> false
    }
}

fun getMiniAppIcon(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "checklist", "check", "task", "todo" -> Icons.Default.Checklist
        "add", "plus" -> Icons.Default.Add
        "calculate", "calculator", "calc" -> Icons.Default.Calculate
        "poll", "vote" -> Icons.Default.Poll
        "timer", "clock" -> Icons.Default.Timer
        "bar_chart", "chart", "analytics" -> Icons.Default.BarChart
        "bolt", "flash" -> Icons.Default.FlashOn
        "settings" -> Icons.Default.Settings
        "star", "favorite" -> Icons.Default.Star
        else -> Icons.Default.Widgets
    }
}
