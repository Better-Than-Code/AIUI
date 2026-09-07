package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFE4E4E7),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFFD4D4D8),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E1E24),
    onSurfaceVariant = Color(0xFFE4E4E7),
    outline = Color(0xFF71717A),
    outlineVariant = Color(0xFF3F3F46)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF020617), // Pitch black primary button
    onPrimary = Color(0xFFFFFFFF), // White text on primary button
    primaryContainer = Color(0xFF0F172A), // Dark slate container
    onPrimaryContainer = Color(0xFFFFFFFF), // White text on dark container
    secondary = Color(0xFF334155),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF1E293B),
    background = Color(0xFFFFFFFF), // Pure white background
    onBackground = Color(0xFF020617), // Pitch black text
    surface = Color(0xFFFFFFFF), // Pure white surface
    onSurface = Color(0xFF020617), // Pitch black text
    surfaceVariant = Color(0xFFF1F5F9), // Light neutral grey container
    onSurfaceVariant = Color(0xFF020617), // Pitch black text
    outline = Color(0xFF0F172A), // High contrast dark outline
    outlineVariant = Color(0xFF64748B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
