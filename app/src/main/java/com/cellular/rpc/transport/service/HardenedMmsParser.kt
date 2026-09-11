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
     * Epic 3: MMS Part Table Latency Retry & SMIL Body Accumulator.
     * Queries content://mms/{mmsId}/part, extracts all text parts (with content URI stream fallback),
     * accumulates multi-region / multi-par SMIL body content, resolves MIME attachments (ignoring 32x32 visual anchors),
     * and performs unescaping and text reassembly.
     */
    fun extractPartsDetailed(context: Context, mmsId: Long): ExtractedParts {
        val textParts = mutableListOf<String>()
        var smilRawText = ""
        var smilExtractedText = ""
        var attachment: MessageAttachment? = null
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
                    val contentType = it.getString(1) ?: ""
                    val name = it.getString(2) ?: "mms_media_$partId"
                    val inlineText = it.getString(3)

                    if (contentType.startsWith("text/") && !contentType.contains("smil")) {
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
                    } else if (contentType == "application/smil" || contentType.contains("smil")) {
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
                    } else if (contentType.startsWith("image/") || contentType.startsWith("video/") || contentType.startsWith("audio/")) {
                        val mediaUri = Uri.parse("content://mms/part/$partId")
                        val ext = when {
                            contentType.contains("png") -> "png"
                            contentType.contains("gif") -> "gif"
                            contentType.contains("audio") || contentType.contains("amr") -> "amr"
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

                            val isAnchor = com.cellular.rpc.transport.failover.TransportFailoverEngine.isVisualAnchor(name, localFile.length())
                            val attType = when {
                                contentType.startsWith("audio/") -> AttachmentType.VOICE_NOTE
                                isAnchor -> AttachmentType.VISUAL_ANCHOR
                                contentType.startsWith("image/") -> AttachmentType.IMAGE
                                else -> AttachmentType.FILE
                            }

                            attachment = MessageAttachment(
                                id = "mms_${mmsId}_$partId",
                                type = attType,
                                uri = Uri.fromFile(localFile).toString(),
                                fileName = name.ifBlank { localFile.name },
                                fileSizeBytes = localFile.length(),
                                mimeType = contentType
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed copying MMS media stream: ${e.message}")
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

        return ExtractedParts(
            textBody = combinedText,
            attachment = attachment,
            rawSmil = smilRawText.ifBlank { null }
        )
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
