package com.cellular.rpc.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class QuickPromptAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val prompt: String,
    val type: String
)

/**
 * Expandable RCS/Slack-Grade Compose Bar:
 * - Quick-action chips (Weather, Markets, News, Poll, Tasks, Tools)
 * - Quoted reply banner
 * - Smart GSM-7 vs UCS-2 PDU encoding and byte calculation
 * - Multi-line auto-expanding field with attach drawer
 */
@Composable
fun NextGenChatInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    replyingToText: String?,
    onCancelReply: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSelectQuickPrompt: (String, String) -> Unit,
    destinationPhone: String,
    isLoopback: Boolean,
    hasSmsPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showActionDrawer by remember { mutableStateOf(false) }

    val quickActions = remember {
        listOf(
            QuickPromptAction(Icons.Default.WbSunny, "Weather", "Please provide current weather in JSON format: {\"type\":\"weather\"}", "weather"),
            QuickPromptAction(Icons.Default.TrendingUp, "Markets", "Please provide market prices in JSON format: {\"type\":\"market_ticker\"}", "market_ticker"),
            QuickPromptAction(Icons.Default.Article, "News Brief", "Please provide top news in JSON format: {\"type\":\"news_digest\"}", "news_digest"),
            QuickPromptAction(Icons.Default.HowToVote, "Poll", "Please create a poll in JSON format: {\"type\":\"poll\"}", "poll"),
            QuickPromptAction(Icons.Default.Checklist, "Tasks", "Please provide a task checklist in JSON format: {\"type\":\"task_checklist\"}", "task_checklist"),
            QuickPromptAction(Icons.Default.Event, "Calendar", "Please provide upcoming events in JSON format: {\"type\":\"calendar_event\"}", "calendar_event"),
            QuickPromptAction(Icons.Default.Payment, "Transfer", "Transfer $25 to Alice for lunch", "transfer")
        )
    }

    // Encoding & PDU calculations
    val byteCount = remember(inputText) { inputText.toByteArray(Charsets.UTF_8).size }
    val pduCount = remember(byteCount) { if (byteCount == 0) 1 else ((byteCount + 139) / 140) }
    val isMultiSegment = byteCount > 140

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Quoted Reply Banner
            AnimatedVisibility(
                visible = replyingToText != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                if (replyingToText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(24.dp)
                                    .background(CyanPrimary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replying to message",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = replyingToText,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = onCancelReply,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel reply",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick-Action Suggestion Chips (Horizontal Carousel)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickActions.forEach { action ->
                    SuggestionChip(
                        onClick = {
                            onSelectQuickPrompt(action.prompt, action.type)
                        },
                        label = {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.label,
                                tint = CyanPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = null
                    )
                }
            }

            // Input Bar & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Attach Drawer Toggle Button
                IconButton(
                    onClick = { showActionDrawer = !showActionDrawer },
                    modifier = Modifier
                        .size(42.dp)
                        .padding(bottom = 2.dp)
                ) {
                    Icon(
                        imageVector = if (showActionDrawer) Icons.Default.Close else Icons.Default.AddCircleOutline,
                        contentDescription = "Quick Tools",
                        tint = CyanPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Text Field
                TextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    placeholder = {
                        Text(
                            "Message AI via SMS...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Send Button
                val canSend = inputText.isNotBlank()
                FilledIconButton(
                    onClick = {
                        if (canSend) {
                            if (!isLoopback && !hasSmsPermissions) {
                                onRequestPermissions()
                            } else {
                                onSendMessage(inputText)
                            }
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .size(42.dp)
                        .padding(bottom = 2.dp)
                        .testTag("chat_send_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (canSend) CyanPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (canSend) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send SMS",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Expanded Quick Tools Grid Drawer
            AnimatedVisibility(
                visible = showActionDrawer,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "AI SMS Tool Triggers",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ToolGridIcon(Icons.Default.WbSunny, "Weather") {
                                onSelectQuickPrompt("Please provide current weather in JSON format: {\"type\":\"weather\"}", "weather")
                                showActionDrawer = false
                            }
                            ToolGridIcon(Icons.Default.TrendingUp, "Markets") {
                                onSelectQuickPrompt("Please provide market prices in JSON format: {\"type\":\"market_ticker\"}", "market_ticker")
                                showActionDrawer = false
                            }
                            ToolGridIcon(Icons.Default.Article, "News") {
                                onSelectQuickPrompt("Please provide top news in JSON format: {\"type\":\"news_digest\"}", "news_digest")
                                showActionDrawer = false
                            }
                            ToolGridIcon(Icons.Default.HowToVote, "Poll") {
                                onSelectQuickPrompt("Please create a poll in JSON format: {\"type\":\"poll\"}", "poll")
                                showActionDrawer = false
                            }
                        }
                    }
                }
            }

            // Status Bar & Encoding Counters
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLoopback) "⚡ Local Loopback" else "📡 Cellular: $destinationPhone",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLoopback) SignalAmber else SignalGreen
                )
                Text(
                    text = "$byteCount/140B • $pduCount PDU" + if (isMultiSegment) " (Multi-part)" else "",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (byteCount <= 140) SignalGreen else SignalAmber
                )
            }
        }
    }
}

@Composable
private fun ToolGridIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CyanPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = CyanPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
