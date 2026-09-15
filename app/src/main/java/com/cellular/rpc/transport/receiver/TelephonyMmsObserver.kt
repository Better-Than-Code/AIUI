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
import com.cellular.rpc.transport.service.HardenedMmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * TelephonyMmsObserver (INC-17)
 *
 * Real-time ContentObserver that monitors Android's MMS provider (Telephony.Mms.CONTENT_URI).
 * Guarantees companion client ingestion when incoming MMS is downloaded by the system default app.
 * Resolves asynchronous MMSC SQLite writes using HardenedMmsParser's retry backoff.
 */
class TelephonyMmsObserver(
    private val context: Context
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queryMutex = Mutex()
    private var lastProcessedMmsId: Long = -1L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        scope.launch {
            queryRecentMmsInbox()
        }
    }

    private suspend fun queryRecentMmsInbox() {
        queryMutex.withLock {
            try {
                val contentResolver = context.contentResolver
                val projection = arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX
                )

                // Query recent received MMS
                val sortOrder = "${Telephony.Mms.DATE} DESC LIMIT 5"

                contentResolver.query(
                    Telephony.Mms.CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
                    val dateCol = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)

                    val candidateIds = mutableListOf<Long>()
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val dateSec = cursor.getLong(dateCol)
                        val ageMs = System.currentTimeMillis() - (dateSec * 1000L)

                        // Discard if older than 5 minutes or already handled
                        if (ageMs > 300000L && lastProcessedMmsId > 0) continue
                        if (lastProcessedMmsId > 0 && id <= lastProcessedMmsId) continue

                        candidateIds.add(id)
                    }

                    // Process chronological (oldest to newest among candidates)
                    for (id in candidateIds.reversed()) {
                        lastProcessedMmsId = maxOf(lastProcessedMmsId, id)
                        Log.i(TAG, "TelephonyMmsObserver: Ingesting MMS ID $id...")

                        val parsedResult = HardenedMmsParser.extractMmsPayloadWithRetry(context, id, maxRetries = 4)
                        val sender = parsedResult.sender
                        val textBody = parsedResult.textBody
                        val attachment = parsedResult.attachment

                        if (textBody.isBlank() && attachment == null) {
                            Log.d(TAG, "MMS ID $id yielded empty content after retries.")
                            continue
                        }

                        // Validate sender strictly via CellularServiceManager (INC-26: Active Number Inbound Filtering & Thread Isolation)
                        val isRecognized = CellularServiceManager.isSenderRecognized(context, sender)
                        if (!isRecognized) {
                            Log.d(TAG, "MMS ID $id from non-active/unrecognized sender '$sender'. Discarding to maintain thread isolation.")
                            continue
                        }

                        Log.i(TAG, "TelephonyMmsObserver successfully ingested MMS ID $id from $sender: text='${textBody.take(40)}...' att=${attachment?.fileName}")

                        val inboundMessage = InboundCellularMessage(
                            transportType = CellularTransportType.MMS_WAP_PUSH,
                            senderAddress = sender,
                            rawText = textBody,
                            rawBytes = null,
                            frame = null,
                            attachment = attachment
                        )

                        CellularMessageDispatcher.dispatchInbound(context, inboundMessage)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "READ_SMS permission not yet granted for TelephonyMmsObserver: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error in TelephonyMmsObserver: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "TelephonyMmsObserver"
        private var instance: TelephonyMmsObserver? = null

        fun register(context: Context) {
            if (instance != null) return
            try {
                val observer = TelephonyMmsObserver(context.applicationContext)
                context.applicationContext.contentResolver.registerContentObserver(
                    Telephony.Mms.CONTENT_URI,
                    true,
                    observer
                )
                instance = observer
                Log.i(TAG, "TelephonyMmsObserver registered on Telephony.Mms.CONTENT_URI.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register TelephonyMmsObserver: ${e.message}")
            }
        }

        fun unregister(context: Context) {
            try {
                instance?.let {
                    context.applicationContext.contentResolver.unregisterContentObserver(it)
                    instance = null
                    Log.i(TAG, "TelephonyMmsObserver unregistered.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering TelephonyMmsObserver: ${e.message}")
            }
        }
    }
}
