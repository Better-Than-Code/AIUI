package com.cellular.rpc.engine

data class DualParsedResponse(
    val conversationalText: String,
    val widgetData: WidgetData?,
    val threadId: String? = null
)

object DualResponseParser {
    // Regex matches [TID:xyz] or [THREAD:xyz] or (TID:xyz)
    private val threadIdRegex = Regex("""(?:\[(?:TID|THREAD):([a-zA-Z0-9_\-]+)\]|\((?:TID|THREAD):([a-zA-Z0-9_\-]+)\))""", RegexOption.IGNORE_CASE)

    /**
     * Extracts threadId tag [TID:...] if present, strips it from the user visible text,
     * and extracts both conversational commentary and embedded structured WidgetData.
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

        // 1. If pure JSON, parse directly
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

        // 2. Search for embedded JSON (e.g. ```json ... ``` or { ... })
        val jsonBlockRegex = Regex("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?\\})\\s*```")
        val match = jsonBlockRegex.find(trimmed)

        if (match != null) {
            val jsonCandidate = match.groupValues[1]
            if (extractedThreadId == null) {
                extractedThreadId = extractTidFromJson(jsonCandidate)
            }
            val widget = WidgetData.parse(jsonCandidate)
            if (widget != null) {
                val cleanText = trimmed.removeRange(match.range).trim()
                return DualParsedResponse(cleanText, widget, extractedThreadId)
            }
        }

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
                return DualParsedResponse(conversational, widget, extractedThreadId)
            }
        }

        return DualParsedResponse(trimmed, null, extractedThreadId)
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
