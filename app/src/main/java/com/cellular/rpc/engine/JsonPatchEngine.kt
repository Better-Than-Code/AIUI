package com.cellular.rpc.engine

import org.json.JSONArray
import org.json.JSONObject
import android.util.Log

/**
 * JsonPatchEngine (Sprint 2.1 - Differential Mini-App Patching / OTA Deltas)
 *
 * Implements an RFC 6902 compliant JSON Patch application engine for AST trees.
 * Allows bandwidth-optimized delta patches to be transmitted over compressed
 * cellular SMS/MMS frames, applying only structural AST diffs rather than full payloads.
 */
object JsonPatchEngine {
    private const val TAG = "JsonPatchEngine"

    data class PatchOperation(
        val op: String, // "add", "remove", "replace", "test"
        val path: String, // JSON Pointer e.g. "/rootNode/children/0/text"
        val value: Any? = null
    )

    /**
     * Parses a JSON Patch array string and returns a list of PatchOperations.
     */
    fun parsePatch(patchJsonStr: String): List<PatchOperation> {
        val operations = mutableListOf<PatchOperation>()
        try {
            val jsonArray = JSONArray(patchJsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val op = obj.getString("op")
                val path = obj.getString("path")
                val value = if (obj.has("value")) obj.get("value") else null
                operations.add(PatchOperation(op, path, value))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON patch: ${e.message}", e)
        }
        return operations
    }

    /**
     * Applies an RFC 6902 JSON Patch to a target JSONObject root in place.
     */
    fun applyPatch(root: JSONObject, patchOperations: List<PatchOperation>): Boolean {
        try {
            for (patch in patchOperations) {
                val success = when (patch.op) {
                    "add" -> applyAdd(root, patch.path, patch.value)
                    "remove" -> applyRemove(root, patch.path)
                    "replace" -> applyReplace(root, patch.path, patch.value)
                    "test" -> applyTest(root, patch.path, patch.value)
                    else -> {
                        Log.w(TAG, "Unsupported patch operation: ${patch.op}")
                        false
                    }
                }
                if (!success) {
                    Log.e(TAG, "Patch operation '${patch.op}' failed at path '${patch.path}'")
                    com.example.CellularRpcApp.instance.let { app ->
                        com.cellular.rpc.engine.CellularMedic.onFaultDetected(
                            context = app,
                            componentName = "JsonPatchEngine.applyPatch",
                            faultDescription = "Patch validation failed at path: ${patch.path}",
                            rawPayload = patchOperations.toString()
                        )
                    }
                    return false
                }
            }
            
            // If completely successful, notify the Medic to close the loop
            com.example.CellularRpcApp.instance.let { app ->
                com.cellular.rpc.engine.CellularMedic.onPatchApplied(
                    context = app,
                    componentName = "JsonPatchEngine",
                    patchJson = patchOperations.toString()
                )
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying JSON patch: ${e.message}", e)
            com.example.CellularRpcApp.instance.let { app ->
                com.cellular.rpc.engine.CellularMedic.onFaultDetected(
                    context = app,
                    componentName = "JsonPatchEngine.applyPatch",
                    faultDescription = "Exception applying JSON patch: ${e.message}",
                    rawPayload = patchOperations.toString()
                )
            }
            return false
        }
    }

    private fun parsePointer(path: String): List<String> {
        if (path.isEmpty() || path == "/") return emptyList()
        return path.removePrefix("/").split("/").map {
            it.replace("~1", "/").replace("~0", "~")
        }
    }

    private fun applyAdd(root: JSONObject, path: String, value: Any?): Boolean {
        val tokens = parsePointer(path)
        if (tokens.isEmpty()) return false

        val parentTokens = tokens.dropLast(1)
        val lastToken = tokens.last()
        val parent = navigateToContainer(root, parentTokens) ?: return false

        when (parent) {
            is JSONObject -> {
                parent.put(lastToken, value)
                return true
            }
            is JSONArray -> {
                if (lastToken == "-") {
                    parent.put(value)
                    return true
                }
                val index = lastToken.toIntOrNull() ?: return false
                if (index in 0..parent.length()) {
                    // JSONArray doesn't have a direct insert at index, but we can build a new array or shift
                    val newList = mutableListOf<Any?>()
                    for (i in 0 until parent.length()) {
                        if (i == index) newList.add(value)
                        newList.add(parent.opt(i))
                    }
                    if (index >= parent.length()) {
                        newList.add(value)
                    }
                    // clear and refill
                    for (i in 0 until parent.length()) parent.remove(0)
                    for (item in newList) parent.put(item)
                    return true
                }
            }
        }
        return false
    }

    private fun applyRemove(root: JSONObject, path: String): Boolean {
        val tokens = parsePointer(path)
        if (tokens.isEmpty()) return false

        val parentTokens = tokens.dropLast(1)
        val lastToken = tokens.last()
        val parent = navigateToContainer(root, parentTokens) ?: return false

        when (parent) {
            is JSONObject -> {
                if (parent.has(lastToken)) {
                    parent.remove(lastToken)
                    return true
                }
            }
            is JSONArray -> {
                val index = lastToken.toIntOrNull() ?: return false
                if (index in 0 until parent.length()) {
                    val newList = mutableListOf<Any?>()
                    for (i in 0 until parent.length()) {
                        if (i != index) newList.add(parent.opt(i))
                    }
                    for (i in 0 until parent.length()) parent.remove(0)
                    for (item in newList) parent.put(item)
                    return true
                }
            }
        }
        return false
    }

    private fun applyReplace(root: JSONObject, path: String, value: Any?): Boolean {
        // Replace is equivalent to remove + add or direct property update
        val tokens = parsePointer(path)
        if (tokens.isEmpty()) return false

        val parentTokens = tokens.dropLast(1)
        val lastToken = tokens.last()
        val parent = navigateToContainer(root, parentTokens) ?: return false

        when (parent) {
            is JSONObject -> {
                if (parent.has(lastToken)) {
                    parent.put(lastToken, value)
                    return true
                }
            }
            is JSONArray -> {
                val index = lastToken.toIntOrNull() ?: return false
                if (index in 0 until parent.length()) {
                    parent.put(index, value)
                    return true
                }
            }
        }
        return false
    }

    private fun applyTest(root: JSONObject, path: String, expectedValue: Any?): Boolean {
        val tokens = parsePointer(path)
        val current = navigateToValue(root, tokens) ?: return false
        return current.toString() == expectedValue.toString()
    }

    private fun navigateToContainer(root: JSONObject, tokens: List<String>): Any? {
        var current: Any = root
        for (token in tokens) {
            current = when (current) {
                is JSONObject -> current.opt(token) ?: return null
                is JSONArray -> {
                    val idx = token.toIntOrNull() ?: return null
                    current.opt(idx) ?: return null
                }
                else -> return null
            }
        }
        return current
    }

    private fun navigateToValue(root: JSONObject, tokens: List<String>): Any? {
        if (tokens.isEmpty()) return root
        val parentTokens = tokens.dropLast(1)
        val lastToken = tokens.last()
        val parent = navigateToContainer(root, parentTokens) ?: return null
        return when (parent) {
            is JSONObject -> parent.opt(lastToken)
            is JSONArray -> {
                val idx = lastToken.toIntOrNull() ?: return null
                parent.opt(idx)
            }
            else -> null
        }
    }
}
