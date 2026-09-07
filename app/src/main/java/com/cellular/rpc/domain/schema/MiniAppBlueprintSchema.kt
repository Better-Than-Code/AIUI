package com.cellular.rpc.domain.schema

import com.cellular.rpc.engine.WidgetData
import org.json.JSONObject

/**
 * Standard Schema for Mini App Blueprints (Deterministic Server-Driven UI AST).
 */
object MiniAppBlueprintSchema : CellularSchema<WidgetData.MiniAppPreview> {
    override val schemaId: String = "mini_app_blueprint"
    override val version: Int = 1
    override val displayName: String = "Universal Mini App"
    override val description: String = "Self-authoring Server-Driven UI AST blueprint with local reactive state and offline execution."
    override val category: SchemaCategory = SchemaCategory.PRODUCTIVITY

    override val fields: List<SchemaFieldDefinition> = listOf(
        SchemaFieldDefinition("appId", SchemaFieldType.STRING, true, "Unique application identifier", "app_sprint_tracker"),
        SchemaFieldDefinition("version", SchemaFieldType.INTEGER, false, "Blueprint schema version", "1"),
        SchemaFieldDefinition("metadata", SchemaFieldType.OBJECT, true, "Title, icon, description, category", "{\"title\":\"App\",\"icon\":\"checklist\"}"),
        SchemaFieldDefinition("initialState", SchemaFieldType.OBJECT, true, "Initial key-value reactive state", "{\"counter\":0}"),
        SchemaFieldDefinition("ui", SchemaFieldType.OBJECT, true, "Root UI AST node hierarchy", "{\"type\":\"Column\",\"children\":[]}")
    )

    override fun serialize(data: WidgetData.MiniAppPreview): String = data.toJson()
    override fun deserialize(json: JSONObject): WidgetData.MiniAppPreview = WidgetData.MiniAppPreview.fromJson(json)

    override fun renderFallbackSummary(data: WidgetData.MiniAppPreview): String {
        return "Mini-App: ${data.title} (v${data.version})"
    }

    override fun sampleJson(): String =
        """{"type":"mini_app_blueprint","appId":"app_counter","metadata":{"title":"Tally Counter","icon":"add"},"initialState":{"count":0},"ui":{"type":"Column","children":[{"type":"Text","text":"Count: 0"},{"type":"Button","label":"Increment","action":{"type":"SET_VALUE","key":"count","value":1}}]}}"""
}
