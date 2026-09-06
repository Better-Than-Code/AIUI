package com.cellular.rpc.domain.schema

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Standard category classification for cellular schemas.
 */
enum class SchemaCategory {
    WIDGET,
    TOOL,
    TRANSACTION,
    FINANCIAL,
    SOCIAL,
    PRODUCTIVITY,
    SYSTEM
}

/**
 * Field data types supported in the standardized schema definition.
 */
enum class SchemaFieldType {
    STRING,
    INTEGER,
    FLOAT,
    BOOLEAN,
    LIST_STRING,
    LIST_FLOAT,
    OBJECT
}

/**
 * Metadata definition for an individual field within a schema.
 */
data class SchemaFieldDefinition(
    val name: String,
    val type: SchemaFieldType,
    val required: Boolean = true,
    val description: String = "",
    val exampleValue: String = ""
)

/**
 * Result of schema validation against a data payload instance.
 */
sealed class SchemaValidationResult {
    data object Valid : SchemaValidationResult()
    data class Invalid(val errors: List<String>) : SchemaValidationResult() {
        val errorMessage: String get() = errors.joinToString("; ")
    }
}

/**
 * Base contract for all standardized Cellular RPC and Widget schemas.
 *
 * Developers can register any domain object with the system by providing an
 * implementation of [CellularSchema] in [CellularSchemaRegistry].
 */
interface CellularSchema<T : Any> {
    val schemaId: String
    val version: Int
    val displayName: String
    val description: String
    val category: SchemaCategory
    val fields: List<SchemaFieldDefinition>

    /**
     * Serializes domain instance into a standardized JSON string.
     */
    fun serialize(data: T): String

    /**
     * Deserializes JSON into the typed domain instance.
     */
    fun deserialize(json: JSONObject): T

    /**
     * Computes the 32-bit (8 hex chars) SHA-256 content hash for ETag comparison.
     */
    fun computeContentHash(data: T): String {
        val json = serialize(data)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(json.toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates domain constraints (e.g. range bounds, required non-empty strings).
     */
    fun validate(data: T): SchemaValidationResult {
        return SchemaValidationResult.Valid
    }

    /**
     * Returns a human-friendly plain-text summary suitable for SMS fallback,
     * status bars, or screen readers.
     */
    fun renderFallbackSummary(data: T): String

    /**
     * Returns an example JSON string demonstrating the schema structure.
     */
    fun sampleJson(): String
}
