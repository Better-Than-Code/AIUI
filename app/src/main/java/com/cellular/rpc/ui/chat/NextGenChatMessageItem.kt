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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.domain.dynamic.DynamicFeature
import com.cellular.rpc.ui.chat.theme.ChatThemeManager
import com.cellular.rpc.ui.chat.theme.ChatThemeConfig
import com.cellular.rpc.engine.ChatMessage
import com.cellular.rpc.engine.MessageDeliveryStatus
import com.cellular.rpc.engine.MessageSender
import kotlinx.coroutines.launch
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
    onDelete: ((ChatMessage) -> Unit)? = null,
    audioPlayerManager: com.cellular.rpc.engine.AudioPlayerManager? = null,
    onJumpToTail: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == MessageSender.USER
    val themeConfig by ChatThemeManager.themeFlow.collectAsState()
    val bubbleRadius = themeConfig.bubbleCornerRadiusDp.dp
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
                    Text(
                        text = "✨",
                        fontSize = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

                // Render Attachment if present (ignore VISUAL_ANCHOR carrier vehicles)
                if (message.attachment != null && message.attachment.type != com.cellular.rpc.engine.AttachmentType.VISUAL_ANCHOR) {
                    MessageAttachmentBubble(
                        attachment = message.attachment,
                        isUser = isUser,
                        audioPlayerManager = audioPlayerManager,
                        modifier = Modifier.padding(bottom = if (message.text.isNotBlank()) 6.dp else 0.dp)
                    )
                }

                if (isUser) {
                    // USER BUBBLE (Dynamic Themed Pill)
                    if (message.text.isNotBlank()) {
                        Surface(
                            color = themeConfig.outgoingBubbleColor,
                            shape = RoundedCornerShape(
                                topStart = bubbleRadius,
                                topEnd = bubbleRadius,
                                bottomStart = bubbleRadius,
                                bottomEnd = 4.dp
                            ),
                            border = if (themeConfig.bubbleBorderWidthDp > 0f) {
                                BorderStroke(themeConfig.bubbleBorderWidthDp.dp, themeConfig.bubbleBorderColor)
                            } else null,
                            modifier = Modifier.combinedClickable(
                                onClick = { },
                                onLongClick = { showContextMenu = true }
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                                RichMarkdownText(
                                    text = message.text,
                                    textColor = themeConfig.outgoingTextColor
                                )
                            }
                        }
                    }
                } else {
                    // AI BUBBLE / INLINE WIDGET CARD
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (message.text.isNotBlank()) {
                            AiMarkdownBubble(
                                text = message.text,
                                themeConfig = themeConfig,
                                onLongClick = { showContextMenu = true }
                            )
                        }

                        if (message.widgetData != null) {
                            if (message.isSuperseded) {
                                var isExpanded by remember { mutableStateOf(false) }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = DarkNavySurface.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, DarkNavyBorder.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("superseded_card_${message.id}")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isExpanded = !isExpanded },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Text(text = "📦", fontSize = 12.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "v1.${(message.revision - 1).coerceAtLeast(0)} (Superseded)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White.copy(alpha = 0.55f),
                                                    fontSize = 11.sp
                                                )
                                                if (onJumpToTail != null) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "• See latest at tail ↓",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = CyanPrimary.copy(alpha = 0.85f),
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.clickable { onJumpToTail() }
                                                    )
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (isExpanded) "Hide" else "View",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    color = Color.White.copy(alpha = 0.45f)
                                                )
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Toggle Superseded Card",
                                                    tint = Color.White.copy(alpha = 0.45f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        AnimatedVisibility(visible = isExpanded) {
                                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                                Box(modifier = Modifier.alpha(0.7f)) {
                                                    WidgetCardRenderer(
                                                        widget = message.widgetData,
                                                        message = message,
                                                        onRefreshWidget = onRefreshWidget,
                                                        onConfirmTransfer = onConfirmTransfer,
                                                        onVote = onVote,
                                                        onContextMenu = { showContextMenu = true }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column {
                                    if (message.revision > 1) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = CyanPrimary.copy(alpha = 0.12f),
                                            border = BorderStroke(0.5.dp, CyanPrimary.copy(alpha = 0.45f)),
                                            modifier = Modifier
                                                .padding(bottom = 6.dp)
                                                .testTag("revision_badge_${message.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Feed-tail project",
                                                    tint = CyanPrimary,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "v1.${message.revision - 1} • Updated from previous revision",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    color = CyanPrimary,
                                                    letterSpacing = 0.3.sp
                                                )
                                            }
                                        }
                                    }

                                    WidgetCardRenderer(
                                        widget = message.widgetData,
                                        message = message,
                                        onRefreshWidget = onRefreshWidget,
                                        onConfirmTransfer = onConfirmTransfer,
                                        onVote = onVote,
                                        onContextMenu = { showContextMenu = true }
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
                        if (themeConfig.showPduBadge) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "• ${message.byteSize}B (${message.pduCount} PDU)",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (message.is304NotModified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "• 304 Cached",
                                fontSize = 9.sp,
                                color = SignalGreen
                            )
                        } else if (themeConfig.showPduBadge) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "• ${message.byteSize}B (${message.pduCount} PDU)",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

    // iMessage / iOS Tapback Overlay & Floating Action Pill
    if (showContextMenu) {
        MessageTapbackOverlay(
            message = message,
            isUser = isUser,
            onDismiss = { showContextMenu = false },
            onReact = { reaction ->
                // Visual reaction or reply prefix
            },
            onReply = onReply,
            onResend = onResend,
            onInspectWire = {
                showWireDetails = true
            },
            onDelete = onDelete
        )
    }
}

@Composable
fun SkeletonChatCard(skeleton: WidgetData.Skeleton) {
    com.cellular.rpc.ui.components.DynamicNamedSkeletonCard(
        label = skeleton.label,
        targetType = skeleton.targetType,
        iconEmoji = skeleton.iconEmoji
    )
}

@Composable
private fun WidgetCardRenderer(
    widget: WidgetData,
    message: ChatMessage,
    onRefreshWidget: (String) -> Unit,
    onConfirmTransfer: (String) -> Unit,
    onVote: (String, Int) -> Unit,
    onContextMenu: () -> Unit
) {
    when (widget) {
        is WidgetData.Weather -> WeatherChatCard(
            weather = widget,
            is304 = message.is304NotModified,
            onRefresh = { onRefreshWidget("weather") }
        )
        is WidgetData.NewsDigest -> NewsChatCard(news = widget)
        is WidgetData.MarketTicker -> MarketChatCard(
            isRefreshing = message.deliveryStatus == MessageDeliveryStatus.IN_FLIGHT || message.deliveryStatus == MessageDeliveryStatus.QUEUED,
            ticker = widget,
            onQuerySymbol = { sym ->
                onRefreshWidget("market_ticker:${widget.widgetId}:$sym")
            }
        )
        is WidgetData.CellularTransfer -> TransferChatCard(
            transfer = widget,
            onConfirm = { onConfirmTransfer(widget.id) }
        )
        is WidgetData.CellularPoll -> PollChatCard(
            poll = widget,
            onVote = { onVote(widget.id, it) }
        )
        is WidgetData.CellularTool -> ToolChatCard(tool = widget)
        is WidgetData.CalendarEvent -> CalendarChatCard(event = widget)
        is WidgetData.TaskChecklist -> TaskChecklistChatCard(checklist = widget)
        is WidgetData.SystemStatus -> SystemStatusChatCard(status = widget)
        is WidgetData.Skeleton -> SkeletonChatCard(skeleton = widget)
        is WidgetData.MiniAppPreview -> {
            val blueprint = com.cellular.rpc.domain.miniapp.MiniAppBlueprint.fromJson(widget.rawBlueprintJson)
            if (blueprint != null) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val coroutineScope = rememberCoroutineScope()
                com.cellular.rpc.ui.miniapp.DynamicAppHost(
                    blueprint = blueprint,
                    isPreviewMode = true,
                    onInstallToDeck = { bp, st ->
                        coroutineScope.launch {
                            com.cellular.rpc.domain.miniapp.MiniAppDeckManager.installApp(context, bp, st)
                        }
                    }
                )
            } else {
                AiMarkdownBubble(text = widget.rawBlueprintJson, onLongClick = onContextMenu)
            }
        }
        is WidgetData.DynamicBlueprint -> DynamicBlueprintChatCard(blueprint = widget)
        is WidgetData.ChatText -> if (message.text.isBlank()) {
            AiMarkdownBubble(text = widget.text, onLongClick = onContextMenu)
        }
        else -> if (message.text.isBlank()) {
            AiMarkdownBubble(text = widget.toJson(), onLongClick = onContextMenu)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiMarkdownBubble(
    text: String,
    themeConfig: ChatThemeConfig = ChatThemeConfig.DEFAULT,
    onLongClick: () -> Unit
) {
    val bubbleRadius = themeConfig.bubbleCornerRadiusDp.dp
    Surface(
        color = themeConfig.incomingBubbleColor,
        shape = RoundedCornerShape(
            topStart = 4.dp,
            topEnd = bubbleRadius,
            bottomStart = bubbleRadius,
            bottomEnd = bubbleRadius
        ),
        border = if (themeConfig.bubbleBorderWidthDp > 0f) {
            BorderStroke(themeConfig.bubbleBorderWidthDp.dp, themeConfig.bubbleBorderColor)
        } else null,
        modifier = Modifier.combinedClickable(
            onClick = { },
            onLongClick = onLongClick
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            RichMarkdownText(
                text = text,
                textColor = themeConfig.incomingTextColor
            )
        }
    }
}
