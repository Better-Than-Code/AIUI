package com.cellular.rpc.domain.dynamic

import org.json.JSONObject

/**
 * Validated in-memory representation of a dynamic cellular micro-app.
 */
data class DynamicFeature(
    val featureId: String,
    val title: String,
    val version: String,
    val description: String = "",
    val iconName: String = "extension",
    val initialStateJson: String,
    val currentStateJson: String,
    val uiAstJson: String,
    val jsLogic: String
)

/**
 * Parser for incoming cellular [APP:BUILD:<feature_id>] text payloads.
 *
 * Wire payload format:
 * [APP:BUILD:calculator]{
 *   "title": "Solar Calculator",
 *   "version": "1.0.0",
 *   "description": "Calculates solar panel array output",
 *   "icon": "solar",
 *   "state": { "kw": 5.0, "hours": 6, "result": 30 },
 *   "ui": {
 *     "type": "Column",
 *     "children": [ ... ]
 *   },
 *   "js": "function calculate() { state.result = state.kw * state.hours; bridge.notify('Solar Calc', 'Generated ' + state.result + ' kWh'); bridge.commit(JSON.stringify(state)); }"
 * }
 */
object DynamicFeatureWireParser {

    private val APP_BUILD_REGEX = Regex("""\[APP:BUILD:([a-zA-Z0-9_-]+)\](.*)""", RegexOption.DOT_MATCHES_ALL)

    fun isAppBuildPayload(text: String): Boolean {
        return APP_BUILD_REGEX.containsMatchIn(text.trim())
    }

    fun parsePayload(text: String): DynamicFeature? {
        val trimmed = text.trim()
        val match = APP_BUILD_REGEX.find(trimmed) ?: return null
        val featureId = match.groupValues[1].trim()
        val jsonBody = match.groupValues[2].trim()

        if (featureId.isEmpty() || jsonBody.isEmpty()) return null

        return try {
            val root = JSONObject(jsonBody)

            val title = root.optString("title", featureId.replaceFirstChar { it.uppercase() })
            val version = root.optString("version", "1.0.0")
            val description = root.optString("description", "")
            val iconName = root.optString("icon", "extension")

            val stateObj = root.optJSONObject("state") ?: JSONObject()
            val initialStateJson = stateObj.toString()

            val uiObj = root.optJSONObject("ui") ?: JSONObject().apply {
                put("type", "Column")
                put("children", org.json.JSONArray())
            }
            val uiAstJson = uiObj.toString()

            val jsLogic = root.optString("js", "")

            DynamicFeature(
                featureId = featureId,
                title = title,
                version = version,
                description = description,
                iconName = iconName,
                initialStateJson = initialStateJson,
                currentStateJson = initialStateJson,
                uiAstJson = uiAstJson,
                jsLogic = jsLogic
            )
        } catch (e: Exception) {
            null
        }
    }
}
