package com.cellular.rpc.transport.receiver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.cellular.rpc.transport.handler.CellularMessageDispatcher
import com.cellular.rpc.transport.handler.CellularTransportType
import com.cellular.rpc.transport.handler.InboundCellularMessage
import kotlinx.coroutines.*

/**
 * Robust SMS ContentObserver monitoring `Telephony.Sms.CONTENT_URI`.
 *
 * Implements:
 * 1. Debounced ingestion (1200ms) to allow multi-part carrier SMS fragments to land in SQLite.
 * 2. Segment reassembly: buffers and joins multi-part rows in chronological order (_id ASC)
 *    so concatenated messages are never jumbled.
 * 3. Channel deduplication: skips messages already handled by the primary PallySmsReceiver.
 * 4. Automatic MMS sync delegation to PallyMmsHelper.
 */
class PallySmsObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    private data class RawSmsRow(
        val id: Long,
        val sender: String,
        val body: String,
        val date: Long
    )

    companion object {
        private const val TAG = "PallySmsObserver"

        @Volatile
        private var lastProcessedId = -1L
        private var observerInstance: PallySmsObserver? = null
        private var debounceJob: Job? = null
        private val observerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun register(context: Context) {
            if (observerInstance != null) return
            try {
                val observer = PallySmsObserver(context.applicationContext)
                context.contentResolver.registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    observer
                )
                observerInstance = observer
                Log.i(TAG, "Successfully registered Telephony.Sms.CONTENT_URI ContentObserver.")

                // Seed lastProcessedId to the latest SMS ID on startup
                initializeLatestId(context)

                // Also register MMS helper
                PallyMmsHelper.register(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register SMS ContentObserver: ${e.message}")
            }
        }

        private fun initializeLatestId(context: Context) {
            observerScope.launch {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                    return@launch
                }
                try {
                    val cursor = context.contentResolver.query(
                        Telephony.Sms.Inbox.CONTENT_URI,
                        arrayOf(Telephony.Sms._ID),
                        null,
                        null,
                        "${Telephony.Sms._ID} DESC LIMIT 1"
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val id = it.getLong(0)
                            if (lastProcessedId == -1L) {
                                lastProcessedId = id
                                Log.i(TAG, "Initialized lastProcessedId to latest SMS ID: $lastProcessedId")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not initialize latest SMS ID: ${e.message}")
                }
            }
        }

        /**
         * Actively scan the SMS and MMS inboxes for newly written inbound messages.
         */
        fun checkInboxNow(context: Context) {
            observerScope.launch {
                queryInbox(context.applicationContext)
                PallyMmsHelper.checkMmsInboxNow(context.applicationContext)
            }
        }

        private suspend fun queryInbox(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                return
            }

            try {
                // Query recent inbox messages
                val cursor = context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                    null,
                    null,
                    "${Telephony.Sms._ID} DESC LIMIT 15"
                )

                cursor?.use {
                    val candidateRows = mutableListOf<RawSmsRow>()
                    var maxSeenId = lastProcessedId
                    val now = System.currentTimeMillis()

                    while (it.moveToNext()) {
                        val idCol = it.getColumnIndex(Telephony.Sms._ID)
                        val addressCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
                        val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
                        val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
                        val typeCol = it.getColumnIndex(Telephony.Sms.TYPE)

                        if (idCol != -1 && addressCol != -1 && bodyCol != -1) {
                            val msgId = it.getLong(idCol)
                            val sender = it.getString(addressCol) ?: ""
                            val body = it.getString(bodyCol) ?: ""
                            val date = if (dateCol != -1) it.getLong(dateCol) else now
                            val msgType = if (typeCol != -1) it.getInt(typeCol) else Telephony.Sms.MESSAGE_TYPE_INBOX

                            if (msgId > maxSeenId) {
                                maxSeenId = msgId
                            }

                            // Only process messages newer than lastProcessedId
                            val isNew = if (lastProcessedId != -1L) {
                                msgId > lastProcessedId
                            } else {
                                (now - date) <= 30_000L // 30s window on cold start
                            }

                            val isRecognized = com.cellular.rpc.domain.service.CellularServiceManager.isSenderRecognized(context, sender)
                            if (isNew && msgType == Telephony.Sms.MESSAGE_TYPE_INBOX && body.isNotEmpty() && isRecognized) {
                                candidateRows.add(RawSmsRow(msgId, sender, body, date))
                            }
                        }
                    }

                    if (maxSeenId > lastProcessedId) {
                        lastProcessedId = maxSeenId
                    }

                    if (candidateRows.isEmpty()) return@use

                    // Group multi-part segments by sender and 3-second timestamp window
                    val grouped = candidateRows.groupBy { row ->
                        val cleanSender = row.sender.filter { it.isDigit() || it == '+' }
                        "$cleanSender:${row.date / 3000L}"
                    }

                    for ((_, fragments) in grouped) {
                        // Sort fragments by _id ASC so segments are strictly in chronological arrival order
                        val ordered = fragments.sortedBy { it.id }
                        val sender = ordered.first().sender
                        val assembledText = ordered.joinToString("") { it.body }
                        val date = ordered.first().date

                        // Reject corrupted binary text
                        if (PallySmsTracker.isCorruptedOrBinaryText(assembledText)) {
                            Log.w(TAG, "ContentObserver ignoring corrupted binary text: $assembledText")
                            continue
                        }

                        // Check if primary PallySmsReceiver already handled this assembled text
                        if (PallySmsTracker.wasHandledRecently(sender, assembledText)) {
                            Log.d(TAG, "ContentObserver: message was already handled by BroadcastReceiver. Skipping: ${assembledText.take(30)}")
                            continue
                        }

                        Log.i(TAG, "ContentObserver assembled SMS from $sender (${assembledText.length} chars, ${ordered.size} segments): $assembledText")
                        PallySmsTracker.markHandled(sender, assembledText)

                        val inboundMessage = InboundCellularMessage(
                            transportType = CellularTransportType.SMS_TEXT_WIRE,
                            senderAddress = sender,
                            rawText = assembledText,
                            timestampMs = date
                        )
                        CellularMessageDispatcher.dispatchInbound(context, inboundMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying SMS ContentObserver inbox: ${e.message}")
            }
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        // Debounce by 1200ms to allow all segments of multi-part carrier SMS to finish inserting
        debounceJob?.cancel()
        debounceJob = observerScope.launch {
            delay(1200)
            queryInbox(context)
        }
    }
}
