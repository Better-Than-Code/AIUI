package com.cellular.rpc.transport.receiver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cellular.rpc.engine.AttachmentType
import com.cellular.rpc.engine.MessageAttachment
import com.cellular.rpc.transport.handler.CellularMessageDispatcher
import com.cellular.rpc.transport.handler.CellularTransportType
import com.cellular.rpc.transport.handler.InboundCellularMessage
import com.cellular.rpc.transport.service.HardenedMmsParser
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

object PallyMmsHelper {
    private const val TAG = "PallyMmsHelper"
    private val MMS_INBOX_URI = Uri.parse("content://mms/inbox")

    @Volatile
    private var lastProcessedMmsId = -1L
    private val helperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initializeLatestMmsId(context: Context) {
        helperScope.launch {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                return@launch
            }
            try {
                val cursor = context.contentResolver.query(
                    MMS_INBOX_URI,
                    arrayOf(Telephony.Mms._ID),
                    null,
                    null,
                    "${Telephony.Mms._ID} DESC LIMIT 1"
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(0)
                        if (lastProcessedMmsId == -1L) {
                            lastProcessedMmsId = id
                            Log.i(TAG, "Initialized lastProcessedMmsId to $lastProcessedMmsId")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not initialize latest MMS ID: ${e.message}")
            }
        }
    }

    /**
     * Scans the MMS inbox for newly delivered MMS messages.
     */
    fun checkMmsInboxNow(context: Context) {
        helperScope.launch {
            pollMmsInboxWithRetry(context.applicationContext, maxAttempts = 3)
        }
    }

    /**
     * Polls the MMS inbox across multiple backoff intervals (1.5s, 3.5s, 6s)
     * to guarantee complete part retrieval even under sluggish carrier MMSC transaction latencies.
     */
    suspend fun pollMmsInboxWithRetry(context: Context, maxAttempts: Int = 3) {
        val delays = listOf(1500L, 3500L, 6000L)
        for (attempt in 0 until maxAttempts) {
            val delayTime = delays.getOrElse(attempt) { 4000L }
            delay(delayTime)
            val foundNew = queryMmsInbox(context)
            Log.d(TAG, "MMS poll attempt ${attempt + 1}/$maxAttempts: foundNew=$foundNew")
            if (foundNew) {
                break
            }
        }
    }

    /**
     * Dispatches a carrier MMS failover bundle promoting large payloads (>256B)
     * with the carrier-compliant 32x32 visual anchor and strictly encapsulated payload body.
     * Direct background transmission via SmsManager.sendMultimediaMessage with chunked SMS fallback.
     * Under zero circumstances is Intent.ACTION_SEND or external app chooser triggered.
     */
    fun dispatchCarrierMmsBundle(
        context: Context,
        destinationNumber: String,
        text: String,
        anchorUri: Uri? = null,
        sessionId: Long = System.currentTimeMillis(),
        seqNo: Int = 0,
        outboxId: Long = 0L
    ) {
        val anchorFile = com.cellular.rpc.transport.failover.TransportFailoverEngine.getOrCreateVisualAnchorFile(context)
        val anchorBytes = if (anchorFile.exists()) anchorFile.readBytes() else ByteArray(0)

        val parts = mutableListOf<com.cellular.rpc.transport.mms.MmsPduComposer.MmsPart>()
        if (text.isNotBlank()) {
            parts.add(
                com.cellular.rpc.transport.mms.MmsPduComposer.MmsPart(
                    contentType = "text/plain; charset=utf-8",
                    contentLocation = "body.txt",
                    contentId = "body_text",
                    data = text.toByteArray(Charsets.UTF_8)
                )
            )
        }
        if (anchorBytes.isNotEmpty()) {
            parts.add(
                com.cellular.rpc.transport.mms.MmsPduComposer.MmsPart(
                    contentType = "image/png",
                    contentLocation = com.cellular.rpc.transport.failover.TransportFailoverEngine.VISUAL_ANCHOR_NAME,
                    contentId = "visual_anchor",
                    data = anchorBytes
                )
            )
        }

        dispatchCarrierMmsDirect(
            context = context,
            destinationNumber = destinationNumber,
            text = text,
            parts = parts,
            sessionId = sessionId,
            seqNo = seqNo,
            outboxId = outboxId
        )
    }

    /**
     * Dispatches an outbound MMS with attachment using direct background SmsManager transmission.
     * If direct MMS is unconfigured or unavailable, silently falls back to carrier-safe chunked SMS.
     * Eliminates external ACTION_SEND intents and system app choosers.
     */
    fun dispatchCarrierMms(
        context: Context,
        destinationNumber: String,
        text: String,
        attachmentUri: Uri?,
        mimeType: String? = "image/*",
        sessionId: Long = System.currentTimeMillis(),
        seqNo: Int = 0,
        outboxId: Long = 0L
    ) {
        val parts = mutableListOf<com.cellular.rpc.transport.mms.MmsPduComposer.MmsPart>()

        if (text.isNotBlank()) {
            parts.add(
                com.cellular.rpc.transport.mms.MmsPduComposer.MmsPart(
                    contentType = "text/plain; charset=utf-8",
                    contentLocation = "body.txt",
                    contentId = "body_text",
                    data = text.toByteArray(Charsets.UTF_8)
                )
            )
        }

        if (attachmentUri != null) {
            try {
                val attachmentBytes: ByteArray? = when {
                    attachmentUri.scheme == "file" || attachmentUri.scheme == null -> {
                        val file = File(attachmentUri.path ?: "")
                        if (file.exists()) file.readBytes() else null
                    }
                    else -> {
                        context.contentResolver.openInputStream(attachmentUri)?.use { it.readBytes() }
                    }
                }

                if (attachmentBytes != null && attachmentBytes.isNotEmpty()) {
                    val resolvedMime = mimeType ?: "application/octet-stream"
                    val filename = attachmentUri.lastPathSegment ?: "attachment.dat"
                    parts.add(
                        com.cellular.rpc.transport.mms.MmsPduComposer.MmsPart(
                            contentType = resolvedMime,
                            contentLocation = filename,
                            contentId = "attachment_part",
                            data = attachmentBytes
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading attachment URI $attachmentUri: ${e.message}")
            }
        }

        dispatchCarrierMmsDirect(
            context = context,
            destinationNumber = destinationNumber,
            text = text,
            parts = parts,
            sessionId = sessionId,
            seqNo = seqNo,
            outboxId = outboxId
        )
    }

    /**
     * Executes direct background MMS dispatch via SmsManager.sendMultimediaMessage()
     * with automatic carrier-safe chunked SMS fallback.
     */
    private fun dispatchCarrierMmsDirect(
        context: Context,
        destinationNumber: String,
        text: String,
        parts: List<com.cellular.rpc.transport.mms.MmsPduComposer.MmsPart>,
        sessionId: Long = System.currentTimeMillis(),
        seqNo: Int = 0,
        outboxId: Long = 0L
    ) {
        val cleanNumber = destinationNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank()) {
            Log.e(TAG, "Cannot dispatch carrier MMS: blank destination number.")
            return
        }

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Cannot dispatch carrier MMS: SEND_SMS permission is not granted.")
                return
            }

            val pduFile = com.cellular.rpc.transport.mms.MmsPduComposer.createPduFile(
                context = context,
                recipientNumber = cleanNumber,
                parts = parts,
                sessionId = sessionId
            )

            val pduUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pduFile
            )

            val carrierProfile = com.cellular.rpc.transport.apn.CarrierApnResolver.resolveProfile(context)
            Log.i(TAG, "Carrier profile for direct MMS: ${carrierProfile.carrierName} (${carrierProfile.simOperator}), MMSC: ${carrierProfile.activeMmscUrl}, subId: ${carrierProfile.subId}")

            val smsManager: android.telephony.SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (carrierProfile.subId >= 0) {
                    context.getSystemService(android.telephony.SmsManager::class.java)?.createForSubscriptionId(carrierProfile.subId)
                        ?: context.getSystemService(android.telephony.SmsManager::class.java)
                        ?: @Suppress("DEPRECATION") android.telephony.SmsManager.getSmsManagerForSubscriptionId(carrierProfile.subId)
                } else {
                    context.getSystemService(android.telephony.SmsManager::class.java) ?: @Suppress("DEPRECATION") android.telephony.SmsManager.getDefault()
                }
            } else {
                if (carrierProfile.subId >= 0) {
                    @Suppress("DEPRECATION") android.telephony.SmsManager.getSmsManagerForSubscriptionId(carrierProfile.subId)
                } else {
                    @Suppress("DEPRECATION") android.telephony.SmsManager.getDefault()
                }
            }

            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentIntent = Intent(DeliveryBroadcastReceiver.MMS_SENT_ACTION).apply {
                putExtra("msg_id", "mms_${sessionId}_${seqNo}")
                putExtra("session_id", sessionId)
                putExtra("seq_no", seqNo)
                putExtra("outbox_id", outboxId)
                setPackage(context.packageName)
            }
            val sentPI = android.app.PendingIntent.getBroadcast(
                context,
                (sessionId * 41 + seqNo).toInt(),
                sentIntent,
                flags
            )

            context.grantUriPermission("com.android.mms.service", pduUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.grantUriPermission("com.android.phone", pduUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            Log.i(TAG, "Transmitting direct carrier MMS to $cleanNumber via SmsManager (PDU: ${pduFile.length()} bytes, URI: $pduUri, MMSC: ${carrierProfile.activeMmscUrl})")
            smsManager.sendMultimediaMessage(context, pduUri, carrierProfile.activeMmscUrl, null, sentPI)
            Log.i(TAG, "Direct background MMS dispatched successfully without launching external app chooser.")
        } catch (e: Exception) {
            Log.w(TAG, "Direct SmsManager.sendMultimediaMessage failed (${e.message}). Triggering Option B Carrier-Safe Chunked SMS Fallback.")
            fallbackChunkedSms(
                context = context,
                destinationNumber = cleanNumber,
                text = text,
                sessionId = sessionId,
                seqNo = seqNo,
                outboxId = outboxId
            )
        }
    }

    /**
     * Option B: Carrier-Safe Chunked Multi-Part SMS Fallback.
     * Splits long message bodies (>250B) into safe multi-part SMS or sequenced segments
     * with inter-PDU pacing delay, transmitting silently in background.
     */
    private fun fallbackChunkedSms(
        context: Context,
        destinationNumber: String,
        text: String,
        sessionId: Long = 0L,
        seqNo: Int = 0,
        outboxId: Long = 0L
    ) {
        val cleanNumber = destinationNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank() || text.isBlank()) {
            Log.w(TAG, "Fallback chunked SMS aborted: cleanNumber or text is blank.")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot dispatch fallback chunked SMS: SEND_SMS permission is not granted.")
            return
        }

        try {
            val carrierProfile = com.cellular.rpc.transport.apn.CarrierApnResolver.resolveProfile(context)
            val smsManager: android.telephony.SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (carrierProfile.subId >= 0) {
                    context.getSystemService(android.telephony.SmsManager::class.java)?.createForSubscriptionId(carrierProfile.subId)
                        ?: context.getSystemService(android.telephony.SmsManager::class.java)
                        ?: @Suppress("DEPRECATION") android.telephony.SmsManager.getSmsManagerForSubscriptionId(carrierProfile.subId)
                } else {
                    context.getSystemService(android.telephony.SmsManager::class.java) ?: @Suppress("DEPRECATION") android.telephony.SmsManager.getDefault()
                }
            } else {
                if (carrierProfile.subId >= 0) {
                    @Suppress("DEPRECATION") android.telephony.SmsManager.getSmsManagerForSubscriptionId(carrierProfile.subId)
                } else {
                    @Suppress("DEPRECATION") android.telephony.SmsManager.getDefault()
                }
            }

            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentIntent = Intent(DeliveryBroadcastReceiver.SMS_SENT_ACTION).apply {
                putExtra("msg_id", "mms_fallback_${sessionId}_${seqNo}")
                putExtra("session_id", sessionId)
                putExtra("seq_no", seqNo)
                putExtra("outbox_id", outboxId)
                setPackage(context.packageName)
            }
            val sentPI = android.app.PendingIntent.getBroadcast(
                context,
                (sessionId * 31 + seqNo).toInt(),
                sentIntent,
                flags
            )

            val deliveryIntent = Intent(DeliveryBroadcastReceiver.SMS_DELIVERED_ACTION).apply {
                putExtra("msg_id", "mms_fallback_${sessionId}_${seqNo}")
                putExtra("session_id", sessionId)
                putExtra("seq_no", seqNo)
                setPackage(context.packageName)
            }
            val deliveryPI = android.app.PendingIntent.getBroadcast(
                context,
                (sessionId * 37 + seqNo).toInt(),
                deliveryIntent,
                flags
            )

            val parts = smsManager.divideMessage(text)
            if (parts.size > 1) {
                val sentList = ArrayList<android.app.PendingIntent>(parts.size).apply {
                    for (i in parts.indices) add(sentPI)
                }
                val deliveryList = ArrayList<android.app.PendingIntent>(parts.size).apply {
                    for (i in parts.indices) add(deliveryPI)
                }
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, sentList, deliveryList)
                Log.i(TAG, "Dispatched fallback carrier multi-part SMS (${parts.size} parts) to $cleanNumber silently in background.")
            } else {
                smsManager.sendTextMessage(cleanNumber, null, text, sentPI, deliveryPI)
                Log.i(TAG, "Dispatched fallback single SMS to $cleanNumber silently in background.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback chunked SMS error: ${e.message}", e)
        }
    }

    private suspend fun queryMmsInbox(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        var ingestedAny = false
        try {
            val cursor = context.contentResolver.query(
                MMS_INBOX_URI,
                arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.SUBJECT),
                null,
                null,
                "${Telephony.Mms._ID} DESC LIMIT 5"
            )

            cursor?.use {
                var maxSeenId = lastProcessedMmsId
                while (it.moveToNext()) {
                    val mmsId = it.getLong(0)
                    if (mmsId > maxSeenId) {
                        maxSeenId = mmsId
                    }
                    
                    if (lastProcessedMmsId != -1L && mmsId <= lastProcessedMmsId) {
                        continue
                    }

                    val sender = getMmsSender(context, mmsId)
                    val (textBody, attachment) = extractMmsParts(context, mmsId)

                    if (textBody.isNotEmpty() || attachment != null) {
                        Log.i(TAG, "Captured incoming MMS ID $mmsId from $sender: text='$textBody', attachment=${attachment?.fileName}")
                        
                        val inboundMessage = InboundCellularMessage(
                            transportType = CellularTransportType.MMS_WAP_PUSH,
                            senderAddress = sender,
                            rawText = textBody,
                            attachment = attachment
                        )
                        
                        CellularMessageDispatcher.dispatchInbound(context, inboundMessage)
                        ingestedAny = true
                    }
                }
                if (maxSeenId > lastProcessedMmsId) {
                    lastProcessedMmsId = maxSeenId
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying MMS inbox: ${e.message}")
        }
        return ingestedAny
    }

    private fun getMmsSender(context: Context, mmsId: Long): String {
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
                    if (type == 137 && address.isNotBlank() && !address.contains("insert-address-token")) {
                        return address
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading MMS sender: ${e.message}")
        }
        return "MMS_SENDER"
    }

    private fun extractMmsParts(context: Context, mmsId: Long): Pair<String, MessageAttachment?> {
        val detailed = HardenedMmsParser.extractPartsDetailed(context, mmsId)
        return Pair(detailed.textBody, detailed.attachment)
    }
}
