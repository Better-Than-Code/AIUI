package com.cellular.rpc.domain.dynamic

import org.json.JSONArray
import org.json.JSONObject

/**
 * Models for the Dynamic UI Abstract Syntax Tree (AST).
 */
sealed class AstNode {
    abstract val type: String
    abstract val key: String?

    data class Container(
        override val type: String, // "Column", "Row", "Card", "Box"
        override val key: String? = null,
        val spacing: Int = 8,
        val padding: Int = 8,
        val horizontalAlignment: String = "start", // "start", "center", "end"
        val children: List<AstNode> = emptyList()
    ) : AstNode()

    data class TextNode(
        override val type: String = "Text",
        override val key: String? = null,
        val text: String, // May contain template variables e.g. "Count: {count}"
        val style: String = "body", // "headline", "title", "body", "label", "caption"
        val isBold: Boolean = false,
        val colorHex: String? = null
    ) : AstNode()

    data class ButtonNode(
        override val type: String = "Btn",
        override val key: String? = null,
        val label: String,
        val action: String, // JavaScript function/action name to execute e.g. "increment"
        val paramsJson: String = "{}",
        val variant: String = "filled", // "filled", "outlined", "tonal", "text"
        val icon: String? = null
    ) : AstNode()

    data class InputNode(
        override val type: String = "Input",
        override val key: String? = null,
        val label: String,
        val stateKey: String, // Property in state to bind two-way
        val inputType: String = "text" // "text", "number", "decimal"
    ) : AstNode()

    data class DividerNode(
        override val type: String = "Divider",
        override val key: String? = null
    ) : AstNode()

    data class UnknownNode(
        override val type: String,
        override val key: String? = null,
        val rawJson: String
    ) : AstNode()
}

object AstParser {

    fun parse(jsonStr: String): AstNode {
        return try {
            val root = JSONObject(jsonStr)
            parseNode(root)
        } catch (e: Exception) {
            AstNode.UnknownNode("Error", null, jsonStr)
        }
    }

    fun parseNode(obj: JSONObject): AstNode {
        val type = obj.optString("type", "Column")
        val key = obj.optString("key").takeIf { it.isNotBlank() }

        return when (type) {
            "Column", "Row", "Card", "Box" -> {
                val spacing = obj.optInt("spacing", 8)
                val padding = obj.optInt("padding", 8)
                val horizontalAlignment = obj.optString("alignment", "start")
                val childrenJson = obj.optJSONArray("children") ?: JSONArray()
                val children = mutableListOf<AstNode>()
                for (i in 0 until childrenJson.length()) {
                    val childObj = childrenJson.optJSONObject(i)
                    if (childObj != null) {
                        children.add(parseNode(childObj))
                    }
                }
                AstNode.Container(
                    type = type,
                    key = key,
                    spacing = spacing,
                    padding = padding,
                    horizontalAlignment = horizontalAlignment,
                    children = children
                )
            }
            "Text" -> {
                val text = obj.optString("text", "")
                val style = obj.optString("style", "body")
                val isBold = obj.optBoolean("bold", false)
                val colorHex = obj.optString("color").takeIf { it.isNotBlank() }
                AstNode.TextNode(
                    key = key,
                    text = text,
                    style = style,
                    isBold = isBold,
                    colorHex = colorHex
                )
            }
            "Btn", "Button" -> {
                val label = obj.optString("label", "Action")
                val action = obj.optString("action", "")
                val paramsObj = obj.optJSONObject("params")
                val paramsJson = paramsObj?.toString() ?: "{}"
                val variant = obj.optString("variant", "filled")
                val icon = obj.optString("icon").takeIf { it.isNotBlank() }
                AstNode.ButtonNode(
                    key = key,
                    label = label,
                    action = action,
                    paramsJson = paramsJson,
                    variant = variant,
                    icon = icon
                )
            }
            "Input", "TextField" -> {
                val label = obj.optString("label", "")
                val stateKey = obj.optString("stateKey", "value")
                val inputType = obj.optString("inputType", "text")
                AstNode.InputNode(
                    key = key,
                    label = label,
                    stateKey = stateKey,
                    inputType = inputType
                )
            }
            "Divider" -> AstNode.DividerNode(key = key)
            else -> AstNode.UnknownNode(type, key, obj.toString())
        }
    }

    /**
     * Replaces `{key}` tokens in text with live values from the state dictionary.
     */
    fun resolveTemplate(template: String, stateJson: String): String {
        if (!template.contains("{")) return template
        return try {
            val stateObj = JSONObject(stateJson)
            var result = template
            val regex = Regex("""\{([a-zA-Z0-9_]+)\}""")
            regex.findAll(template).forEach { match ->
                val token = match.groupValues[1]
                if (stateObj.has(token)) {
                    val value = stateObj.opt(token)?.toString() ?: ""
                    result = result.replace("{${token}}", value)
                }
            }
            result
        } catch (e: Exception) {
            template
        }
    }
}
