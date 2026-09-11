package com.cellular.rpc.engine

import org.json.JSONObject
import java.util.Stack

/**
 * Fuzzy Stream Demuxer & Self-Repairing JSON Parser.
 * Rescues truncated, half-delivered, or malformed JSON payloads from SMS/MMS streams
 * by repairing unclosed quotes, brackets, unbalanced braces, and trailing commas.
 */
object FuzzyStreamDemuxer {

    /**
     * Attempts to parse raw candidate JSON. If standard parsing fails,
     * applies heuristic repairs to rescue the structured payload.
     */
    fun repairAndParse(rawJson: String): JSONObject? {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return null

        // 1. Try standard JSON parse first
        try {
            return JSONObject(trimmed)
        } catch (_: Exception) {
            // Proceed to heuristic repair
        }

        // 2. Extract potential JSON boundary between first '{' and last '}' or end of string
        val firstBrace = trimmed.indexOf('{')
        if (firstBrace == -1) return null

        var candidate = trimmed.substring(firstBrace)

        // Clean unescaped control characters and invalid trailing characters
        candidate = sanitizeJsonCandidate(candidate)

        // 3. Balance quotes and structural braces
        val repaired = autoBalanceJson(candidate)

        return try {
            JSONObject(repaired)
        } catch (_: Exception) {
            // 4. Fallback: try aggressive trailing cleanup
            try {
                val aggressive = aggressiveTrailingFix(repaired)
                JSONObject(aggressive)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun sanitizeJsonCandidate(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            // Remove markdown code fence remnants if any
            .replace("```json", "")
            .replace("```", "")
            .trim()
    }

    private fun autoBalanceJson(input: String): String {
        val sb = StringBuilder()
        val stack = Stack<Char>()
        var inString = false
        var isEscaped = false

        for (i in input.indices) {
            val c = input[i]

            if (inString) {
                if (isEscaped) {
                    isEscaped = false
                    sb.append(c)
                } else if (c == '\\') {
                    isEscaped = true
                    sb.append(c)
                } else if (c == '"') {
                    inString = false
                    sb.append(c)
                } else {
                    sb.append(c)
                }
            } else {
                when (c) {
                    '"' -> {
                        inString = true
                        sb.append(c)
                    }
                    '{' -> {
                        stack.push('}')
                        sb.append(c)
                    }
                    '[' -> {
                        stack.push(']')
                        sb.append(c)
                    }
                    '}' -> {
                        if (stack.isNotEmpty() && stack.peek() == '}') {
                            stack.pop()
                        }
                        sb.append(c)
                    }
                    ']' -> {
                        if (stack.isNotEmpty() && stack.peek() == ']') {
                            stack.pop()
                        }
                        sb.append(c)
                    }
                    else -> sb.append(c)
                }
            }
        }

        // If string was left open, close it
        if (inString) {
            sb.append('"')
        }

        // Clean any trailing comma before closing braces
        var result = sb.toString().trim()
        while (result.endsWith(",") || result.endsWith(":") || result.endsWith(",")) {
            result = result.dropLast(1).trim()
        }

        // Close all unclosed brackets/braces in reverse order
        val closeBuffer = StringBuilder(result)
        while (stack.isNotEmpty()) {
            val needed = stack.pop()
            closeBuffer.append(needed)
        }

        return closeBuffer.toString()
    }

    private fun aggressiveTrailingFix(jsonStr: String): String {
        // Strip trailing incomplete key-value pairs (e.g., ,"incomplete_key":)
        var fixed = jsonStr
        val lastComma = fixed.lastIndexOf(',')
        val lastBrace = fixed.lastIndexOf('}')
        if (lastComma > 0 && lastComma > lastBrace) {
            fixed = fixed.substring(0, lastComma) + "}"
        }
        return fixed
    }
}
