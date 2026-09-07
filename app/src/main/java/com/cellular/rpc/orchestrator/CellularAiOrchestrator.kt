package com.cellular.rpc.orchestrator

import android.content.Context
import com.cellular.rpc.domain.dynamic.DynamicFeature
import com.cellular.rpc.domain.dynamic.DynamicFeatureManager
import com.cellular.rpc.domain.dynamic.DynamicFeatureWireParser
import com.cellular.rpc.engine.DualParsedResponse
import com.cellular.rpc.engine.DualResponseParser
import com.cellular.rpc.engine.WidgetData
import org.json.JSONObject

enum class OrchestratorSchemaType {
    AUTO,
    SDUI_BLUEPRINT,
    DYNAMIC_MINIAPP,
    TOOL_WIDGET,
    RAW_CONVERSATIONAL
}

data class OutboundOrchestrationResult(
    val wireText: String,
    val threadId: String?,
    val schemaType: OrchestratorSchemaType,
    val estimatedBytes: Int
)

data class InboundOrchestrationResult(
    val conversationalProse: String,
    val widgetData: WidgetData?,
    val installedMiniApp: DynamicFeature?,
    val threadId: String?,
    val rawJson: String?
)

/**
 * Universal Bidirectional AI <-> App Orchestration Layer.
 *
 * Governs the structured data lifecycle between mobile client and remote AI gateway over cellular SMS/MMS:
 * - Outbound: Injects compact schema contracts, thread session headers, and template specifications.
 * - Inbound: Dissects multi-part wire frames, parses SDUI blueprints, compiles dynamic mini-app ASTs,
 *   and registers them into the runtime state machine.
 */
class CellularAiOrchestrator(
    private val context: Context
) {
    companion object {
        @Volatile
        private var INSTANCE: CellularAiOrchestrator? = null

        fun getInstance(context: Context): CellularAiOrchestrator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CellularAiOrchestrator(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Schema Trigger Prefixes for AI
        const val SCHEMA_SDUI_PROMPT = "[SCHEMA:SDUI]"
        const val SCHEMA_MINIAPP_PROMPT = "[SCHEMA:MINIAPP]"
    }

    /**
     * Prepares an outbound message to the AI, attaching thread session ID
     * and compact JSON schema contracts if structured output is requested.
     */
    fun prepareOutbound(
        userPrompt: String,
        threadId: String? = null,
        schemaType: OrchestratorSchemaType = OrchestratorSchemaType.AUTO,
        templateId: String? = null
    ): OutboundOrchestrationResult {
        val sb = StringBuilder()

        // 1. Attach Thread ID Header if present
        if (!threadId.isNullOrBlank()) {
            sb.append("[TID:").append(threadId.trim()).append("] ")
        }

        // 2. Attach Schema / Template Directives if explicitly requested
        when (schemaType) {
            OrchestratorSchemaType.SDUI_BLUEPRINT -> {
                sb.append(SCHEMA_SDUI_PROMPT).append(" ")
            }
            OrchestratorSchemaType.DYNAMIC_MINIAPP -> {
                sb.append(SCHEMA_MINIAPP_PROMPT).append(" ")
            }
            else -> { /* Standard / Auto */ }
        }

        // 3. Attach template context if a specific template was chosen
        if (!templateId.isNullOrBlank()) {
            val template = OrchestratorTemplateCatalog.getById(templateId)
            if (template != null) {
                sb.append(template.schemaPromptPrefix).append(" ")
            }
        }

        // 4. Attach User Prompt Text
        sb.append(userPrompt.trim())

        val wireText = sb.toString()
        return OutboundOrchestrationResult(
            wireText = wireText,
            threadId = threadId,
            schemaType = schemaType,
            estimatedBytes = wireText.toByteArray(Charsets.UTF_8).size
        )
    }

    /**
     * Ingests an incoming raw cellular payload from the AI, extracting:
     * 1. Conversational prose
     * 2. Embedded Server-Driven UI (SDUI) blueprints or widget cards
     * 3. Installable Dynamic Mini-Apps (AST features)
     * 4. Thread session identifiers
     */
    suspend fun processInbound(rawPayload: String, senderAddress: String = ""): InboundOrchestrationResult {
        val trimmed = rawPayload.trim()
        if (trimmed.isEmpty()) {
            return InboundOrchestrationResult("", null, null, null, null)
        }

        // Step 1: Check if the payload contains a dynamic mini-app feature definition [APP:BUILD:...]
        if (DynamicFeatureWireParser.isAppBuildPayload(trimmed)) {
            val feature = DynamicFeatureWireParser.parsePayload(trimmed)
            if (feature != null) {
                DynamicFeatureManager.handleInboundPayload(context, trimmed, senderAddress)
                val cleanProse = "Installed dynamic mini-app: ${feature.title}"
                return InboundOrchestrationResult(
                    conversationalProse = cleanProse,
                    widgetData = null,
                    installedMiniApp = feature,
                    threadId = null,
                    rawJson = trimmed
                )
            }
        }

        // Step 2: Use DualResponseParser to extract dual text + widget
        val dualParsed = DualResponseParser.parse(trimmed)
        val cleanProse = dualParsed.conversationalText
        val parsedWidget = dualParsed.widgetData
        val extractedThreadId = dualParsed.threadId

        val extractedCandidateJson = extractJsonSubstring(trimmed)

        return InboundOrchestrationResult(
            conversationalProse = cleanProse,
            widgetData = parsedWidget,
            installedMiniApp = null,
            threadId = extractedThreadId,
            rawJson = extractedCandidateJson
        )
    }

    private fun extractJsonSubstring(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start != -1 && end > start) {
            return text.substring(start, end + 1)
        }
        return null
    }
}
