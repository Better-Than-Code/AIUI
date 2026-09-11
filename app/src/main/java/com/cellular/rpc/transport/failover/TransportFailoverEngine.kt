package com.cellular.rpc.transport.failover

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.cellular.rpc.engine.AttachmentType
import com.cellular.rpc.engine.MessageAttachment
import com.cellular.rpc.domain.protocol.Frame
import com.cellular.rpc.transport.handler.CellularTransportType
import com.cellular.rpc.transport.handler.InboundCellularMessage
import java.io.File
import java.io.FileOutputStream

/**
 * Epic 2: Inbound Transport Failover Protocol Engine
 *
 * Enforces:
 * 1. Strict 256-byte ceiling on plain SMS delivery (<= 2 segments).
 * 2. Automatic promotion of payloads > 256B to single-container MMS bundles.
 * 3. Generation and caching of the carrier-compliant 32x32 PNG AIUI visual anchor.
 * 4. Strict single-container SMIL / body encapsulation (companion SMS preambles prohibited).
 */
object TransportFailoverEngine {

    private const val TAG = "TransportFailover"

    /** Maximum allowed byte length for plain SMS delivery before triggering carrier throttling. */
    const val SMS_PAYLOAD_CEILING_BYTES = 256

    /** Maximum allowed concatenated SMS segments before carrier drops are observed. */
    const val SMS_MAX_SAFE_SEGMENTS = 2

    /** Standard visual anchor filename required for carrier MMSC PDN routing. */
    const val VISUAL_ANCHOR_NAME = "aiui_mms_anchor.png"
    const val VISUAL_ANCHOR_WIDTH = 32
    const val VISUAL_ANCHOR_HEIGHT = 32

    /**
     * Transport promotion decision outcome.
     */
    sealed interface TransportDecision {
        data class PlainSms(val byteCount: Int, val segmentCount: Int) : TransportDecision
        data class PromoteToMms(val byteCount: Int, val segmentCountIfSms: Int, val reason: String) : TransportDecision
    }

    /**
     * Evaluates whether a raw payload should travel over plain SMS or be promoted to an MMS bundle.
     */
    fun evaluateTransport(payload: String): TransportDecision {
        val byteCount = payload.toByteArray(Charsets.UTF_8).size
        val segmentCount = ((byteCount + 139) / 140).coerceAtLeast(1)

        return if (shouldPromoteToMms(byteCount)) {
            TransportDecision.PromoteToMms(
                byteCount = byteCount,
                segmentCountIfSms = segmentCount,
                reason = "Payload ($byteCount bytes, $segmentCount segments) exceeds safe $SMS_PAYLOAD_CEILING_BYTES-byte ceiling. Promoted to MMS bundle with 32x32 visual anchor."
            )
        } else {
            TransportDecision.PlainSms(
                byteCount = byteCount,
                segmentCount = segmentCount
            )
        }
    }

    /**
     * Returns true if payload size strictly exceeds the 256B ceiling.
     */
    fun shouldPromoteToMms(payloadByteCount: Int): Boolean {
        return payloadByteCount > SMS_PAYLOAD_CEILING_BYTES
    }

    /**
     * Generates or retrieves the cached 32x32 PNG visual anchor file.
     * Required by carrier MMSCs to route via MMS PDN rather than SMS PDU gateways.
     */
    @Synchronized
    fun getOrCreateVisualAnchorFile(context: Context): File {
        val anchorDir = File(context.filesDir, "transport_anchors").apply { mkdirs() }
        val anchorFile = File(anchorDir, VISUAL_ANCHOR_NAME)

        if (!anchorFile.exists() || anchorFile.length() == 0L) {
            try {
                val bitmap = Bitmap.createBitmap(VISUAL_ANCHOR_WIDTH, VISUAL_ANCHOR_HEIGHT, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                // Background: Sleek Dark Navy rounded rectangle
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#0A1118")
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(RectF(0f, 0f, 32f, 32f), 8f, 8f, bgPaint)

                // Border: Subtle Cyan Primary border
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#00E5FF")
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }
                canvas.drawRoundRect(RectF(1f, 1f, 31f, 31f), 7f, 7f, borderPaint)

                // Visual Core Dot (Glowing Cyan Accent)
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#00E5FF")
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(16f, 16f, 6f, dotPaint)

                // Save to file
                FileOutputStream(anchorFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                Log.i(TAG, "Generated 32x32 AIUI MMS visual anchor at ${anchorFile.absolutePath} (${anchorFile.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating visual anchor bitmap: ${e.message}", e)
            }
        }
        return anchorFile
    }

    /**
     * Resolves a shareable content:// URI via FileProvider for carrier MMS transmission.
     */
    fun getVisualAnchorUri(context: Context): Uri {
        val file = getOrCreateVisualAnchorFile(context)
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            // Fallback for Robolectric or non-activity contexts
            Uri.fromFile(file)
        }
    }

    /**
     * Generates standard carrier-compliant SMIL document encapsulating the visual anchor
     * and the text payload into a single atomic MMS bundle.
     */
    fun generateSingleContainerSmil(
        textPartName: String = "text_0.txt",
        imagePartName: String = VISUAL_ANCHOR_NAME
    ): String {
        return """
            <smil xmlns="http://www.w3.org/2000/SMIL20/CR/Language">
              <head>
                <layout>
                  <root-layout width="32" height="32"/>
                  <region id="Image" width="32" height="32" fit="meet"/>
                  <region id="Text" top="32"/>
                </layout>
              </head>
              <body>
                <par dur="5000ms">
                  <img src="$imagePartName" region="Image"/>
                  <text src="$textPartName" region="Text"/>
                </par>
              </body>
            </smil>
        """.trimIndent()
    }

    /**
     * Checks if an attachment is the 32x32 visual anchor used purely for carrier transport routing.
     */
    fun isVisualAnchor(attachment: MessageAttachment?): Boolean {
        if (attachment == null) return false
        if (attachment.type == AttachmentType.VISUAL_ANCHOR) return true
        val name = attachment.fileName.lowercase()
        return name.contains("aiui_mms_anchor") ||
                name.contains("transport_anchor") ||
                name.contains("visual_anchor") ||
                (name.endsWith(".png") && attachment.fileSizeBytes in 100..4096)
    }

    /**
     * Checks if a part name or size matches the visual anchor.
     */
    fun isVisualAnchor(name: String?, fileSizeBytes: Long = 0): Boolean {
        val lower = name?.lowercase() ?: ""
        return lower.contains("aiui_mms_anchor") ||
                lower.contains("transport_anchor") ||
                lower.contains("visual_anchor") ||
                (lower.endsWith(".png") && fileSizeBytes in 100..4096)
    }

    /**
     * Creates an InboundCellularMessage promoted to MMS_WAP_PUSH container with the 32x32 anchor.
     */
    fun createPromotedMmsMessage(
        context: Context,
        senderAddress: String,
        payloadText: String,
        frame: Frame? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): InboundCellularMessage {
        val anchorFile = getOrCreateVisualAnchorFile(context)
        val anchorAttachment = MessageAttachment(
            id = "anchor_${timestampMs}",
            type = AttachmentType.VISUAL_ANCHOR,
            uri = Uri.fromFile(anchorFile).toString(),
            fileName = VISUAL_ANCHOR_NAME,
            fileSizeBytes = anchorFile.length(),
            mimeType = "image/png"
        )

        return InboundCellularMessage(
            transportType = CellularTransportType.MMS_WAP_PUSH,
            senderAddress = senderAddress,
            rawText = payloadText,
            rawBytes = frame?.payload ?: payloadText.toByteArray(Charsets.UTF_8),
            frame = frame,
            timestampMs = timestampMs,
            attachment = anchorAttachment
        )
    }
}
