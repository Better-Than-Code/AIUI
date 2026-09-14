package com.cellular.rpc.domain.protocol

import org.json.JSONObject

/**
 * Parses cellular protocol ACKs from SMS/MMS payloads in various wire formats:
 * 1. Protocol Colon Wire: ACK:2.1.0:<hash>[:<chunks>]
 * 2. MCP Tag Wire: [MCP_ACK <chunk>/<total> hash=<hash>]
 * 3. Natural Language Wire: ack chunks <range> of <total> (hash <hash>)
 * 4. Dual-Format JSON Wire: {"version":"2.1.0","rpc":{"type":"ack","hash":"...","chunks":[...]}}
 */
object CellularAckParser {

    data class ParsedAck(
        val hash: String,
        val chunks: List<Int> = emptyList(),
        val status: String = "OK"
    )

    fun isAck(text: String): Boolean {
        return parse(text) != null
    }

    fun parse(text: String): ParsedAck? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // 1. Wire format: ACK:2.1.0:<hash>[:<chunks>]
        val ackColonRegex = Regex("""^ACK:[\d\.]+:(?<hash>[a-fA-F0-9]+)(?::(?<chunks>[\d,]+))?""")
        val matchColon = ackColonRegex.find(trimmed)
        if (matchColon != null) {
            val hash = matchColon.groups["hash"]?.value ?: ""
            val chunksStr = matchColon.groups["chunks"]?.value
            val chunks = chunksStr?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
            return ParsedAck(hash = hash, chunks = chunks)
        }

        // 2. MCP format: [MCP_ACK 1/67 hash=5B212384]
        val mcpAckRegex = Regex("""\[MCP_ACK\s+(?<chunk>\d+)/\d+\s+hash=(?<hash>[a-fA-F0-9]+)\]""", RegexOption.IGNORE_CASE)
        val matchMcp = mcpAckRegex.find(trimmed)
        if (matchMcp != null) {
            val chunk = matchMcp.groups["chunk"]?.value?.toIntOrNull()
            val hash = matchMcp.groups["hash"]?.value ?: ""
            return ParsedAck(hash = hash, chunks = if (chunk != null) listOf(chunk) else emptyList())
        }

        // 3. Natural language format: ack chunks 1-2 of 67 (hash 5b212384)
        val nlAckRegex = Regex("""ack\s+chunks?\s+(?<range>[\d\-,]+)\s+of\s+\d+.*?hash\s+(?<hash>[a-fA-F0-9]+)""", RegexOption.IGNORE_CASE)
        val matchNl = nlAckRegex.find(trimmed)
        if (matchNl != null) {
            val hash = matchNl.groups["hash"]?.value ?: ""
            val range = matchNl.groups["range"]?.value ?: ""
            val chunks = parseRange(range)
            return ParsedAck(hash = hash, chunks = chunks)
        }

        // 4. JSON Schema formats (direct, delimiter-separated, or fenced)
        val jsonCandidate = extractJsonCandidate(trimmed)
        if (jsonCandidate != null) {
            try {
                val obj = JSONObject(jsonCandidate)
                val rpcObj = obj.optJSONObject("rpc") ?: if (obj.optString("type") == "ack") obj else null
                if (rpcObj != null && rpcObj.optString("type", "ack") == "ack") {
                    val hash = rpcObj.optString("hash", "")
                    val status = rpcObj.optString("status", "OK")
                    val chunksList = mutableListOf<Int>()
                    val arr = rpcObj.optJSONArray("chunks")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            chunksList.add(arr.optInt(i))
                        }
                    }
                    return ParsedAck(hash = hash, chunks = chunksList, status = status)
                }
            } catch (e: Exception) {
                // Not valid JSON
            }
        }

        return null
    }

    private fun extractJsonCandidate(trimmed: String): String? {
        return when {
            trimmed.contains("---CELLULAR_DATA---") -> {
                trimmed.split("---CELLULAR_DATA---").last().trim()
            }
            trimmed.contains("```aiui") -> {
                val start = trimmed.indexOf("```aiui")
                val end = trimmed.indexOf("```", start + 7)
                if (end > start) trimmed.substring(start + 7, end).trim() else trimmed.substring(start + 7).trim()
            }
            trimmed.contains("```json") -> {
                val start = trimmed.indexOf("```json")
                val end = trimmed.indexOf("```", start + 7)
                if (end > start) trimmed.substring(start + 7, end).trim() else trimmed.substring(start + 7).trim()
            }
            trimmed.startsWith("{") -> trimmed
            trimmed.contains("{") && trimmed.contains("}") -> {
                val s = trimmed.indexOf('{')
                val e = trimmed.lastIndexOf('}')
                trimmed.substring(s, e + 1)
            }
            else -> null
        }
    }

    private fun parseRange(rangeStr: String): List<Int> {
        val result = mutableListOf<Int>()
        val parts = rangeStr.split(",")
        for (part in parts) {
            if (part.contains("-")) {
                val rangeParts = part.split("-")
                val start = rangeParts.getOrNull(0)?.trim()?.toIntOrNull()
                val end = rangeParts.getOrNull(1)?.trim()?.toIntOrNull()
                if (start != null && end != null) {
                    for (i in start..end) result.add(i)
                }
            } else {
                part.trim().toIntOrNull()?.let { result.add(it) }
            }
        }
        return result
    }
}
