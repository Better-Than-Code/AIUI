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
    primary = ClassicPrimary,
    onPrimary = Color.White,
    primaryContainer = ClassicPrimaryContainer,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = AccentGreen,
    onSecondary = Color.White,
    tertiary = AccentAmber,
    background = ClassicWhiteBackground,
    onBackground = ClassicTextPrimary,
    surface = ClassicWhiteSurface,
    onSurface = ClassicTextPrimary,
    surfaceVariant = ClassicWhiteSurfaceVariant,
    onSurfaceVariant = ClassicTextSecondary,
    outline = ClassicWhiteBorder
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


