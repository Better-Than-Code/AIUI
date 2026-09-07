package com.cellular.rpc.transport.receiver

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.cellular.rpc.transport.handler.CellularMessageDispatcher
import com.cellular.rpc.transport.handler.InboundCellularMessage
import com.cellular.rpc.transport.handler.CellularTransportType
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register SMS ContentObserver: ${e.message}")
            }
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Query inbox messages sorted by _id DESC to catch newly inserted incoming texts
                val cursor = context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                    null,
                    null,
                    "${Telephony.Sms._ID} DESC LIMIT 5"
                )

                cursor?.use {
                    var maxSeenId = lastProcessedId
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
                            val date = if (dateCol != -1) it.getLong(dateCol) else System.currentTimeMillis()
                            val msgType = if (typeCol != -1) it.getInt(typeCol) else Telephony.Sms.MESSAGE_TYPE_INBOX

                            // If this message ID is newer than what we've processed and it's an inbox (received) message
                            if (msgId > lastProcessedId && msgType == Telephony.Sms.MESSAGE_TYPE_INBOX && body.isNotEmpty()) {
                                if (msgId > maxSeenId) {
                                    maxSeenId = msgId
                                }
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
}

