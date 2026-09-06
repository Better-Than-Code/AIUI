package com.cellular.rpc.domain.payload

import com.cellular.rpc.domain.schema.CellularSchemaRegistry
import org.json.JSONObject
import java.util.UUID

/**
 * Standard action verbs supported in Cellular RPC requests.
 */
enum class CellularAction(val wireCode: String) {
    GET("GET"),
    PULL("PULL"),
    QUERY("QRY"),
    ACTION("ACT"),
    SYNC("SYNC"),
    PING("PING"),
    POST("POST"),
    CUSTOM("CUST");

    companion object {
        fun fromCode(code: String): CellularAction {
            return entries.firstOrNull { it.wireCode.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true) }
                ?: CUSTOM
        }
    }
}

/**
 * Standard HTTP-aligned status codes adapted for Cellular SMS/MMS RPC.
 */
enum class CellularStatusCode(val code: Int, val reason: String) {
    OK_200(200, "OK"),
    NOT_MODIFIED_304(304, "Not Modified"),
    BAD_REQUEST_400(400, "Bad Request"),
    NOT_FOUND_404(404, "Not Found"),
    PAYLOAD_TOO_LARGE_413(413, "Payload Exceeds MTU"),
    SERVER_ERROR_500(500, "Internal Server Error"),
    RATE_LIMITED_503(503, "Carrier Velocity Throttle");

    companion object {
        fun fromCode(code: Int): CellularStatusCode {
            return entries.firstOrNull { it.code == code } ?: SERVER_ERROR_500
        }
    }
}

/**
 * Standardized Cellular RPC Request envelope.
 * Supports both ultra-compact ASCII wire serialization (for 140B SMS PDUs) and JSON.
 */
data class CellularRequest(
    val reqId: String = generateShortId(),
    val action: CellularAction,
    val target: String,
    val etag: String? = null,
    val params: Map<String, String> = emptyMap(),
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Compact single-SMS wire representation:
     * REQ:<reqId>:<action>:<target>[:etag=<hash>][:<k>=<v>...]
     */
    fun toCompactWire(): String {
        val sb = StringBuilder("REQ:")
        sb.append(reqId).append(":")
        sb.append(action.wireCode).append(":")
        sb.append(target)
        if (!etag.isNullOrBlank()) {
            sb.append(":etag=").append(etag)
        }
        for ((k, v) in params) {
            sb.append(":").append(k).append("=").append(v)
        }
        return sb.toString()
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("reqId", reqId)
        obj.put("action", action.name)
        obj.put("target", target)
        if (etag != null) obj.put("etag", etag)
        if (params.isNotEmpty()) {
            val pObj = JSONObject()
            for ((k, v) in params) pObj.put(k, v)
            obj.put("params", pObj)
        }
        obj.put("ts", timestampMs)
        return obj.toString()
    }

    companion object {
        fun generateShortId(): String = UUID.randomUUID().toString().take(4).uppercase()

        /**
         * Parses from either compact wire (REQ:...) or JSON string.
         */
        fun fromWire(raw: String): CellularRequest? {
            val trimmed = raw.trim()
            if (trimmed.startsWith("REQ:") || trimmed.startsWith("REQ")) {
                val parts = trimmed.removePrefix("REQ:").removePrefix("REQ").trim().split(":")
                if (parts.isEmpty()) return null

                var reqId = generateShortId()
                var action = CellularAction.PULL
                var target = "weather"
                var etag: String? = null
                val params = mutableMapOf<String, String>()

                var idx = 0
                if (idx < parts.size && parts[idx].length <= 8 && !parts[idx].contains("=")) {
                    reqId = parts[idx]
                    idx++
                }
                if (idx < parts.size && !parts[idx].contains("=")) {
                    action = CellularAction.fromCode(parts[idx])
                    idx++
                }
                val targetParts = mutableListOf<String>()
                while (idx < parts.size && !parts[idx].contains("=")) {
                    targetParts.add(parts[idx])
                    idx++
                }
                if (targetParts.isNotEmpty()) {
                    target = targetParts.joinToString(":")
                }

                while (idx < parts.size) {
                    val kv = parts[idx]
                    if (kv.contains("=")) {
                        val split = kv.split("=", limit = 2)
                        if (split[0] == "etag" || split[0] == "hash") {
                            etag = split[1]
                        } else {
                            params[split[0]] = split[1]
                        }
                    }
                    idx++
                }

                return CellularRequest(
                    reqId = reqId,
                    action = action,
                    target = target,
                    etag = etag,
                    params = params
                )
            } else if (trimmed.startsWith("{")) {
                return try {
                    val obj = JSONObject(trimmed)
                    val pMap = mutableMapOf<String, String>()
                    val pObj = obj.optJSONObject("params")
                    if (pObj != null) {
                        for (key in pObj.keys()) {
                            pMap[key] = pObj.optString(key)
                        }
                    }
                    CellularRequest(
                        reqId = obj.optString("reqId", generateShortId()),
                        action = CellularAction.valueOf(obj.optString("action", "PULL")),
                        target = obj.optString("target", "weather"),
                        etag = if (obj.has("etag")) obj.optString("etag") else null,
                        params = pMap,
                        timestampMs = obj.optLong("ts", System.currentTimeMillis())
                    )
                } catch (e: Exception) {
                    null
                }
            }
            return null
        }
    }
}

/**
 * Standardized Cellular RPC Response envelope.
 */
data class CellularResponse(
    val reqId: String? = null,
    val status: CellularStatusCode = CellularStatusCode.OK_200,
    val schemaId: String,
    val payload: String = "",
    val parsedData: Any? = null,
    val etag: String = "",
    val errorMsg: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val statusCode: Int get() = status.code
    val isSuccess: Boolean get() = status == CellularStatusCode.OK_200
    val isNotModified: Boolean get() = status == CellularStatusCode.NOT_MODIFIED_304

    constructor(
        status: Int,
        schemaId: String,
        etag: String = "",
        payload: String = "",
        reqId: String? = null
    ) : this(
        reqId = reqId,
        status = CellularStatusCode.fromCode(status),
        schemaId = schemaId,
        payload = payload,
        etag = etag
    )

    /**
     * Compact representation for cellular SMS reply:
     * - 304 -> "304" or "RES:<reqId>:304:<schemaId>:<etag>"
     * - 200 -> JSON payload or "RES:<reqId>:200:<schemaId>:<payload>"
     */
    fun toCompactWire(): String {
        if (status == CellularStatusCode.NOT_MODIFIED_304) {
            return if (schemaId.isNotEmpty() && schemaId != "unknown") {
                "RES:${reqId ?: "0"}:304:$schemaId:$etag"
            } else {
                if (reqId != null) "RES:$reqId:304" else "304"
            }
        }
        if (status != CellularStatusCode.OK_200) {
            return "ERR:${reqId ?: "0"}:${status.code}:${errorMsg ?: status.reason}"
        }
        return payload
    }

    companion object {
        fun success(
            reqId: String?,
            schemaId: String,
            data: Any,
            json: String,
            etag: String = CellularSchemaRegistry.computeHash(schemaId, data)
        ): CellularResponse {
            return CellularResponse(
                reqId = reqId,
                status = CellularStatusCode.OK_200,
                schemaId = schemaId,
                payload = json,
                parsedData = data,
                etag = etag
            )
        }

        fun notModified(reqId: String?, schemaId: String, etag: String): CellularResponse {
            return CellularResponse(
                reqId = reqId,
                status = CellularStatusCode.NOT_MODIFIED_304,
                schemaId = schemaId,
                payload = "304",
                etag = etag
            )
        }

        fun error(reqId: String?, status: CellularStatusCode, message: String): CellularResponse {
            return CellularResponse(
                reqId = reqId,
                status = status,
                schemaId = "error",
                payload = """{"error":"$message","code":${status.code}}""",
                errorMsg = message
            )
        }

        /**
         * Parses an inbound cellular message string into a typed CellularResponse.
         */
        fun fromWire(raw: String): CellularResponse {
            val trimmed = raw.trim()

            // 1. Check for 304 Not Modified
            if (trimmed == "304" || trimmed.startsWith("RES:") && trimmed.contains(":304:")) {
                val parts = trimmed.split(":")
                val reqId = if (parts.size >= 2 && parts[1] != "0") parts[1] else null
                val schemaId = if (parts.size >= 4) parts[3] else "unknown"
                val etag = if (parts.size >= 5) parts[4] else ""
                return notModified(reqId, schemaId, etag)
            }

            // 2. Check for ERR response
            if (trimmed.startsWith("ERR:")) {
                val parts = trimmed.split(":", limit = 4)
                val reqId = if (parts.size >= 2) parts[1] else null
                val code = if (parts.size >= 3) parts[2].toIntOrNull() ?: 500 else 500
                val msg = if (parts.size >= 4) parts[3] else "Error"
                return error(reqId, CellularStatusCode.fromCode(code), msg)
            }

            // 3. Try CellularSchemaRegistry parsing JSON
            val parsedPair = CellularSchemaRegistry.parse(trimmed)
            if (parsedPair != null) {
                val (schema, domainObj) = parsedPair
                val etag = CellularSchemaRegistry.computeHash(schema.schemaId, domainObj)
                return success(null, schema.schemaId, domainObj, trimmed, etag)
            }

            // 4. Default plain text response
            return CellularResponse(
                reqId = null,
                status = CellularStatusCode.OK_200,
                schemaId = "chat",
                payload = trimmed,
                parsedData = null,
                etag = ""
            )
        }
    }
}
