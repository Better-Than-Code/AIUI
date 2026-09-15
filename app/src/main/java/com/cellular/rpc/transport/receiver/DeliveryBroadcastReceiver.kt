package com.cellular.rpc.transport.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens for native telephony sent & delivery intents and updates local outbox state
 * and fires zero-data return acknowledgments (ack:<msg_id>) over cellular radio to confirm delivery.
 */
class DeliveryBroadcastReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "DeliveryReceiver"
        const val SMS_SENT_ACTION = "com.cellular.rpc.SMS_SENT"
        const val MMS_SENT_ACTION = "com.cellular.rpc.MMS_SENT"
        const val SMS_DELIVERED_ACTION = "com.cellular.rpc.SMS_DELIVERED"

        suspend fun processSentIntent(appContext: Context, action: String, msgId: String, sessionId: Int, seqNo: Int, outboxId: Long, resultCode: Int) {
            try {
                val db = com.cellular.rpc.data.local.AppDatabase.getInstance(appContext)
                val queueEngine = CarrierSafeQueueEngine.getInstance(appContext)
                val circuitBreaker = com.cellular.rpc.transport.cooldown.CarrierCooldownCircuitBreaker.getInstance(appContext)

                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "Physical radio SENT success ($action) for msg: $msgId")
                    circuitBreaker.recordSuccess()
                    queueEngine.windowController.markFrameAcknowledged(seqNo)
                    db.outboxDao().markAcknowledged(sessionId, seqNo)
                    db.outboxDao().clearAcknowledged()
                } else {
                    val failureReason = when (resultCode) {
                        android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO_OFF (Airplane mode or cellular radio powered down)"
                        android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE -> "NO_SERVICE (Device out of cellular coverage / no cell tower)"
                        android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "GENERIC_FAILURE (Modem / carrier transmission failure)"
                        android.telephony.SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "LIMIT_EXCEEDED (SMS transmission rate limit exceeded)"
                        android.telephony.SmsManager.RESULT_ERROR_NULL_PDU -> "NULL_PDU (Invalid PDU generated for carrier transmission)"
                        else -> "Radio transmission failure (code $resultCode)"
                    }

                    // Immediately release in-flight slot in SlidingWindowController so permits aren't stuck in limbo
                    queueEngine.windowController.releaseFrame(seqNo)

                    val outboxDao = db.outboxDao()
                    val entity = if (outboxId > 0L) {
                        outboxDao.getById(outboxId)
                    } else {
                        outboxDao.getBySessionAndSeq(sessionId, seqNo)
                    }

                    val currentRetries = entity?.retries ?: 0
                    val targetId = entity?.id ?: outboxId

                    circuitBreaker.recordFailure(resultCode, failureReason)

                    if (currentRetries >= 3) {
                        Log.w(TAG, "INC-30: $failureReason for msg $msgId. Max retries ($currentRetries) reached. Transitioning to FAILED.")
                        if (targetId > 0L) {
                            outboxDao.markFailed(targetId, System.currentTimeMillis())
                        } else {
                            outboxDao.markFailedBySeq(sessionId, seqNo, System.currentTimeMillis())
                        }
                    } else {
                        // Exponential backoff: 2s -> 4s -> 8s
                        val backoffMs = 2000L * (1 shl currentRetries)
                        val nextAttemptTimestamp = System.currentTimeMillis() + backoffMs
                        Log.w(TAG, "INC-30: $failureReason for msg $msgId. Transitioning to PENDING with ${backoffMs}ms backoff (retry ${currentRetries + 1}/3).")
                        if (targetId > 0L) {
                            outboxDao.markAttempted(
                                id = targetId,
                                newStatus = com.cellular.rpc.data.local.OutboxEntity.STATUS_PENDING,
                                timestamp = nextAttemptTimestamp
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling $action: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val msgId = intent.getStringExtra("msg_id") ?: "msg_${System.currentTimeMillis()}"
        val sessionId = intent.getIntExtra("session_id", 0)
        val seqNo = intent.getIntExtra("seq_no", 0)
        val outboxId = intent.getLongExtra("outbox_id", 0L)
        val resultCode = if (intent.hasExtra("result_code")) {
            intent.getIntExtra("result_code", Activity.RESULT_OK)
        } else {
            try {
                resultCode
            } catch (e: Exception) {
                Activity.RESULT_OK
            }
        }
        val appContext = context.applicationContext

        Log.i(TAG, "Telephony intent received ($action) for message ID $msgId (session: $sessionId, seq: $seqNo) with result code: $resultCode")

        if (action == SMS_SENT_ACTION || action == MMS_SENT_ACTION) {
            val pendingResult = try { goAsync() } catch (e: Exception) { null }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    processSentIntent(appContext, action, msgId, sessionId, seqNo, outboxId, resultCode)
                } finally {
                    try { pendingResult?.finish() } catch (e: Exception) {}
                }
            }
        } else if (action == SMS_DELIVERED_ACTION || action == "android.provider.Telephony.SMS_DELIVERED") {
            if (resultCode == Activity.RESULT_OK) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val queueEngine = CarrierSafeQueueEngine.getInstance(appContext)
                        val ackPayload = "ack:$msgId"
                        Log.i(TAG, "Cellular return path ACK enqueued: $ackPayload")
                        queueEngine.enqueueCellularText(ackPayload)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error enqueuing delivery ACK: ${e.message}")
                    }
                }
            }
        }
    }
}
