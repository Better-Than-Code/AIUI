package com.cellular.rpc.ui.chat.audio

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.roundToInt

/**
 * Section 2.11: Interactive Live Audio Voice Note Recorder with Real-Time Waveform & Cancellation Scrubber.
 *
 * Features:
 * - Dynamic 50-60Hz audio amplitude level waveform rendering with glowing Cyan accents.
 * - Live recording time counter (MM:SS) with pulsing recording LED dot.
 * - Horizontal slide-to-cancel gesture scrubber with tactile haptic threshold feedback.
 * - Hands-free lock-to-record toggle allowing longer voice notes without holding.
 * - Discard / Send controls with carrier MMS auto-compression readiness.
 */
@Composable
fun LiveVoiceRecordingBar(
    isRecording: Boolean,
    isLocked: Boolean,
    recordDurationMs: Long,
    amplitudes: List<Float>,
    onCancel: () -> Unit,
    onSendOrKeep: () -> Unit,
    onToggleLock: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val cancelThreshold = -120f // drag left 120dp / px threshold to cancel

    // Reset drag offset when recording state changes
    LaunchedEffect(isRecording) {
        dragOffsetX = 0f
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = if (isLocked) CyanPrimary else MaterialTheme.colorScheme.error
        ),
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("live_voice_recording_bar")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Pulsing Record Indicator LED
                val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(550, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_scale"
                )

                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(12.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(if (isLocked) CyanPrimary else MaterialTheme.colorScheme.error)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 2. Elapsed Duration Counter
                val seconds = (recordDurationMs / 1000) % 60
                val minutes = (recordDurationMs / 1000) / 60
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = if (isLocked) CyanPrimary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("recording_duration_label")
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 3. Real-Time Dynamic Waveform Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (dragOffsetX < -30f) {
                        // Slide-to-cancel visual feedback during drag
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Release to cancel",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        // 50-60Hz dynamic amplitude wave bars
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                        ) {
                            val barWidth = 3.dp.toPx()
                            val spacing = 2.5.dp.toPx()
                            val totalSlot = barWidth + spacing
                            val count = (size.width / totalSlot).toInt().coerceAtLeast(1)

                            val displayAmps = if (amplitudes.size >= count) {
                                amplitudes.takeLast(count)
                            } else {
                                List(count - amplitudes.size) { 0.08f } + amplitudes
                            }

                            displayAmps.forEachIndexed { index, amp ->
                                val x = index * totalSlot
                                val barH = (amp * size.height).coerceIn(4f, size.height)
                                val top = (size.height - barH) / 2f

                                val barColor = if (isLocked) CyanPrimary else Color(0xFF38BDF8)
                                drawRoundRect(
                                    color = barColor.copy(alpha = (0.35f + amp * 0.65f).coerceIn(0.2f, 1.0f)),
                                    topLeft = Offset(x, top),
                                    size = Size(barWidth, barH),
                                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 4. Lock / Unlock Toggle Button (Hands-free recording)
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onToggleLock(!isLocked)
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("record_lock_toggle")
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (isLocked) "Locked Recording" else "Lock Recording",
                        tint = if (isLocked) CyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 5. Discard / Trash Button
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        onCancel()
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("record_discard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Discard Voice Note",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 6. Send / Commit Voice Attachment Button
                FilledIconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onSendOrKeep()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("record_send_keep_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Voice Note",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Slide to Cancel Gesture Scrubber Layer (Active when not locked)
            if (!isLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX = (dragOffsetX + dragAmount).coerceAtMost(0f)
                                    if (dragOffsetX < cancelThreshold) {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    }
                                },
                                onDragEnd = {
                                    if (dragOffsetX < cancelThreshold) {
                                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                        onCancel()
                                    }
                                    dragOffsetX = 0f
                                },
                                onDragCancel = {
                                    dragOffsetX = 0f
                                }
                            )
                        }
                )
            }
        }
    }
}
