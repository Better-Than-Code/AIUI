package com.cellular.rpc.transport.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cellular.rpc.domain.protocol.Frame
import com.cellular.rpc.transport.handler.CellularMessageDispatcher
import com.cellular.rpc.transport.handler.CellularTransportType
import com.cellular.rpc.transport.handler.InboundCellularMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for handling inbound MMS WAP Push payloads.
 *
 * Captures `android.provider.Telephony.WAP_PUSH_RECEIVED` and extracts multi-part
 * or binary payloads that exceed single SMS MTU budgets.
 */
class PallyWapPushReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PallyWapPushReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != "android.provider.Telephony.WAP_PUSH_RECEIVED") return

        val mimeType = intent.type ?: ""
        Log.d(TAG, "Received WAP Push intent with MIME: $mimeType")

        val data = intent.getByteArrayExtra("data") ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Try parsing binary Frame directly from WAP Push PDU
                val frame = Frame.fromBinary(data)
                val rawText = String(data, Charsets.UTF_8)

                val inboundMessage = InboundCellularMessage(
                    transportType = CellularTransportType.MMS_WAP_PUSH,
                    senderAddress = intent.getStringExtra("address") ?: "MMS_GATEWAY",
                    rawText = rawText,
                    rawBytes = data,
                    frame = frame
                )

                CellularMessageDispatcher.dispatchInbound(context.applicationContext, inboundMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing WAP Push payload: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
