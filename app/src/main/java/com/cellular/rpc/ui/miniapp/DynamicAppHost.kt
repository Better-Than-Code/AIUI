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
import com.cellular.rpc.domain.miniapp.MiniAppDeckManager
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isInstalledLocally by remember(blueprint.appId, isPreviewMode) {
        mutableStateOf(!isPreviewMode)
    }
    var appState by remember(blueprint.appId, initialStateOverride) {
        mutableStateOf(initialStateOverride ?: blueprint.initialState)
    }

    val handleInstallToDeck: () -> Unit = {
        isInstalledLocally = true
        onInstallToDeck?.invoke(blueprint, appState)
        coroutineScope.launch {
            MiniAppDeckManager.installApp(context, blueprint, appState)
        }
    }

    val onPerformAction: (Map<String, Any?>?, Map<String, Any?>?) -> Unit = { actionMap, itemContext ->
        if (actionMap != null) {
            val (newState, changed) = ActionExecutor.execute(actionMap, appState, itemContext, context)
            if (changed) {
                appState = newState
                onStateChanged?.invoke(newState)
                if (isInstalledLocally || !isPreviewMode) {
                    coroutineScope.launch {
                        MiniAppDeckManager.updateState(context, blueprint.appId, newState)
                    }
                }
            }

            if (ActionExecutor.hasScriptAction(actionMap)) {
                coroutineScope.launch {
                    val (asyncState, asyncChanged) = ActionExecutor.executeAsync(actionMap, appState, itemContext, context)
                    if (asyncChanged) {
                        appState = asyncState
                        onStateChanged?.invoke(asyncState)
                        if (isInstalledLocally || !isPreviewMode) {
                            MiniAppDeckManager.updateState(context, blueprint.appId, asyncState)
                        }
                    }
                }
            }

            val actionName = actionMap["action"]?.toString()
            if (actionName == "INSTALL_APP") {
                handleInstallToDeck()
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
        if (isInstalledLocally || !isPreviewMode) {
            coroutineScope.launch {
                MiniAppDeckManager.updateState(context, blueprint.appId, updated)
            }
        }
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
                    color = if (isInstalledLocally) SignalGreen.copy(alpha = 0.15f) else SignalAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isInstalledLocally) "INSTALLED TO DECK ✓" else "IN-FEED PREVIEW",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isInstalledLocally) SignalGreen else SignalAmber,
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
                onInstallToDeck = handleInstallToDeck,
                onDiscardPreview = { onDiscardPreview?.invoke(blueprint.appId) }
            )

            // Auto-render install bar for in-feed previews that lack an explicit InstallFooter node
            if (isPreviewMode && !isInstalledLocally && !hasInstallFooter(blueprint.uiRoot)) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onDiscardPreview?.invoke(blueprint.appId) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discard", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = handleInstallToDeck,
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
            val rawText = formatAndInterpolateText(
                textTemplate = node.text,
                bindExpr = node.bind,
                format = node.format,
                style = node.style,
                modifier = node.modifier,
                appState = appState,
                itemContext = itemContext
            )

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
                fontWeight = if (node.style.contains("Title", ignoreCase = true) || node.style.contains("Bold", ignoreCase = true)) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (isStrikethrough) TextDecoration.LineThrough else TextDecoration.None,
                color = if (isStrikethrough) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
            )
        }

        "badge" -> {
            val badgeText = formatAndInterpolateText(
                textTemplate = node.text,
                bindExpr = node.bind,
                format = node.format,
                style = node.style,
                modifier = node.modifier,
                appState = appState,
                itemContext = itemContext
            )
            Surface(
                color = CyanPrimary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        "select", "segmented", "segmented_button", "radio", "radiogroup", "options", "tabs", "choice", "chips" -> {
            val bindKey = node.bindValue.ifBlank { node.bind.ifBlank { "selected" } }
            val currentSelected = appState[bindKey]?.toString() ?: ""

            val optionsList: List<Pair<String, Any>> = remember(node.options, node.modifier, node.hint, node.children) {
                extractOptionsList(node)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = (node.padding / 2).dp)
            ) {
                if (node.text.isNotBlank()) {
                    Text(
                        text = node.text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    optionsList.forEach { (label, rawValue) ->
                        val isSelected = currentSelected == rawValue.toString() ||
                                currentSelected == label ||
                                (currentSelected.toDoubleOrNull() != null && rawValue is Number && currentSelected.toDouble() == rawValue.toDouble())

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    onDirectStateMutation(bindKey, rawValue)
                                    val actionMap = node.onSelect ?: node.onClick ?: node.onToggle
                                    if (actionMap != null) {
                                        onPerformAction(
                                            actionMap,
                                            mapOf("selected" to rawValue, "value" to rawValue, "prop" to bindKey)
                                        )
                                    }
                                },
                            color = if (isSelected) CyanPrimary else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
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
                        val changeAction = node.actions?.get("onChange") as? Map<String, Any?>
                            ?: node.actions?.get("input") as? Map<String, Any?>
                            ?: node.onClick
                        if (changeAction != null) {
                            onPerformAction(changeAction, mapOf("value" to newValue, "prop" to node.bindValue))
                        }
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

        "canvas", "canvas_view", "draw_canvas", "sketch", "paint" -> {
            val canvasHeight = (node.modifier["height"] as? Number)?.toInt()
                ?: (node.modifier["canvasHeight"] as? Number)?.toInt()
                ?: 260
            val colorHex = (node.modifier["color"] as? String) ?: node.hint.ifBlank { null }
            val canvasId = if (node.bind.isNotBlank()) node.bind else "canvas_${node.text.hashCode()}"
            CanvasLeafView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(node.padding.dp),
                canvasId = canvasId,
                canvasHeightDp = canvasHeight,
                initialColorHex = colorHex,
                onStateExport = { strokeCount, strokeColorHex ->
                    val bindKey = if (node.bind.isNotBlank()) node.bind else "canvas_strokes"
                    onDirectStateMutation(bindKey, strokeCount)
                    onDirectStateMutation("canvas_color", strokeColorHex)
                }
            )
        }

        "markdown", "reader", "article" -> {
            val rawMarkdown = when {
                node.bind.isNotBlank() -> resolveBinding(node.bind, appState, itemContext)
                node.text.isNotBlank() -> node.text
                else -> ""
            }
            com.cellular.rpc.ui.chat.RichMarkdownText(
                text = rawMarkdown,
                textColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(node.padding.dp)
            )
        }

        "switch", "toggle" -> {
            val isChecked = when {
                node.bindChecked.isNotBlank() -> resolveBooleanBinding(node.bindChecked, appState, itemContext)
                node.bind.isNotBlank() -> resolveBooleanBinding(node.bind, appState, itemContext)
                else -> false
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (node.text.isNotBlank()) {
                    Text(
                        text = node.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = isChecked,
                    onCheckedChange = { newState ->
                        if (node.bindChecked.isNotBlank()) {
                            onDirectStateMutation(node.bindChecked, newState)
                        } else if (node.bind.isNotBlank()) {
                            onDirectStateMutation(node.bind, newState)
                        }
                        if (node.onToggle != null) {
                            onPerformAction(node.onToggle, itemContext)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanPrimary,
                        checkedTrackColor = CyanPrimary.copy(alpha = 0.5f)
                    )
                )
            }
        }

        "progress", "progressbar" -> {
            val progressVal = when {
                node.bindValue.isNotBlank() -> (appState[node.bindValue] as? Number)?.toFloat() ?: 0f
                node.bind.isNotBlank() -> (appState[node.bind] as? Number)?.toFloat() ?: 0f
                node.modifier.containsKey("progress") -> (node.modifier["progress"] as? Number)?.toFloat() ?: 0f
                else -> 0f
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (node.text.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = node.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(progressVal.coerceIn(0f, 1f) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                LinearProgressIndicator(
                    progress = { progressVal.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = CyanPrimary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
            }
        }

        else -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Unsupported Component: ${node.type}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
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
    return com.cellular.rpc.domain.miniapp.SemanticDesignTokens.resolveIcon(iconName)
}

private fun hasInstallFooter(node: MiniAppUiNode): Boolean {
    if (node.type.equals("installfooter", ignoreCase = true)) return true
    return node.children.any { hasInstallFooter(it) } || (node.itemTemplate != null && hasInstallFooter(node.itemTemplate))
}

private fun extractOptionsList(node: MiniAppUiNode): List<Pair<String, Any>> {
    if (node.options.isNotEmpty()) {
        return node.options.map { opt -> normalizeOption(opt) }
    }
    val modifierOptions = node.modifier["options"] as? List<*>
    if (!modifierOptions.isNullOrEmpty()) {
        return modifierOptions.map { opt -> normalizeOption(opt) }
    }
    if (node.hint.contains(",")) {
        return node.hint.split(",").map { it.trim() }.filter { it.isNotBlank() }.map { opt -> normalizeOption(opt) }
    }
    if (node.children.isNotEmpty()) {
        return node.children.map { child ->
            val label = child.text.ifBlank { child.hint.ifBlank { child.bindValue } }
            val value = child.bindValue.ifBlank { child.modifier["value"]?.toString() ?: label }
            Pair(label, value)
        }
    }
    return emptyList()
}

private fun normalizeOption(opt: Any?): Pair<String, Any> {
    return when (opt) {
        is Map<*, *> -> {
            val label = (opt["label"] ?: opt["text"] ?: opt["name"] ?: opt["value"] ?: "").toString()
            val value = opt["value"] ?: opt["val"] ?: label
            Pair(label, value)
        }
        is Number -> {
            val label = if (opt.toDouble() % 1.0 == 0.0) opt.toInt().toString() else opt.toString()
            Pair(label, opt)
        }
        else -> {
            val str = opt?.toString() ?: ""
            if (str.endsWith("%")) {
                val numPart = str.dropLast(1).trim().toDoubleOrNull()
                if (numPart != null) {
                    Pair(str, if (numPart % 1.0 == 0.0) numPart.toInt() else numPart)
                } else {
                    Pair(str, str)
                }
            } else if (str.toDoubleOrNull() != null) {
                val d = str.toDouble()
                Pair(str, if (d % 1.0 == 0.0) d.toInt() else d)
            } else {
                Pair(str, str)
            }
        }
    }
}

private fun formatAndInterpolateText(
    textTemplate: String,
    bindExpr: String,
    format: String,
    style: String,
    modifier: Map<String, Any?>,
    appState: Map<String, Any?>,
    itemContext: Map<String, Any?>?
): String {
    val effectiveFormat = format.ifBlank { modifier["format"]?.toString() ?: "" }.lowercase()
    val isCurrency = effectiveFormat == "currency" || style.contains("currency", ignoreCase = true)
    val isPercent = effectiveFormat == "percent"
    val isDecimal = effectiveFormat == "decimal" || effectiveFormat == "fixed2"

    if (bindExpr.isNotBlank() && textTemplate.isBlank()) {
        val rawVal = resolveBinding(bindExpr, appState, itemContext)
        return formatSingleValue(rawVal, isCurrency, isPercent, isDecimal)
    }

    val source = textTemplate.ifBlank { bindExpr }
    if (source.isBlank()) return ""

    val regex = Regex("""\$?\$?\{?(state|item)\.([a-zA-Z0-9_]+)\}?""")
    var result = regex.replace(source) { match ->
        val fullMatch = match.value
        val hasLeadingDollar = fullMatch.startsWith("$$") || (fullMatch.startsWith("$") && fullMatch.length > 1 && fullMatch[1] == '$')
        val scope = match.groupValues[1]
        val key = match.groupValues[2]

        val rawVal = if (scope == "state") {
            appState[key]
        } else {
            itemContext?.get(key)
        }

        if (rawVal == null) {
            "0.00"
        } else {
            val num = when (rawVal) {
                is Number -> rawVal.toDouble()
                is String -> rawVal.replace("$", "").trim().toDoubleOrNull()
                else -> null
            }

            if (num != null) {
                if (hasLeadingDollar || isCurrency || key.contains("amount", ignoreCase = true) || key.contains("total", ignoreCase = true) || key.contains("tip", ignoreCase = true) || key.contains("price", ignoreCase = true)) {
                    String.format(java.util.Locale.US, "%.2f", num)
                } else if (num % 1.0 == 0.0) {
                    num.toInt().toString()
                } else {
                    String.format(java.util.Locale.US, "%.2f", num)
                }
            } else {
                rawVal.toString()
            }
        }
    }

    if (isCurrency && !result.startsWith("$") && !source.contains("$")) {
        val num = result.toDoubleOrNull()
        if (num != null) {
            result = String.format(java.util.Locale.US, "$%.2f", num)
        }
    }

    return result
}

private fun formatSingleValue(rawVal: String, isCurrency: Boolean, isPercent: Boolean, isDecimal: Boolean): String {
    val clean = rawVal.replace("$", "").trim()
    val num = clean.toDoubleOrNull()
    return when {
        isCurrency && num != null -> String.format(java.util.Locale.US, "$%.2f", num)
        isPercent && num != null -> "${num.toInt()}%"
        isDecimal && num != null -> String.format(java.util.Locale.US, "%.2f", num)
        isCurrency && clean.isNotBlank() -> "$$clean"
        else -> rawVal
    }
}
