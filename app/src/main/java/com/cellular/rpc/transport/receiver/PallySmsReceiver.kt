package com.cellular.rpc.transport.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.WidgetCacheEntity
import com.cellular.rpc.domain.protocol.Frame
import com.cellular.rpc.domain.protocol.FrameTokenizer
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

        val isRecognizedOrProtocol = { sender: String, text: String ->
            // Accept all incoming SMS messages in Cellular RPC app
            true
        }

        // Try standard Android Intents helper which correctly merges multi-part/concatenated SMS
        val messages = try {
            android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            null
        }

        if (!messages.isNullOrEmpty()) {
            val sender = normalizePhoneNumber(messages.first().originatingAddress)
            val combinedText = messages.joinToString("") { it.messageBody ?: "" }
            val firstSms = messages.first()

            if (isRecognizedOrProtocol(sender, combinedText)) {
                Log.i(TAG, "Intercepted incoming AI SMS from $sender (${combinedText.length} chars): $combinedText")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        processIncomingPallyMessage(context.applicationContext, firstSms, combinedText, sender)
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }
        }

        // Fallback PDU parser for data SMS or custom broadcasts
        val bundle = intent.extras ?: return
        val pdus = bundle.get("pdus") as? Array<*> ?: return
        val format = bundle.getString("format")

        val sb = StringBuilder()
        var fallbackSender = ""
        var fallbackSms: SmsMessage? = null

        for (pduObj in pdus) {
            val pduBytes = pduObj as? ByteArray ?: continue
            val sms = SmsMessage.createFromPdu(pduBytes, format) ?: continue
            fallbackSms = sms
            if (fallbackSender.isEmpty()) {
                fallbackSender = normalizePhoneNumber(sms.originatingAddress)
            }
            sb.append(sms.messageBody ?: "")
        }

        val fullText = sb.toString()
        if (fallbackSms != null && isRecognizedOrProtocol(fallbackSender, fullText)) {
            Log.i(TAG, "Intercepted fallback PDU message from $fallbackSender: $fullText")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    processIncomingPallyMessage(context.applicationContext, fallbackSms, fullText, fallbackSender)
                } finally {
                    pendingResult.finish()
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

        if (binaryFrame != null) {
            val inboundMessage = InboundCellularMessage(
                transportType = CellularTransportType.SMS_DATA_PORT_8901,
                senderAddress = sender,
                rawText = textBody,
                rawBytes = userData,
                frame = binaryFrame
            )
            CellularMessageDispatcher.dispatchInbound(context, inboundMessage)
            return
        }

        // 2. Tokenize stream for batched/concatenated ASCII frames
        val tokenizeResult = FrameTokenizer.tokenize(textBody)
        if (tokenizeResult.frames.isNotEmpty()) {
            for (frame in tokenizeResult.frames) {
                val inboundMessage = InboundCellularMessage(
                    transportType = CellularTransportType.SMS_TEXT_WIRE,
                    senderAddress = sender,
                    rawText = textBody,
                    rawBytes = userData,
                    frame = frame
                )
                CellularMessageDispatcher.dispatchInbound(context, inboundMessage)
            }
        } else {
            // Fallback unencapsulated message
            val fallbackFrame = Frame.fromAsciiWire(textBody)
            val inboundMessage = InboundCellularMessage(
                transportType = CellularTransportType.SMS_TEXT_WIRE,
                senderAddress = sender,
                rawText = textBody,
                rawBytes = userData,
                frame = fallbackFrame
            )
            CellularMessageDispatcher.dispatchInbound(context, inboundMessage)
        }
    }
}
