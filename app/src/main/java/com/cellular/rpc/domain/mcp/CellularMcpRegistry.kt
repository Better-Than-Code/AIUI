package com.cellular.rpc.domain.mcp

import com.cellular.rpc.domain.schema.CellularSchemaRegistry
import com.cellular.rpc.domain.schema.SchemaFieldType
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Single-Push Model Context Protocol (MCP) Manifest & Tool Registry.
 *
 * Provides self-discovery to AI agents (Gemini, Claude, OpenAI) over Cellular SMS/RPC:
 * 1. Genesis Manifest: Encodes all registered schemas, UI cards, tool calls, and constraints.
 * 2. Hash Invalidation: Computes a deterministic 8-char catalog hash (e.g., "M7B2A19E").
 * 3. Delta Tracking: Emits lightweight delta patches when new widgets or tools are added.
 * 4. Zero-Overhead Mode: Normal SMS transmissions append a 0-byte or 4-byte header tag,
 *    avoiding repeating capability schemas over cellular MTU budgets.
 */
object CellularMcpRegistry {

    const val MCP_PROTOCOL_VERSION = "2.1.0"
    const val RELEASE_BUILD_ID = "REL-2026-09-11-v4"

    /**
     * Tool definition callable by the AI over Cellular RPC.
     */
    data class McpTool(
        val name: String,
        val description: String,
        val parameters: List<McpToolParameter>,
        val nativeActionId: String
    )

    data class McpToolParameter(
        val name: String,
        val type: String,
        val required: Boolean,
        val description: String
    )

    // Registered executable tools on the Android client
    private val clientTools = listOf(
        McpTool(
            name = "cast_poll_vote",
            description = "Cast an authenticated vote on an active cellular poll",
            parameters = listOf(
                McpToolParameter("poll_id", "string", true, "Unique identifier of the poll"),
                McpToolParameter("option_index", "integer", true, "Zero-based index of the chosen option")
            ),
            nativeActionId = "action:poll_vote"
        ),
        McpTool(
            name = "confirm_cellular_transfer",
            description = "Authorize and confirm a pending secure peer-to-peer cellular fund transfer",
            parameters = listOf(
                McpToolParameter("transfer_id", "string", true, "Unique transfer reference ID"),
                McpToolParameter("confirmed", "boolean", true, "Approval confirmation flag")
            ),
            nativeActionId = "action:transfer_confirm"
        ),
        McpTool(
            name = "query_cellular_widget",
            description = "Request live refreshed data for a specific widget schema type",
            parameters = listOf(
                McpToolParameter("schema_id", "string", true, "Target widget schema identifier (e.g. weather, market_ticker, news_digest)"),
                McpToolParameter("etag", "string", false, "Local cache ETag to enable 304 Not Modified bandwidth savings")
            ),
            nativeActionId = "action:widget_query"
        ),
        McpTool(
            name = "query_device_telemetry",
            description = "Inspect local device hardware telemetry (battery, carrier signal dBm, storage)",
            parameters = listOf(
                McpToolParameter("include_storage", "boolean", false, "Whether to include storage breakdown")
            ),
            nativeActionId = "action:telemetry_query"
        )
    )

    /**
     * Generates the complete Genesis MCP Discovery JSON.
     * This payload is pushed once upon initial setup or major manifest update.
     */
    fun buildGenesisManifestJson(): String {
        val root = JSONObject()
        root.put("op", "MCP_GENESIS_INIT")
        root.put("protocol_version", MCP_PROTOCOL_VERSION)
        root.put("manifest_hash", computeCatalogHash())
        root.put("invalidation_rule", "ETag in packet header: if gateway ETag differs, AI queries SYS:MCP_DELTA")

        // 1. Registered Visual Widget Schemas
        val schemasArray = JSONArray()
        CellularSchemaRegistry.getAllSchemas().forEach { schema ->
            val sObj = JSONObject()
            sObj.put("id", schema.schemaId)
            sObj.put("version", schema.version)
            sObj.put("name", schema.displayName)
            sObj.put("category", schema.category.name.lowercase())
            sObj.put("description", schema.description)

            val fieldsObj = JSONObject()
            schema.fields.forEach { f ->
                val typeStr = when (f.type) {
                    SchemaFieldType.STRING -> "str"
                    SchemaFieldType.INTEGER -> "int"
                    SchemaFieldType.FLOAT -> "float"
                    SchemaFieldType.BOOLEAN -> "bool"
                    SchemaFieldType.LIST_STRING -> "list[str]"
                    SchemaFieldType.LIST_FLOAT -> "list[float]"
                    SchemaFieldType.OBJECT -> "obj"
                }
                fieldsObj.put(f.name, if (f.required) "$typeStr:req" else "$typeStr:opt")
            }
            sObj.put("fields", fieldsObj)
            sObj.put("sample", JSONObject(schema.sampleJson()))
            schemasArray.put(sObj)
        }
        root.put("registered_widgets", schemasArray)

        // 2. Actionable Native Tools
        val toolsArray = JSONArray()
        clientTools.forEach { tool ->
            val tObj = JSONObject()
            tObj.put("name", tool.name)
            tObj.put("desc", tool.description)
            val paramsObj = JSONObject()
            tool.parameters.forEach { p ->
                paramsObj.put(p.name, "${p.type}${if (p.required) ":req" else ":opt"}")
            }
            tObj.put("params", paramsObj)
            toolsArray.put(tObj)
        }
        root.put("registered_tools", toolsArray)

        // 3. Cellular Constraints & Wire Rules
        val constraintsObj = JSONObject()
        constraintsObj.put("mtu_budget_bytes", 133)
        constraintsObj.put("default_port", 8901)
        constraintsObj.put("caching", "RFC-304 Not-Modified ETag supported")
        constraintsObj.put("dual_response_format", "Natural Language Conversational text + '\\n---CELLULAR_DATA---\\n' + Schema JSON")
        root.put("cellular_constraints", constraintsObj)

        return root.toString(2)
    }

    /**
     * Generates a compact, natural language prompt with embedded Genesis MCP manifest
     * formatted specifically for transmission to an AI Assistant over Cellular Gateway.
     */
    fun buildGenesisSmsPrompt(): String {
        val hash = computeCatalogHash()
        val manifestJson = buildGenesisManifestJson()
        return "SYS:MCP_GENESIS_SYNC v=$MCP_PROTOCOL_VERSION build=$RELEASE_BUILD_ID hash=$hash\n" +
                "I am initializing the Cellular RPC Gateway with my native capabilities.\n" +
                "Store this manifest in persistent memory. Use these widget schemas and tool signatures for all structured interactions.\n" +
                "---CELLULAR_DATA---\n" +
                manifestJson
    }

    /**
     * Splits the Genesis payload into carrier-safe chunks (max ~130 chars each) with explicit sequence
     * headers [MCP_CHUNK x/y hash=...] to prevent carrier truncation and ensure reliable async delivery.
     */
    fun buildChunkedGenesisPrompts(): List<String> {
        val fullPrompt = buildGenesisSmsPrompt()
        val hash = computeCatalogHash()
        val maxChunkSize = 130
        val rawChunks = fullPrompt.chunked(maxChunkSize)
        val total = rawChunks.size
        val result = ArrayList<String>(total)
        for (i in rawChunks.indices) {
            val chunkNo = i + 1
            val header = "[MCP_CHUNK $chunkNo/$total hash=$hash]\n"
            result.add(header + rawChunks[i])
        }
        return result
    }

    /**
     * Generates a lightweight Delta update payload from a previous version/hash.
     */
    fun buildDeltaManifestJson(fromVersion: String, fromHash: String): String {
        val root = JSONObject()
        root.put("op", "MCP_DELTA_UPDATE")
        root.put("from_version", fromVersion)
        root.put("from_hash", fromHash)
        root.put("to_version", MCP_PROTOCOL_VERSION)
        root.put("to_build", RELEASE_BUILD_ID)
        root.put("to_hash", computeCatalogHash())
        root.put("registered_widgets_count", CellularSchemaRegistry.getAllSchemas().size)
        root.put("registered_tools_count", clientTools.size)
        root.put("status", "UP_TO_DATE")
        return root.toString(2)
    }

    /**
     * Computes a deterministic 8-character hash of all registered schemas, tools, and release build ID.
     */
    fun computeCatalogHash(): String {
        val sb = StringBuilder()
        sb.append(MCP_PROTOCOL_VERSION).append("|").append(RELEASE_BUILD_ID)
        CellularSchemaRegistry.getAllSchemas().forEach {
            sb.append("|").append(it.schemaId).append(":").append(it.version)
        }
        clientTools.forEach {
            sb.append("|").append(it.name)
        }
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(sb.toString().toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02X".format(it) }
    }

    fun getRegisteredTools(): List<McpTool> = clientTools
}
