package com.cellular.rpc.domain.protocol

/**
 * CRC16 CCITT-FALSE Implementation
 * Polynomial: 0x1021
 * Initial: 0xFFFF
 * RefIn: false, RefOut: false
 * XorOut: 0x0000
 */
object Crc16CcittFalse {
    private const val POLYNOMIAL = 0x1021
    private const val INITIAL = 0xFFFF

    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = INITIAL
        val end = offset + length
        for (i in offset until end) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            for (j in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor POLYNOMIAL) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }

    fun computeAscii(asciiString: String): Int {
        return compute(asciiString.toByteArray(Charsets.US_ASCII))
    }

    fun formatHex(crc: Int): String {
        return String.format("%04X", crc and 0xFFFF)
    }
}
