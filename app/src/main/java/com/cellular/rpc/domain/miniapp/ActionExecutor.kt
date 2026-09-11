package com.cellular.rpc.domain.miniapp

import android.content.Context
import android.util.Log
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import com.cellular.rpc.data.local.AppDatabase
import com.cellular.rpc.data.local.MiniAppDocumentEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Universal In-Memory State Mutation & Calculation Engine for SDUI Mini Apps.
 * Executes local state operations:
 * - TOGGLE_PROP: Toggles boolean property of a specific item in a collection
 * - APPEND: Appends a new item into a list
 * - REMOVE: Removes an item by id from a list
 * - SET_VALUE: Mutates a key in the state map (with formula/expression evaluation)
 * - INCREMENT: Increments a numeric value
 * - DECREMENT: Decrements a numeric value
 * - CALCULATE: Evaluates arithmetic formulas (e.g. bill * (tip_pct / 100))
 * - RUN_SCRIPT / EVAL: Runs sandboxed JavaScript logic via AndroidX JavaScriptEngine with 50ms budget
 */
object ActionExecutor {
    private const val TAG = "ActionExecutor"
    private const val SCRIPT_TIMEOUT_MS = 50L

    private val cachedSandbox = AtomicReference<JavaScriptSandbox?>()

    fun hasScriptAction(actionMap: Map<String, Any?>?): Boolean {
        if (actionMap == null) return false
        val action = actionMap["action"]?.toString()?.uppercase()
        val op = actionMap["op"]?.toString()?.uppercase()
        return action in listOf("RUN_SCRIPT", "EVAL", "CALCULATE", "EXECUTE_SCRIPT", "SCRIPT") ||
                op in listOf("RUN_SCRIPT", "EVAL", "CALCULATE", "SCRIPT") ||
                actionMap.containsKey("script") || actionMap.containsKey("formula") || actionMap.containsKey("expr")
    }

    /**
     * Synchronous state mutation execution. Handles all standard mutations, formula calculations,
     * and immediate JavaScript fallback evaluation for zero-latency 60-120fps UI updates.
     */
    fun execute(
        actionMap: Map<String, Any?>?,
        currentState: Map<String, Any?>,
        itemContext: Map<String, Any?>? = null,
        context: Context? = null
    ): Pair<Map<String, Any?>, Boolean> {
        if (actionMap == null) return Pair(currentState, false)

        val actionType = actionMap["action"]?.toString() ?: return Pair(currentState, false)
        val mutableState = currentState.toMutableMap()
        var stateChanged = false

        when (actionType.uppercase()) {
            "RUN_SCRIPT", "EVAL", "EXECUTE_SCRIPT", "SCRIPT" -> {
                val script = actionMap["script"]?.toString()
                    ?: actionMap["code"]?.toString()
                    ?: actionMap["eval"]?.toString()
                    ?: ""
                if (script.isNotBlank()) {
                    val mutated = executeScriptSync(script, mutableState, itemContext, context)
                    if (mutated != mutableState) {
                        mutableState.clear()
                        mutableState.putAll(mutated)
                        stateChanged = true
                    }
                }
            }

            "CALCULATE" -> {
                val target = actionMap["target"]?.toString() ?: actionMap["prop"]?.toString()
                val formula = actionMap["formula"]?.toString()
                    ?: actionMap["expr"]?.toString()
                    ?: actionMap["script"]?.toString()
                    ?: ""
                if (target != null && formula.isNotBlank()) {
                    val result = SafeScriptEngine.evaluateExpression(formula, mutableState, itemContext)
                    mutableState[target] = result
                    stateChanged = true
                }
            }

            "MUTATE_STATE" -> {
                val target = actionMap["target"]?.toString()
                val op = actionMap["op"]?.toString()?.uppercase()

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
                        val formula = actionMap["formula"]?.toString() ?: actionMap["expr"]?.toString()
                        if (prop != null) {
                            if (formula != null) {
                                val calcResult = SafeScriptEngine.evaluateExpression(formula, mutableState, itemContext)
                                mutableState[prop] = calcResult
                            } else {
                                val resolvedVal = resolvePlaceholder(rawValue?.toString(), itemContext, currentState)
                                mutableState[prop] = resolvedVal
                            }
                            stateChanged = true
                        }
                    }

                    "CALCULATE" -> {
                        val prop = actionMap["prop"]?.toString() ?: target
                        val formula = actionMap["formula"]?.toString()
                            ?: actionMap["expr"]?.toString()
                            ?: actionMap["script"]?.toString()
                            ?: ""
                        if (prop != null && formula.isNotBlank()) {
                            val calcResult = SafeScriptEngine.evaluateExpression(formula, mutableState, itemContext)
                            mutableState[prop] = calcResult
                            stateChanged = true
                        }
                    }

                    "RUN_SCRIPT", "EVAL", "SCRIPT" -> {
                        val script = actionMap["script"]?.toString()
                            ?: actionMap["code"]?.toString()
                            ?: actionMap["formula"]?.toString()
                            ?: ""
                        if (script.isNotBlank()) {
                            val mutated = executeScriptSync(script, mutableState, itemContext, context)
                            if (mutated != mutableState) {
                                mutableState.clear()
                                mutableState.putAll(mutated)
                                stateChanged = true
                            }
                        }
                    }

                    "INCREMENT" -> {
                        val prop = actionMap["prop"]?.toString() ?: target
                        val step = (actionMap["step"] as? Number)?.toInt() ?: 1
                        if (prop != null) {
                            val cur = (mutableState[prop] as? Number)?.toInt()
                                ?: mutableState[prop]?.toString()?.toIntOrNull() ?: 0
                            mutableState[prop] = cur + step
                            stateChanged = true
                        }
                    }

                    "DECREMENT" -> {
                        val prop = actionMap["prop"]?.toString() ?: target
                        val step = (actionMap["step"] as? Number)?.toInt() ?: 1
                        if (prop != null) {
                            val cur = (mutableState[prop] as? Number)?.toInt()
                                ?: mutableState[prop]?.toString()?.toIntOrNull() ?: 0
                            mutableState[prop] = (cur - step).coerceAtLeast(0)
                            stateChanged = true
                        }
                    }
                }
            }

            "DOC_UPSERT", "UPSERT_DOC", "DOCUMENT_UPSERT" -> {
                val appId = actionMap["appId"]?.toString() ?: "default_app"
                val collection = actionMap["collection"]?.toString() ?: "documents"
                val docId = resolvePlaceholder(actionMap["docId"]?.toString(), itemContext, currentState)
                    ?: UUID.randomUUID().toString()
                val payloadMap = actionMap["payload"] as? Map<*, *>
                    ?: actionMap["data"] as? Map<*, *>
                    ?: itemContext
                    ?: emptyMap<Any, Any>()
                val resolvedPayload = resolveTemplateMap(payloadMap, itemContext, currentState)
                val jsonStr = JSONObject(resolvedPayload).toString()

                if (context != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            AppDatabase.getInstance(context).miniAppDocumentDao().upsertDocument(
                                MiniAppDocumentEntity(
                                    appId = appId,
                                    collection = collection,
                                    docId = docId,
                                    jsonPayload = jsonStr
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to upsert mini app document: ${e.message}")
                        }
                    }
                }
                stateChanged = true
            }

            "DOC_DELETE", "DELETE_DOC", "DOCUMENT_DELETE" -> {
                val appId = actionMap["appId"]?.toString() ?: "default_app"
                val collection = actionMap["collection"]?.toString() ?: "documents"
                val docId = resolvePlaceholder(actionMap["docId"]?.toString(), itemContext, currentState)
                if (docId != null && context != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            AppDatabase.getInstance(context).miniAppDocumentDao().deleteDocument(
                                appId = appId,
                                collection = collection,
                                docId = docId
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to delete mini app document: ${e.message}")
                        }
                    }
                    stateChanged = true
                }
            }

            "DOC_CLEAR", "CLEAR_DOCUMENTS" -> {
                val appId = actionMap["appId"]?.toString() ?: "default_app"
                val collection = actionMap["collection"]?.toString() ?: "documents"
                if (context != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            AppDatabase.getInstance(context).miniAppDocumentDao().clearCollection(
                                appId = appId,
                                collection = collection
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to clear mini app collection: ${e.message}")
                        }
                    }
                    stateChanged = true
                }
            }
        }

        return Pair(mutableState, stateChanged)
    }

    /**
     * Asynchronous evaluation executing scripts via AndroidX JavaScriptEngine Sandbox isolate
     * with hard 50ms execution bounds and fallback to deterministic in-memory interpreter.
     */
    suspend fun executeAsync(
        actionMap: Map<String, Any?>?,
        currentState: Map<String, Any?>,
        itemContext: Map<String, Any?>? = null,
        context: Context? = null
    ): Pair<Map<String, Any?>, Boolean> = withContext(Dispatchers.IO) {
        if (actionMap == null) return@withContext Pair(currentState, false)

        val script = actionMap["script"]?.toString()
            ?: actionMap["code"]?.toString()
            ?: actionMap["formula"]?.toString()
            ?: actionMap["expr"]?.toString()

        if (script.isNullOrBlank()) {
            return@withContext execute(actionMap, currentState, itemContext, context)
        }

        val mutableState = currentState.toMutableMap()
        val target = actionMap["target"]?.toString() ?: actionMap["prop"]?.toString()

        // If it's a simple formula without full JS statements
        if (target != null && !script.contains(";") && !script.contains("state.")) {
            val calcResult = SafeScriptEngine.evaluateExpression(script, mutableState, itemContext)
            mutableState[target] = calcResult
            return@withContext Pair(mutableState, true)
        }

        // Try isolated AndroidX JavaScriptEngine
        if (context != null) {
            try {
                val isolateResult = withTimeout(SCRIPT_TIMEOUT_MS) {
                    evaluateInJsSandbox(context, script, mutableState, itemContext)
                }
                if (isolateResult != null) {
                    return@withContext Pair(isolateResult, true)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Sandbox isolate evaluation failed or timed out (${e.message}), falling back to internal evaluator")
            }
        }

        // Deterministic Fallback Engine
        val mutated = SafeScriptEngine.evaluateScript(script, mutableState, itemContext)
        return@withContext Pair(mutated, mutated != currentState)
    }

    private fun executeScriptSync(
        script: String,
        currentState: Map<String, Any?>,
        itemContext: Map<String, Any?>?,
        context: Context?
    ): Map<String, Any?> {
        // Run internal deterministic evaluator for instant 60fps response
        val stateCopy = currentState.toMutableMap()
        return SafeScriptEngine.evaluateScript(script, stateCopy, itemContext)
    }

    private suspend fun evaluateInJsSandbox(
        context: Context,
        script: String,
        state: Map<String, Any?>,
        itemContext: Map<String, Any?>?
    ): Map<String, Any?>? {
        if (!JavaScriptSandbox.isSupported()) return null

        var sandbox = cachedSandbox.get()
        if (sandbox == null) {
            try {
                sandbox = JavaScriptSandbox.createConnectedInstanceAsync(context.applicationContext)
                    .get(300, TimeUnit.MILLISECONDS)
                cachedSandbox.set(sandbox)
            } catch (e: Exception) {
                Log.w(TAG, "Could not obtain JavaScriptSandbox: ${e.message}")
                return null
            }
        }

        var isolate: JavaScriptIsolate? = null
        try {
            isolate = sandbox.createIsolate()
            val stateJson = JSONObject(state).toString()
            val paramsJson = if (itemContext != null) JSONObject(itemContext).toString() else "{}"

            val runner = """
                var state = $stateJson;
                var params = $paramsJson;
                (function() {
                    $script;
                    return JSON.stringify(state);
                })();
            """.trimIndent()

            val future = isolate.evaluateJavaScriptAsync(runner)
            val resultJsonStr = future.get(SCRIPT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val resultObj = JSONObject(resultJsonStr)

            val resultMap = mutableMapOf<String, Any?>()
            val keys = resultObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                resultMap[k] = resultObj.opt(k)
            }
            return resultMap
        } finally {
            try {
                isolate?.close()
            } catch (e: Exception) {
                // Isolate cleanup
            }
        }
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

/**
 * High-speed deterministic arithmetic and lightweight JavaScript interpreter.
 * Executes formula operations and scripts with sub-millisecond latency:
 * - state.key = expression
 * - toFixed(2), parseFloat(), parseInt(), Number()
 * - Math.round, Math.floor, Math.ceil
 * - Conditionals / ternary: cond ? a : b
 */
object SafeScriptEngine {
    private const val TAG = "SafeScriptEngine"

    fun evaluateScript(
        script: String,
        state: MutableMap<String, Any?>,
        itemContext: Map<String, Any?>?
    ): Map<String, Any?> {
        val statements = script.split(";", "\n").map { it.trim() }.filter { it.isNotBlank() }
        val localVars = mutableMapOf<String, Any?>()

        for (stmt in statements) {
            try {
                // Ignore pure comments
                if (stmt.startsWith("//")) continue

                // Check assignment: var/let/const name = expr OR state.name = expr OR name = expr
                val assignMatch = Regex("""^(?:(?:var|let|const)\s+)?([a-zA-Z0-9_$.]+)\s*=\s*(.+)$""").find(stmt)
                if (assignMatch != null) {
                    val rawTarget = assignMatch.groupValues[1]
                    val rawExpr = assignMatch.groupValues[2]

                    val evaluatedValue = evaluateExpression(rawExpr, state, itemContext, localVars)

                    if (rawTarget.startsWith("state.")) {
                        val stateKey = rawTarget.removePrefix("state.")
                        state[stateKey] = evaluatedValue
                    } else if (rawTarget == "state") {
                        if (evaluatedValue is Map<*, *>) {
                            evaluatedValue.forEach { (k, v) -> if (k != null) state[k.toString()] = v }
                        }
                    } else {
                        localVars[rawTarget] = evaluatedValue
                        // If variable name matches an existing state key, update state too
                        if (state.containsKey(rawTarget)) {
                            state[rawTarget] = evaluatedValue
                        }
                    }
                } else if (stmt.contains("state.")) {
                    // Try pattern: state.tip = ...
                    val parts = stmt.split("=")
                    if (parts.size == 2) {
                        val lhs = parts[0].trim()
                        val rhs = parts[1].trim()
                        if (lhs.startsWith("state.")) {
                            val stateKey = lhs.removePrefix("state.")
                            state[stateKey] = evaluateExpression(rhs, state, itemContext, localVars)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error executing statement '$stmt': ${e.message}")
            }
        }
        return state
    }

    fun evaluateExpression(
        expression: String,
        state: Map<String, Any?>,
        itemContext: Map<String, Any?>? = null,
        localVars: Map<String, Any?> = emptyMap()
    ): Any {
        var expr = expression.trim()

        // Handle .toFixed(n)
        val toFixedMatch = Regex("""\((.+)\)\.toFixed\((\d+)\)""").find(expr)
            ?: Regex("""([a-zA-Z0-9_$.]+)\.toFixed\((\d+)\)""").find(expr)
        if (toFixedMatch != null) {
            val innerExpr = toFixedMatch.groupValues[1]
            val digits = toFixedMatch.groupValues[2].toIntOrNull() ?: 2
            val num = evaluateToDouble(innerExpr, state, itemContext, localVars)
            return String.format(java.util.Locale.US, "%.${digits}f", num)
        }

        // Handle Ternary: cond ? a : b
        val ternaryMatch = Regex("""^(.+?)\?(.+?):(.+)$""").find(expr)
        if (ternaryMatch != null) {
            val cond = evaluateToBoolean(ternaryMatch.groupValues[1], state, itemContext, localVars)
            return if (cond) {
                evaluateExpression(ternaryMatch.groupValues[2], state, itemContext, localVars)
            } else {
                evaluateExpression(ternaryMatch.groupValues[3], state, itemContext, localVars)
            }
        }

        // Try numeric arithmetic evaluation
        val numResult = evaluateArithmetic(expr, state, itemContext, localVars)
        if (numResult != null) {
            // Check if it should be an integer
            return if (numResult % 1.0 == 0.0 && !expr.contains("toFixed") && !expr.contains(".")) {
                numResult.toInt()
            } else {
                numResult
            }
        }

        // Fallback string / variable resolution
        return resolveToken(expr, state, itemContext, localVars)
    }

    private fun evaluateToDouble(
        expr: String,
        state: Map<String, Any?>,
        itemContext: Map<String, Any?>?,
        localVars: Map<String, Any?>
    ): Double {
        return evaluateArithmetic(expr, state, itemContext, localVars) ?: 0.0
    }

    private fun evaluateToBoolean(
        expr: String,
        state: Map<String, Any?>,
        itemContext: Map<String, Any?>?,
        localVars: Map<String, Any?>
    ): Boolean {
        val trimmed = expr.trim()
        val cmp = Regex("""^(.+?)\s*(===|==|!==|!=|>=|<=|>|<)\s*(.+)$""").find(trimmed)
        if (cmp != null) {
            val left = evaluateToDouble(cmp.groupValues[1], state, itemContext, localVars)
            val op = cmp.groupValues[2]
            val right = evaluateToDouble(cmp.groupValues[3], state, itemContext, localVars)
            return when (op) {
                "==", "===" -> left == right
                "!=", "!==" -> left != right
                ">" -> left > right
                "<" -> left < right
                ">=" -> left >= right
                "<=" -> left <= right
                else -> false
            }
        }
        val doubleVal = evaluateArithmetic(trimmed, state, itemContext, localVars)
        if (doubleVal != null) return doubleVal != 0.0
        val token = resolveToken(trimmed, state, itemContext, localVars).toString()
        return token.equals("true", ignoreCase = true)
    }

    private fun evaluateArithmetic(
        expression: String,
        state: Map<String, Any?>,
        itemContext: Map<String, Any?>?,
        localVars: Map<String, Any?>
    ): Double? {
        var clean = expression.trim()

        // Strip JS wrappers: parseFloat(x), parseInt(x), Number(x)
        clean = clean.replace(Regex("""parseFloat\(([^)]+)\)""")) { it.groupValues[1] }
        clean = clean.replace(Regex("""parseInt\(([^)]+)\)""")) { it.groupValues[1] }
        clean = clean.replace(Regex("""Number\(([^)]+)\)""")) { it.groupValues[1] }

        // Substitute Math functions
        clean = clean.replace(Regex("""Math\.round\(([^)]+)\)""")) {
            val v = evaluateToDouble(it.groupValues[1], state, itemContext, localVars)
            Math.round(v).toString()
        }
        clean = clean.replace(Regex("""Math\.floor\(([^)]+)\)""")) {
            val v = evaluateToDouble(it.groupValues[1], state, itemContext, localVars)
            Math.floor(v).toString()
        }
        clean = clean.replace(Regex("""Math\.ceil\(([^)]+)\)""")) {
            val v = evaluateToDouble(it.groupValues[1], state, itemContext, localVars)
            Math.ceil(v).toString()
        }

        // Replace variable tokens with numeric values
        val tokenPattern = Regex("""[a-zA-Z_$][a-zA-Z0-9_$.]*""")
        val replaced = tokenPattern.replace(clean) { m ->
            val tok = m.value
            val num = resolveTokenToNumber(tok, state, itemContext, localVars)
            num?.toString() ?: "0"
        }

        return try {
            SimpleMathParser.parse(replaced)
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveTokenToNumber(
        token: String,
        state: Map<String, Any?>,
        itemContext: Map<String, Any?>?,
        localVars: Map<String, Any?>
    ): Double? {
        val resolved = resolveToken(token, state, itemContext, localVars)
        return when (resolved) {
            is Number -> resolved.toDouble()
            is String -> resolved.replace("$", "").trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun resolveToken(
        token: String,
        state: Map<String, Any?>,
        itemContext: Map<String, Any?>?,
        localVars: Map<String, Any?>
    ): Any {
        val clean = token.trim().trim('"', '\'')
        if (clean.toDoubleOrNull() != null) return clean.toDouble()
        if (clean.equals("true", ignoreCase = true)) return true
        if (clean.equals("false", ignoreCase = true)) return false

        if (localVars.containsKey(clean)) return localVars[clean] ?: ""

        val stateKey = when {
            clean.startsWith("state.") -> clean.removePrefix("state.")
            clean.startsWith("\$state.") -> clean.removePrefix("\$state.")
            else -> clean
        }
        if (state.containsKey(stateKey)) return state[stateKey] ?: ""

        val itemKey = when {
            clean.startsWith("item.") -> clean.removePrefix("item.")
            clean.startsWith("\$item.") -> clean.removePrefix("\$item.")
            else -> null
        }
        if (itemKey != null && itemContext?.containsKey(itemKey) == true) {
            return itemContext[itemKey] ?: ""
        }

        return clean
    }
}

/**
 * Standard Recursive-Descent Expression Parser for Safe Local Math.
 * Handles +, -, *, /, %, parenthesis, and precedence safely without arbitrary code execution.
 */
object SimpleMathParser {
    fun parse(str: String): Double {
        return Parser(str).parse()
    }

    private class Parser(private val str: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < str.length) str[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            return parseExpression()
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        x = if (divisor != 0.0) x / divisor else 0.0
                    }
                    eat('%'.code) -> {
                        val divisor = parseFactor()
                        x = if (divisor != 0.0) x % divisor else 0.0
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return +parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = str.substring(startPos, pos).toDoubleOrNull() ?: 0.0
            } else {
                nextChar()
                x = 0.0
            }
            return x
        }
    }
}

