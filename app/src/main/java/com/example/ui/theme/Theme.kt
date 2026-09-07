package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ModernPrimary,
    onPrimary = Color(0xFF001E30),
    primaryContainer = ModernPrimaryContainer,
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = AccentGreen,
    onSecondary = Color(0xFF003820),
    tertiary = AccentAmber,
    background = ModernDarkBackground,
    onBackground = ModernTextPrimary,
    surface = ModernDarkSurface,
    onSurface = ModernTextPrimary,
    surfaceVariant = ModernDarkSurfaceVariant,
    onSurfaceVariant = ModernTextSecondary,
    outline = ModernDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = HighContrastPrimary,
    onPrimary = Color.White,
    primaryContainer = HighContrastPrimaryContainer,
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = AccentGreen,
    onSecondary = Color.White,
    tertiary = AccentAmber,
    background = HighContrastLightBackground,
    onBackground = HighContrastTextPrimary,
    surface = HighContrastLightSurface,
    onSurface = HighContrastTextPrimary,
    surfaceVariant = HighContrastLightSurfaceVariant,
    onSurfaceVariant = HighContrastTextSecondary,
    outline = HighContrastLightBorder
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
