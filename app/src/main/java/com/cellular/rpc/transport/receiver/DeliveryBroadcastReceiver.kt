package com.cellular.rpc.transport.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.util.Log
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Component B (Sprint 4.3): Android companion client delivery monitor & telemetry ACK loop.
 * Listens for native telephony sent & delivery intents and updates local outbox state
 * and fires zero-data return acknowledgments (ack:<msg_id>) over cellular radio to confirm delivery.
 */
class DeliveryBroadcastReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "DeliveryReceiver"
        const val SMS_SENT_ACTION = "com.cellular.rpc.SMS_SENT"
        const val SMS_DELIVERED_ACTION = "com.cellular.rpc.SMS_DELIVERED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val msgId = intent.getStringExtra("msg_id") ?: "msg_${System.currentTimeMillis()}"
        val sessionId = intent.getIntExtra("session_id", 0)
        val seqNo = intent.getIntExtra("seq_no", 0)
        val resultCode = resultCode
        val appContext = context.applicationContext

        Log.i(TAG, "SMS intent received ($action) for message ID $msgId (session: $sessionId, seq: $seqNo) with result code: $resultCode")

        if (action == SMS_SENT_ACTION) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = com.cellular.rpc.data.local.AppDatabase.getInstance(appContext)
                    val queueEngine = CarrierSafeQueueEngine.getInstance(appContext)
                    if (resultCode == Activity.RESULT_OK) {
                        Log.i(TAG, "Physical radio SENT success for msg: $msgId")
                        // Mark acknowledged in sliding window and Room
                        queueEngine.windowController.markFrameAcknowledged(seqNo)
                        db.outboxDao().markAcknowledged(sessionId, seqNo)
                        db.outboxDao().clearAcknowledged()
                    } else {
                        Log.w(TAG, "Physical radio SENT failure (code: $resultCode) for msg: $msgId. Marking stalled for retry.")
                        // Keep in outbox or reset to pending so retry watchdog will resend
                        db.outboxDao().markAttempted(
                            id = intent.getLongExtra("outbox_id", 0L),
                            newStatus = com.cellular.rpc.data.local.OutboxEntity.STATUS_PENDING,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling SMS_SENT: ${e.message}")
                }
            }
        } else if (action == SMS_DELIVERED_ACTION || action == "android.provider.Telephony.SMS_DELIVERED") {
            if (resultCode == Activity.RESULT_OK) {
                // Fire zero-data return acknowledgment: ack:<msg_id>
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val queueEngine = CarrierSafeQueueEngine.getInstance(appContext)
                        // Enqueue ACK packet back to AI Gateway to cancel backend watchdog timer
                        queueEngine.enqueueMultiSegmentBinary(
                            sessionId = msgId.hashCode(),
                            fullData = "ack:$msgId".toByteArray(Charsets.UTF_8)
                        )
                        Log.i(TAG, "Successfully fired client delivery acknowledgement: ack:$msgId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to dispatch delivery ACK: ${e.message}", e)
                    }
                }
            }
        }
    }
}
