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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Industry-standard SMS ContentObserver monitoring `Telephony.Sms.CONTENT_URI` with `_id` tracking.
 * Mirrors working production SMS apps to guarantee immediate inbound message ingestion on real devices
 * without relying on background broadcast receivers.
 */
class PallySmsObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "PallySmsObserver"
        @Volatile
        private var lastProcessedId = -1L
        private var observerInstance: PallySmsObserver? = null

        fun register(context: Context) {
            if (observerInstance != null) return
            try {
                val observer = PallySmsObserver(context.applicationContext)
                // Observe all SMS content changes (true for notifyForDescendants)
                context.contentResolver.registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    observer
                )
                observerInstance = observer
                Log.i(TAG, "Successfully registered Telephony.Sms.CONTENT_URI ContentObserver.")
                // Query immediately if permissions are already granted
                checkInboxNow(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register SMS ContentObserver: ${e.message}")
            }
        }

        /**
         * Actively scan the SMS inbox for any pending or newly written inbound SMS messages.
         * Safe to call on app launch, on resume, or immediately after permissions are granted.
         */
        fun checkInboxNow(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                queryInbox(context.applicationContext)
            }
        }

        private suspend fun queryInbox(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "READ_SMS permission not granted; cannot query SMS inbox provider.")
                return
            }

            try {
                // Query inbox messages sorted by _id DESC to catch newly inserted incoming texts
                val cursor = context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                    null,
                    null,
                    "${Telephony.Sms._ID} DESC LIMIT 10"
                )

                cursor?.use {
                    var maxSeenId = lastProcessedId
                    val now = System.currentTimeMillis()
                    val isFirstRun = (lastProcessedId == -1L)

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

                            // If this is the initial run on boot, only ingest messages from the last 3 minutes (180,000ms)
                            // to avoid replaying ancient historical texts while catching any response in flight.
                            val shouldProcess = if (isFirstRun) {
                                (now - date) <= 180_000L
                            } else {
                                msgId > lastProcessedId
                            }

                            if (shouldProcess && msgType == Telephony.Sms.MESSAGE_TYPE_INBOX && body.isNotEmpty()) {
                                Log.i(TAG, "ContentObserver caught incoming SMS ID $msgId from $sender: $body")

                                val inboundMessage = InboundCellularMessage(
                                    transportType = CellularTransportType.SMS_TEXT_WIRE,
                                    senderAddress = sender,
                                    rawText = body,
                                    timestampMs = date
                                )
                                CellularMessageDispatcher.dispatchInbound(context, inboundMessage)
                            }
                        }
                    }
                    if (maxSeenId > lastProcessedId) {
                        lastProcessedId = maxSeenId
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying SMS ContentObserver inbox: ${e.message}")
            }
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        CoroutineScope(Dispatchers.IO).launch {
            queryInbox(context)
        }
    }
}

