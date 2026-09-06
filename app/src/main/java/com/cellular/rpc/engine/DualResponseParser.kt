package com.cellular.rpc.engine

data class DualParsedResponse(
    val conversationalText: String,
    val widgetData: WidgetData?
)

object DualResponseParser {
    /**
     * Extracts both conversational commentary and embedded structured WidgetData from incoming text.
     * E.g.: "Here is your SF forecast!\n```json\n{"type":"weather","temp":72,"city":"SF","cond":"Sunny"}\n```"
     * -> conversationalText: "Here is your SF forecast!"
     * -> widgetData: WidgetData.Weather(...)
     */
    fun parse(rawText: String): DualParsedResponse {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return DualParsedResponse("", null)
        }

        // 1. If pure JSON, parse directly
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val widget = WidgetData.parse(trimmed)
            if (widget != null && widget !is WidgetData.ChatText) {
                return DualParsedResponse("", widget)
            }
        }

        // 2. Search for embedded JSON (e.g. ```json ... ``` or { ... })
        val jsonBlockRegex = Regex("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?\\})\\s*```")
        val match = jsonBlockRegex.find(trimmed)

        if (match != null) {
            val jsonCandidate = match.groupValues[1]
            val widget = WidgetData.parse(jsonCandidate)
            if (widget != null) {
                val cleanText = trimmed.removeRange(match.range).trim()
                return DualParsedResponse(cleanText, widget)
            }
        }

        val startIdx = trimmed.indexOf('{')
        val endIdx = trimmed.lastIndexOf('}')
        if (startIdx != -1 && endIdx > startIdx) {
            val candidate = trimmed.substring(startIdx, endIdx + 1)
            val widget = WidgetData.parse(candidate)
            if (widget != null && widget !is WidgetData.ChatText) {
                val prefix = trimmed.substring(0, startIdx).trim()
                val suffix = trimmed.substring(endIdx + 1).trim()
                val conversational = listOf(prefix, suffix).filter { it.isNotEmpty() }.joinToString("\n")
                return DualParsedResponse(conversational, widget)
            }
        }

        return DualParsedResponse(trimmed, null)
    }
}
