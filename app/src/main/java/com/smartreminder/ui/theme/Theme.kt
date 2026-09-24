package com.smartreminder.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue

/**
 * Single source of truth for design tokens in Cue.
 * Access via `CueTheme.colors` or `CueTheme.typography`.
 */
object CueTheme {
    val colors: CueColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCueColors.current

    val typography = Typography
}

@Composable
private fun provideCueColors(darkTheme: Boolean): CueColors {
    return if (darkTheme) {
        CueColors(
            background = CueDarkBackground,
            surface = CueDarkSurface,
            surfaceSubtle = CueDarkSurfaceSubtle,
            textPrimary = CueDarkTextPrimary,
            textSecondary = CueDarkTextSecondary,
            textMuted = CueDarkTextMuted,
            border = CueDarkBorder,
            borderStrong = CueDarkBorderStrong,
            accent = CueDarkAccent,
            accentStrong = CueDarkAccentStrong,
            accentContainer = CueDarkAccentContainer,
            cta = CueDarkCta,
            onCta = CueDarkOnCta,
            success = CueDarkSuccess,
            successContainer = CueDarkSuccessContainer,
            warning = CueDarkWarning,
            warningContainer = CueDarkWarningContainer,
            error = CueDarkError,
            errorContainer = CueDarkErrorContainer,
            isDark = true
        )
    } else {
        CueColors(
            background = CueLightBackground,
            surface = CueLightSurface,
            surfaceSubtle = CueLightSurfaceSubtle,
            textPrimary = CueLightTextPrimary,
            textSecondary = CueLightTextSecondary,
            textMuted = CueLightTextMuted,
            border = CueLightBorder,
            borderStrong = CueLightBorderStrong,
            accent = CueLightAccent,
            accentStrong = CueLightAccentStrong,
            accentContainer = CueLightAccentContainer,
            cta = CueLightCta,
            onCta = CueLightOnCta,
            success = CueLightSuccess,
            successContainer = CueLightSuccessContainer,
            warning = CueLightWarning,
            warningContainer = CueLightWarningContainer,
            error = CueLightError,
            errorContainer = CueLightErrorContainer,
            isDark = false
        )
    }
}

@Composable
fun SmartReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val cueColors = provideCueColors(darkTheme)

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = cueColors.accent,
            onPrimary = cueColors.surface, // Contrast ~5.94:1 (#818CF8 on #18181B)
            primaryContainer = cueColors.accentContainer,
            onPrimaryContainer = cueColors.accentStrong,
            secondary = cueColors.textSecondary,
            onSecondary = cueColors.surface,
            background = cueColors.background,
            onBackground = cueColors.textPrimary,
            surface = cueColors.surface,
            onSurface = cueColors.textPrimary,
            surfaceVariant = cueColors.surfaceSubtle,
            onSurfaceVariant = cueColors.textSecondary,
            outline = cueColors.borderStrong, // Stronger outline for boundary
            outlineVariant = cueColors.border, // Softer outline for subtle dividers
            error = cueColors.error,
            onError = cueColors.surface,
            errorContainer = cueColors.errorContainer,
            onErrorContainer = cueColors.error
        )
    } else {
        lightColorScheme(
            primary = cueColors.accent,
            onPrimary = cueColors.onCta,
            primaryContainer = cueColors.accentContainer,
            onPrimaryContainer = cueColors.accentStrong,
            secondary = cueColors.textSecondary,
            onSecondary = cueColors.surface,
            background = cueColors.background,
            onBackground = cueColors.textPrimary,
            surface = cueColors.surface,
            onSurface = cueColors.textPrimary,
            surfaceVariant = cueColors.surfaceSubtle,
            onSurfaceVariant = cueColors.textSecondary,
            outline = cueColors.borderStrong, // Stronger outline
            outlineVariant = cueColors.border, // Softer outline
            error = cueColors.error,
            onError = cueColors.surface,
            errorContainer = cueColors.errorContainer,
            onErrorContainer = cueColors.error
        )
    }

    CompositionLocalProvider(
        LocalCueColors provides cueColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}