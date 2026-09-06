package com.cellular.rpc.domain.schema

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe Central Registry for all Cellular RPC, Widget, and Tool schemas.
 *
 * Allows dynamic registration of new schemas without modifying core networking or
 * UI code. When a payload arrives over SMS or MMS, the registry resolves the
 * schema identifier, validates the structure, and deserializes the typed object.
 */
object CellularSchemaRegistry {

    private val registry = ConcurrentHashMap<String, CellularSchema<*>>()

    init {
        // Register core standard schemas
        register(WeatherSchema)
        register(NewsDigestSchema)
        register(MarketTickerSchema)
        register(CellularTransferSchema)
        register(CellularPollSchema)
        register(CellularToolSchema)
        register(ChatTextSchema)
        register(CalendarEventSchema)
        register(TaskChecklistSchema)
        register(SystemStatusSchema)
    }

    /**
     * Registers a new schema. If a schema with the same ID already exists,
     * it will be updated (allowing version bumps or plugins).
     */
    fun register(schema: CellularSchema<*>) {
        registry[schema.schemaId] = schema
    }

    /**
     * Unregisters a schema by its ID.
     */
    fun unregister(schemaId: String): Boolean {
        return registry.remove(schemaId) != null
    }

    /**
     * Retrieves a registered schema by ID.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getSchema(schemaId: String): CellularSchema<T>? {
        return registry[schemaId] as? CellularSchema<T>
    }

    /**
     * Retrieves all registered schemas ordered by category and display name.
     */
    fun getAllSchemas(): List<CellularSchema<*>> {
        return registry.values.sortedWith(
            compareBy({ it.category.ordinal }, { it.displayName })
        )
    }

    /**
     * Checks if a schema ID is registered.
     */
    fun hasSchema(schemaId: String): Boolean = registry.containsKey(schemaId)

    /**
     * Parses a raw string by resolving the matching schema.
     * Supports direct JSON or embedded JSON inside conversational text from any AI SMS service.
     * Returns a pair of (Schema, DomainObject) or null if unparseable or unknown.
     */
    fun parse(input: String): Pair<CellularSchema<*>, Any>? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // 1. Direct JSON parse
        parseJsonInternal(trimmed)?.let { return it }

        // 2. Embedded JSON block extraction
        val startIdx = trimmed.indexOf('{')
        val endIdx = trimmed.lastIndexOf('}')
        if (startIdx != -1 && endIdx > startIdx) {
            val candidate = trimmed.substring(startIdx, endIdx + 1)
            parseJsonInternal(candidate)?.let { return it }
        }

        return null
    }

    private fun parseJsonInternal(jsonString: String): Pair<CellularSchema<*>, Any>? {
        return try {
            val json = JSONObject(jsonString)
            val type = json.optString("type")
            if (type.isBlank()) return null
            val schema = registry[type] ?: return null
            val domainObj = schema.deserialize(json)
            Pair(schema, domainObj)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validates a typed object against its registered schema.
     */
    @Suppress("UNCHECKED_CAST")
    fun validate(schemaId: String, data: Any): SchemaValidationResult {
        val schema = registry[schemaId] as? CellularSchema<Any>
            ?: return SchemaValidationResult.Invalid(listOf("Unknown schema: $schemaId"))
        return schema.validate(data)
    }

    /**
     * Computes the 304 ETag hash for any registered data object.
     */
    @Suppress("UNCHECKED_CAST")
    fun computeHash(schemaId: String, data: Any): String {
        val schema = registry[schemaId] as? CellularSchema<Any>
            ?: return ""
        return schema.computeContentHash(data)
    }

    /**
     * Renders a plain-text fallback summary for any registered data object.
     */
    @Suppress("UNCHECKED_CAST")
    fun renderFallback(schemaId: String, data: Any): String {
        val schema = registry[schemaId] as? CellularSchema<Any>
            ?: return data.toString()
        return schema.renderFallbackSummary(data)
    }
}
