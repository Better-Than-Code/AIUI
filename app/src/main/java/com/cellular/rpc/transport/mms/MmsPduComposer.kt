package com.cellular.rpc.transport.mms

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * INC-07: Standards-Compliant WAP-209 / OMA MMS 1.2 PDU Composer
 *
 * Encapsulates outbound payloads into binary M-Send.req (0x80) PDUs
 * for direct background cellular transmission via SmsManager.sendMultimediaMessage().
 * Eliminates the need for external ACTION_SEND intents or system app choosers.
 */
object MmsPduComposer {

    private const val TAG = "MmsPduComposer"

    // WAP-209 Field Constants
    private const val HDR_MESSAGE_TYPE = 0x8C
    private const val MESSAGE_TYPE_SEND_REQ = 0x80
    private const val HDR_TRANSACTION_ID = 0x98
    private const val HDR_MMS_VERSION = 0x8D
    private const val MMS_VERSION_1_2 = 0x92
    private const val HDR_DATE = 0x85
    private const val HDR_FROM = 0x89
    private const val HDR_TO = 0x97
    private const val HDR_SUBJECT = 0x96
    private const val HDR_CONTENT_TYPE = 0x84

    // Content Type Assignment
    private const val CT_MULTIPART_RELATED = "application/vnd.wap.multipart.related"

    // Part Headers
    private const val PART_CONTENT_TYPE = 0x84
    private const val PART_CONTENT_LOCATION = 0x8E
    private const val PART_CONTENT_ID = 0xC0

    data class MmsPart(
        val contentType: String,
        val contentLocation: String,
        val contentId: String,
        val data: ByteArray
    )

    /**
     * Builds a binary M-Send.req PDU byte array.
     */
    fun composeSendReqPdu(
        recipientNumber: String,
        parts: List<MmsPart>,
        transactionId: String = "TXN_${System.currentTimeMillis()}"
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Header: X-Mms-Message-Type: m-send-req (0x80)
        out.write(HDR_MESSAGE_TYPE)
        out.write(MESSAGE_TYPE_SEND_REQ)

        // 2. Header: X-Mms-Transaction-ID
        out.write(HDR_TRANSACTION_ID)
        out.write(transactionId.toByteArray(StandardCharsets.US_ASCII))
        out.write(0x00)

        // 3. Header: X-Mms-MMS-Version: 1.2 (0x92)
        out.write(HDR_MMS_VERSION)
        out.write(MMS_VERSION_1_2)

        // 4. Header: Date
        out.write(HDR_DATE)
        val nowSec = (System.currentTimeMillis() / 1000L)
        val dateBytes = ByteArray(4) { i -> ((nowSec ushr ((3 - i) * 8)) and 0xFFL).toByte() }
        out.write(dateBytes.size)
        out.write(dateBytes)

        // 5. Header: From: Insert-address-token (0x81)
        out.write(HDR_FROM)
        out.write(1)
        out.write(0x81)

        // 6. Header: To: recipient
        val cleanNumber = recipientNumber.replace(Regex("[^0-9+]"), "")
        val toEncoded = if (cleanNumber.endsWith("/TYPE=PLMN")) cleanNumber else "$cleanNumber/TYPE=PLMN"
        out.write(HDR_TO)
        out.write(toEncoded.toByteArray(StandardCharsets.US_ASCII))
        out.write(0x00)

        // 7. Header: Content-Type: application/vnd.wap.multipart.related
        out.write(HDR_CONTENT_TYPE)
        val ctBytes = CT_MULTIPART_RELATED.toByteArray(StandardCharsets.US_ASCII)
        out.write(writeUintvar(ctBytes.size + 1))
        out.write(ctBytes)
        out.write(0x00)

        // 8. Body: Multipart
        // nEntries (Uintvar)
        out.write(writeUintvar(parts.size))

        // Write each part
        for (part in parts) {
            val headerStream = ByteArrayOutputStream()

            // Part Content-Type
            headerStream.write(part.contentType.toByteArray(StandardCharsets.US_ASCII))
            headerStream.write(0x00)

            // Part Content-Location
            if (part.contentLocation.isNotBlank()) {
                headerStream.write(PART_CONTENT_LOCATION)
                headerStream.write(part.contentLocation.toByteArray(StandardCharsets.US_ASCII))
                headerStream.write(0x00)
            }

            // Part Content-ID
            if (part.contentId.isNotBlank()) {
                headerStream.write(PART_CONTENT_ID)
                val cid = if (part.contentId.startsWith("<") && part.contentId.endsWith(">")) {
                    part.contentId
                } else {
                    "<${part.contentId}>"
                }
                headerStream.write(cid.toByteArray(StandardCharsets.US_ASCII))
                headerStream.write(0x00)
            }

            val headerBytes = headerStream.toByteArray()
            val headerLen = headerBytes.size
            val dataLen = part.data.size

            // Part Header Length (Uintvar)
            out.write(writeUintvar(headerLen))
            // Part Data Length (Uintvar)
            out.write(writeUintvar(dataLen))
            // Part Headers
            out.write(headerBytes)
            // Part Data
            out.write(part.data)
        }

        return out.toByteArray()
    }

    /**
     * Helper to write Uintvar bytes according to WAP specification.
     */
    fun writeUintvar(value: Int): ByteArray {
        var v = value.toLong() and 0xFFFFFFFFL
        val buf = ByteArray(5)
        var idx = 0
        buf[idx++] = (v and 0x7F).toByte()
        v = v ushr 7
        while (v > 0) {
            buf[idx++] = ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        val result = ByteArray(idx)
        for (i in 0 until idx) {
            result[i] = buf[idx - 1 - i]
        }
        return result
    }

    /**
     * Creates a temporary PDU file in the app cache and returns it.
     */
    fun createPduFile(
        context: Context,
        recipientNumber: String,
        parts: List<MmsPart>,
        sessionId: Long = System.currentTimeMillis()
    ): File {
        val outboxDir = File(context.cacheDir, "mms_outbox").apply { mkdirs() }
        val pduFile = File(outboxDir, "send_req_${sessionId}_${System.currentTimeMillis()}.pdu")
        val pduBytes = composeSendReqPdu(recipientNumber, parts)
        FileOutputStream(pduFile).use { it.write(pduBytes) }
        Log.i(TAG, "Created M-Send.req PDU (${pduBytes.size} bytes) for $recipientNumber at ${pduFile.absolutePath}")
        return pduFile
    }
}
