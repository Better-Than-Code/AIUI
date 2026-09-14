package com.cellular.rpc.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cellular.rpc.engine.AttachmentType
import com.cellular.rpc.engine.AudioPlayerManager
import com.cellular.rpc.engine.MessageAttachment
import com.example.ui.theme.*

/**
 * Native Attachment View inside Chat Bubbles:
 * - High-resolution Image card with rounded corners
 * - Voice Note Player with interactive Play/Pause, live waveform seek scrubber, speed toggle (1.0x / 1.5x / 2.0x), and dual timestamp
 * - File / Document card with size and file type badges
 */
@Composable
fun MessageAttachmentBubble(
    attachment: MessageAttachment,
    isUser: Boolean,
    audioPlayerManager: AudioPlayerManager? = null,
    modifier: Modifier = Modifier
) {
    when (attachment.type) {
        AttachmentType.VISUAL_ANCHOR -> {
            // Visual anchor is an internal carrier transport vehicle; do not display as a user media attachment
        }
        AttachmentType.IMAGE -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isUser) CyanPrimary.copy(alpha = 0.4f) else DarkNavyBorder),
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                AsyncImage(
                    model = attachment.uri,
                    contentDescription = "Message Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        AttachmentType.VOICE_NOTE -> {
            val isPlaying = audioPlayerManager?.playingAttachmentId?.collectAsState()?.value == attachment.id
            val currentPos = audioPlayerManager?.currentPositionMs?.collectAsState()?.value ?: 0L
            val playbackSpeed = audioPlayerManager?.playbackSpeed?.collectAsState()?.value ?: 1.0f

            var waveformWidthPx by remember { mutableStateOf(1) }
            var isScrubbing by remember { mutableStateOf(false) }
            var scrubFraction by remember { mutableStateOf(0f) }

            val effectiveProgress = if (isScrubbing) {
                scrubFraction
            } else if (attachment.durationMs > 0) {
                (currentPos.toFloat() / attachment.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Surface(
                color = if (isUser) CyanPrimaryDark else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isUser) CyanPrimary.copy(alpha = 0.5f) else DarkNavyBorder),
                modifier = modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause Button
                    FilledIconButton(
                        onClick = {
                            if (audioPlayerManager != null) {
                                if (isPlaying) audioPlayerManager.pause() else audioPlayerManager.play(attachment)
                            }
                        },
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isUser) CyanPrimary else MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Waveform + Playback Time + Scrubber
                    Column(modifier = Modifier.weight(1f)) {
                        // Interactive Waveform Seek Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .onSizeChanged { size ->
                                    waveformWidthPx = if (size.width > 0) size.width else 1
                                }
                                .pointerInput(attachment.id, attachment.durationMs) {
                                    detectTapGestures(
                                        onPress = { offset ->
                                            val fraction = (offset.x / waveformWidthPx.toFloat()).coerceIn(0f, 1f)
                                            isScrubbing = true
                                            scrubFraction = fraction
                                            val seekMs = (fraction * attachment.durationMs).toLong()
                                            audioPlayerManager?.seekTo(seekMs)
                                            tryAwaitRelease()
                                            isScrubbing = false
                                        }
                                    )
                                }
                                .pointerInput(attachment.id, attachment.durationMs) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            isScrubbing = true
                                            val fraction = (offset.x / waveformWidthPx.toFloat()).coerceIn(0f, 1f)
                                            scrubFraction = fraction
                                            audioPlayerManager?.seekTo((fraction * attachment.durationMs).toLong())
                                        },
                                        onDragEnd = {
                                            isScrubbing = false
                                        },
                                        onDragCancel = {
                                            isScrubbing = false
                                        },
                                        onDrag = { change, _ ->
                                            val fraction = (change.position.x / waveformWidthPx.toFloat()).coerceIn(0f, 1f)
                                            scrubFraction = fraction
                                            audioPlayerManager?.seekTo((fraction * attachment.durationMs).toLong())
                                        }
                                    )
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val amps = if (attachment.voiceAmplitudes.isNotEmpty()) attachment.voiceAmplitudes else listOf(0.3f, 0.6f, 0.9f, 0.4f, 0.8f, 0.5f, 0.7f, 0.3f, 0.6f, 0.9f, 0.5f, 0.2f, 0.4f, 0.7f, 0.6f, 0.3f, 0.8f, 0.5f, 0.4f, 0.7f, 0.9f, 0.6f, 0.4f, 0.2f)
                                amps.take(24).forEachIndexed { index, amp ->
                                    val barRatio = (index + 0.5f) / amps.size.toFloat()
                                    val isPlayed = (isPlaying || isScrubbing) && barRatio <= effectiveProgress

                                    val barHeight = (amp * 20.dp.value).coerceIn(4f, 20f).dp
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                if (isPlayed) {
                                                    if (isUser) CyanPrimary else SignalGreen
                                                } else {
                                                    if (isUser) Color.White.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                }
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeDisplayMs = if (isScrubbing) {
                                (scrubFraction * attachment.durationMs).toLong()
                            } else if (isPlaying) {
                                currentPos
                            } else 0L

                            val curSec = (activeDisplayMs / 1000) % 60
                            val curMin = (activeDisplayMs / 1000) / 60
                            val totSec = (attachment.durationMs / 1000) % 60
                            val totMin = (attachment.durationMs / 1000) / 60

                            val timeText = if (isPlaying || isScrubbing) {
                                String.format("%02d:%02d / %02d:%02d", curMin, curSec, totMin, totSec)
                            } else {
                                String.format("%02d:%02d", totMin, totSec)
                            }

                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isUser) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Playback Speed Selector (Toggles between 1.0x, 1.5x, 2.0x)
                                if (isPlaying) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isUser) Color.White.copy(alpha = 0.2f) else CyanPrimary.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .clickable {
                                                val nextSpeed = when (playbackSpeed) {
                                                    1.0f -> 1.5f
                                                    1.5f -> 2.0f
                                                    else -> 1.0f
                                                }
                                                audioPlayerManager?.setPlaybackSpeed(nextSpeed)
                                            }
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "${playbackSpeed}x",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isUser) Color.White else CyanPrimary
                                            ),
                                            modifier = Modifier.padding(horizontal = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                Text(
                                    text = "Cellular Audio",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = if (isUser) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        AttachmentType.FILE -> {
            Surface(
                color = if (isUser) CyanPrimaryDark else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isUser) CyanPrimary.copy(alpha = 0.5f) else DarkNavyBorder),
                modifier = modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isUser) CyanPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = "Document",
                            tint = if (isUser) CyanPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachment.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "${attachment.fileSizeBytes / 1024} KB • Document",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isUser) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Download File",
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
