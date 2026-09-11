package com.cellular.rpc.transport.receiver

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
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

/**
 * Robust MMS helper for observing incoming MMS (text and images), extracting parts,
 * and dispatching outbound carrier MMS messages with attachments.
 */
class PallyMmsHelper(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "PallyMmsHelper"
        private val MMS_CONTENT_URI = Uri.parse("content://mms")
        private val MMS_INBOX_URI = Uri.parse("content://mms/inbox")
        private val MMS_PART_URI = Uri.parse("content://mms/part")

        @Volatile
        private var lastProcessedMmsId = -1L
        private var observerInstance: PallyMmsHelper? = null
        private var debounceJob: Job? = null
        private val helperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        /**
         * Registers the MMS ContentObserver.
         */
        fun register(context: Context) {
            if (observerInstance != null) return
            try {
                val observer = PallyMmsHelper(context.applicationContext)
                context.contentResolver.registerContentObserver(
                    MMS_CONTENT_URI,
                    true,
                    observer
                )
                observerInstance = observer
                Log.i(TAG, "Successfully registered Telephony MMS ContentObserver.")

                // Initialize lastProcessedMmsId to latest on startup
                initializeLatestMmsId(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register MMS ContentObserver: ${e.message}")
            }
        }

        private fun initializeLatestMmsId(context: Context) {
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
        private suspend fun pollMmsInboxWithRetry(context: Context, maxAttempts: Int = 3) {
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
         * Companion SMS preamble is prohibited.
         */
        fun dispatchCarrierMmsBundle(
            context: Context,
            destinationNumber: String,
            text: String,
            anchorUri: Uri? = null
        ) {
            val resolvedAnchorUri = anchorUri ?: com.cellular.rpc.transport.failover.TransportFailoverEngine.getVisualAnchorUri(context)
            dispatchCarrierMms(
                context = context,
                destinationNumber = destinationNumber,
                text = text,
                attachmentUri = resolvedAnchorUri,
                mimeType = "image/png"
            )
        }

        /**
         * Dispatches an outbound MMS with attachment using the system telephony provider / carrier SMS-MMS handler.
         * Ensures file:// URIs are converted to shareable content:// URIs via FileProvider.
         */
        fun dispatchCarrierMms(
            context: Context,
            destinationNumber: String,
            text: String,
            attachmentUri: Uri?,
            mimeType: String? = "image/*"
        ) {
            try {
                val cleanNumber = destinationNumber.replace(Regex("[^0-9+]"), "")
                
                // Convert file:// or relative paths to FileProvider content:// URI if necessary
                val shareableUri = if (attachmentUri != null && (attachmentUri.scheme == "file" || attachmentUri.scheme == null)) {
                    val filePath = attachmentUri.path ?: ""
                    val file = File(filePath)
                    if (file.exists()) {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    } else {
                        attachmentUri
                    }
                } else {
                    attachmentUri
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType ?: "*/*"
                    putExtra("address", cleanNumber)
                    putExtra(Intent.EXTRA_PHONE_NUMBER, cleanNumber)
                    putExtra("sms_body", text)
                    putExtra(Intent.EXTRA_TEXT, text)
                    if (shareableUri != null) {
                        putExtra(Intent.EXTRA_STREAM, shareableUri)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.i(TAG, "Dispatched carrier MMS intent for $cleanNumber with shareableUri $shareableUri (MIME: $mimeType)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch carrier MMS intent: ${e.message}", e)
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

                        // Process only new MMS messages that haven't been ingested
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
                        // In MMS spec, type 137 is PduHeaders.FROM
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

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        debounceJob?.cancel()
        debounceJob = helperScope.launch {
            // Delay 1500ms to allow telephony framework to complete downloading all MMS parts
            delay(1500)
            queryMmsInbox(context)
        }
    }
}
