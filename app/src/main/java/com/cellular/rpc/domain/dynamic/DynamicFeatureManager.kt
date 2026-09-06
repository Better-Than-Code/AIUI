package com.cellular.rpc.domain.dynamic

import android.content.Context
import android.util.Log
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.ChatMessageEntity
import com.cellular.rpc.data.local.DynamicFeatureEntity
import com.cellular.rpc.engine.MessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manager and lifecycle dispatcher for dynamic cellular micro-apps deployed via SMS.
 */
object DynamicFeatureManager {

    private const val TAG = "DynamicFeatureManager"

    private val _featureInstalledEvents = MutableSharedFlow<DynamicFeatureEntity>(extraBufferCapacity = 32)
    val featureInstalledEvents: SharedFlow<DynamicFeatureEntity> = _featureInstalledEvents.asSharedFlow()

    /**
     * Attempts to intercept and process incoming [APP:BUILD:<feature_id>] text payloads.
     * Returns true if the message was an app-build payload and was successfully processed.
     */
    suspend fun handleInboundPayload(context: Context, rawText: String, sender: String = ""): Boolean {
        if (!DynamicFeatureWireParser.isAppBuildPayload(rawText)) {
            return false
        }

        val dynamicFeature = DynamicFeatureWireParser.parsePayload(rawText) ?: run {
            Log.w(TAG, "Malformed [APP:BUILD] payload intercepted, dropping safely.")
            return false
        }

        Log.i(TAG, "Installing/Updating dynamic feature: ${dynamicFeature.featureId} (${dynamicFeature.title} v${dynamicFeature.version})")

        val entity = DynamicFeatureEntity(
            featureId = dynamicFeature.featureId,
            title = dynamicFeature.title,
            version = dynamicFeature.version,
            description = dynamicFeature.description,
            iconName = dynamicFeature.iconName,
            initialStateJson = dynamicFeature.initialStateJson,
            currentStateJson = dynamicFeature.currentStateJson,
            uiAstJson = dynamicFeature.uiAstJson,
            jsLogic = dynamicFeature.jsLogic,
            lastUpdatedMs = System.currentTimeMillis()
        )

        val db = AppDatabase.getInstance(context)
        db.dynamicFeatureDao().insertOrUpdate(entity)

        // Save confirmation in chat log
        val chatEntity = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.AI_GATEWAY.name,
            text = "✨ [Cellular App Engine] Deployed '${dynamicFeature.title}' (v${dynamicFeature.version}) with AST UI & isolated JS logic.",
            widgetDataJson = null,
            byteSize = rawText.toByteArray(Charsets.UTF_8).size,
            pduCount = ((rawText.toByteArray(Charsets.UTF_8).size + 139) / 140).coerceAtLeast(1),
            timestampMs = System.currentTimeMillis()
        )
        db.chatMessageDao().insertMessage(chatEntity)

        _featureInstalledEvents.tryEmit(entity)
        return true
    }

    /**
     * Seeds initial sample offline dynamic features if database is empty.
     */
    fun seedSampleFeaturesIfEmpty(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            if (db.dynamicFeatureDao().getCount() == 0) {
                // 1. Solar Array Production Calculator
                val solarAst = """
                {
                  "type": "Column",
                  "spacing": 10,
                  "padding": 8,
                  "children": [
                    { "type": "Text", "text": "Solar Array Estimator", "style": "title", "bold": true },
                    { "type": "Text", "text": "Calculates daily generation based on panel capacity and peak sun hours.", "style": "caption" },
                    { "type": "Divider" },
                    {
                      "type": "Row",
                      "spacing": 8,
                      "children": [
                        { "type": "Input", "label": "Panel kW", "stateKey": "kw", "inputType": "decimal" },
                        { "type": "Input", "label": "Peak Sun Hours", "stateKey": "hours", "inputType": "decimal" }
                      ]
                    },
                    {
                      "type": "Card",
                      "padding": 10,
                      "spacing": 6,
                      "children": [
                        { "type": "Text", "text": "Estimated Output: {result} kWh / day", "style": "headline", "bold": true, "color": "#00E5FF" },
                        { "type": "Text", "text": "Monthly Estimate: {monthly} kWh", "style": "body" }
                      ]
                    },
                    {
                      "type": "Row",
                      "spacing": 8,
                      "children": [
                        { "type": "Btn", "label": "Calculate", "action": "calculate", "variant": "filled", "icon": "calculate" },
                        { "type": "Btn", "label": "Notify Output", "action": "notifyOutput", "variant": "outlined", "icon": "notify" }
                      ]
                    }
                  ]
                }
                """.trimIndent()

                val solarJs = """
                function calculate(p) {
                    state.result = Math.round((Number(state.kw || 5.0) * Number(state.hours || 5.5)) * 10) / 10;
                    state.monthly = Math.round(state.result * 30);
                    bridge.commit(JSON.stringify(state));
                }
                function notifyOutput(p) {
                    calculate();
                    bridge.notify("Solar Estimator", "Daily generation: " + state.result + " kWh");
                }
                """.trimIndent()

                db.dynamicFeatureDao().insertOrUpdate(
                    DynamicFeatureEntity(
                        featureId = "solar_calc",
                        title = "Solar Array Estimator",
                        version = "1.0.0",
                        description = "Solar panel output and daily generation calculator",
                        iconName = "solar",
                        initialStateJson = """{"kw": 6.5, "hours": 5.0, "result": 32.5, "monthly": 975}""",
                        currentStateJson = """{"kw": 6.5, "hours": 5.0, "result": 32.5, "monthly": 975}""",
                        uiAstJson = solarAst,
                        jsLogic = solarJs
                    )
                )

                // 2. Off-Grid Field Tally Counter
                val tallyAst = """
                {
                  "type": "Column",
                  "spacing": 12,
                  "padding": 8,
                  "alignment": "center",
                  "children": [
                    { "type": "Text", "text": "Field Inventory Counter", "style": "title", "bold": true },
                    { "type": "Card", "padding": 16, "alignment": "center", "children": [
                      { "type": "Text", "text": "{count}", "style": "headline", "bold": true, "color": "#00E5FF" },
                      { "type": "Text", "text": "Target: {target} units", "style": "caption" }
                    ]},
                    {
                      "type": "Row",
                      "spacing": 12,
                      "children": [
                        { "type": "Btn", "label": "-1", "action": "decrement", "variant": "outlined", "icon": "remove" },
                        { "type": "Btn", "label": "+1", "action": "increment", "variant": "filled", "icon": "add" },
                        { "type": "Btn", "label": "Reset", "action": "reset", "variant": "tonal", "icon": "refresh" }
                      ]
                    }
                  ]
                }
                """.trimIndent()

                val tallyJs = """
                function increment() {
                    state.count = (state.count || 0) + 1;
                    bridge.commit(JSON.stringify(state));
                }
                function decrement() {
                    state.count = Math.max(0, (state.count || 0) - 1);
                    bridge.commit(JSON.stringify(state));
                }
                function reset() {
                    state.count = 0;
                    bridge.commit(JSON.stringify(state));
                }
                """.trimIndent()

                db.dynamicFeatureDao().insertOrUpdate(
                    DynamicFeatureEntity(
                        featureId = "field_tally",
                        title = "Field Inventory Counter",
                        version = "1.0.0",
                        description = "Offline item inventory and tally recorder",
                        iconName = "add",
                        initialStateJson = """{"count": 12, "target": 50}""",
                        currentStateJson = """{"count": 12, "target": 50}""",
                        uiAstJson = tallyAst,
                        jsLogic = tallyJs
                    )
                )
            }
        }
    }
}
