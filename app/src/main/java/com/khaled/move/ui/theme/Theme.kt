package com.khaled.move.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val DarkColors = darkColorScheme(
    primary = AccentRed,
    onPrimary = OnAccentRed,
    secondary = AccentRed,
    onSecondary = OnAccentRed,
    background = AmoledBlack,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = AccentRed,
    onError = OnAccentRed,
)

private val LightColors = lightColorScheme(
    primary = AccentRed,
    onPrimary = OnAccentRed,
    secondary = AccentRed,
    onSecondary = OnAccentRed,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceLightElevated,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = AccentRed,
    onError = OnAccentRed,
)

/**
 * App theme. [themeMode] defaults to [ThemeMode.DARK] rather than
 * [ThemeMode.SYSTEM] — the brief prioritizes the AMOLED look as the default
 * identity, with System/Light available as explicit user choices later
 * (settings UI isn't built yet; MainViewModel already exposes the state
 * for when it is).
 *
 * Dynamic color is intentionally never used: the point of the AMOLED /
 * Nothing OS-inspired look is a fixed, deliberate palette, not one derived
 * from the user's wallpaper.
 */
@Composable
fun MoveTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = MoveTypography,
        content = content,
    )
}
