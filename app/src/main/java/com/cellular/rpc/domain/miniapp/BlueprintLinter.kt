package com.cellular.rpc.domain.miniapp

/**
 * Headless AST Schema Linter and Structural Validator for Declarative Mini-App Blueprints.
 * Validates node types, state bindings, accessibility compliance, and structural bounds.
 */
object BlueprintLinter {

    data class LintResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>
    )

    private val VALID_NODE_TYPES = setOf(
        "column", "row", "box", "card", "text", "button", "input", "textfield",
        "checkbox", "switch", "badge", "progress", "slider", "select", "segmented",
        "radio", "tabs", "chips", "canvas", "canvas_view", "draw_canvas", "chart",
        "linechart", "barchart", "piechart", "list", "lazycolumn", "spacer", "divider",
        "icon", "image"
    )

    fun lint(blueprint: MiniAppBlueprint): LintResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (blueprint.appId.isBlank()) {
            errors.add("Missing required 'appId' field.")
        }
        if (blueprint.metadata.title.isBlank()) {
            warnings.add("App metadata title is blank.")
        }

        val declaredStateKeys = blueprint.initialState.keys.toSet()

        lintNode(
            node = blueprint.uiRoot,
            path = "root",
            declaredStateKeys = declaredStateKeys,
            errors = errors,
            warnings = warnings
        )

        return LintResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    private fun lintNode(
        node: MiniAppUiNode,
        path: String,
        declaredStateKeys: Set<String>,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val normalizedType = node.type.lowercase().trim()

        if (normalizedType.isNotBlank() && normalizedType !in VALID_NODE_TYPES) {
            warnings.add("Unknown or non-standard node type '$normalizedType' at $path; will fallback to default container.")
        }

        // Check bindings against declared initialState
        checkBinding(node.bind, "bind", path, declaredStateKeys, warnings)
        checkBinding(node.bindValue, "bindValue", path, declaredStateKeys, warnings)
        checkBinding(node.bindChecked, "bindChecked", path, declaredStateKeys, warnings)
        checkBinding(node.bindItems, "bindItems", path, declaredStateKeys, warnings)

        // Accessibility checks
        if (normalizedType in setOf("button", "icon") && node.text.isBlank() && node.icon.isBlank() && node.bind.isBlank()) {
            warnings.add("Interactive $normalizedType at $path has no text, icon, or binding (possible missing accessible label).")
        }

        if (normalizedType in setOf("input", "textfield") && node.hint.isBlank() && node.text.isBlank()) {
            warnings.add("Input field at $path has no hint or accessible label.")
        }

        // Recursively lint child nodes
        node.children.forEachIndexed { index, child ->
            lintNode(child, "$path.children[$index]", declaredStateKeys, errors, warnings)
        }

        node.itemTemplate?.let { template ->
            lintNode(template, "$path.itemTemplate", declaredStateKeys, errors, warnings)
        }
    }

    private fun checkBinding(
        bindingExpr: String,
        bindingName: String,
        path: String,
        declaredStateKeys: Set<String>,
        warnings: MutableList<String>
    ) {
        if (bindingExpr.isBlank()) return
        val rawKey = bindingExpr.removePrefix("$").removePrefix("{").removeSuffix("}").trim()
        val rootKey = rawKey.split(".").firstOrNull()?.split("[")?.firstOrNull() ?: rawKey

        if (rootKey.isNotBlank() && rootKey !in declaredStateKeys && rootKey != "item" && rootKey != "index" && !rootKey.startsWith("state.")) {
            warnings.add("Node at $path has $bindingName='$bindingExpr' referencing undeclared state key '$rootKey'.")
        }
    }
}
