package com.cellular.rpc.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cellular.rpc.engine.ChatMessage
import com.example.ui.theme.*

/**
 * iMessage/iOS Style Tapback + Floating Glassmorphic Context Menu Overlay
 */
@Composable
fun MessageTapbackOverlay(
    message: ChatMessage,
    isUser: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: (ChatMessage) -> Unit,
    onResend: (ChatMessage) -> Unit,
    onInspectWire: (ChatMessage) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    val tapbackReactions = listOf("❤️", "👍", "👎", "😂", "😮", "⚡")

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        // Scrim backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .padding(16.dp)
                    .clickable(enabled = false) {}, // Prevent dismiss when tapping inside
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. EMOJI TAPBACK CAPSULE (iOS style floating pill)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(26.dp),
                    border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.4f)),
                    shadowElevation = 12.dp,
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tapbackReactions.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable {
                                        onReact(emoji)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }
                }

                // 2. HIGHLIGHTED PREVIEW BUBBLE
                Surface(
                    color = if (isUser) CyanPrimaryDark else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, CyanPrimary),
                    shadowElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (isUser) "You" else "AI Gateway",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.text.ifBlank { message.widgetData?.toJson() ?: "Rich Widget Payload" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 4
                        )
                    }
                }

                // 3. ATTACHED GLASSMORPHIC ACTION MENU
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DarkNavyBorder),
                    shadowElevation = 14.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        // Action: Reply / Quote
                        ContextActionRow(
                            icon = Icons.AutoMirrored.Filled.Reply,
                            iconTint = CyanPrimary,
                            label = "Reply",
                            onClick = {
                                onReply(message)
                                onDismiss()
                            }
                        )

                        HorizontalDivider(color = DarkNavyBorder.copy(alpha = 0.6f), thickness = 0.5.dp)

                        // Action: Copy
                        ContextActionRow(
                            icon = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            iconTint = if (isCopied) SignalGreen else CyanPrimary,
                            label = if (isCopied) "Copied to Clipboard" else "Copy Text",
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.widgetData?.toJson() ?: message.text))
                                isCopied = true
                                onDismiss()
                            }
                        )

                        HorizontalDivider(color = DarkNavyBorder.copy(alpha = 0.6f), thickness = 0.5.dp)

                        // Action: Resend / Re-query
                        ContextActionRow(
                            icon = Icons.Default.Refresh,
                            iconTint = SignalAmber,
                            label = if (isUser) "Resend SMS Prompt" else "Re-query AI Assistant",
                            onClick = {
                                onResend(message)
                                onDismiss()
                            }
                        )

                        HorizontalDivider(color = DarkNavyBorder.copy(alpha = 0.6f), thickness = 0.5.dp)

                        // Action: Inspect Wire PDU
                        ContextActionRow(
                            icon = Icons.Default.Code,
                            iconTint = MaterialTheme.colorScheme.primary,
                            label = "Inspect Cellular Wire PDU",
                            onClick = {
                                onInspectWire(message)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
    }
}
