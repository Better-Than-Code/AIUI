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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.cellular.rpc.engine.ChatMessage
import com.example.ui.theme.*

/**
 * Modern floating horizontal circular action bar & reaction pill popup.
 * Avoids old vertical menus in favor of a sleek floating toolbar directly above the message.
 */
@Composable
fun MessageTapbackOverlay(
    message: ChatMessage,
    isUser: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: (ChatMessage) -> Unit,
    onResend: (ChatMessage) -> Unit,
    onInspectWire: (ChatMessage) -> Unit,
    onDelete: ((ChatMessage) -> Unit)? = null
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
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .padding(20.dp)
                    .clickable(enabled = false) {}, // Prevent dismiss when tapping inside
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. FLOATING HORIZONTAL CIRCULAR REACTION & ACTION TOOLBAR (Modern Pill)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f)),
                    shadowElevation = 16.dp,
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top Row: Horizontal Emojis (Circular)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tapbackReactions.forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

                        // Bottom Row: Horizontal Circular Action Buttons (Reply, Copy, Resend, Wire, Delete)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Reply
                            CircularActionButton(
                                icon = Icons.AutoMirrored.Filled.Reply,
                                tint = CyanPrimary,
                                label = "Reply"
                            ) {
                                onReply(message)
                                onDismiss()
                            }

                            // Copy
                            CircularActionButton(
                                icon = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                tint = if (isCopied) SignalGreen else CyanPrimary,
                                label = "Copy"
                            ) {
                                clipboardManager.setText(AnnotatedString(message.widgetData?.toJson() ?: message.text))
                                isCopied = true
                                onDismiss()
                            }

                            // Resend / Re-query
                            CircularActionButton(
                                icon = Icons.Default.Refresh,
                                tint = SignalAmber,
                                label = "Resend"
                            ) {
                                onResend(message)
                                onDismiss()
                            }

                            // Inspect Wire PDU
                            CircularActionButton(
                                icon = Icons.Default.Code,
                                tint = MaterialTheme.colorScheme.primary,
                                label = "Wire"
                            ) {
                                onInspectWire(message)
                                onDismiss()
                            }

                            if (onDelete != null) {
                                // Delete
                                CircularActionButton(
                                    icon = Icons.Default.DeleteOutline,
                                    tint = Color(0xFFFF5252),
                                    label = "Delete"
                                ) {
                                    onDelete(message)
                                    onDismiss()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
