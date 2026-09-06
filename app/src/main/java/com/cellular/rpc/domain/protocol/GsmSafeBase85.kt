package com.cellular.rpc.domain.protocol

object GsmSafeBase85 {
    // 85 safe characters strictly residing in the GSM 03.38 Basic Character Set
    private const val ENCODING_CHARS = 
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%&()*+-./:;<=>?@_^{|}"
    
    private val DECODING_ARRAY = IntArray(128) { -1 }.apply {
        for (i in ENCODING_CHARS.indices) {
            this[ENCODING_CHARS[i].code] = i
        }
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size * 5 + 3) / 4)
        var block = 0L
        var count = 0

        for (b in data) {
            block = (block shl 8) or (b.toLong() and 0xFF)
            count++
            if (count == 4) {
                appendEncodedBlock(sb, block, 5)
                block = 0
                count = 0
            }
        }

        if (count > 0) {
            val padding = 4 - count
            block = block shl (padding * 8)
            appendEncodedBlock(sb, block, count + 1)
        }

        return sb.toString()
    }

    private fun appendEncodedBlock(sb: StringBuilder, value: Long, charsToEmit: Int) {
        var temp = value
        val blockChars = CharArray(5)
        for (i in 4 downTo 0) {
            blockChars[i] = ENCODING_CHARS[(temp % 85).toInt()]
            temp /= 85
        }
        for (i in 0 until charsToEmit) {
            sb.append(blockChars[i])
        }
    }

    fun decode(encoded: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var block = 0L
        var count = 0

        for (ch in encoded) {
            val v = if (ch.code < 128) DECODING_ARRAY[ch.code] else -1
            if (v == -1) continue // Skip invalid/whitespace padding
            block = block * 85 + v
            count++
            if (count == 5) {
                emitDecodedBytes(out, block, 4)
                block = 0
                count = 0
            }
        }

        if (count > 1) {
            val padding = 5 - count
            for (i in 0 until padding) {
                block = block * 85 + 84 // Shift with padding char equivalent
            }
            emitDecodedBytes(out, block, count - 1)
        }

        return out.toByteArray()
    }

    private fun emitDecodedBytes(out: java.io.ByteArrayOutputStream, value: Long, bytesToWrite: Int) {
        for (i in 3 downTo (4 - bytesToWrite)) {
            out.write(((value ushr (i * 8)) and 0xFF).toInt())
        }
    }
}
