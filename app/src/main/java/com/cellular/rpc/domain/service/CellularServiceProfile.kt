package com.cellular.rpc.domain.service

import org.json.JSONObject

/**
 * Protocol framing modes supported across different AI SMS service backends.
 */
enum class ServiceProtocolMode(val displayName: String, val description: String) {
    SMS_CONVERSATIONAL(
        displayName = "Standard AI SMS Prompt",
        description = "Direct natural language prompts for AI SMS services"
    ),
    PALLY_COMPACT(
        displayName = "Pally Cellular RPC",
        description = "Compact micro-wire framing (~...# and REQ/RES) with 304 ETag caching"
    ),
    JSON_WIRE(
        displayName = "Structured JSON Wire",
        description = "Standard JSON RPC payloads over SMS/MMS for programmable LLM gateways"
    )
}

/**
 * Profile configuration for any AI SMS service backend.
 *
 * Agnostic to any AI SMS service: user can enter their provider's phone number,
 * give it a custom name, rename it, delete it, or switch between numbers.
 */
data class CellularServiceProfile(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val description: String = "",
    val protocolMode: ServiceProtocolMode = ServiceProtocolMode.SMS_CONVERSATIONAL,
    val promptPrefix: String = "",
    val isBuiltIn: Boolean = false,
    val colorHex: Long = 0xFF00E5FF
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("phoneNumber", phoneNumber)
        json.put("description", description)
        json.put("protocolMode", protocolMode.name)
        json.put("promptPrefix", promptPrefix)
        json.put("isBuiltIn", isBuiltIn)
        json.put("colorHex", colorHex)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): CellularServiceProfile? {
            return try {
                val obj = JSONObject(jsonStr)
                CellularServiceProfile(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    phoneNumber = obj.getString("phoneNumber"),
                    description = obj.optString("description", ""),
                    protocolMode = try {
                        ServiceProtocolMode.valueOf(obj.optString("protocolMode", ServiceProtocolMode.SMS_CONVERSATIONAL.name))
                    } catch (e: Exception) {
                        ServiceProtocolMode.SMS_CONVERSATIONAL
                    },
                    promptPrefix = obj.optString("promptPrefix", ""),
                    isBuiltIn = obj.optBoolean("isBuiltIn", false),
                    colorHex = obj.optLong("colorHex", 0xFF00E5FF)
                )
            } catch (e: Exception) {
                null
            }
        }

        val DEFAULT_AI = CellularServiceProfile(
            id = "ai_default",
            name = "Pally AI",
            phoneNumber = "+16462619684",
            description = "AI SMS Assistant",
            protocolMode = ServiceProtocolMode.SMS_CONVERSATIONAL,
            promptPrefix = "",
            isBuiltIn = false,
            colorHex = 0xFF00E5FF
        )

        val DEFAULT_PALLY = DEFAULT_AI

        fun createCustom(
            name: String,
            phoneNumber: String,
            description: String = "AI SMS endpoint",
            protocolMode: ServiceProtocolMode = ServiceProtocolMode.SMS_CONVERSATIONAL,
            promptPrefix: String = ""
        ): CellularServiceProfile {
            val safeId = "ai_${System.currentTimeMillis()}_${phoneNumber.takeLast(4).filter { it.isDigit() }}"
            return CellularServiceProfile(
                id = safeId,
                name = name.ifBlank { "AI (${phoneNumber.takeLast(10)})" },
                phoneNumber = phoneNumber.trim(),
                description = description,
                protocolMode = protocolMode,
                promptPrefix = promptPrefix,
                isBuiltIn = false,
                colorHex = 0xFF00E5FF
            )
        }
    }
}
