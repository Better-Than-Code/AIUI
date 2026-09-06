package com.cellular.rpc.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.engine.ChatMessage
import com.cellular.rpc.engine.MessageDeliveryStatus
import com.cellular.rpc.engine.MessageSender
import com.cellular.rpc.engine.WidgetData
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * iMessage/RCS-Grade Chat Bubble Item
 * - Fluid asymmetric geometry with sender tails
 * - Long-press contextual actions (Copy, Reply, Share, Wire PDU inspector)
 * - Markdown & Code syntax highlighting
 * - Real-time Delivery status pills (Queued, Sent, Delivered)
 * - Integrated rich interactive widget cards
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NextGenChatMessageItem(
    message: ChatMessage,
    assistantName: String,
    onVote: (String, Int) -> Unit,
    onConfirmTransfer: (String) -> Unit,
    onRefreshWidget: (String) -> Unit,
    onReply: (ChatMessage) -> Unit,
    onResend: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == MessageSender.USER
    val clipboardManager = LocalClipboardManager.current
    var showContextMenu by remember { mutableStateOf(false) }
    var showWireDetails by remember { mutableStateOf(false) }

    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestampMs) { timeFormatter.format(Date(message.timestampMs)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            // Assistant Avatar on Left for AI Messages
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(CyanPrimary, DarkNavyBorder))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = assistantName,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // The Message Bubble or Card
            Column(
                modifier = Modifier.widthIn(max = if (message.widgetData != null) 340.dp else 300.dp),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                // Header (Assistant Name & Time for AI)
                if (!isUser) {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = assistantName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    }
                }

                if (isUser) {
                    // USER BUBBLE (iMessage Style Teal/Cyan Pill)
                    Surface(
                        color = CyanPrimaryDark,
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 4.dp
                        ),
                        modifier = Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = { showContextMenu = true }
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                            RichMarkdownText(
                                text = message.text,
                                textColor = Color.White
                            )
                        }
                    }
                } else {
                    // AI BUBBLE / INLINE WIDGET CARD
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (message.text.isNotBlank()) {
                            AiMarkdownBubble(
                                text = message.text,
                                onLongClick = { showContextMenu = true }
                            )
                        }

                        if (message.widgetData != null) {
                            // Render rich inline widget
                            when (val widget = message.widgetData) {
                                is WidgetData.Weather -> com.example.WeatherChatCard(
                                    weather = widget,
                                    is304 = message.is304NotModified,
                                    onRefresh = { onRefreshWidget("weather") }
                                )
                                is WidgetData.NewsDigest -> com.example.NewsChatCard(news = widget)
                                is WidgetData.MarketTicker -> com.example.MarketChatCard(ticker = widget)
                                is WidgetData.CellularTransfer -> com.example.TransferChatCard(
                                    transfer = widget,
                                    onConfirm = { onConfirmTransfer(widget.id) }
                                )
                                is WidgetData.CellularPoll -> com.example.PollChatCard(
                                    poll = widget,
                                    onVote = { onVote(widget.id, it) }
                                )
                                is WidgetData.CellularTool -> com.example.ToolChatCard(tool = widget)
                                is WidgetData.CalendarEvent -> com.example.CalendarChatCard(event = widget)
                                is WidgetData.TaskChecklist -> com.example.TaskChecklistChatCard(checklist = widget)
                                is WidgetData.SystemStatus -> com.example.SystemStatusChatCard(status = widget)
                                is WidgetData.ChatText -> if (message.text.isBlank()) {
                                    AiMarkdownBubble(
                                        text = widget.text,
                                        onLongClick = { showContextMenu = true }
                                    )
                                }
                                else -> if (message.text.isBlank()) {
                                    AiMarkdownBubble(
                                        text = widget.toJson(),
                                        onLongClick = { showContextMenu = true }
                                    )
                                }
                            }
                        }
                    }
                }

                // Delivery Status & Cellular Telemetry Pill
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable { showWireDetails = !showWireDetails },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isUser) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "• ${message.byteSize}B (${message.pduCount} PDU)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.deliveryStatus) {
                            MessageDeliveryStatus.QUEUED -> {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Queued",
                                    tint = SignalAmber,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            MessageDeliveryStatus.IN_FLIGHT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            MessageDeliveryStatus.DELIVERED -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = SignalGreen,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    } else {
                        Icon(
                            imageVector = if (showWireDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Inspect",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (message.is304NotModified) "304 ETag Cache Match (0 PDU)" else "${message.byteSize}B SMS • CRC Verified",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (message.is304NotModified) SignalGreen else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Collapsible Micro-Wire Inspection Drawer
                AnimatedVisibility(visible = showWireDetails) {
                    Surface(
                        color = Color(0xFF0D1117),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        border = BorderStroke(1.dp, DarkNavyBorder)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "RAW CELLULAR SMS PDU / WIRE:",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = message.wirePacket ?: (message.widgetData?.toJson() ?: message.text),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyanPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    // Contextual Bottom Sheet / Menu
    if (showContextMenu) {
        ModalBottomSheet(
            onDismissRequest = { showContextMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Message Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyanPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Action: Copy Text
                ListItem(
                    headlineContent = { Text("Copy Text") },
                    leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyanPrimary) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(message.widgetData?.toJson() ?: message.text))
                            showContextMenu = false
                        }
                )

                // Action: Quote / Reply
                ListItem(
                    headlineContent = { Text("Reply / Quote") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = CyanPrimary) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onReply(message)
                            showContextMenu = false
                        }
                )

                // Action: Resend / Re-query
                ListItem(
                    headlineContent = { Text(if (isUser) "Resend SMS" else "Re-query AI Provider") },
                    leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null, tint = SignalAmber) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onResend(message)
                            showContextMenu = false
                        }
                )

                // Action: Inspect Raw Wire
                ListItem(
                    headlineContent = { Text("Inspect Raw Cellular PDU") },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showWireDetails = true
                            showContextMenu = false
                        }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiMarkdownBubble(
    text: String,
    onLongClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 18.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp
        ),
        border = BorderStroke(1.dp, DarkNavyBorder),
        modifier = Modifier.combinedClickable(
            onClick = { },
            onLongClick = onLongClick
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            RichMarkdownText(
                text = text,
                textColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
