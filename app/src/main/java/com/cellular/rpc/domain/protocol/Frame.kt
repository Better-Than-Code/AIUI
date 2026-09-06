package com.cellular.rpc.domain.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Transport Frame for Cellular RPC over SMS/MMS.
 *
 * MTU Budget:
 * - Physical SMS PDU: 140 Bytes
 * - UDH: 7 Bytes
 * - Header: 9 Bytes [Magic (1B) + Ver (1B) + Session (2B) + Type (1B) + Seq (2B) + Len (2B)]
 * - Payload: up to 122 Bytes
 * - Trailer: 2 Bytes (CRC16 CCITT-FALSE)
 * - Total Safe Frame Size: 133 Bytes
 */
data class Frame(
    val magic: Byte = MAGIC_BYTE,
    val version: Byte = PROTOCOL_VERSION,
    val sessionId: Int,
    val pktType: Byte,
    val seqNo: Int,
    val payload: ByteArray,
    val ackBits: Long = 0L,
    val crc16: Int = 0,
    val timestampMs: Long = System.currentTimeMillis()
) {
    init {
        require(payload.size <= MAX_PAYLOAD_SIZE) {
            "Payload exceeds safe MTU allocation of $MAX_PAYLOAD_SIZE bytes (was ${payload.size})"
        }
    }

    val payloadLength: Int get() = payload.size

    /**
     * Serializes this frame into a binary byte array according to the 9B header + payload + 2B CRC16 spec.
     */
    fun toBinary(): ByteArray {
        val frameSize = HEADER_SIZE + payload.size + TRAILER_SIZE
        val buffer = ByteBuffer.allocate(frameSize).order(ByteOrder.BIG_ENDIAN)

        buffer.put(magic)
        buffer.put(version)
        buffer.putShort((sessionId and 0xFFFF).toShort())
        buffer.put(pktType)
        buffer.putShort((seqNo and 0xFFFF).toShort())
        buffer.putShort((payload.size and 0xFFFF).toShort())
        buffer.put(payload)

        // Compute CRC16 over Header + Payload
        val dataForCrc = ByteArray(HEADER_SIZE + payload.size)
        System.arraycopy(buffer.array(), 0, dataForCrc, 0, dataForCrc.size)
        val computedCrc = Crc16CcittFalse.compute(dataForCrc)

        buffer.putShort((computedCrc and 0xFFFF).toShort())
        return buffer.array()
    }

    /**
     * Serializes this frame into the ASCII Fallback Wire Format:
     * ~<SES_ID>:<PKT_TYPE>:<SEQ>:<ACK_BITS>:<PAYLOAD>:<CRC16>#
     */
    fun toAsciiWire(): String {
        val sesHex = String.format("%04X", sessionId and 0xFFFF)
        val typeHex = String.format("%02X", pktType.toInt() and 0xFF)
        val seqHex = String.format("%04X", seqNo and 0xFFFF)
        val ackHex = String.format("%08X", ackBits and 0xFFFFFFFFL)

        // Payload in GSM-safe Base85 or direct ASCII if simple control
        val payloadStr = if (payload.isEmpty()) {
            ""
        } else {
            val asUtf8 = String(payload, Charsets.UTF_8)
            // If it's a simple 304 code or pure ASCII string without colons or control chars, we can keep it readable
            if (payload.size <= 4 && asUtf8.all { it.isDigit() }) {
                asUtf8
            } else {
                GsmSafeBase85.encode(payload)
            }
        }

        val bodyWithoutCrc = "$sesHex:$typeHex:$seqHex:$ackHex:$payloadStr"
        val crc = Crc16CcittFalse.computeAscii(bodyWithoutCrc)
        val crcHex = Crc16CcittFalse.formatHex(crc)

        return "~$bodyWithoutCrc:$crcHex#"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Frame
        if (sessionId != other.sessionId) return false
        if (pktType != other.pktType) return false
        if (seqNo != other.seqNo) return false
        if (!payload.contentEquals(other.payload)) return false
        if (ackBits != other.ackBits) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sessionId
        result = 31 * result + pktType
        result = 31 * result + seqNo
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + ackBits.hashCode()
        return result
    }

    companion object {
        const val MAGIC_BYTE: Byte = 0x7E
        const val PROTOCOL_VERSION: Byte = 0x01
        const val HEADER_SIZE: Int = 9
        const val MAX_PAYLOAD_SIZE: Int = 122
        const val TRAILER_SIZE: Int = 2
        const val MAX_FRAME_SIZE: Int = HEADER_SIZE + MAX_PAYLOAD_SIZE + TRAILER_SIZE // 133 Bytes

        // Packet Types
        const val PKT_RPC_REQ: Byte = 0x01
        const val PKT_CTL_ACK: Byte = 0x02
        const val PKT_RPC_RES: Byte = 0x04
        const val PKT_BIN_DAT: Byte = 0x05
        const val PKT_BIN_FIN: Byte = 0x06
        const val PKT_CTL_RST: Byte = 0x07

        fun typeName(type: Byte): String = when (type) {
            PKT_RPC_REQ -> "RPC_REQ"
            PKT_CTL_ACK -> "CTL_ACK"
            PKT_RPC_RES -> "RPC_RES"
            PKT_BIN_DAT -> "BIN_DAT"
            PKT_BIN_FIN -> "BIN_FIN"
            PKT_CTL_RST -> "CTL_RST"
            else -> "UNKNOWN(0x${String.format("%02X", type.toInt() and 0xFF)})"
        }

        /**
         * Parses a binary frame byte array and validates CRC16.
         */
        fun fromBinary(data: ByteArray): Frame? {
            if (data.size < HEADER_SIZE + TRAILER_SIZE) return null

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.get()
            if (magic != MAGIC_BYTE) return null

            val ver = buffer.get()
            val ses = buffer.short.toInt() and 0xFFFF
            val type = buffer.get()
            val seq = buffer.short.toInt() and 0xFFFF
            val len = buffer.short.toInt() and 0xFFFF

            if (len > MAX_PAYLOAD_SIZE || data.size < HEADER_SIZE + len + TRAILER_SIZE) {
                return null
            }

            val payloadBytes = ByteArray(len)
            buffer.get(payloadBytes)
            val receivedCrc = buffer.short.toInt() and 0xFFFF

            val expectedCrc = Crc16CcittFalse.compute(data, 0, HEADER_SIZE + len)
            if (receivedCrc != expectedCrc) {
                return null // CRC mismatch
            }

            return Frame(
                magic = magic,
                version = ver,
                sessionId = ses,
                pktType = type,
                seqNo = seq,
                payload = payloadBytes,
                crc16 = receivedCrc
            )
        }

        /**
         * Parses an ASCII wire string: ~<SES_ID>:<PKT_TYPE>:<SEQ>:<ACK_BITS>:<PAYLOAD>:<CRC16>#
         */
        fun fromAsciiWire(wire: String): Frame? {
            val trimmed = wire.trim()
            if (!trimmed.startsWith("~") || !trimmed.endsWith("#")) return null

            val inner = trimmed.substring(1, trimmed.length - 1)
            val parts = inner.split(":")
            if (parts.size < 6) return null

            val sesHex = parts[0]
            val typeHex = parts[1]
            val seqHex = parts[2]
            val ackHex = parts[3]
            val crcHex = parts[parts.size - 1]

            // Payload may contain colons if decoded string contains colons
            val payloadStr = parts.subList(4, parts.size - 1).joinToString(":")

            val bodyWithoutCrc = inner.substring(0, inner.lastIndexOf(":$crcHex"))
            val calculatedCrc = Crc16CcittFalse.computeAscii(bodyWithoutCrc)
            val receivedCrc = crcHex.toIntOrNull(16) ?: return null

            if (calculatedCrc != receivedCrc) {
                return null // Corrupted frame
            }

            val ses = sesHex.toIntOrNull(16) ?: 0
            val type = (typeHex.toIntOrNull(16) ?: 0).toByte()
            val seq = seqHex.toIntOrNull(16) ?: 0
            val ack = ackHex.toLongOrNull(16) ?: 0L

            val payloadBytes = if (payloadStr.isEmpty()) {
                ByteArray(0)
            } else if (payloadStr == "304" || payloadStr.startsWith("{") || payloadStr.startsWith("REQ:")) {
                // If payload is plain text / JSON or 304 code
                payloadStr.toByteArray(Charsets.UTF_8)
            } else {
                try {
                    GsmSafeBase85.decode(payloadStr)
                } catch (e: Exception) {
                    payloadStr.toByteArray(Charsets.UTF_8)
                }
            }

            return Frame(
                sessionId = ses,
                pktType = type,
                seqNo = seq,
                payload = payloadBytes,
                ackBits = ack,
                crc16 = receivedCrc
            )
        }
    }
}
