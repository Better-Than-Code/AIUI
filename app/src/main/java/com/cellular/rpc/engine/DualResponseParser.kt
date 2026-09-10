package com.cellular.rpc.engine

import android.util.Base64
import com.cellular.rpc.transport.queue.CompressionUtils

data class DualParsedResponse(
    val conversationalText: String,
    val widgetData: WidgetData?,
    val threadId: String? = null
)

object DualResponseParser {
    // Regex matches [TID:xyz] or [THREAD:xyz] or (TID:xyz)
    private val threadIdRegex = Regex("""(?:\[(?:TID|THREAD):([a-zA-Z0-9_\-]+)\]|\((?:TID|THREAD):([a-zA-Z0-9_\-]+)\))""", RegexOption.IGNORE_CASE)

    // Delimiters separating conversational commentary from structured cellular payload envelopes
    private val payloadDelimiters = listOf(
        "---CELLULAR_DATA---",
        "---PAYLOAD---",
        "---SDUI---",
        "---DATA---"
    )

    /**
     * Extracts threadId tag [TID:...], suppresses cellular payload envelope framing (e.g. ---CELLULAR_DATA---),
     * decompresses DATA:GZ:<base64> streams, and extracts both conversational commentary and embedded structured WidgetData.
     */
    fun parse(rawText: String): DualParsedResponse {
        var trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return DualParsedResponse("", null, null)
        }

        // 0. Extract and strip threadId
        var extractedThreadId: String? = null
        val tidMatch = threadIdRegex.find(trimmed)
        if (tidMatch != null) {
            extractedThreadId = tidMatch.groupValues[1].ifEmpty { tidMatch.groupValues[2] }
            trimmed = trimmed.removeRange(tidMatch.range).trim()
        }

        // 1. Direct Compressed Stream: DATA:GZ:<base64>
        if (trimmed.startsWith("DATA:GZ:")) {
            val decompressed = tryDecompressGzipPayload(trimmed.removePrefix("DATA:GZ:").trim())
            if (decompressed != null) {
                val widget = WidgetData.parse(decompressed)
                if (widget != null && widget !is WidgetData.ChatText) {
                    return DualParsedResponse("", widget, extractedThreadId ?: extractTidFromJson(decompressed))
                }
            }
        }

        // 2. Split on cellular payload envelopes (e.g. ---CELLULAR_DATA---)
        for (delimiter in payloadDelimiters) {
            if (trimmed.contains(delimiter)) {
                val parts = trimmed.split(delimiter, limit = 2)
                val conversationalPart = parts[0].trim()
                var payloadPart = parts[1].trim()

                // Handle compressed GZ inside envelope
                if (payloadPart.startsWith("DATA:GZ:")) {
                    payloadPart = tryDecompressGzipPayload(payloadPart.removePrefix("DATA:GZ:").trim()) ?: payloadPart
                }

                val widget = WidgetData.parse(payloadPart)
                if (widget != null && widget !is WidgetData.ChatText) {
                    return DualParsedResponse(conversationalPart, widget, extractedThreadId ?: extractTidFromJson(payloadPart))
                }
            }
        }

        // 3. If pure JSON, parse directly
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            // Also check if JSON has "tid" or "threadId" property
            if (extractedThreadId == null) {
                extractedThreadId = extractTidFromJson(trimmed)
            }
            val widget = WidgetData.parse(trimmed)
            if (widget != null && widget !is WidgetData.ChatText) {
                return DualParsedResponse("", widget, extractedThreadId)
            }
        }

        // 4. Search for embedded JSON (e.g. ```json ... ``` or { ... })
        val jsonBlockRegex = Regex("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?\\})\\s*```")
        val match = jsonBlockRegex.find(trimmed)

        if (match != null) {
            val jsonCandidate = match.groupValues[1]
            if (extractedThreadId == null) {
                extractedThreadId = extractTidFromJson(jsonCandidate)
            }
            val widget = WidgetData.parse(jsonCandidate)
            if (widget != null) {
                val cleanText = sanitizeConversationalText(trimmed.removeRange(match.range).trim())
                return DualParsedResponse(cleanText, widget, extractedThreadId)
            }
        }

        // 5. Embedded braces
        val startIdx = trimmed.indexOf('{')
        val endIdx = trimmed.lastIndexOf('}')
        if (startIdx != -1 && endIdx > startIdx) {
            val candidate = trimmed.substring(startIdx, endIdx + 1)
            if (extractedThreadId == null) {
                extractedThreadId = extractTidFromJson(candidate)
            }
            val widget = WidgetData.parse(candidate)
            if (widget != null && widget !is WidgetData.ChatText) {
                val prefix = trimmed.substring(0, startIdx).trim()
                val suffix = trimmed.substring(endIdx + 1).trim()
                val conversational = listOf(prefix, suffix).filter { it.isNotEmpty() }.joinToString("\n")
                return DualParsedResponse(sanitizeConversationalText(conversational), widget, extractedThreadId)
            }
        }

        return DualParsedResponse(sanitizeConversationalText(trimmed), null, extractedThreadId)
    }

    private fun tryDecompressGzipPayload(base64Payload: String): String? {
        return try {
            // Strip any whitespace, line wraps, or carrier injected carriage returns
            var cleanBase64 = base64Payload.replace("\\s+".toRegex(), "").trim()
            // Restore missing '=' padding if stripped across carrier SMS boundaries
            val mod = cleanBase64.length % 4
            if (mod > 0) {
                cleanBase64 += "=".repeat(4 - mod)
            }
            val compressedBytes = Base64.decode(cleanBase64, Base64.DEFAULT or Base64.NO_WRAP)
            CompressionUtils.decompress(compressedBytes)
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitizeConversationalText(text: String): String {
        var clean = text
        for (delim in payloadDelimiters) {
            clean = clean.replace(delim, "")
        }
        return clean.trim()
    }

    private fun extractTidFromJson(jsonStr: String): String? {
        return try {
            val obj = org.json.JSONObject(jsonStr)
            when {
                obj.has("tid") -> obj.optString("tid").takeIf { it.isNotBlank() }
                obj.has("threadId") -> obj.optString("threadId").takeIf { it.isNotBlank() }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
