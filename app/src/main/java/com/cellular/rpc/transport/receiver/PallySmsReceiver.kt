package com.cellular.rpc.transport.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.WidgetCacheEntity
import com.cellular.rpc.domain.protocol.Frame
import com.cellular.rpc.engine.WidgetData
import com.cellular.rpc.transport.queue.CarrierSafeQueueEngine
import com.cellular.rpc.transport.handler.CellularMessageDispatcher
import com.cellular.rpc.transport.handler.CellularTransportType
import com.cellular.rpc.transport.handler.InboundCellularMessage
import com.cellular.rpc.widget.CellularNewsAppWidgetProvider
import com.cellular.rpc.widget.CellularWeatherAppWidgetProvider
import com.cellular.rpc.widget.WidgetPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PallySmsReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "PallySmsReceiver"
        const val DATA_PORT: Short = 8901

        fun normalizePhoneNumber(number: String?): String {
            if (number.isNullOrBlank()) return ""
            return number.replace(Regex("[^0-9+]"), "")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != "android.provider.Telephony.SMS_RECEIVED" &&
            action != "android.intent.action.DATA_SMS_RECEIVED"
        ) {
            return
        }

        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")
        val configuredPallyNumber = normalizePhoneNumber(WidgetPreferences.getPallyPhoneNumber(context))

        for (pduObj in pdus) {
            val pduBytes = pduObj as? ByteArray ?: continue
            val sms = SmsMessage.createFromPdu(pduBytes, format)
            val sender = normalizePhoneNumber(sms.originatingAddress)

            // Check if sender matches Pally number or if payload contains cellular protocol tags
            val textBody = sms.messageBody ?: ""
            val isPallySender = sender.isNotEmpty() && (
                sender == configuredPallyNumber ||
                sender.endsWith(configuredPallyNumber.takeLast(10))
            )
            val hasProtocolHeader = textBody.startsWith("~") || textBody.contains("[WIDGET:") || textBody.contains("304") || textBody.startsWith("REQ:") || textBody.startsWith("RES:")

            if (isPallySender || hasProtocolHeader) {
                Log.d(TAG, "Intercepted Pally AI cellular message from $sender: $textBody")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        processIncomingPallyMessage(context.applicationContext, sms, textBody, sender)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun processIncomingPallyMessage(
        context: Context,
        sms: SmsMessage,
        textBody: String,
        sender: String
    ) {
        // 1. Try binary frame parsing from User Data
        val userData = sms.userData
        val binaryFrame = userData?.let { Frame.fromBinary(it) }

        // 2. Try ASCII wire parsing from text
        val frame = binaryFrame ?: Frame.fromAsciiWire(textBody)

        val transportType = if (binaryFrame != null) {
            CellularTransportType.SMS_DATA_PORT_8901
        } else {
            CellularTransportType.SMS_TEXT_WIRE
        }

        val inboundMessage = InboundCellularMessage(
            transportType = transportType,
            senderAddress = sender,
            rawText = textBody,
            rawBytes = userData,
            frame = frame
        )

        // Delegate to centralized standardized dispatcher
        CellularMessageDispatcher.dispatchInbound(context, inboundMessage)
    }
}
