package com.cellular.rpc.transport.queue

/**
 * Sprint 6.2: The Compressor (Algorithmic BPE Engine)
 * Replaces common JSON keys and RPC phrases with single non-ASCII byte tokens.
 * This saves significant SMS segment space compared to raw strings or GZIP on tiny payloads.
 */
object CellularBpeTokenizer {

    // Dictionary mapping common strings to single-byte tokens (represented as Chars here for safe String replacement).
    // Using Unicode Private Use Area or other safe non-ASCII characters to avoid collisions with standard text.
    private val dictionary = mapOf(
        "\"action\":\"" to "\u0080",
        "\"target\":\"" to "\u0081",
        "\"etag\":\"" to "\u0082",
        "\"params\":{" to "\u0083",
        "\"payload\":\"" to "\u0084",
        "\"status\":" to "\u0085",
        "\"schemaId\":\"" to "\u0086",
        "\"widget:weather\"" to "\u0087",
        "\"widget:news\"" to "\u0088",
        "\"widget:calendar\"" to "\u0089",
        "\"temp\":" to "\u008A",
        "\"city\":\"" to "\u008B",
        "\"cond\":\"" to "\u008C",
        "\"high\":" to "\u008D",
        "\"low\":" to "\u008E",
        "\"id\":\"" to "\u008F",
        "\"headline\":\"" to "\u0090",
        "\"summary\":\"" to "\u0091",
        "\"source\":\"" to "\u0092",
        "\"sym\":\"" to "\u0093",
        "\"price\":\"" to "\u0094",
        "\"chg\":\"" to "\u0095",
        "\"sparkline\":[" to "\u0096",
        "\"to\":\"" to "\u0097",
        "\"amount\":\"" to "\u0098",
        "\"memo\":\"" to "\u0099",
        "\"CONFIRMED\"" to "\u009A",
        "\"question\":\"" to "\u009B",
        "\"options\":[" to "\u009C",
        "\"votes\":[" to "\u009D",
        "\"totalVotes\":" to "\u009E",
        "\"batteryPct\":" to "\u009F",
        "\"networkType\":\"" to "\u00A0",
        "\"signalStrength\":" to "\u00A1",
        "\"isRoaming\":" to "\u00A2",
        "\"lastPingMs\":" to "\u00A3"
    )

    private val reverseDictionary = dictionary.entries.associate { (k, v) -> v to k }

    fun compress(input: String): ByteArray {
        var compressed = input
        for ((phrase, token) in dictionary) {
            compressed = compressed.replace(phrase, token)
        }
        return compressed.toByteArray(Charsets.UTF_8)
    }

    fun decompress(inputBytes: ByteArray): String {
        var decompressed = String(inputBytes, Charsets.UTF_8)
        for ((token, phrase) in reverseDictionary) {
            decompressed = decompressed.replace(token, phrase)
        }
        return decompressed
    }
}
