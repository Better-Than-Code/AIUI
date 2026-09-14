package com.cellular.rpc.transport.service

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.cellular.rpc.engine.AttachmentType
import com.cellular.rpc.engine.MessageAttachment
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream

/**
 * Robust MMS Part extraction engine handling carrier download latencies, sender resolution,
 * and binary stream extraction from the Android telephony provider.
 */
object HardenedMmsParser {

    private const val TAG = "HardenedMmsParser"

    data class MmsResult(
        val sender: String,
        val textBody: String,
        val attachment: MessageAttachment?,
        val rawSmil: String? = null
    )

    // 4-stage adaptive retry backoff: 0ms (immediate), 1200ms, 2500ms, 4500ms
    // Accommodates asynchronous carrier MMSC downloads where headers (PDU) land before parts in Telephony SQLite
    val RETRY_BACKOFF_MS = listOf(0L, 1200L, 2500L, 4500L)

    /**
     * Attempts extraction with 4-stage backoff retries (0ms, 1200ms, 2500ms, 4500ms) to ensure
     * that all asynchronous MMSC parts have finished committing to SQLite.
     */
    suspend fun extractMmsPayloadWithRetry(context: Context, mmsId: Long, maxRetries: Int = 4): MmsResult {
        var result = MmsResult(sender = "UNKNOWN", textBody = "", attachment = null, rawSmil = null)

        for (attempt in 0 until maxRetries) {
            val delayMs = RETRY_BACKOFF_MS.getOrElse(attempt) { 4500L }
            if (attempt > 0 && delayMs > 0L) {
                delay(delayMs)
            }

            val sender = getMmsSender(context, mmsId)
            val extracted = extractPartsDetailed(context, mmsId)

            if (extracted.textBody.isNotBlank() || extracted.attachment != null) {
                return MmsResult(
                    sender = sender,
                    textBody = extracted.textBody,
                    attachment = extracted.attachment,
                    rawSmil = extracted.rawSmil
                )
            }
            Log.d(TAG, "MMS ID $mmsId parts not ready on attempt ${attempt + 1}/$maxRetries (waited ${delayMs}ms). Retrying...")
        }

        return result
    }

    fun getMmsSender(context: Context, mmsId: Long): String {
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        try {
            val cursor = context.contentResolver.query(
                addrUri,
                arrayOf("address", "type"),
                null,
                null,
                null
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val type = it.getInt(1)
                    val address = it.getString(0) ?: ""
                    // Type 137 is standard PduHeaders.FROM in telephony provider
                    if (type == 137 && address.isNotBlank() && !address.contains("insert-address-token")) {
                        return address
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving MMS sender for ID $mmsId: ${e.message}")
        }
        return "UNKNOWN_MMS_SENDER"
    }

    data class ExtractedParts(
        val textBody: String,
        val attachment: MessageAttachment?,
        val rawSmil: String? = null
    )

    fun extractParts(context: Context, mmsId: Long): Pair<String, MessageAttachment?> {
        val detailed = extractPartsDetailed(context, mmsId)
        return Pair(detailed.textBody, detailed.attachment)
    }

    /**
     * Generates a normalized list of 24 waveform amplitude bars (0.15f to 1.0f)
     * sampled from the raw audio bytes for native waveform playback rendering.
     */
    fun generateAudioWaveform(file: File, sampleCount: Int = 24): List<Float> {
        val defaultAmps = listOf(0.3f, 0.6f, 0.9f, 0.4f, 0.8f, 0.5f, 0.7f, 0.3f, 0.6f, 0.9f, 0.5f, 0.2f,
                                 0.4f, 0.7f, 0.8f, 0.5f, 0.9f, 0.6f, 0.4f, 0.8f, 0.6f, 0.3f, 0.5f, 0.2f)
        if (!file.exists() || file.length() < 10) {
            return defaultAmps
        }
        try {
            val bytes = file.readBytes()
            if (bytes.size >= sampleCount) {
                val step = (bytes.size / sampleCount).coerceAtLeast(1)
                return (0 until sampleCount).map { i ->
                    val byteVal = Math.abs(bytes[(i * step).coerceAtMost(bytes.size - 1)].toInt())
                    (byteVal / 128f).coerceIn(0.15f, 1.0f)
                }
            }
        } catch (ignored: Exception) {}
        return defaultAmps
    }

    /**
     * Epic 3 & INC-05: MMS Part Table Latency Retry & SMIL Body Accumulator with Audio Voice Note Ingestion.
     * Queries content://mms/{mmsId}/part, extracts all text parts (with content URI stream fallback),
     * accumulates multi-region / multi-par SMIL body content, resolves binary media (prioritizing audio voice notes
     * and ignoring 32x32 visual transport anchors), and populates duration/waveform metadata.
     */
    fun extractPartsDetailed(context: Context, mmsId: Long): ExtractedParts {
        val textParts = mutableListOf<String>()
        var smilRawText = ""
        var smilExtractedText = ""
        val candidateAttachments = mutableListOf<MessageAttachment>()
        val partUri = Uri.parse("content://mms/$mmsId/part")

        try {
            val cursor = context.contentResolver.query(
                partUri,
                arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.NAME, Telephony.Mms.Part.TEXT),
                null,
                null,
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val partId = it.getLong(0)
                    val rawContentType = it.getString(1) ?: ""
                    val lowerContentType = rawContentType.lowercase().trim()
                    val name = it.getString(2) ?: "mms_media_$partId"
                    val inlineText = it.getString(3)

                    if (lowerContentType.startsWith("text/") && !lowerContentType.contains("smil")) {
                        if (!inlineText.isNullOrBlank()) {
                            textParts.add(inlineText)
                        } else {
                            // Fallback: read text stream from part URI
                            val streamUri = Uri.parse("content://mms/part/$partId")
                            try {
                                val streamContent = context.contentResolver.openInputStream(streamUri)?.bufferedReader()?.use { r -> r.readText() }
                                if (!streamContent.isNullOrBlank()) {
                                    textParts.add(streamContent)
                                }
                            } catch (ignored: Exception) {}
                        }
                    } else if (lowerContentType == "application/smil" || lowerContentType.contains("smil")) {
                        val streamUri = Uri.parse("content://mms/part/$partId")
                        try {
                            val smilContent = if (!inlineText.isNullOrBlank()) {
                                inlineText
                            } else {
                                context.contentResolver.openInputStream(streamUri)?.bufferedReader()?.use { r -> r.readText() }
                            }
                            if (!smilContent.isNullOrBlank()) {
                                smilRawText = smilContent
                                smilExtractedText = extractTextFromSmil(smilContent)
                            }
                        } catch (ignored: Exception) {}
                    } else {
                        val isAudio = lowerContentType.startsWith("audio/") ||
                                lowerContentType == "application/ogg" ||
                                (lowerContentType == "application/octet-stream" && (
                                    name.endsWith(".amr", ignoreCase = true) ||
                                    name.endsWith(".3gp", ignoreCase = true) ||
                                    name.endsWith(".m4a", ignoreCase = true) ||
                                    name.endsWith(".aac", ignoreCase = true) ||
                                    name.endsWith(".mp3", ignoreCase = true) ||
                                    name.endsWith(".ogg", ignoreCase = true) ||
                                    name.endsWith(".wav", ignoreCase = true)
                                ))

                        val isMedia = isAudio || lowerContentType.startsWith("image/") || lowerContentType.startsWith("video/") || lowerContentType == "application/octet-stream"

                        if (isMedia) {
                            val mediaUri = Uri.parse("content://mms/part/$partId")
                            val ext = when {
                                lowerContentType.contains("amr") || name.endsWith(".amr", ignoreCase = true) -> "amr"
                                lowerContentType.contains("3gpp") || lowerContentType.contains("3gp") || name.endsWith(".3gp", ignoreCase = true) -> "3gp"
                                lowerContentType.contains("mp4") || lowerContentType.contains("m4a") || name.endsWith(".m4a", ignoreCase = true) || name.endsWith(".mp4", ignoreCase = true) -> "m4a"
                                lowerContentType.contains("aac") || name.endsWith(".aac", ignoreCase = true) -> "aac"
                                lowerContentType.contains("ogg") || name.endsWith(".ogg", ignoreCase = true) -> "ogg"
                                lowerContentType.contains("wav") || name.endsWith(".wav", ignoreCase = true) -> "wav"
                                lowerContentType.contains("mpeg") || lowerContentType.contains("mp3") || name.endsWith(".mp3", ignoreCase = true) -> "mp3"
                                isAudio -> "amr"
                                lowerContentType.contains("png") -> "png"
                                lowerContentType.contains("gif") -> "gif"
                                else -> "jpg"
                            }
                            val cacheDir = File(context.cacheDir, "mms_media").apply { mkdirs() }
                            val localFile = File(cacheDir, "mms_${mmsId}_$partId.$ext")

                            try {
                                context.contentResolver.openInputStream(mediaUri)?.use { input ->
                                    FileOutputStream(localFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }

                                val isAnchor = !isAudio && com.cellular.rpc.transport.failover.TransportFailoverEngine.isVisualAnchor(name, localFile.length())
                                val attType = when {
                                    isAudio -> AttachmentType.VOICE_NOTE
                                    isAnchor -> AttachmentType.VISUAL_ANCHOR
                                    lowerContentType.startsWith("image/") -> AttachmentType.IMAGE
                                    else -> AttachmentType.FILE
                                }

                                var durationMs = 0L
                                var waveforms = emptyList<Float>()

                                if (isAudio) {
                                    try {
                                        val retriever = android.media.MediaMetadataRetriever()
                                        retriever.setDataSource(localFile.absolutePath)
                                        val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                        durationMs = durStr?.toLongOrNull() ?: 0L
                                        retriever.release()
                                    } catch (e: Exception) {
                                        Log.d(TAG, "Could not extract audio duration from ${localFile.name}: ${e.message}")
                                    }

                                    if (durationMs <= 0L && ext == "amr" && localFile.length() > 6) {
                                        // Standard AMR-NB is ~1600 bytes per second
                                        val approxSec = (localFile.length() - 6) / 1600.0
                                        durationMs = (approxSec * 1000).toLong().coerceIn(1000L, 300000L)
                                    }

                                    waveforms = generateAudioWaveform(localFile, 24)
                                }

                                val messageAttachment = MessageAttachment(
                                    id = "mms_${mmsId}_$partId",
                                    type = attType,
                                    uri = Uri.fromFile(localFile).toString(),
                                    fileName = if (name.isNotBlank() && name != "mms_media_$partId") name else (if (isAudio) "VoiceNote_${mmsId}_$partId.$ext" else localFile.name),
                                    fileSizeBytes = localFile.length(),
                                    mimeType = if (isAudio && !lowerContentType.startsWith("audio/")) "audio/$ext" else rawContentType,
                                    durationMs = durationMs,
                                    voiceAmplitudes = waveforms
                                )
                                candidateAttachments.add(messageAttachment)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed copying MMS media stream: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying MMS parts: ${e.message}")
        }

        // Reconcile text body: Prioritize concatenated text parts; if absent, use accumulated SMIL body
        val combinedText = when {
            textParts.isNotEmpty() -> textParts.joinToString("")
            smilExtractedText.isNotBlank() -> smilExtractedText
            else -> ""
        }

        // Select the primary candidate attachment:
        // Prioritize VOICE_NOTE > real IMAGE/FILE > non-anchor media
        val primaryCandidate = candidateAttachments.firstOrNull { it.type == AttachmentType.VOICE_NOTE }
            ?: candidateAttachments.firstOrNull { it.type != AttachmentType.VISUAL_ANCHOR && !com.cellular.rpc.transport.failover.TransportFailoverEngine.isVisualAnchor(it) }
            ?: candidateAttachments.firstOrNull()

        // Section 2.1 & INC-22: Media Intent Envelope & Anchor Suppression
        val resolvedAttachment = processMediaIntentAndAnchors(
            context = context,
            mmsId = mmsId,
            rawText = combinedText,
            rawSmil = smilRawText,
            candidate = primaryCandidate
        )

        return ExtractedParts(
            textBody = combinedText,
            attachment = resolvedAttachment,
            rawSmil = smilRawText.ifBlank { null }
        )
    }

    /**
     * Section 2.1 & INC-22: Media Intent Routing & 32x32 Visual Anchor Suppression.
     * Routes explicit media_intent payloads (widget_asset, transport_dummy, inline_user, voice_note)
     * and guarantees carrier bypass anchors are completely stripped from chat feed.
     */
    private fun processMediaIntentAndAnchors(
        context: Context,
        mmsId: Long,
        rawText: String,
        rawSmil: String,
        candidate: MessageAttachment?
    ): MessageAttachment? {
        if (candidate == null) return null

        val localFile = try {
            val uri = Uri.parse(candidate.uri)
            if (uri.scheme == "file" || uri.scheme == null) File(uri.path ?: "") else null
        } catch (e: Exception) {
            null
        }

        // 1. Detect and suppress 32x32 carrier bypass anchors via filename, size, or bitmap bounds
        val isAnchor = candidate.type != AttachmentType.VOICE_NOTE &&
                (candidate.type == AttachmentType.VISUAL_ANCHOR ||
                com.cellular.rpc.transport.failover.TransportFailoverEngine.isVisualAnchor(candidate) ||
                (localFile != null && com.cellular.rpc.transport.failover.TransportFailoverEngine.isVisualAnchor(localFile)))

        if (isAnchor) {
            Log.d(TAG, "Suppressed 32x32 carrier transport anchor for MMS ID $mmsId (${candidate.fileName})")
            try { localFile?.delete() } catch (ignored: Exception) {}
            return null
        }

        // 2. Extract media_intent from SMIL or text payload
        val intentRegex = """["']?media_intent["']?\s*[:=]\s*["']?([a-zA-Z0-9_-]+)["']?""".toRegex(RegexOption.IGNORE_CASE)
        val matchedIntent = intentRegex.find(rawSmil)?.groupValues?.getOrNull(1)?.lowercase()
            ?: intentRegex.find(rawText)?.groupValues?.getOrNull(1)?.lowercase()

        if (matchedIntent != null) {
            Log.i(TAG, "MMS ID $mmsId matched media_intent='$matchedIntent'")
            return when (matchedIntent) {
                "widget_asset" -> {
                    // Binary routes to internal widget cache disk; hidden from chat feed
                    try {
                        if (localFile != null && localFile.exists()) {
                            val widgetCacheDir = File(context.filesDir, "widget_assets").apply { mkdirs() }
                            val targetFile = File(widgetCacheDir, "asset_${mmsId}_${candidate.fileName}")
                            localFile.copyTo(targetFile, overwrite = true)
                            Log.i(TAG, "Routed widget_asset to internal cache: ${targetFile.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed caching widget asset: ${e.message}")
                    }
                    null // Strip from chat feed bubble
                }
                "transport_dummy" -> {
                    try { localFile?.delete() } catch (ignored: Exception) {}
                    null // Suppress transport dummy completely
                }
                "inline_user" -> {
                    // Explicit media requested by user: photo, chart, document
                    candidate.copy(type = if (candidate.mimeType?.startsWith("image/") == true) AttachmentType.IMAGE else AttachmentType.FILE)
                }
                "voice_note" -> {
                    candidate.copy(type = AttachmentType.VOICE_NOTE)
                }
                else -> candidate
            }
        }

        // 3. Default Policy: Encapsulated protocol MMS without explicit intent suppresses media by default
        val isProtocolPayload = rawText.contains("```") ||
                rawText.contains("REQ:GET") ||
                rawText.contains("RES:") ||
                rawText.contains("PALLY:") ||
                rawText.contains("\"action\":") ||
                rawText.contains("\"node\":") ||
                rawText.trimStart().startsWith("{")

        if (isProtocolPayload && candidate.mimeType?.startsWith("image/") == true) {
            Log.d(TAG, "Protocol MMS without explicit media_intent: suppressing media by default (${candidate.fileName})")
            return null
        }

        return candidate
    }

    /**
     * Extracts and accumulates inline text content encapsulated within SMIL markup.
     * Supports:
     * 1. Multi-part text nodes: <text ...>body</text> across sequential or parallel <par> containers
     * 2. Direct attribute texts: text="..." or alt="..."
     * 3. XML entity unescaping (&quot;, &amp;, &lt;, &gt;, &#39;, &apos;)
     */
    fun extractTextFromSmil(smil: String): String {
        val accumulated = mutableListOf<String>()

        // 1. Extract content within <text>...</text> tags
        val textTagRegex = Regex("""<text[^>]*>([\s\S]*?)<\/text>""", RegexOption.IGNORE_CASE)
        val tagMatches = textTagRegex.findAll(smil)
            .map { unescapeXmlEntities(it.groupValues[1].trim()) }
            .filter { it.isNotEmpty() }
            .toList()
        accumulated.addAll(tagMatches)

        // 2. If tag bodies are empty, scan for text="..." or alt="..." attributes in text/img tags
        if (accumulated.isEmpty()) {
            val altRegex = Regex("""(?:alt|text)="([^"]+)"""", RegexOption.IGNORE_CASE)
            val altMatches = altRegex.findAll(smil)
                .map { unescapeXmlEntities(it.groupValues[1].trim()) }
                .filter { it.isNotEmpty() }
                .toList()
            accumulated.addAll(altMatches)
        }

        return accumulated.joinToString("\n")
    }

    /**
     * Unescapes standard XML/HTML entities from SMIL markup strings.
     * Decodes double-escaped entities safely by running up to 2 passes.
     */
    fun unescapeXmlEntities(raw: String): String {
        if (!raw.contains('&')) return raw
        var current = raw
        for (i in 0 until 2) {
            if (!current.contains('&')) break
            val next = current
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
            if (next == current) break
            current = next
        }
        return current
    }
}
