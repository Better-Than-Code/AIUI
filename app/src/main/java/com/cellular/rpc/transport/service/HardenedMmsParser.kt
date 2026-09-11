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
        val attachment: MessageAttachment?
    )

    /**
     * Attempts extraction with 3-stage backoff retries (0ms, 1500ms, 3500ms) to ensure
     * that all asynchronous MMSC parts have finished committing to SQLite.
     */
    suspend fun extractMmsPayloadWithRetry(context: Context, mmsId: Long, maxRetries: Int = 3): MmsResult {
        val retryDelays = listOf(0L, 1500L, 3500L)
        var result = MmsResult(sender = "UNKNOWN", textBody = "", attachment = null)

        for (attempt in 0 until maxRetries) {
            if (attempt > 0) delay(retryDelays[attempt])

            val sender = getMmsSender(context, mmsId)
            val (body, attachment) = extractParts(context, mmsId)

            if (body.isNotBlank() || attachment != null) {
                return MmsResult(sender, body, attachment)
            }
            Log.d(TAG, "MMS ID $mmsId parts not ready on attempt ${attempt + 1}. Retrying...")
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

    fun extractParts(context: Context, mmsId: Long): Pair<String, MessageAttachment?> {
        var textBody = ""
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

                    if (contentType == "text/plain") {
                        if (!inlineText.isNullOrBlank()) {
                            textBody = inlineText
                        } else {
                            // Fallback: read text stream from part URI
                            val streamUri = Uri.parse("content://mms/part/$partId")
                            try {
                                val streamContent = context.contentResolver.openInputStream(streamUri)?.bufferedReader()?.use { r -> r.readText() }
                                if (!streamContent.isNullOrBlank()) {
                                    textBody = streamContent
                                }
                            } catch (ignored: Exception) {}
                        }
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

                            val attType = when {
                                contentType.startsWith("audio/") -> AttachmentType.VOICE_NOTE
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

        return Pair(textBody, attachment)
    }
}
