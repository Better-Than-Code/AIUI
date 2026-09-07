package com.cellular.rpc.engine

/**
 * Balanced-brace JSON stream demuxer for incoming SMS strings that may contain
 * concatenated or batched JSON objects. Tracks nested '{' and '}' depth while
 * respecting quoted strings and escape sequences.
 */
object JsonStreamDemuxer {

    fun extractJsonObjects(input: String): List<String> {
        val results = mutableListOf<String>()
        if (input.isBlank()) return results

        var cursor = 0
        val length = input.length

        while (cursor < length) {
            val startIdx = input.indexOf('{', cursor)
            if (startIdx == -1) break

            var depth = 0
            var inString = false
            var escaped = false
            var endIdx = -1

            for (i in startIdx until length) {
                val c = input[i]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (c == '\\') {
                    escaped = true
                    continue
                }
                if (c == '"') {
                    inString = !inString
                    continue
                }
                if (!inString) {
                    if (c == '{') {
                        depth++
                    } else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            endIdx = i
                            break
                        }
                    }
                }
            }

            if (endIdx != -1 && endIdx > startIdx) {
                val jsonCandidate = input.substring(startIdx, endIdx + 1).trim()
                if (jsonCandidate.isNotEmpty()) {
                    results.add(jsonCandidate)
                }
                cursor = endIdx + 1
            } else {
                // Unbalanced or incomplete brace
                break
            }
        }

        return results
    }
}
