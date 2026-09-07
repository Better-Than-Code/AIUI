package com.cellular.rpc.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cellular.rpc.data.local.ConversationThreadEntity
import com.example.ui.theme.*

/**
 * Top Tabbed Chat Bar for managing multiple active conversational threads.
 * Allows instant tab switching, starting new chats, closing tabs, and opening
 * any past saved conversation from SQLite/Room history.
 */
@Composable
fun ChatTabBar(
    openTabIds: List<String>,
    allThreads: List<ConversationThreadEntity>,
    activeThreadId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onOpenHistoryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Find thread metadata for open tabs
    val threadMap = remember(allThreads) {
        allThreads.associateBy { it.threadId }
    }

    // Auto-scroll to active tab when it changes
    LaunchedEffect(activeThreadId, openTabIds) {
        val index = openTabIds.indexOf(activeThreadId)
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_tab_bar"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab Scroll Area
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(openTabIds, key = { it }) { tid ->
                        val thread = threadMap[tid]
                        val isActive = tid == activeThreadId
                        val title = thread?.title ?: if (tid == "th_main") "General Chat" else "Chat ${tid.takeLast(4)}"
                        val unread = thread?.unreadCount ?: 0
                        val isPinned = thread?.isPinned == true

                        ChatTabPill(
                            title = title,
                            isActive = isActive,
                            unreadCount = unread,
                            isPinned = isPinned,
                            canClose = openTabIds.size > 1,
                            onClick = { onSelectTab(tid) },
                            onClose = { onCloseTab(tid) }
                        )
                    }
                }

                // Action Controls: New Chat (+) and History (📂)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Quick New Chat Button
                    Surface(
                        color = CyanPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .height(34.dp)
                            .clickable(onClick = onNewChatClick)
                            .testTag("tab_bar_new_chat_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Start New Chat Tab",
                                tint = CyanPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "New",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanPrimary
                            )
                        }
                    }

                    // All Saved Chats / History Drawer Trigger
                    IconButton(
                        onClick = onOpenHistoryClick,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkNavySurfaceVariant.copy(alpha = 0.6f))
                            .testTag("tab_bar_history_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (allThreads.size > openTabIds.size) {
                                    Badge(
                                        containerColor = CyanPrimary,
                                        contentColor = Color.Black
                                    ) {
                                        Text(
                                            text = "${allThreads.size}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open Saved Chat History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = DarkNavyBorder.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun ChatTabPill(
    title: String,
    isActive: Boolean,
    unreadCount: Int,
    isPinned: Boolean,
    canClose: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val backgroundColor = if (isActive) {
        CyanPrimary.copy(alpha = 0.16f)
    } else {
        DarkNavySurface.copy(alpha = 0.85f)
    }

    val borderColor = if (isActive) {
        CyanPrimary
    } else {
        DarkNavyBorder.copy(alpha = 0.7f)
    }

    val textColor = if (isActive) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isActive) 1.2.dp else 0.8.dp, borderColor),
        modifier = Modifier
            .height(34.dp)
            .clickable(onClick = onClick)
            .testTag("tab_pill_${title.take(10)}")
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = if (canClose) 4.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active Glowing Indicator Dot / Sparkle
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CyanPrimary)
                )
                Spacer(modifier = Modifier.width(6.dp))
            } else if (isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = SignalAmber,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Tab Title
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.1.sp
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp)
            )

            // Unread Count Badge
            if (unreadCount > 0 && !isActive) {
                Spacer(modifier = Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SignalAmber)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$unreadCount",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }

            // Close Tab 'x' button (Closes tab view; keeps chat history in Room DB)
            if (canClose) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("tab_close_${title.take(8)}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Tab ($title)",
                        tint = if (isActive) CyanPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
