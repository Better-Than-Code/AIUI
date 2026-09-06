package com.cellular.rpc.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cellular.rpc.engine.*
import com.example.ui.theme.*
import java.io.File

/**
 * Native-grade Chat Input Bar with Photo Picker, Document Selector,
 * Live Voice Note Recorder with live waveforms, and Quick AI Tool Triggers.
 */
@Composable
fun FullNativeChatInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    pendingAttachments: List<MessageAttachment>,
    onAddAttachment: (MessageAttachment) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    replyingToText: String?,
    onCancelReply: () -> Unit,
    onSendMessage: (String, List<MessageAttachment>) -> Unit,
    onSelectQuickPrompt: (String, String) -> Unit,
    destinationPhone: String,
    isLoopback: Boolean,
    hasSmsPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAttachmentDrawer by remember { mutableStateOf(false) }

    // Audio Recorder Setup
    val audioRecorder = remember { AudioRecorderManager(context) }
    val isRecording by audioRecorder.isRecording.collectAsState()
    val recordDurationMs by audioRecorder.recordDurationMs.collectAsState()
    val amplitudes by audioRecorder.amplitudes.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            audioRecorder.release()
        }
    }

    // Native Photo Picker (Android Photo Picker - Zero Permission API)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val fileName = "photo_${System.currentTimeMillis()}.jpg"
            onAddAttachment(
                MessageAttachment(
                    id = "img_${System.currentTimeMillis()}_${uri.hashCode()}",
                    type = AttachmentType.IMAGE,
                    uri = uri.toString(),
                    fileName = fileName,
                    mimeType = "image/jpeg"
                )
            )
        }
    }

    // Native Document / File Picker
    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
            onAddAttachment(
                MessageAttachment(
                    id = "doc_${System.currentTimeMillis()}_${uri.hashCode()}",
                    type = AttachmentType.FILE,
                    uri = uri.toString(),
                    fileName = fileName,
                    mimeType = "*/*"
                )
            )
        }
    }

    // Permission launcher for audio recording
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            audioRecorder.startRecording()
        }
    }

    val byteCount = remember(inputText, pendingAttachments) {
        inputText.toByteArray(Charsets.UTF_8).size + (pendingAttachments.size * 20)
    }
    val pduCount = remember(byteCount) { if (byteCount == 0) 1 else ((byteCount + 139) / 140) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // 1. Quoted Reply Banner
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

            // 2. Pending Attachments Carousel Preview
            AnimatedVisibility(
                visible = pendingAttachments.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingAttachments, key = { it.id }) { att ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyBorder),
                            modifier = Modifier.height(64.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                when (att.type) {
                                    AttachmentType.IMAGE -> {
                                        AsyncImage(
                                            model = att.uri,
                                            contentDescription = "Image attachment",
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    AttachmentType.VOICE_NOTE -> {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(CyanPrimary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Voice Note",
                                                tint = CyanPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = "Voice Note",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val seconds = (att.durationMs / 1000) % 60
                                            val minutes = (att.durationMs / 1000) / 60
                                            Text(
                                                text = String.format("%02d:%02d", minutes, seconds),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    AttachmentType.FILE -> {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.InsertDriveFile,
                                                contentDescription = "Document",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = att.fileName.take(12),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onRemoveAttachment(att.id) },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Remove attachment",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Compose Field / Voice Recording Bar
            if (isRecording) {
                // Live Voice Recording Pill with animated waveform
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flashing recording red dot
                        val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.8f,
                            targetValue = 1.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_scale"
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Duration text
                        val seconds = (recordDurationMs / 1000) % 60
                        val minutes = (recordDurationMs / 1000) / 60
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Waveform bars
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            amplitudes.takeLast(20).forEach { amp ->
                                val barHeight = (amp * 24.dp.value).coerceIn(4f, 24f).dp
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(CyanPrimary)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Cancel Button
                        IconButton(
                            onClick = { audioRecorder.stopRecording(discard = true) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Discard Voice Note",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Done / Save Button
                        FilledIconButton(
                            onClick = {
                                val voiceAtt = audioRecorder.stopRecording(discard = false)
                                if (voiceAtt != null) {
                                    onAddAttachment(voiceAtt)
                                }
                            },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = CyanPrimary)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Keep Voice Note",
                                tint = Color.Black
                            )
                        }
                    }
                }
            } else {
                // Regular Text Input + Attachment Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Attach (+) Button
                    IconButton(
                        onClick = { showAttachmentDrawer = !showAttachmentDrawer },
                        modifier = Modifier
                            .size(42.dp)
                            .padding(bottom = 2.dp)
                            .testTag("chat_attach_button")
                    ) {
                        Icon(
                            imageVector = if (showAttachmentDrawer) Icons.Default.Close else Icons.Default.AddCircleOutline,
                            contentDescription = "Attach Options",
                            tint = CyanPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Text Input Field
                    TextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        placeholder = {
                            Text(
                                "Message AI Assistant...",
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

                    val canSend = inputText.isNotBlank() || pendingAttachments.isNotEmpty()

                    if (canSend) {
                        // Send Button
                        FilledIconButton(
                            onClick = {
                                if (!isLoopback && !hasSmsPermissions) {
                                    onRequestPermissions()
                                } else {
                                    onSendMessage(inputText, pendingAttachments)
                                }
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .padding(bottom = 2.dp)
                                .testTag("chat_send_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = CyanPrimary,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Message",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Voice Note Mic Button (Tap or Hold to record)
                        FilledIconButton(
                            onClick = {
                                recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .padding(bottom = 2.dp)
                                .testTag("chat_record_mic_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = CyanPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record Voice Note",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 4. Native Attachment & Tool Trays (Expandable Drawer)
            AnimatedVisibility(
                visible = showAttachmentDrawer,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkNavyBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Native Media & Attachments",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Row 1: Native Media Actions (Photos, Document, Voice Note)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            NativeOptionItem(
                                icon = Icons.Default.PhotoLibrary,
                                label = "Photo Picker",
                                tint = CyanPrimary,
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    showAttachmentDrawer = false
                                }
                            )

                            NativeOptionItem(
                                icon = Icons.Default.AttachFile,
                                label = "Documents",
                                tint = SignalGreen,
                                onClick = {
                                    docPickerLauncher.launch(arrayOf("*/*"))
                                    showAttachmentDrawer = false
                                }
                            )

                            NativeOptionItem(
                                icon = Icons.Default.Mic,
                                label = "Voice Note",
                                tint = SignalAmber,
                                onClick = {
                                    recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    showAttachmentDrawer = false
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DarkNavyBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Instant Cellular AI Actions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Row 2: Cellular AI Structured Tool Triggers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ToolGridIcon(Icons.Default.WbSunny, "Weather") {
                                onSelectQuickPrompt("Please provide current weather in JSON format: {\"type\":\"weather\"}", "weather")
                                showAttachmentDrawer = false
                            }
                            ToolGridIcon(Icons.Default.TrendingUp, "Markets") {
                                onSelectQuickPrompt("Please provide market prices in JSON format: {\"type\":\"market_ticker\"}", "market_ticker")
                                showAttachmentDrawer = false
                            }
                            ToolGridIcon(Icons.Default.Article, "News") {
                                onSelectQuickPrompt("Please provide top news in JSON format: {\"type\":\"news_digest\"}", "news_digest")
                                showAttachmentDrawer = false
                            }
                            ToolGridIcon(Icons.Default.HowToVote, "Poll") {
                                onSelectQuickPrompt("Please create a poll in JSON format: {\"type\":\"poll\"}", "poll")
                                showAttachmentDrawer = false
                            }
                        }
                    }
                }
            }

            // 5. Status Telemetry
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLoopback) "⚡ Local Loopback Gateway" else "📡 Cellular: $destinationPhone",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isLoopback) SignalAmber else SignalGreen
                )
                Text(
                    text = "$byteCount/140B • $pduCount PDU" + if (pendingAttachments.isNotEmpty()) " (${pendingAttachments.size} media)" else "",
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
private fun NativeOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
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
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(CyanPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = CyanPrimary,
                modifier = Modifier.size(18.dp)
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
