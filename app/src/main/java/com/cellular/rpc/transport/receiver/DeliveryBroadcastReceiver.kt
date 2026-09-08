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
 * Listens for native telephony delivery intents and immediately fires zero-data return
 * acknowledgments (ack:<msg_id>) over cellular radio to confirm delivery and clear backend timers.
 */
class DeliveryBroadcastReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "DeliveryReceiver"
        const val SMS_DELIVERED_ACTION = "com.cellular.rpc.SMS_DELIVERED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == SMS_DELIVERED_ACTION || action == "android.provider.Telephony.SMS_DELIVERED") {
            val msgId = intent.getStringExtra("msg_id") ?: "msg_${System.currentTimeMillis()}"
            val resultCode = resultCode

            Log.i(TAG, "SMS Delivery intent received for message ID $msgId with result code: $resultCode")

            if (resultCode == Activity.RESULT_OK) {
                // Fire zero-data return acknowledgment: ack:<msg_id>
                val appContext = context.applicationContext
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
