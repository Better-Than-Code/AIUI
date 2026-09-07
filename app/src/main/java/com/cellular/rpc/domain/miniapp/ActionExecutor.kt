package com.cellular.rpc.domain.miniapp

import java.util.UUID

/**
 * Universal In-Memory State Mutation Engine for SDUI Mini Apps.
 * Executes local state operations:
 * - TOGGLE_PROP: Toggles boolean property of a specific item in a collection
 * - APPEND: Appends a new item into a list
 * - REMOVE: Removes an item by id from a list
 * - SET_VALUE: Mutates a key in the state map
 * - INCREMENT: Increments a numeric value
 * - DECREMENT: Decrements a numeric value
 */
object ActionExecutor {

    fun execute(
        actionMap: Map<String, Any?>?,
        currentState: Map<String, Any?>,
        itemContext: Map<String, Any?>? = null
    ): Pair<Map<String, Any?>, Boolean> {
        if (actionMap == null) return Pair(currentState, false)

        val actionType = actionMap["action"]?.toString() ?: return Pair(currentState, false)
        val mutableState = currentState.toMutableMap()
        var stateChanged = false

        when (actionType) {
            "MUTATE_STATE" -> {
                val target = actionMap["target"]?.toString()
                val op = actionMap["op"]?.toString()

                when (op) {
                    "TOGGLE_PROP" -> {
                        val prop = actionMap["prop"]?.toString() ?: "done"
                        val idParam = actionMap["id"]?.toString()
                        val targetId = resolvePlaceholder(idParam, itemContext, currentState)

                        if (target != null && targetId != null) {
                            val items = (mutableState[target] as? List<*>) ?: emptyList<Any>()
                            val updatedItems = items.map { item ->
                                if (item is Map<*, *>) {
                                    val itemMap = item.toMutableMap()
                                    val itemId = itemMap["id"]?.toString()
                                    if (itemId == targetId) {
                                        val currentVal = itemMap[prop] as? Boolean ?: false
                                        itemMap[prop] = !currentVal
                                        stateChanged = true
                                    }
                                    itemMap
                                } else {
                                    item
                                }
                            }
                            mutableState[target] = updatedItems
                        }
                    }

                    "APPEND" -> {
                        val valueTemplate = actionMap["value"] as? Map<*, *>
                        if (target != null && valueTemplate != null) {
                            val resolvedItem = resolveTemplateMap(valueTemplate, itemContext, currentState)
                            val items = ((mutableState[target] as? List<*>) ?: emptyList<Any>()).toMutableList()
                            items.add(resolvedItem)
                            mutableState[target] = items
                            stateChanged = true
                        }

                        val clearKey = actionMap["clearAfter"]?.toString()
                        if (clearKey != null) {
                            mutableState[clearKey] = ""
                            stateChanged = true
                        }
                    }

                    "REMOVE" -> {
                        val idParam = actionMap["id"]?.toString()
                        val targetId = resolvePlaceholder(idParam, itemContext, currentState)
                        if (target != null && targetId != null) {
                            val items = (mutableState[target] as? List<*>) ?: emptyList<Any>()
                            val filtered = items.filter { item ->
                                if (item is Map<*, *>) {
                                    item["id"]?.toString() != targetId
                                } else {
                                    true
                                }
                            }
                            mutableState[target] = filtered
                            stateChanged = true
                        }
                    }

                    "SET_VALUE" -> {
                        val prop = actionMap["prop"]?.toString() ?: target
                        val rawValue = actionMap["value"]
                        val resolvedVal = resolvePlaceholder(rawValue?.toString(), itemContext, currentState)
                        if (prop != null) {
                            mutableState[prop] = resolvedVal
                            stateChanged = true
                        }
                    }

                    "INCREMENT" -> {
                        val prop = actionMap["prop"]?.toString() ?: target
                        val step = (actionMap["step"] as? Number)?.toInt() ?: 1
                        if (prop != null) {
                            val cur = (mutableState[prop] as? Number)?.toInt() ?: 0
                            mutableState[prop] = cur + step
                            stateChanged = true
                        }
                    }

                    "DECREMENT" -> {
                        val prop = actionMap["prop"]?.toString() ?: target
                        val step = (actionMap["step"] as? Number)?.toInt() ?: 1
                        if (prop != null) {
                            val cur = (mutableState[prop] as? Number)?.toInt() ?: 0
                            mutableState[prop] = (cur - step).coerceAtLeast(0)
                            stateChanged = true
                        }
                    }
                }
            }
        }

        return Pair(mutableState, stateChanged)
    }

    private fun resolveTemplateMap(
        template: Map<*, *>,
        itemContext: Map<String, Any?>?,
        currentState: Map<String, Any?>
    ): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        for ((k, v) in template) {
            val keyStr = k.toString()
            when (v) {
                is String -> {
                    result[keyStr] = when {
                        v == "\$uuid" -> UUID.randomUUID().toString().take(8)
                        v.startsWith("\$state.") -> {
                            val stateKey = v.removePrefix("\$state.")
                            currentState[stateKey] ?: ""
                        }
                        v.startsWith("\$item.") -> {
                            val itemKey = v.removePrefix("\$item.")
                            itemContext?.get(itemKey) ?: ""
                        }
                        else -> v
                    }
                }
                is Map<*, *> -> result[keyStr] = resolveTemplateMap(v, itemContext, currentState)
                else -> result[keyStr] = v
            }
        }
        return result
    }

    private fun resolvePlaceholder(
        value: String?,
        itemContext: Map<String, Any?>?,
        currentState: Map<String, Any?>
    ): String? {
        if (value == null) return null
        return when {
            value == "\$uuid" -> UUID.randomUUID().toString().take(8)
            value.startsWith("\$state.") -> {
                val key = value.removePrefix("\$state.")
                currentState[key]?.toString() ?: ""
            }
            value.startsWith("\$item.") -> {
                val key = value.removePrefix("\$item.")
                itemContext?.get(key)?.toString() ?: ""
            }
            else -> value
        }
    }
}
