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
 * Robust fallback SMS ContentObserver that listens to system SMS inbox changes (`content://sms/inbox`).
 * Guarantees that even if background broadcast receivers are suppressed by OEM battery managers
 * or Android OS power restrictions on real devices without ADB, incoming replies are instantly
 * detected and ingested the moment they are written to the device's telephony provider.
 */
class PallySmsObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "PallySmsObserver"
        private var lastCheckedTimestamp = System.currentTimeMillis()
        private var observerInstance: PallySmsObserver? = null

        fun register(context: Context) {
            if (observerInstance != null) return
            try {
                val observer = PallySmsObserver(context.applicationContext)
                context.contentResolver.registerContentObserver(
                    Uri.parse("content://sms/inbox"),
                    true,
                    observer
                )
                observerInstance = observer
                Log.i(TAG, "Successfully registered secure SMS Inbox ContentObserver for real-device fallback.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register SMS ContentObserver: ${e.message}")
            }
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cursor = context.contentResolver.query(
                    Uri.parse("content://sms/inbox"),
                    arrayOf("address", "body", "date"),
                    "date > ?",
                    arrayOf(lastCheckedTimestamp.toString()),
                    "date DESC LIMIT 1"
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val addressCol = it.getColumnIndex("address")
                        val bodyCol = it.getColumnIndex("body")
                        val dateCol = it.getColumnIndex("date")

                        if (addressCol != -1 && bodyCol != -1 && dateCol != -1) {
                            val sender = it.getString(addressCol) ?: ""
                            val body = it.getString(bodyCol) ?: ""
                            val date = it.getLong(dateCol)

                            if (date > lastCheckedTimestamp && body.isNotEmpty()) {
                                lastCheckedTimestamp = date
                                Log.i(TAG, "Inbox ContentObserver detected new inbound SMS from $sender: $body")

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
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying SMS inbox in ContentObserver: ${e.message}")
            }
        }
    }
}
