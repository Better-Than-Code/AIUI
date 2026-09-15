package com.cellular.rpc.transport.receiver

import android.content.Context
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * PduReassemblyBuffer (INC-27)
 *
 * Implements 3GPP TS 23.040 User Data Header (UDH) and text-header based reassembly
 * for fragmented multi-part / concatenated SMS PDUs.
 *
 * Prevents split, duplicate, or out-of-order partial chat bubbles when carrier networks
 * (e.g., TracFone / Verizon LTE) deliver concatenated segments (>160 GSM-7 / >70 UCS-2 chars)
 * across distinct broadcast intents.
 */
object PduReassemblyBuffer {
    private const val TAG = "PduReassemblyBuffer"
    private const val REASSEMBLY_TIMEOUT_MS = 15_000L

    data class UdhConcatHeader(
        val refNumber: Int,
        val totalParts: Int,
        val partSeq: Int // 1-based index (1..totalParts)
    )

    data class BufferedSmsPart(
        val partSeq: Int,
        val text: String,
        val sms: SmsMessage,
        val timestamp: Long = System.currentTimeMillis()
    )

    private data class ConcatBufferEntry(
        val sender: String,
        val refKey: String,
        val totalParts: Int,
        val parts: MutableMap<Int, BufferedSmsPart> = mutableMapOf(),
        val createdAt: Long = System.currentTimeMillis(),
        var timeoutJob: Job? = null
    )

    private val activeBuffers = ConcurrentHashMap<String, ConcatBufferEntry>()
    private val bufferScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Parses the 3GPP SMS-DELIVER PDU byte stream to extract UDH concatenation headers.
     * Supports both 8-bit reference numbers (IEI 0x00) and 16-bit reference numbers (IEI 0x08).
     */
    fun parseUdhConcatHeader(pdu: ByteArray?): UdhConcatHeader? {
        if (pdu == null || pdu.isEmpty()) return null
        try {
            var offset = 0
            // 1. SMSC address length
            val smscLen = pdu[offset++].toInt() and 0xFF
            offset += smscLen
            if (offset >= pdu.size) return null

            // 2. First octet of SMS-DELIVER
            val firstOctet = pdu[offset++].toInt() and 0xFF
            val hasUdhi = (firstOctet and 0x40) != 0 // Bit 6 = TP-UDHI (User Data Header Indicator)
            if (!hasUdhi) return null

            // 3. TP-OA (Originating Address)
            val oaDigits = pdu[offset++].toInt() and 0xFF
            offset++ // OA Type of Address
            val oaBytes = (oaDigits + 1) / 2
            offset += oaBytes
            if (offset >= pdu.size) return null

            // 4. TP-PID (1 byte)
            offset += 1
            // 5. TP-DCS (1 byte)
            offset += 1
            // 6. TP-SCTS (7 bytes timestamp)
            offset += 7
            if (offset >= pdu.size) return null

            // 7. TP-UDL (User Data Length in septets or octets)
            offset += 1
            if (offset >= pdu.size) return null

            // 8. UDHL (User Data Header Length)
            val udhl = pdu[offset++].toInt() and 0xFF
            val udhEnd = offset + udhl
            if (udhEnd > pdu.size) return null

            while (offset < udhEnd) {
                val iei = pdu[offset++].toInt() and 0xFF
                if (offset >= udhEnd) break
                val ieLen = pdu[offset++].toInt() and 0xFF
                if (offset + ieLen > udhEnd) break

                when (iei) {
                    0x00 -> {
                        // 8-bit concatenated short message: ref(1B), max(1B), seq(1B)
                        if (ieLen >= 3) {
                            val ref = pdu[offset].toInt() and 0xFF
                            val total = pdu[offset + 1].toInt() and 0xFF
                            val seq = pdu[offset + 2].toInt() and 0xFF
                            if (total in 2..255 && seq in 1..total) {
                                return UdhConcatHeader(ref, total, seq)
                            }
                        }
                    }
                    0x08 -> {
                        // 16-bit concatenated short message: ref(2B), max(1B), seq(1B)
                        if (ieLen >= 4) {
                            val ref = ((pdu[offset].toInt() and 0xFF) shl 8) or (pdu[offset + 1].toInt() and 0xFF)
                            val total = pdu[offset + 2].toInt() and 0xFF
                            val seq = pdu[offset + 3].toInt() and 0xFF
                            if (total in 2..255 && seq in 1..total) {
                                return UdhConcatHeader(ref, total, seq)
                            }
                        }
                    }
                }
                offset += ieLen
            }
        } catch (e: Exception) {
            Log.d(TAG, "PDU UDH parse error: ${e.message}")
        }
        return null
    }

    /**
     * Fallback parser for carrier gateways that prepend sequencing prefixes
     * in plain text such as [1/3] or (1/3).
     */
    fun parseTextSequenceHeader(text: String): Pair<UdhConcatHeader, String>? {
        val clean = text.trimStart()
        val match = Regex("""^\[(\d+)/(\d+)\]\s*(.*)""", RegexOption.DOT_MATCHES_ALL).find(clean)
            ?: Regex("""^\((\d+)/(\d+)\)\s*(.*)""", RegexOption.DOT_MATCHES_ALL).find(clean)

        if (match != null) {
            val seq = match.groupValues[1].toIntOrNull() ?: return null
            val total = match.groupValues[2].toIntOrNull() ?: return null
            val remainder = match.groupValues[3]
            if (total in 2..255 && seq in 1..total) {
                val pseudoRef = (total * 31) + (clean.length / 50)
                return Pair(UdhConcatHeader(pseudoRef, total, seq), remainder)
            }
        }
        return null
    }

    /**
     * Ingests an incoming SMS part.
     * If the message is part of a multi-segment sequence, it buffers until all parts arrive
     * or flushes on timeout. If it is a standalone single-part message, it invokes [onComplete] immediately.
     */
    fun ingest(
        context: Context,
        sender: String,
        sms: SmsMessage,
        bodyText: String,
        onComplete: (combinedText: String, representativeSms: SmsMessage) -> Unit
    ) {
        val cleanSender = sender.filter { it.isDigit() || it == '+' }
        val udhHeader = parseUdhConcatHeader(sms.pdu)
        val textSeq = if (udhHeader == null) parseTextSequenceHeader(bodyText) else null

        val concatInfo = udhHeader ?: textSeq?.first
        val partBody = if (udhHeader != null) bodyText else (textSeq?.second ?: bodyText)

        // If not a multi-part message, deliver immediately
        if (concatInfo == null || concatInfo.totalParts <= 1) {
            onComplete(bodyText, sms)
            return
        }

        val bufferKey = "$cleanSender:ref_${concatInfo.refNumber}_of_${concatInfo.totalParts}"
        Log.d(TAG, "Buffering multi-part SMS: sender=$cleanSender, part=${concatInfo.partSeq}/${concatInfo.totalParts}, ref=${concatInfo.refNumber}")

        val entry = activeBuffers.computeIfAbsent(bufferKey) {
            val newEntry = ConcatBufferEntry(
                sender = cleanSender,
                refKey = bufferKey,
                totalParts = concatInfo.totalParts
            )
            // Schedule fail-safe timeout flush
            newEntry.timeoutJob = bufferScope.launch {
                delay(REASSEMBLY_TIMEOUT_MS)
                flushPartialBuffer(bufferKey, onComplete)
            }
            newEntry
        }

        synchronized(entry) {
            entry.parts[concatInfo.partSeq] = BufferedSmsPart(
                partSeq = concatInfo.partSeq,
                text = partBody,
                sms = sms
            )

            // Check if all parts have arrived
            if (entry.parts.size >= entry.totalParts) {
                entry.timeoutJob?.cancel()
                activeBuffers.remove(bufferKey)

                val orderedParts = (1..entry.totalParts).mapNotNull { entry.parts[it] }
                val combinedText = orderedParts.joinToString("") { it.text }
                val firstSms = orderedParts.firstOrNull()?.sms ?: sms

                Log.i(TAG, "Successfully reassembled multi-part SMS from $cleanSender (${entry.totalParts} parts, ${combinedText.length} chars).")
                onComplete(combinedText, firstSms)
            } else {
                Log.d(TAG, "Multi-part SMS pending: ${entry.parts.size}/${entry.totalParts} received for $bufferKey")
            }
        }
    }

    private fun flushPartialBuffer(
        bufferKey: String,
        onComplete: (combinedText: String, representativeSms: SmsMessage) -> Unit
    ) {
        val entry = activeBuffers.remove(bufferKey) ?: return
        synchronized(entry) {
            if (entry.parts.isEmpty()) return
            val orderedParts = entry.parts.values.sortedBy { it.partSeq }
            val combinedText = orderedParts.joinToString("") { it.text }
            val representativeSms = orderedParts.first().sms
            Log.w(TAG, "Flushing incomplete multi-part SMS buffer after timeout: ${entry.parts.size}/${entry.totalParts} parts recovered.")
            onComplete(combinedText, representativeSms)
        }
    }

    fun clear() {
        for ((_, entry) in activeBuffers) {
            entry.timeoutJob?.cancel()
        }
        activeBuffers.clear()
    }
}
