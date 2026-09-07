package com.cellular.rpc.domain.protocol

/**
 * Sliding window scanner / stream tokenizer for incoming SMS PDUs that may batch
 * or concatenate multiple delimited frames into a single concatenated text string.
 */
object FrameTokenizer {

    data class TokenizeResult(
        val frames: List<Frame>,
        val unparsedText: String
    )

    fun containsFrames(rawBody: String): Boolean {
        return rawBody.contains("~") && tokenize(rawBody).frames.isNotEmpty()
    }

    /**
     * Scans raw incoming SMS string body for all occurrences of delimited frames:
     * ~<SES_ID>:<PKT_TYPE>:<SEQ>:<ACK_BITS>:<PAYLOAD>:<CRC16>#
     */
    fun tokenize(rawBody: String): TokenizeResult {
        val frames = mutableListOf<Frame>()
        val sbUnparsed = StringBuilder()

        var cursor = 0
        val length = rawBody.length

        while (cursor < length) {
            val nextTilde = rawBody.indexOf('~', cursor)
            if (nextTilde == -1) {
                // No more opening sentinels, remaining chars are unencapsulated text
                sbUnparsed.append(rawBody.substring(cursor))
                break
            }

            // Append any prefix text before the tilde as unparsed or legacy text
            if (nextTilde > cursor) {
                sbUnparsed.append(rawBody.substring(cursor, nextTilde))
            }

            // Find closing sentinel '#' after nextTilde
            val nextHash = rawBody.indexOf('#', nextTilde + 1)
            if (nextHash == -1) {
                // Incomplete frame at the end of PDU (or truncated batch)
                sbUnparsed.append(rawBody.substring(nextTilde))
                break
            }

            val candidate = rawBody.substring(nextTilde, nextHash + 1)
            val parsedFrame = Frame.fromAsciiWire(candidate)

            if (parsedFrame != null) {
                frames.add(parsedFrame)
                cursor = nextHash + 1
            } else {
                // Invalid frame (CRC or structure mismatch), treat tilde as literal or skip past
                sbUnparsed.append('~')
                cursor = nextTilde + 1
            }
        }

        return TokenizeResult(frames = frames, unparsedText = sbUnparsed.toString().trim())
    }
}
