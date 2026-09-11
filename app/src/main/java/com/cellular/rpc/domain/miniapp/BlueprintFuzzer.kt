package com.cellular.rpc.domain.miniapp

import android.content.Context

/**
 * Headless Property Fuzzer and Test Runner for Mini-App Blueprints.
 * Tests edge cases (negative values, zeroes, empty strings, nulls, boundary inputs)
 * against all declared blueprint actions before presenting the card to the user.
 */
object BlueprintFuzzer {

    data class FuzzResult(
        val passed: Boolean,
        val actionsTested: Int,
        val executionTimeMs: Long,
        val issues: List<String>
    )

    fun fuzz(blueprint: MiniAppBlueprint, context: Context? = null): FuzzResult {
        val startTime = System.currentTimeMillis()
        val issues = mutableListOf<String>()
        val actions = mutableListOf<Map<String, Any?>>()

        collectActions(blueprint.uiRoot, actions)

        if (actions.isEmpty()) {
            return FuzzResult(
                passed = true,
                actionsTested = 0,
                executionTimeMs = System.currentTimeMillis() - startTime,
                issues = emptyList()
            )
        }

        // Generate synthetic state variations
        val baseState = blueprint.initialState
        val syntheticStates = listOf(
            baseState,
            mutateStateWithZeroes(baseState),
            mutateStateWithNegatives(baseState),
            mutateStateWithBoundaries(baseState)
        )

        var testsRun = 0

        for (action in actions) {
            for (state in syntheticStates) {
                testsRun++
                try {
                    val (resultingState, changed) = ActionExecutor.execute(
                        actionMap = action,
                        currentState = state,
                        itemContext = mapOf("id" to "fuzz_item_1", "name" to "Test Item", "completed" to false),
                        context = context
                    )
                    // Ensure resulting state is a valid non-null map
                    if (resultingState.isEmpty() && state.isNotEmpty()) {
                        issues.add("Action ${action["action"]} completely wiped state during fuzz test.")
                    }
                } catch (e: Throwable) {
                    issues.add("Action ${action["action"]} threw unhandled exception during fuzz: ${e.message}")
                }
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        return FuzzResult(
            passed = issues.isEmpty(),
            actionsTested = testsRun,
            executionTimeMs = totalTime,
            issues = issues
        )
    }

    private fun collectActions(node: MiniAppUiNode, accumulator: MutableList<Map<String, Any?>>) {
        node.onClick?.let { accumulator.add(it) }
        node.onToggle?.let { accumulator.add(it) }
        node.onSelect?.let { accumulator.add(it) }
        node.actions?.let { accumulator.add(it) }

        node.children.forEach { collectActions(it, accumulator) }
        node.itemTemplate?.let { collectActions(it, accumulator) }
    }

    private fun mutateStateWithZeroes(state: Map<String, Any?>): Map<String, Any?> {
        return state.mapValues { (_, v) ->
            when (v) {
                is Number -> 0
                is String -> ""
                is Boolean -> false
                is List<*> -> emptyList<Any>()
                else -> v
            }
        }
    }

    private fun mutateStateWithNegatives(state: Map<String, Any?>): Map<String, Any?> {
        return state.mapValues { (_, v) ->
            when (v) {
                is Int -> -100
                is Double -> -99.99
                is Float -> -99.99f
                is Long -> -100L
                is String -> "-100"
                else -> v
            }
        }
    }

    private fun mutateStateWithBoundaries(state: Map<String, Any?>): Map<String, Any?> {
        return state.mapValues { (_, v) ->
            when (v) {
                is Int -> 999999
                is Double -> 999999.99
                is Float -> 999999.99f
                is Long -> 999999L
                is String -> "A".repeat(500)
                else -> v
            }
        }
    }
}
