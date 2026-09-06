package com.cellular.rpc.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
 * - Voice Note Player with interactive Play/Pause, live progress scrubbing, and waveforms
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

                    // Waveform + Playback Time
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val amps = if (attachment.voiceAmplitudes.isNotEmpty()) attachment.voiceAmplitudes else listOf(0.3f, 0.6f, 0.9f, 0.4f, 0.8f, 0.5f, 0.7f, 0.3f, 0.6f, 0.9f, 0.5f, 0.2f)
                            amps.take(24).forEachIndexed { index, amp ->
                                val progressRatio = if (attachment.durationMs > 0) currentPos.toFloat() / attachment.durationMs.toFloat() else 0f
                                val barRatio = index.toFloat() / amps.size.toFloat()
                                val isPlayed = isPlaying && barRatio <= progressRatio

                                val barHeight = (amp * 20.dp.value).coerceIn(4f, 20f).dp
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (isPlayed) CyanPrimary else (if (isUser) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val durationDisplay = if (isPlaying) currentPos else attachment.durationMs
                            val seconds = (durationDisplay / 1000) % 60
                            val minutes = (durationDisplay / 1000) / 60
                            Text(
                                text = String.format("%02d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isUser) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Voice Note • Cellular Audio",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = if (isUser) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
