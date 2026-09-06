package com.cellular.rpc.domain.service

import org.json.JSONObject

/**
 * Protocol framing modes supported across different AI SMS service backends.
 */
enum class ServiceProtocolMode(val displayName: String, val description: String) {
    PALLY_COMPACT(
        displayName = "Pally Cellular RPC",
        description = "Compact micro-wire framing (~...# and REQ/RES) with 304 ETag caching"
    ),
    SMS_CONVERSATIONAL(
        displayName = "Standard AI SMS Prompt",
        description = "Direct natural language prompts for cloud AI SMS services (Twilio, OpenAI, etc.)"
    ),
    JSON_WIRE(
        displayName = "Structured JSON Wire",
        description = "Standard JSON RPC payloads over SMS/MMS for programmable LLM gateways"
    )
}

/**
 * Profile configuration for any AI SMS service backend.
 *
 * Enables the app to be completely provider-agnostic: users can select Pally AI,
 * cloud AI SMS gateways (Twilio, Telnyx, Sinch), self-hosted GSM LLM nodes,
 * or simply manually enter any custom destination phone number.
 */
data class CellularServiceProfile(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val description: String = "",
    val protocolMode: ServiceProtocolMode = ServiceProtocolMode.PALLY_COMPACT,
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
                        ServiceProtocolMode.valueOf(obj.optString("protocolMode", ServiceProtocolMode.PALLY_COMPACT.name))
                    } catch (e: Exception) {
                        ServiceProtocolMode.PALLY_COMPACT
                    },
                    promptPrefix = obj.optString("promptPrefix", ""),
                    isBuiltIn = obj.optBoolean("isBuiltIn", false),
                    colorHex = obj.optLong("colorHex", 0xFF00E5FF)
                )
            } catch (e: Exception) {
                null
            }
        }

        val DEFAULT_PALLY = CellularServiceProfile(
            id = "pally_default",
            name = "Pally AI (Cellular)",
            phoneNumber = "+18005550199",
            description = "Pally conversational AI SMS assistant with native interactive cards and widget sync.",
            protocolMode = ServiceProtocolMode.SMS_CONVERSATIONAL,
            promptPrefix = "",
            isBuiltIn = true,
            colorHex = 0xFF00E5FF
        )

        val PRESET_TWILIO_AI = CellularServiceProfile(
            id = "preset_twilio_ai",
            name = "Cloud Webhook AI Gateway",
            phoneNumber = "+18885550144",
            description = "Twilio/Telnyx bridge connecting to OpenAI GPT-4, Claude, or custom conversational webhooks.",
            protocolMode = ServiceProtocolMode.SMS_CONVERSATIONAL,
            promptPrefix = "",
            isBuiltIn = true,
            colorHex = 0xFF3D5AFE
        )

        val PRESET_LOCAL_LLM = CellularServiceProfile(
            id = "preset_local_gsm",
            name = "Self-Hosted GSM LLM Gateway",
            phoneNumber = "+15550109988",
            description = "Private GSM modem gateway running open-weight local models (Llama 3 / Mistral / DeepSeek).",
            protocolMode = ServiceProtocolMode.JSON_WIRE,
            promptPrefix = "[LLM_INSTRUCT]",
            isBuiltIn = true,
            colorHex = 0xFFB388FF
        )

        fun createCustom(
            name: String,
            phoneNumber: String,
            description: String = "Custom user-configured AI SMS endpoint",
            protocolMode: ServiceProtocolMode = ServiceProtocolMode.SMS_CONVERSATIONAL,
            promptPrefix: String = ""
        ): CellularServiceProfile {
            val safeId = "custom_${System.currentTimeMillis()}_${phoneNumber.takeLast(4).filter { it.isDigit() }}"
            return CellularServiceProfile(
                id = safeId,
                name = name.ifBlank { "Custom Service ($phoneNumber)" },
                phoneNumber = phoneNumber.trim(),
                description = description,
                protocolMode = protocolMode,
                promptPrefix = promptPrefix,
                isBuiltIn = false,
                colorHex = 0xFF00E676
            )
        }
    }
}
