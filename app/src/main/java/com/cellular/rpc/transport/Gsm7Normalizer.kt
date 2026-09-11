package com.cellular.rpc.transport

/**
 * GSM-7 Normalizer & Wire Accountant.
 * Transliterates Unicode smart punctuation, typographic glyphs, and non-GSM characters
 * into standard 7-bit GSM-7 equivalents to guarantee 160-character single-SMS capacity
 * and prevent carrier 70-character UCS-2 fee escalation.
 */
object Gsm7Normalizer {

    // Standard GSM 7-bit default alphabet character set
    private val GSM7_CHARS = (
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ" +
        " !\"#¤%&'()*+,-./0123456789:;<=>?" +
        "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§" +
        "¿abcdefghijklmnopqrstuvwxyzäöñüà"
    ).toSet()

    // Extended GSM-7 characters (cost 2 bytes per char via escape 0x1B)
    private val GSM7_EXTENDED_CHARS = setOf('^', '{', '}', '\\', '[', '~', ']', '|', '€')

    /**
     * Transliterates smart quotes, dashes, ellipses, and unicode symbols into GSM-7 equivalents.
     */
    fun normalizeToGsm7(text: String): String {
        if (text.isEmpty()) return text

        val sb = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                // Smart single quotes & apostrophes
                '‘', '’', '‚', '‛', '`', '´' -> sb.append('\'')
                // Smart double quotes
                '“', '”', '„', '‟', '«', '»' -> sb.append('"')
                // Dashes & hyphens
                '—', '–', '―', '‒', '−' -> sb.append('-')
                // Ellipses
                '…' -> sb.append("...")
                // Bullet points & symbols
                '•', '·', '∙' -> sb.append('*')
                '™' -> sb.append("(TM)")
                '©' -> sb.append("(C)")
                '®' -> sb.append("(R)")
                // Common fractions
                '½' -> sb.append("1/2")
                '¼' -> sb.append("1/4")
                '¾' -> sb.append("3/4")
                // Whitespace variants
                '\u00A0', '\u2007', '\u202F', '\uFEFF' -> sb.append(' ')
                else -> {
                    // If character is within standard or extended GSM7, preserve it
                    if (c in GSM7_CHARS || c in GSM7_EXTENDED_CHARS) {
                        sb.append(c)
                    } else {
                        // Fallback: strip accents or preserve if acceptable
                        sb.append(stripAccentOrFallback(c))
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun stripAccentOrFallback(c: Char): Char {
        return when (c) {
            'á', 'â', 'ã', 'ā', 'ă', 'ą' -> 'a'
            'Á', 'Â', 'Ã', 'Ā', 'Ă', 'Ą' -> 'A'
            'ć', 'č', 'ç', 'ĉ', 'ċ' -> 'c'
            'Ć', 'Č', 'Ç', 'Ĉ', 'Ċ' -> 'C'
            'ď', 'đ' -> 'd'
            'Ď', 'Đ' -> 'D'
            'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě' -> 'e'
            'Ê', 'Ë', 'Ē', 'Ĕ', 'Ė', 'Ę', 'Ě' -> 'E'
            'í', 'î', 'ï', 'ī', 'ĭ', 'į' -> 'i'
            'Í', 'Î', 'Ï', 'Ī', 'Ĭ', 'Į' -> 'I'
            'ł', 'ĺ', 'ľ', 'ļ' -> 'l'
            'Ł', 'Ĺ', 'Ľ', 'Ļ' -> 'L'
            'ń', 'ň', 'ņ', 'ŋ' -> 'n'
            'Ń', 'Ň', 'Ņ', 'Ŋ' -> 'N'
            'ó', 'ô', 'õ', 'ō', 'ŏ', 'ő' -> 'o'
            'Ó', 'Ô', 'Õ', 'Ō', 'Ŏ', 'Ő' -> 'O'
            'ŕ', 'ř', 'ŗ' -> 'r'
            'Ŕ', 'Ř', 'Ŗ' -> 'R'
            'ś', 'š', 'ş', 'ŝ', 'ș' -> 's'
            'Ś', 'Š', 'Ş', 'Ŝ', 'Ș' -> 'S'
            'ť', 'ţ', 'ț' -> 't'
            'Ť', 'Ţ', 'Ț' -> 'T'
            'ú', 'û', 'ū', 'ŭ', 'ů', 'ű', 'ų' -> 'u'
            'Ú', 'Û', 'Ū', 'Ŭ', 'Ů', 'Ű', 'Ų' -> 'U'
            'ý', 'ÿ', 'ŷ' -> 'y'
            'Ý', 'Ÿ', 'Ŷ' -> 'Y'
            'ź', 'ž', 'ż' -> 'z'
            'Ź', 'Ž', 'Ż' -> 'Z'
            else -> c
        }
    }

    /**
     * Computes the wire tariff statistics for a given text message.
     */
    fun analyzeWireTariff(rawText: String): WireTariff {
        val normalized = normalizeToGsm7(rawText)
        var isPureGsm7 = true
        var septetCount = 0

        for (c in normalized) {
            when {
                c in GSM7_CHARS -> septetCount += 1
                c in GSM7_EXTENDED_CHARS -> septetCount += 2 // Escaped
                else -> {
                    isPureGsm7 = false
                    break
                }
            }
        }

        return if (isPureGsm7) {
            val pduCount = if (septetCount <= 160) {
                1
            } else {
                // Multi-part SMS has a 6-byte UDH header, leaving 153 chars per PDU
                Math.ceil(septetCount.toDouble() / 153.0).toInt()
            }
            val remainingInCurrentPdu = if (septetCount <= 160) {
                160 - septetCount
            } else {
                val currentPduFill = septetCount % 153
                if (currentPduFill == 0) 0 else 153 - currentPduFill
            }
            WireTariff(
                encoding = "GSM-7",
                characterCount = normalized.length,
                pduCount = pduCount,
                remainingInPdu = remainingInCurrentPdu,
                isSinglePdu = pduCount == 1,
                normalizedText = normalized
            )
        } else {
            // UCS-2 encoding fallback (70 chars single, 67 multi-part)
            val charCount = rawText.length
            val pduCount = if (charCount <= 70) {
                1
            } else {
                Math.ceil(charCount.toDouble() / 67.0).toInt()
            }
            val remainingInCurrentPdu = if (charCount <= 70) {
                70 - charCount
            } else {
                val currentPduFill = charCount % 67
                if (currentPduFill == 0) 0 else 67 - currentPduFill
            }
            WireTariff(
                encoding = "UCS-2",
                characterCount = charCount,
                pduCount = pduCount,
                remainingInPdu = remainingInCurrentPdu,
                isSinglePdu = pduCount == 1,
                normalizedText = rawText
            )
        }
    }
}

data class WireTariff(
    val encoding: String,
    val characterCount: Int,
    val pduCount: Int,
    val remainingInPdu: Int,
    val isSinglePdu: Boolean,
    val normalizedText: String
)
