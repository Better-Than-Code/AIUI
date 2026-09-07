package com.cellular.rpc.transport.receiver

import java.util.concurrent.ConcurrentHashMap

/**
 * Shared tracker for deduplicating incoming SMS messages across BroadcastReceiver
 * and ContentObserver, and detecting raw binary/corrupted PDU sequences.
 */
object PallySmsTracker {

    private val handledSignatures = ConcurrentHashMap<String, Long>()

    /**
     * Marks a message as handled by the primary receiver.
     */
    fun markHandled(sender: String, text: String) {
        val key = buildKey(sender, text)
        handledSignatures[key] = System.currentTimeMillis()
        pruneExpired()
    }

    /**
     * Checks if a message was already handled within the given time window.
     */
    fun wasHandledRecently(sender: String, text: String, windowMs: Long = 60_000L): Boolean {
        val key = buildKey(sender, text)
        val lastSeen = handledSignatures[key] ?: return false
        val age = System.currentTimeMillis() - lastSeen
        return age < windowMs
    }

    /**
     * Detects if a string contains binary control sequences, WSP/MMS header bytes,
     * or unicode replacement characters (\\uFFFD) indicating corrupted decoding.
     */
    fun isCorruptedOrBinaryText(text: String): Boolean {
        if (text.isBlank()) return false
        var badCount = 0
        for (c in text) {
            if (c == '\uFFFD') {
                badCount += 3
            } else if (c.code < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                badCount++
            }
        }
        // If more than 4% of chars are non-printable/replacement, it's corrupted binary data
        return badCount > 0 && (badCount.toDouble() / text.length) > 0.04
    }

    private fun buildKey(sender: String, text: String): String {
        val cleanSender = sender.filter { it.isDigit() || it == '+' }
        return "$cleanSender:${text.trim()}"
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        val it = handledSignatures.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (now - entry.value > 120_000L) {
                it.remove()
            }
        }
    }
}
