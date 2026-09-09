package com.cellular.rpc.transport.queue

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Epic 3.1: Compression (Zlib/GZIP)
 * Reduces payload size to minimize SMS fragmentation.
 */
object CompressionUtils {
    /**
     * GZIP compresses a JSON string payload.
     */
    fun compress(payload: String): ByteArray {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(bytes) }
        return bos.toByteArray()
    }

    /**
     * Attempts to GZIP decompress a byte array. If it's not GZIP (missing magic bytes),
     * returns the payload as a standard UTF-8 string.
     */
    fun decompress(compressed: ByteArray): String {
        if (compressed.size < 2) return String(compressed, Charsets.UTF_8)

        // Check GZIP magic number (0x1f 0x8b)
        val head = (compressed[0].toInt() and 0xff) or ((compressed[1].toInt() shl 8) and 0xff00)
        if (head != GZIPInputStream.GZIP_MAGIC) {
            return String(compressed, Charsets.UTF_8)
        }

        try {
            val bis = ByteArrayInputStream(compressed)
            val gzipIn = GZIPInputStream(bis)
            return gzipIn.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            // Fallback to raw string if decompression fails
            return String(compressed, Charsets.UTF_8)
        }
    }
}
