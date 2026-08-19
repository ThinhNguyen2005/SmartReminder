package com.smartreminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = CueAccent,
    onPrimary = CueSurface,
    primaryContainer = CueAccentContainer,
    onPrimaryContainer = CueAccentStrong,
    secondary = CueTextSecondary,
    onSecondary = CueSurface,
    background = CueBackground,
    onBackground = CueTextPrimary,
    surface = CueSurface,
    onSurface = CueTextPrimary,
    surfaceVariant = CueSurfaceSubtle,
    onSurfaceVariant = CueTextSecondary,
    outline = CueBorder,
    outlineVariant = CueBorderStrong,
    error = CueError,
    onError = CueSurface,
    errorContainer = CueErrorContainer,
    onErrorContainer = CueError
)

private val DarkColorScheme = darkColorScheme(
    primary = CueAccent,
    onPrimary = CueSurface,
    primaryContainer = CueAccentStrong,
    onPrimaryContainer = CueAccentContainer,
    secondary = CueTextSecondary,
    onSecondary = CueTextPrimary,
    background = CueTextPrimary,
    onBackground = CueBackground,
    surface = Color(0xFF242427),
    onSurface = CueBackground,
    surfaceVariant = Color(0xFF27272A),
    onSurfaceVariant = CueTextTertiary,
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF52525B),
    error = CueError,
    onError = CueSurface,
    errorContainer = CueErrorContainer,
    onErrorContainer = CueError
)

@Composable
fun SmartReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}