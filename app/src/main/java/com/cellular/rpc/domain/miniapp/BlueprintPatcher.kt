package com.cellular.rpc.domain.miniapp

import com.cellular.rpc.engine.JsonPatchEngine
import org.json.JSONObject

/**
 * State-Preserving Blueprint Delta Patcher (RFC 6902).
 * Applies atomic structural updates to declarative mini-apps over cellular transport
 * while preserving active user inputs and runtime state.
 */
object BlueprintPatcher {

    data class PatchResult(
        val success: Boolean,
        val patchedBlueprint: MiniAppBlueprint?,
        val preservedState: Map<String, Any?>,
        val linterResult: BlueprintLinter.LintResult? = null,
        val errorMessage: String? = null
    )

    fun applyDeltaPatch(
        currentBlueprint: MiniAppBlueprint,
        currentRuntimeState: Map<String, Any?>,
        patchJsonStr: String
    ): PatchResult {
        return try {
            val operations = JsonPatchEngine.parsePatch(patchJsonStr)
            if (operations.isEmpty()) {
                return PatchResult(
                    success = false,
                    patchedBlueprint = currentBlueprint,
                    preservedState = currentRuntimeState,
                    errorMessage = "Empty or invalid JSON patch operations."
                )
            }

            val baseJson = if (currentBlueprint.rawJson.isNotBlank()) {
                JSONObject(currentBlueprint.rawJson)
            } else {
                JSONObject().apply {
                    put("appId", currentBlueprint.appId)
                    put("version", currentBlueprint.version)
                }
            }

            val patchSuccess = JsonPatchEngine.applyPatch(baseJson, operations)
            if (!patchSuccess) {
                return PatchResult(
                    success = false,
                    patchedBlueprint = currentBlueprint,
                    preservedState = currentRuntimeState,
                    errorMessage = "Failed to apply RFC 6902 patch operations to AST."
                )
            }

            val patchedBlueprint = MiniAppBlueprint.fromJson(baseJson.toString())
                ?: return PatchResult(
                    success = false,
                    patchedBlueprint = currentBlueprint,
                    preservedState = currentRuntimeState,
                    errorMessage = "Failed to deserialize patched JSON into MiniAppBlueprint."
                )

            // Merge active user state with any new state keys from patched initial state
            val mergedState = mutableMapOf<String, Any?>()
            mergedState.putAll(patchedBlueprint.initialState)
            mergedState.putAll(currentRuntimeState) // User's active inputs take priority

            // Lint patched blueprint to verify structural soundness
            val lintResult = BlueprintLinter.lint(patchedBlueprint)

            PatchResult(
                success = lintResult.isValid,
                patchedBlueprint = patchedBlueprint,
                preservedState = mergedState,
                linterResult = lintResult,
                errorMessage = if (!lintResult.isValid) lintResult.errors.joinToString("; ") else null
            )
        } catch (e: Exception) {
            PatchResult(
                success = false,
                patchedBlueprint = currentBlueprint,
                preservedState = currentRuntimeState,
                errorMessage = "Exception during patch application: ${e.message}"
            )
        }
    }
}
