package com.cellular.rpc.transport.receiver

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.cellular.rpc.domain.service.CellularServiceManager
import com.cellular.rpc.transport.handler.CellularMessageDispatcher
import com.cellular.rpc.transport.handler.CellularTransportType
import com.cellular.rpc.transport.handler.InboundCellularMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * TelephonySmsObserver
 *
 * Real-time ContentObserver that monitors Android's SMS Inbox provider.
 * Serves as a resilient fallback and real-time capture for incoming SMS
 * responses on the active AI conversation thread.
 */
class TelephonySmsObserver(
    private val context: Context
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastProcessedSmsId: Long = -1L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        scope.launch {
            queryRecentInboxMessages()
        }
    }

    private suspend fun queryRecentInboxMessages() {
        try {
            val contentResolver = context.contentResolver
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )

            // Look for received inbox messages (MESSAGE_TYPE_INBOX = 1)
            val selection = "${Telephony.Sms.TYPE} = ?"
            val selectionArgs = arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
            val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT 5"

            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val address = cursor.getString(addressCol) ?: ""
                    val body = cursor.getString(bodyCol) ?: ""
                    val dateMs = cursor.getLong(dateCol)

                    // Skip if too old (> 3 minutes ago) or already seen
                    val ageMs = System.currentTimeMillis() - dateMs
                    if (ageMs > 180000) continue
                    if (id <= lastProcessedSmsId) continue

                    // Automatically process all recent inbox messages
                    lastProcessedSmsId = maxOf(lastProcessedSmsId, id)
                    Log.i(TAG, "TelephonySmsObserver captured inbound SMS from $address: ${body.take(40)}...")

                    val inboundMessage = InboundCellularMessage(
                        transportType = CellularTransportType.SMS_TEXT_WIRE,
                        senderAddress = address,
                        rawText = body,
                        rawBytes = null,
                        frame = null
                    )
                    CellularMessageDispatcher.dispatchInbound(context, inboundMessage)
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "READ_SMS permission not yet granted for ContentObserver: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in TelephonySmsObserver: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "TelephonySmsObserver"
        private var instance: TelephonySmsObserver? = null

        fun register(context: Context) {
            if (instance != null) return
            try {
                val observer = TelephonySmsObserver(context.applicationContext)
                context.applicationContext.contentResolver.registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    observer
                )
                instance = observer
                Log.i(TAG, "TelephonySmsObserver registered successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register TelephonySmsObserver: ${e.message}")
            }
        }

        fun unregister(context: Context) {
            try {
                instance?.let {
                    context.applicationContext.contentResolver.unregisterContentObserver(it)
                    instance = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering observer: ${e.message}")
            }
        }
    }
}
