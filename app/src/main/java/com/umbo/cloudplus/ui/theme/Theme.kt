package com.umbo.cloudplus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Modern Professional Dark Theme Palette
private val DarkBackgroundColor = Color(0xFF0F172A)     // Slate 900
private val DarkSurfaceColor = Color(0xFF1E293B)        // Slate 800
private val DarkSurfaceVariant = Color(0xFF334155)     // Slate 700
private val DarkPrimaryColor = Color(0xFF38BDF8)       // Sky 400
private val DarkPrimaryContainer = Color(0xFF0284C7)   // Sky 600
private val DarkOnPrimaryContainer = Color(0xFFF0F9FF)
private val DarkSecondaryColor = Color(0xFF10B981)     // Emerald 500
private val DarkSecondaryContainer = Color(0xFF065F46) // Emerald 800
private val DarkOnSecondaryContainer = Color(0xFFECFDF5)
private val DarkOnSurface = Color(0xFFF8FAFC)          // Slate 50
private val DarkOnSurfaceVariant = Color(0xFF94A3B8)   // Slate 400
private val DarkOutline = Color(0xFF475569)            // Slate 600
private val DarkErrorColor = Color(0xFFEF4444)          // Red 500

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF059669),
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimaryColor,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondaryColor,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackgroundColor,
    surface = DarkSurfaceColor,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DarkErrorColor
)

@Composable
fun cloudPlusTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = cloudPlusTypography,
        content = content,
    )
}
