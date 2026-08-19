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
private fun provideAnimatedCueColors(darkTheme: Boolean): CueColors {
    // Fast & crisp 200ms transition to avoid muddy intermediate frames
    val duration = 200
    val animSpec = tween<androidx.compose.ui.graphics.Color>(durationMillis = duration, easing = FastOutSlowInEasing)

    val background by animateColorAsState(if (darkTheme) CueDarkBackground else CueLightBackground, animSpec, label = "bg")
    val surface by animateColorAsState(if (darkTheme) CueDarkSurface else CueLightSurface, animSpec, label = "surface")
    val surfaceSubtle by animateColorAsState(if (darkTheme) CueDarkSurfaceSubtle else CueLightSurfaceSubtle, animSpec, label = "surfaceSubtle")
    val textPrimary by animateColorAsState(if (darkTheme) CueDarkTextPrimary else CueLightTextPrimary, animSpec, label = "textPrimary")
    val textSecondary by animateColorAsState(if (darkTheme) CueDarkTextSecondary else CueLightTextSecondary, animSpec, label = "textSecondary")
    val textMuted by animateColorAsState(if (darkTheme) CueDarkTextMuted else CueLightTextMuted, animSpec, label = "textMuted")
    val border by animateColorAsState(if (darkTheme) CueDarkBorder else CueLightBorder, animSpec, label = "border")
    val borderStrong by animateColorAsState(if (darkTheme) CueDarkBorderStrong else CueLightBorderStrong, animSpec, label = "borderStrong")
    val accent by animateColorAsState(if (darkTheme) CueDarkAccent else CueLightAccent, animSpec, label = "accent")
    val accentStrong by animateColorAsState(if (darkTheme) CueDarkAccentStrong else CueLightAccentStrong, animSpec, label = "accentStrong")
    val accentContainer by animateColorAsState(if (darkTheme) CueDarkAccentContainer else CueLightAccentContainer, animSpec, label = "accentContainer")
    val cta by animateColorAsState(if (darkTheme) CueDarkCta else CueLightCta, animSpec, label = "cta")
    val onCta by animateColorAsState(if (darkTheme) CueDarkOnCta else CueLightOnCta, animSpec, label = "onCta")
    val success by animateColorAsState(if (darkTheme) CueDarkSuccess else CueLightSuccess, animSpec, label = "success")
    val successContainer by animateColorAsState(if (darkTheme) CueDarkSuccessContainer else CueLightSuccessContainer, animSpec, label = "successContainer")
    val warning by animateColorAsState(if (darkTheme) CueDarkWarning else CueLightWarning, animSpec, label = "warning")
    val warningContainer by animateColorAsState(if (darkTheme) CueDarkWarningContainer else CueLightWarningContainer, animSpec, label = "warningContainer")
    val error by animateColorAsState(if (darkTheme) CueDarkError else CueLightError, animSpec, label = "error")
    val errorContainer by animateColorAsState(if (darkTheme) CueDarkErrorContainer else CueLightErrorContainer, animSpec, label = "errorContainer")

    return CueColors(
        background = background,
        surface = surface,
        surfaceSubtle = surfaceSubtle,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        border = border,
        borderStrong = borderStrong,
        accent = accent,
        accentStrong = accentStrong,
        accentContainer = accentContainer,
        cta = cta,
        onCta = onCta,
        success = success,
        successContainer = successContainer,
        warning = warning,
        warningContainer = warningContainer,
        error = error,
        errorContainer = errorContainer,
        isDark = darkTheme
    )
}

@Composable
fun SmartReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val animatedCueColors = provideAnimatedCueColors(darkTheme)

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = animatedCueColors.accent,
            onPrimary = animatedCueColors.surface, // Contrast ~5.94:1 (#818CF8 on #18181B)
            primaryContainer = animatedCueColors.accentContainer,
            onPrimaryContainer = animatedCueColors.accentStrong,
            secondary = animatedCueColors.textSecondary,
            onSecondary = animatedCueColors.surface,
            background = animatedCueColors.background,
            onBackground = animatedCueColors.textPrimary,
            surface = animatedCueColors.surface,
            onSurface = animatedCueColors.textPrimary,
            surfaceVariant = animatedCueColors.surfaceSubtle,
            onSurfaceVariant = animatedCueColors.textSecondary,
            outline = animatedCueColors.borderStrong, // Stronger outline for boundary
            outlineVariant = animatedCueColors.border, // Softer outline for subtle dividers
            error = animatedCueColors.error,
            onError = animatedCueColors.surface,
            errorContainer = animatedCueColors.errorContainer,
            onErrorContainer = animatedCueColors.error
        )
    } else {
        lightColorScheme(
            primary = animatedCueColors.accent,
            onPrimary = animatedCueColors.onCta,
            primaryContainer = animatedCueColors.accentContainer,
            onPrimaryContainer = animatedCueColors.accentStrong,
            secondary = animatedCueColors.textSecondary,
            onSecondary = animatedCueColors.surface,
            background = animatedCueColors.background,
            onBackground = animatedCueColors.textPrimary,
            surface = animatedCueColors.surface,
            onSurface = animatedCueColors.textPrimary,
            surfaceVariant = animatedCueColors.surfaceSubtle,
            onSurfaceVariant = animatedCueColors.textSecondary,
            outline = animatedCueColors.borderStrong, // Stronger outline
            outlineVariant = animatedCueColors.border, // Softer outline
            error = animatedCueColors.error,
            onError = animatedCueColors.surface,
            errorContainer = animatedCueColors.errorContainer,
            onErrorContainer = animatedCueColors.error
        )
    }

    CompositionLocalProvider(
        LocalCueColors provides animatedCueColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}