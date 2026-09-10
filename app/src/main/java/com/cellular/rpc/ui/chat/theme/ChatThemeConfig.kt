package com.cellular.rpc.ui.chat.theme

import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/**
 * Declarative Chat UI Theme Configuration.
 * 
 * Controls bubble geometries, colors, border strokes, typography scales,
 * and entry animations dynamically. Can be patched in real-time over cellular
 * natural language without restarting the application.
 */
data class ChatThemeConfig(
    val bubbleCornerRadiusDp: Int = 18,
    val incomingBubbleColorHex: String = "#1E293B",
    val outgoingBubbleColorHex: String = "#00E5FF",
    val incomingTextColorHex: String = "#F8FAFC",
    val outgoingTextColorHex: String = "#030712",
    val bubbleBorderWidthDp: Float = 1.0f,
    val bubbleBorderColorHex: String = "#334155",
    val fontScaleMultiplier: Float = 1.0f,
    val animationStyle: String = "slide", // "slide", "fade", "pop"
    val showPduBadge: Boolean = true
) {
    val incomingBubbleColor: Color
        get() = parseHexColor(incomingBubbleColorHex, Color(0xFF1E293B))

    val outgoingBubbleColor: Color
        get() = parseHexColor(outgoingBubbleColorHex, Color(0xFF00E5FF))

    val incomingTextColor: Color
        get() {
            val bg = incomingBubbleColor
            val fg = parseHexColor(incomingTextColorHex, Color(0xFFF8FAFC))
            return ensureWcagAaContrast(fg, bg)
        }

    val outgoingTextColor: Color
        get() {
            val bg = outgoingBubbleColor
            val fg = parseHexColor(outgoingTextColorHex, Color(0xFF030712))
            return ensureWcagAaContrast(fg, bg)
        }

    val bubbleBorderColor: Color
        get() = parseHexColor(bubbleBorderColorHex, Color(0xFF334155))

    fun toJson(): String {
        return JSONObject().apply {
            put("bubbleCornerRadiusDp", bubbleCornerRadiusDp)
            put("incomingBubbleColorHex", incomingBubbleColorHex)
            put("outgoingBubbleColorHex", outgoingBubbleColorHex)
            put("incomingTextColorHex", incomingTextColorHex)
            put("outgoingTextColorHex", outgoingTextColorHex)
            put("bubbleBorderWidthDp", bubbleBorderWidthDp.toDouble())
            put("bubbleBorderColorHex", bubbleBorderColorHex)
            put("fontScaleMultiplier", fontScaleMultiplier.toDouble())
            put("animationStyle", animationStyle)
            put("showPduBadge", showPduBadge)
        }.toString()
    }

    companion object {
        val DEFAULT = ChatThemeConfig()

        fun fromJson(jsonStr: String): ChatThemeConfig {
            return try {
                val obj = JSONObject(jsonStr)
                ChatThemeConfig(
                    bubbleCornerRadiusDp = obj.optInt("bubbleCornerRadiusDp", 18),
                    incomingBubbleColorHex = obj.optString("incomingBubbleColorHex", "#1E293B"),
                    outgoingBubbleColorHex = obj.optString("outgoingBubbleColorHex", "#00E5FF"),
                    incomingTextColorHex = obj.optString("incomingTextColorHex", "#F8FAFC"),
                    outgoingTextColorHex = obj.optString("outgoingTextColorHex", "#030712"),
                    bubbleBorderWidthDp = obj.optDouble("bubbleBorderWidthDp", 1.0).toFloat(),
                    bubbleBorderColorHex = obj.optString("bubbleBorderColorHex", "#334155"),
                    fontScaleMultiplier = obj.optDouble("fontScaleMultiplier", 1.0).toFloat(),
                    animationStyle = obj.optString("animationStyle", "slide"),
                    showPduBadge = obj.optBoolean("showPduBadge", true)
                )
            } catch (e: Exception) {
                DEFAULT
            }
        }

        private fun parseHexColor(hex: String, fallback: Color): Color {
            return try {
                Color(android.graphics.Color.parseColor(hex))
            } catch (e: Exception) {
                fallback
            }
        }

        /**
         * Calculates relative luminance according to WCAG 2.1/2.2 standard:
         * L = 0.2126 * R + 0.7152 * G + 0.0722 * B
         */
        fun calculateLuminance(color: Color): Float {
            fun linearize(channel: Float): Float {
                return if (channel <= 0.04045f) {
                    channel / 12.92f
                } else {
                    Math.pow(((channel + 0.055) / 1.055).toDouble(), 2.4).toFloat()
                }
            }
            val r = linearize(color.red)
            val g = linearize(color.green)
            val b = linearize(color.blue)
            return 0.2126f * r + 0.7152f * g + 0.0722f * b
        }

        fun calculateContrastRatio(c1: Color, c2: Color): Float {
            val l1 = calculateLuminance(c1)
            val l2 = calculateLuminance(c2)
            val lighter = maxOf(l1, l2)
            val darker = minOf(l1, l2)
            return (lighter + 0.05f) / (darker + 0.05f)
        }

        /**
         * Enforces WCAG 2.2 Level AA (minimum 4.5:1 ratio).
         * If the requested text color against the bubble background fails this standard,
         * automatically selects high-contrast light or dark fallback.
         */
        fun ensureWcagAaContrast(textColor: Color, backgroundColor: Color): Color {
            val ratio = calculateContrastRatio(textColor, backgroundColor)
            if (ratio >= 4.5f) return textColor

            val lightCandidate = Color(0xFFFFFFFF)
            val darkCandidate = Color(0xFF0A0F1D)
            val lightRatio = calculateContrastRatio(lightCandidate, backgroundColor)
            val darkRatio = calculateContrastRatio(darkCandidate, backgroundColor)

            return if (lightRatio >= darkRatio) lightCandidate else darkCandidate
        }
    }
}
