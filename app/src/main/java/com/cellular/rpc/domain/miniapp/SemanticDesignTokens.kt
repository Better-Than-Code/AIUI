package com.cellular.rpc.domain.miniapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic Design Token Palette and Vector Icon Registry.
 * Translates abstract blueprint tokens into accessible, theme-aware Compose styles.
 */
object SemanticDesignTokens {

    // Spacing & Padding Tokens
    fun resolveSpacing(token: String?, defaultDp: Dp = 8.dp): Dp {
        return when (token?.lowercase()?.trim()) {
            "none", "0", "zero" -> 0.dp
            "xs", "extra_small", "4" -> 4.dp
            "sm", "small", "8" -> 8.dp
            "md", "medium", "12", "default" -> 12.dp
            "lg", "large", "16" -> 16.dp
            "xl", "extra_large", "24" -> 24.dp
            "xxl", "32" -> 32.dp
            else -> defaultDp
        }
    }

    // Corner Radius Tokens
    fun resolveCornerRadius(token: String?, defaultDp: Dp = 12.dp): Dp {
        return when (token?.lowercase()?.trim()) {
            "none", "0", "sharp" -> 0.dp
            "xs", "4" -> 4.dp
            "sm", "small", "8" -> 8.dp
            "md", "medium", "12", "default" -> 12.dp
            "lg", "large", "16", "rounded" -> 16.dp
            "xl", "pill", "full", "999" -> 999.dp
            else -> defaultDp
        }
    }

    // Theme Color Tokens
    @Composable
    fun resolveColor(token: String?, defaultColor: Color = MaterialTheme.colorScheme.onSurface): Color {
        val scheme: ColorScheme = MaterialTheme.colorScheme
        return when (token?.lowercase()?.trim()) {
            "primary" -> scheme.primary
            "on_primary", "onprimary" -> scheme.onPrimary
            "primary_container", "primarycontainer" -> scheme.primaryContainer
            "on_primary_container" -> scheme.onPrimaryContainer
            "secondary" -> scheme.secondary
            "secondary_container" -> scheme.secondaryContainer
            "surface" -> scheme.surface
            "surface_variant", "surfacevariant" -> scheme.surfaceVariant
            "background" -> scheme.background
            "error" -> scheme.error
            "outline", "border" -> scheme.outline
            "outline_variant", "divider" -> scheme.outlineVariant
            "text_primary", "on_surface" -> scheme.onSurface
            "text_secondary", "text_muted", "on_surface_variant" -> scheme.onSurfaceVariant
            "accent" -> scheme.tertiary
            "success" -> Color(0xFF2E7D32)
            "warning" -> Color(0xFFED6C02)
            "info" -> Color(0xFF0288D1)
            else -> defaultColor
        }
    }

    // Vector Icon Palette Registry
    fun resolveIcon(name: String?): ImageVector {
        return when (name?.lowercase()?.trim()) {
            "add", "plus", "new" -> Icons.Default.Add
            "check", "done", "checkmark" -> Icons.Default.Check
            "close", "clear", "cancel", "x" -> Icons.Default.Close
            "delete", "trash", "remove" -> Icons.Default.Delete
            "edit", "pencil" -> Icons.Default.Edit
            "refresh", "sync", "reload" -> Icons.Default.Refresh
            "search", "find" -> Icons.Default.Search
            "settings", "gear" -> Icons.Default.Settings
            "share" -> Icons.Default.Share
            "star", "favorite" -> Icons.Default.Star
            "thumb_up", "like" -> Icons.Default.ThumbUp
            "send" -> Icons.AutoMirrored.Filled.Send
            "arrow_back", "back" -> Icons.AutoMirrored.Filled.ArrowBack
            "arrow_forward", "chevron_right", "next" -> Icons.AutoMirrored.Filled.ArrowForward
            "list", "checklist", "tasks" -> Icons.AutoMirrored.Filled.List
            "info" -> Icons.Default.Info
            "warning", "alert" -> Icons.Default.Warning
            "account", "person", "user" -> Icons.Default.Person
            "shopping_cart", "cart" -> Icons.Default.ShoppingCart
            "play", "start" -> Icons.Default.PlayArrow
            "brush", "canvas", "draw", "palette" -> Icons.Default.Edit
            "calculate", "calculator", "math", "dollar" -> Icons.Default.ShoppingCart
            else -> Icons.Default.Widgets
        }
    }
}
