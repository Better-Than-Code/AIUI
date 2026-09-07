package com.cellular.rpc.domain.miniapp

import org.json.JSONArray
import org.json.JSONObject

/**
 * Universal Mini App Blueprint Parser and Schema Models.
 * Represents the full declarative UI graph, initial state, metadata, and actions.
 */
data class MiniAppBlueprint(
    val type: String = "mini_app_blueprint",
    val appId: String,
    val version: Int = 1,
    val metadata: MiniAppMetadata,
    val initialState: Map<String, Any?>,
    val uiRoot: MiniAppUiNode,
    val rawJson: String = ""
) {
    companion object {
        fun fromJson(jsonStr: String): MiniAppBlueprint? {
            return try {
                val obj = JSONObject(jsonStr)
                if (obj.optString("type") != "mini_app_blueprint" && !obj.has("appId")) {
                    return null
                }
                val appId = obj.optString("appId", "app_${System.currentTimeMillis()}")
                val version = obj.optInt("version", 1)
                val metaObj = obj.optJSONObject("metadata") ?: JSONObject()
                val metadata = MiniAppMetadata(
                    title = metaObj.optString("title", "Mini App"),
                    icon = metaObj.optString("icon", "checklist"),
                    description = metaObj.optString("description", ""),
                    category = metaObj.optString("category", "productivity")
                )
                val stateObj = obj.optJSONObject("initialState") ?: JSONObject()
                val initialState = jsonObjectToMap(stateObj)

                val uiObj = obj.optJSONObject("ui") ?: JSONObject()
                val uiRoot = parseUiNode(uiObj)

                MiniAppBlueprint(
                    type = "mini_app_blueprint",
                    appId = appId,
                    version = version,
                    metadata = metadata,
                    initialState = initialState,
                    uiRoot = uiRoot,
                    rawJson = jsonStr
                )
            } catch (e: Exception) {
                null
            }
        }

        fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = jsonValueToKotlin(obj.opt(k))
            }
            return map
        }

        fun jsonArrayToList(arr: JSONArray): List<Any?> {
            val list = mutableListOf<Any?>()
            for (i in 0 until arr.length()) {
                list.add(jsonValueToKotlin(arr.opt(i)))
            }
            return list
        }

        private fun jsonValueToKotlin(value: Any?): Any? {
            return when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }

        fun parseUiNode(obj: JSONObject): MiniAppUiNode {
            val type = obj.optString("type", "Column")
            val padding = obj.optInt("padding", 0)
            val spacing = obj.optInt("spacing", 0)
            val align = obj.optString("align", "")
            val text = obj.optString("text", "")
            val style = obj.optString("style", "BodyMedium")
            val bind = obj.optString("bind", "")
            val hint = obj.optString("hint", "")
            val icon = obj.optString("icon", "")
            val bindValue = obj.optString("bindValue", "")
            val bindChecked = obj.optString("bindChecked", "")
            val strikethroughWhen = obj.optString("strikethroughWhen", "")
            val bindItems = obj.optString("bindItems", "")
            val showInPreviewOnly = obj.optBoolean("showInPreviewOnly", false)

            val modifierMap = obj.optJSONObject("modifier")?.let { jsonObjectToMap(it) } ?: emptyMap()

            val onToggle = obj.optJSONObject("onToggle")?.let { jsonObjectToMap(it) }
            val onClick = obj.optJSONObject("onClick")?.let { jsonObjectToMap(it) }
            val actions = obj.optJSONObject("actions")?.let { jsonObjectToMap(it) }

            val itemTemplate = obj.optJSONObject("itemTemplate")?.let { parseUiNode(it) }

            val children = mutableListOf<MiniAppUiNode>()
            val childrenArr = obj.optJSONArray("children")
            if (childrenArr != null) {
                for (i in 0 until childrenArr.length()) {
                    val childObj = childrenArr.optJSONObject(i)
                    if (childObj != null) {
                        children.add(parseUiNode(childObj))
                    }
                }
            }

            return MiniAppUiNode(
                type = type,
                padding = padding,
                spacing = spacing,
                align = align,
                text = text,
                style = style,
                bind = bind,
                hint = hint,
                icon = icon,
                bindValue = bindValue,
                bindChecked = bindChecked,
                strikethroughWhen = strikethroughWhen,
                bindItems = bindItems,
                showInPreviewOnly = showInPreviewOnly,
                modifier = modifierMap,
                onToggle = onToggle,
                onClick = onClick,
                actions = actions,
                itemTemplate = itemTemplate,
                children = children
            )
        }
    }
}

data class MiniAppMetadata(
    val title: String,
    val icon: String = "checklist",
    val description: String = "",
    val category: String = "productivity"
)

data class MiniAppUiNode(
    val type: String,
    val padding: Int = 0,
    val spacing: Int = 0,
    val align: String = "",
    val text: String = "",
    val style: String = "BodyMedium",
    val bind: String = "",
    val hint: String = "",
    val icon: String = "",
    val bindValue: String = "",
    val bindChecked: String = "",
    val strikethroughWhen: String = "",
    val bindItems: String = "",
    val showInPreviewOnly: Boolean = false,
    val modifier: Map<String, Any?> = emptyMap(),
    val onToggle: Map<String, Any?>? = null,
    val onClick: Map<String, Any?>? = null,
    val actions: Map<String, Any?>? = null,
    val itemTemplate: MiniAppUiNode? = null,
    val children: List<MiniAppUiNode> = emptyList()
)
