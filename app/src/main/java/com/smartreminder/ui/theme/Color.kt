package com.smartreminder.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// LIGHT COLOR PALETTE (Warm Calm Canvas & High-Contrast Typography)
// ============================================================================
val CueLightBackground = Color(0xFFFAFAF9)
val CueLightSurface = Color(0xFFFFFFFF)
val CueLightSurfaceSubtle = Color(0xFFF5F5F4) // Warm temperature matching canvas
val CueLightTextPrimary = Color(0xFF18181B)
val CueLightTextSecondary = Color(0xFF696972) // Increased contrast ~5.2:1 (WCAG AA compliant)
val CueLightTextMuted = Color(0xFFA1A1AA)     // Strictly nonessential/placeholder/decorative
val CueLightBorder = Color(0xFFE4E4E7)
val CueLightBorderStrong = Color(0xFFD4D4D8)
val CueLightAccent = Color(0xFF4F46E5)
val CueLightAccentStrong = Color(0xFF4338CA)
val CueLightAccentContainer = Color(0xFFEEF2FF)
val CueLightCta = Color(0xFF18181B)
val CueLightOnCta = Color(0xFFFFFFFF)
val CueLightSuccess = Color(0xFF15803D)
val CueLightSuccessContainer = Color(0xFFF0FDF4)
val CueLightWarning = Color(0xFFB45309)
val CueLightWarningContainer = Color(0xFFFFFBEB)
val CueLightError = Color(0xFFB91C1C)
val CueLightErrorContainer = Color(0xFFFEF2F2)

// ============================================================================
// DARK COLOR PALETTE (Softened Charcoal & Radiant Indigo Intelligence)
// ============================================================================
val CueDarkBackground = Color(0xFF111113) // Softened charcoal, eliminates pure black-hole strain
val CueDarkSurface = Color(0xFF18181B)
val CueDarkSurfaceSubtle = Color(0xFF242427)
val CueDarkTextPrimary = Color(0xFFECECEF) // Softened white, eliminates glare
val CueDarkTextSecondary = Color(0xFFA1A1AA)
val CueDarkTextMuted = Color(0xFF81818A)
val CueDarkBorder = Color(0xFF2C2C30)
val CueDarkBorderStrong = Color(0xFF52525B) // Clear tactile boundaries for interactive inputs
val CueDarkAccent = Color(0xFF818CF8) // Radiant Indigo for dark mode (contrast ~5.94:1)
val CueDarkAccentStrong = Color(0xFFA5B4FC)
val CueDarkAccentContainer = Color(0xFF272554)
val CueDarkCta = Color(0xFF4F46E5)
val CueDarkOnCta = Color(0xFFFFFFFF)
val CueDarkSuccess = Color(0xFF22C55E)
val CueDarkSuccessContainer = Color(0xFF052E16)
val CueDarkWarning = Color(0xFFF59E0B)
val CueDarkWarningContainer = Color(0xFF451A03)
val CueDarkError = Color(0xFFF87171) // High-visibility error on dark surfaces
val CueDarkErrorContainer = Color(0xFF450A0A)

// ============================================================================
// DYNAMIC COMPOSABLE ACCESSORS (Zero-Churn Global Architecture)
// ============================================================================
val CueBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.background

val CueSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.surface

val CueSurfaceSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.surfaceSubtle

val CueTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.textPrimary

val CueTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.textSecondary

val CueTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.textMuted

// Backward-compatible alias
val CueTextTertiary: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.textMuted

val CueBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.border

val CueBorderStrong: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.borderStrong

val CueAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.accent

val CueAccentStrong: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.accentStrong

val CueAccentContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.accentContainer

val CueCta: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.cta

val CueOnCta: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.onCta

val CueSuccess: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.success

val CueSuccessContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.successContainer

val CueWarning: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.warning

val CueWarningContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.warningContainer

val CueError: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.error

val CueErrorContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = CueTheme.colors.errorContainer

@Immutable
data class CueColors(
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentContainer: Color,
    val cta: Color,
    val onCta: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val isDark: Boolean
) {
    // Backward compatibility property
    val textTertiary: Color get() = textMuted
}

val LocalCueColors = staticCompositionLocalOf {
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