package com.cellular.rpc.ui.dynamic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.data.local.DynamicFeatureEntity
import com.cellular.rpc.domain.dynamic.AstNode
import com.cellular.rpc.domain.dynamic.AstParser
import com.cellular.rpc.domain.dynamic.DynamicScriptSandbox
import kotlinx.coroutines.launch

/**
 * Top-level Host Composable that renders a dynamically deployed cellular micro-app.
 * Observes two-way state changes, runs actions via the sandboxed JavaScript engine,
 * and recursively projects the AST into native Jetpack Compose nodes.
 */
@Composable
fun DynamicScreenHost(
    feature: DynamicFeatureEntity,
    modifier: Modifier = Modifier,
    onStateUpdated: ((String) -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentStateJson by remember(feature.featureId, feature.currentStateJson) {
        mutableStateOf(feature.currentStateJson)
    }
    var isExecuting by remember { mutableStateOf(false) }
    var executionError by remember { mutableStateOf<String?>(null) }

    val rootAstNode = remember(feature.uiAstJson) {
        AstParser.parse(feature.uiAstJson)
    }

    val onExecuteAction: (String, String) -> Unit = { actionName, paramsJson ->
        scope.launch {
            isExecuting = true
            executionError = null
            val sandbox = DynamicScriptSandbox.getInstance(context)
            val result = sandbox.executeAction(
                featureId = feature.featureId,
                actionName = actionName,
                currentStateJson = currentStateJson,
                userScript = feature.jsLogic,
                actionParamsJson = paramsJson
            )
            isExecuting = false
            result.onSuccess { newStateJson ->
                currentStateJson = newStateJson
                onStateUpdated?.invoke(newStateJson)
            }.onFailure { err ->
                executionError = err.message ?: "Action execution failed"
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dynamic_feature_host_${feature.featureId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title, version badge, execution status
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
                        if (feature.description.isNotBlank()) {
                            Text(
                                text = feature.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "v${feature.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (onClose != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = onClose,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Done", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (executionError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = executionError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Recursive AST Node Rendering
            DynamicAstNode(
                node = rootAstNode,
                currentStateJson = currentStateJson,
                onExecuteAction = onExecuteAction,
                onStateValueChanged = { key, newValue ->
                    scope.launch {
                        val sandbox = DynamicScriptSandbox.getInstance(context)
                        sandbox.executeAction(
                            featureId = feature.featureId,
                            actionName = "setValue",
                            currentStateJson = currentStateJson,
                            userScript = feature.jsLogic,
                            actionParamsJson = "{\"key\":\"$key\",\"value\":$newValue}"
                        ).onSuccess {
                            currentStateJson = it
                            onStateUpdated?.invoke(it)
                        }
                    }
                }
            )

            // Footer state preview bar
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "State: $currentStateJson",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (isExecuting) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recursive Composable AST renderer for dynamic UI nodes.
 */
@Composable
fun DynamicAstNode(
    node: AstNode,
    currentStateJson: String,
    onExecuteAction: (actionName: String, paramsJson: String) -> Unit,
    onStateValueChanged: (key: String, newValue: String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (node) {
        is AstNode.Container -> {
            val align = when (node.horizontalAlignment) {
                "center" -> Alignment.CenterHorizontally
                "end" -> Alignment.End
                else -> Alignment.Start
            }

            if (node.type == "Row") {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(node.padding.dp),
                    horizontalArrangement = Arrangement.spacedBy(node.spacing.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    node.children.forEach { child ->
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            DynamicAstNode(
                                node = child,
                                currentStateJson = currentStateJson,
                                onExecuteAction = onExecuteAction,
                                onStateValueChanged = onStateValueChanged
                            )
                        }
                    }
                }
            } else if (node.type == "Card") {
                Card(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(node.padding.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(node.spacing.dp),
                        horizontalAlignment = align
                    ) {
                        node.children.forEach { child ->
                            DynamicAstNode(
                                node = child,
                                currentStateJson = currentStateJson,
                                onExecuteAction = onExecuteAction,
                                onStateValueChanged = onStateValueChanged
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(node.padding.dp),
                    verticalArrangement = Arrangement.spacedBy(node.spacing.dp),
                    horizontalAlignment = align
                ) {
                    node.children.forEach { child ->
                        DynamicAstNode(
                            node = child,
                            currentStateJson = currentStateJson,
                            onExecuteAction = onExecuteAction,
                            onStateValueChanged = onStateValueChanged
                        )
                    }
                }
            }
        }

        is AstNode.TextNode -> {
            val resolvedText = AstParser.resolveTemplate(node.text, currentStateJson)
            val typographyStyle = when (node.style) {
                "headline" -> MaterialTheme.typography.headlineSmall
                "title" -> MaterialTheme.typography.titleMedium
                "label" -> MaterialTheme.typography.labelMedium
                "caption" -> MaterialTheme.typography.bodySmall
                else -> MaterialTheme.typography.bodyMedium
            }

            val textColor = if (node.colorHex != null) {
                try {
                    Color(android.graphics.Color.parseColor(node.colorHex))
                } catch (e: Exception) {
                    MaterialTheme.colorScheme.onSurface
                }
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Text(
                text = resolvedText,
                style = typographyStyle,
                fontWeight = if (node.isBold) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                modifier = modifier
            )
        }

        is AstNode.ButtonNode -> {
            val buttonIcon = node.icon?.let { getFeatureIcon(it) }

            when (node.variant) {
                "outlined" -> {
                    OutlinedButton(
                        onClick = { onExecuteAction(node.action, node.paramsJson) },
                        modifier = modifier
                    ) {
                        if (buttonIcon != null) {
                            Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(node.label)
                    }
                }
                "tonal" -> {
                    FilledTonalButton(
                        onClick = { onExecuteAction(node.action, node.paramsJson) },
                        modifier = modifier
                    ) {
                        if (buttonIcon != null) {
                            Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(node.label)
                    }
                }
                "text" -> {
                    TextButton(
                        onClick = { onExecuteAction(node.action, node.paramsJson) },
                        modifier = modifier
                    ) {
                        if (buttonIcon != null) {
                            Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(node.label)
                    }
                }
                else -> {
                    Button(
                        onClick = { onExecuteAction(node.action, node.paramsJson) },
                        modifier = modifier
                    ) {
                        if (buttonIcon != null) {
                            Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(node.label)
                    }
                }
            }
        }

        is AstNode.InputNode -> {
            var localText by remember(currentStateJson) {
                val stateObj = try { org.json.JSONObject(currentStateJson) } catch (e: Exception) { null }
                mutableStateOf(stateObj?.optString(node.stateKey) ?: "")
            }

            OutlinedTextField(
                value = localText,
                onValueChange = { newVal ->
                    localText = newVal
                    val formattedVal = if (node.inputType == "number" || node.inputType == "decimal") {
                        newVal.toDoubleOrNull()?.toString() ?: "0"
                    } else {
                        "\"$newVal\""
                    }
                    onStateValueChanged(node.stateKey, formattedVal)
                },
                label = { Text(node.label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (node.inputType == "number" || node.inputType == "decimal") KeyboardType.Number else KeyboardType.Text
                ),
                modifier = modifier.fillMaxWidth()
            )
        }

        is AstNode.DividerNode -> {
            HorizontalDivider(
                modifier = modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        is AstNode.UnknownNode -> {
            // Graceful fallback card for unrecognized AST node types
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(6.dp),
                modifier = modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dynamic Node [${node.type}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun getFeatureIcon(name: String): ImageVector {
    return when (name.lowercase()) {
        "calculate", "calculator", "solar" -> Icons.Default.Calculate
        "add", "plus" -> Icons.Default.Add
        "remove", "minus" -> Icons.Default.Remove
        "refresh", "sync" -> Icons.Default.Refresh
        "check", "done" -> Icons.Default.Check
        "notify", "alarm", "bell" -> Icons.Default.Notifications
        "play" -> Icons.Default.PlayArrow
        else -> Icons.Default.Extension
    }
}
