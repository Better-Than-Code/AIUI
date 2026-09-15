package com.cellular.rpc.transport.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.PacketLogEntity
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

        val isRecognizedSender = { sender: String ->
            com.cellular.rpc.domain.service.CellularServiceManager.isSenderRecognized(context, sender)
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

            // INC-26: Strictly reject non-active / unrecognized senders immediately to maintain thread isolation
            if (!isRecognizedSender(sender)) {
                Log.d(TAG, "PallySmsReceiver: Ignoring SMS from non-active contact '$sender' to maintain thread isolation.")
                return
            }

            if (PallySmsTracker.isCorruptedOrBinaryText(combinedText)) {
                Log.w(TAG, "Rejecting corrupted/binary SMS payload from $sender: $combinedText")
                return
            }

            Log.i(TAG, "Intercepted incoming AI SMS from $sender (${combinedText.length} chars): $combinedText")
            PallySmsTracker.markHandled(sender, combinedText)

            // Prevent raw wire frames and protocol traffic from leaking into the system SMS inbox
            if (isOrderedBroadcast) {
                try {
                    abortBroadcast()
                    Log.d(TAG, "Successfully aborted broadcast for handled protocol packet.")
                } catch (e: Exception) {
                    Log.d(TAG, "abortBroadcast error: ${e.message}")
                }
            }

            val pendingResult = goAsync()

            // INC-27: Route through PduReassemblyBuffer to reassemble fragmented multi-part PDUs
            PduReassemblyBuffer.ingest(
                context = context.applicationContext,
                sender = sender,
                sms = firstSms,
                bodyText = combinedText
            ) { reassembledText, representativeSms ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        processIncomingPallyMessage(context.applicationContext, representativeSms, reassembledText, sender)
                    } finally {
                        try {
                            pendingResult.finish()
                        } catch (ignored: Exception) {}
                    }
                }
            }
            return
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
        if (fallbackSms == null || !isRecognizedSender(fallbackSender)) {
            Log.d(TAG, "PallySmsReceiver: Ignoring fallback PDU from non-active contact '$fallbackSender'.")
            return
        }

        if (PallySmsTracker.isCorruptedOrBinaryText(fullText)) {
            Log.w(TAG, "Rejecting corrupted fallback PDU text: $fullText")
            return
        }

        Log.i(TAG, "Intercepted fallback PDU message from $fallbackSender: $fullText")
        PallySmsTracker.markHandled(fallbackSender, fullText)

        if (isOrderedBroadcast) {
            try {
                abortBroadcast()
                Log.d(TAG, "Successfully aborted broadcast for handled fallback PDU packet.")
            } catch (e: Exception) {
                Log.d(TAG, "abortBroadcast error: ${e.message}")
            }
        }

        val pendingResult = goAsync()
        PduReassemblyBuffer.ingest(
            context = context.applicationContext,
            sender = fallbackSender,
            sms = fallbackSms,
            bodyText = fullText
        ) { reassembledText, representativeSms ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    processIncomingPallyMessage(context.applicationContext, representativeSms, reassembledText, fallbackSender)
                } finally {
                    try {
                        pendingResult.finish()
                    } catch (ignored: Exception) {}
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
        val now = System.currentTimeMillis()

        // INC-11: Dual-Path Ingestion Interceptor
        // Inspect raw incoming SMS text for protocol-level or natural language ACKs before passing to chat store
        val parsedAck = com.cellular.rpc.domain.protocol.CellularAckParser.parse(textBody)
        if (parsedAck != null) {
            Log.i(TAG, "Dual-Path Interceptor: Intercepted cellular ACK (hash=${parsedAck.hash}, chunks=${parsedAck.chunks}) from $sender")
            CarrierSafeQueueEngine.getInstance(context).onAckReceived(
                hash = parsedAck.hash,
                chunks = parsedAck.chunks,
                rawWire = textBody
            )

            // If there was preceding conversational prose before an embedded data section, dispatch prose only
            val prose = if (textBody.contains("---CELLULAR_DATA---")) {
                textBody.split("---CELLULAR_DATA---").first().trim()
            } else ""

            if (prose.isNotEmpty()) {
                val conversationalMessage = InboundCellularMessage(
                    transportType = CellularTransportType.SMS_TEXT_WIRE,
                    senderAddress = sender,
                    rawText = prose,
                    rawBytes = null,
                    frame = null
                )
                CellularMessageDispatcher.dispatchInbound(context, conversationalMessage)
            }
            // Suppress SDUI card / chat bubble inflation for pure transport ACKs
            return
        }

        // Helper to handle control / ACK packets without creating UI chat bubbles
        suspend fun handleControlFrame(frame: Frame, rawBytes: ByteArray?, rawText: String, transportType: CellularTransportType): Boolean {
            if (frame.pktType == Frame.PKT_CTL_ACK) {
                Log.d(TAG, "Intercepted pure ACK control frame (session=${frame.sessionId}, seq=${frame.seqNo}, ackBits=${String.format("%08X", frame.ackBits)}) -> routing to QueueEngine")
                try {
                    CarrierSafeQueueEngine.getInstance(context).submitInboundPacket(frame)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed submitting inbound ACK to queue engine: ${e.message}")
                }
                return true
            }
            return false
        }

        // 1. Try binary frame parsing from User Data
        val userData = sms.userData
        val binaryFrame = userData?.let { Frame.fromBinary(it) }

        if (binaryFrame != null) {
            if (handleControlFrame(binaryFrame, userData, textBody, CellularTransportType.SMS_DATA_PORT_8901)) {
                return
            }
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
                if (handleControlFrame(frame, userData, textBody, CellularTransportType.SMS_TEXT_WIRE)) {
                    continue
                }
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
            if (fallbackFrame != null && handleControlFrame(fallbackFrame, userData, textBody, CellularTransportType.SMS_TEXT_WIRE)) {
                return
            }
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
