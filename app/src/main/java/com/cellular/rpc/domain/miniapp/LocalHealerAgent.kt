package com.cellular.rpc.domain.miniapp

import java.util.Stack

/**
 * Local Healer Guardian Agent.
 * Autonomously sanitizes, repairs, and protects mathematical formulas, numeric expressions,
 * and state references from breaking at runtime during offline execution.
 */
object LocalHealerAgent {

    /**
     * Sanitizes and heals a mathematical formula before passing to the evaluator.
     * 1. Replaces comma decimals with standard dots (e.g. 50,00 -> 50.00)
     * 2. Strips currency symbols ($, €, £, ¥, ₹)
     * 3. Auto-balances unclosed parentheses
     * 4. Cleans trailing operators (e.g. "50 * " -> "50")
     * 5. Replaces empty/null tokens with safe zero
     */
    fun healFormula(rawFormula: String, state: Map<String, Any?> = emptyMap()): String {
        var formula = rawFormula.trim()
        if (formula.isEmpty()) return "0"

        // 1. Strip common currency symbols and percentages
        formula = formula.replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace("¥", "")
            .replace("₹", "")

        // 2. Fix comma decimals (e.g. 45,50 -> 45.50), but preserve array/parameter commas
        formula = fixCommaDecimals(formula)

        // 3. Resolve and substitute state variables safely
        formula = resolveVariablesWithSafeDefaults(formula, state)

        // 4. Auto-balance parentheses
        formula = balanceParentheses(formula)

        // 5. Clean invalid trailing operators like +, -, *, /
        formula = cleanTrailingOperators(formula)

        return formula.ifEmpty { "0" }
    }

    /**
     * Replaces comma decimals (digits,digits) with standard dot decimals (digits.digits).
     */
    private fun fixCommaDecimals(input: String): String {
        val regex = Regex("""(\d+),(\d+)""")
        return regex.replace(input) { matchResult ->
            "${matchResult.groupValues[1]}.${matchResult.groupValues[2]}"
        }
    }

    /**
     * Replaces identifier variables in formula with their runtime state values or safe defaults (0.0).
     */
    private fun resolveVariablesWithSafeDefaults(formula: String, state: Map<String, Any?>): String {
        if (state.isEmpty()) return formula

        val identifierRegex = Regex("""\b([a-zA-Z_][a-zA-Z0-9_]*)\b""")
        return identifierRegex.replace(formula) { match ->
            val varName = match.groupValues[1]
            // Skip math function names if any
            if (varName in listOf("min", "max", "round", "floor", "ceil", "abs", "sqrt")) {
                varName
            } else if (state.containsKey(varName)) {
                val value = state[varName]
                when (value) {
                    null -> "0"
                    is Number -> value.toString()
                    is Boolean -> if (value) "1" else "0"
                    is String -> {
                        val cleanNum = value.replace("$", "").replace(",", ".").trim()
                        cleanNum.toDoubleOrNull()?.toString() ?: "0"
                    }
                    else -> "0"
                }
            } else {
                // Unknown variable: heal with 0 rather than crashing
                "0"
            }
        }
    }

    /**
     * Balances parentheses by adding missing opening or closing parentheses.
     */
    private fun balanceParentheses(input: String): String {
        var openCount = 0
        val sb = StringBuilder()

        for (c in input) {
            when (c) {
                '(' -> {
                    openCount++
                    sb.append(c)
                }
                ')' -> {
                    if (openCount > 0) {
                        openCount--
                        sb.append(c)
                    }
                    // Discard stray closing parenthesis
                }
                else -> sb.append(c)
            }
        }

        // Append missing closing parentheses
        while (openCount > 0) {
            sb.append(')')
            openCount--
        }

        return sb.toString()
    }

    private fun cleanTrailingOperators(input: String): String {
        var clean = input.trim()
        val operators = setOf('+', '-', '*', '/', '%', '^')
        while (clean.isNotEmpty() && clean.last() in operators) {
            clean = clean.dropLast(1).trim()
        }
        return clean
    }

    /**
     * Safely coerces any raw object to a valid Double.
     */
    fun safeToDouble(value: Any?, defaultValue: Double = 0.0): Double {
        if (value == null) return defaultValue
        return when (value) {
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            is String -> {
                val clean = value.replace("$", "").replace("€", "").replace("£", "").replace(",", ".").trim()
                clean.toDoubleOrNull() ?: defaultValue
            }
            else -> defaultValue
        }
    }
}
