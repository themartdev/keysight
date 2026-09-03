package dev.simonmartineau.keysight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Material's roles filled from the [Palette], so the notation renderer's `onSurface` is ink,
 * its `primary` cursor is the accent, and any Material component left on a screen sits on
 * paper. The only hue in the scheme is `error`, a judgement colour a stopped run may carry.
 */
private fun schemeOf(palette: Palette, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = palette.inkAccent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.paperDim,
        onPrimaryContainer = palette.ink,
        secondary = palette.onSurfaceMuted,
        onSecondary = palette.paper,
        secondaryContainer = palette.paperDim,
        onSecondaryContainer = palette.ink,
        tertiary = palette.onSurfaceMuted,
        onTertiary = palette.paper,
        tertiaryContainer = palette.paperDim,
        onTertiaryContainer = palette.ink,
        background = palette.ground,
        onBackground = palette.ink,
        surface = palette.paper,
        onSurface = palette.ink,
        surfaceVariant = palette.paperDim,
        onSurfaceVariant = palette.onSurfaceMuted,
        surfaceTint = palette.paper,
        surfaceContainerLowest = palette.paper,
        surfaceContainerLow = palette.paper,
        surfaceContainer = palette.paper,
        surfaceContainerHigh = palette.paperDim,
        surfaceContainerHighest = palette.paperDim,
        surfaceDim = palette.ground,
        surfaceBright = palette.paper,
        inverseSurface = palette.ink,
        inverseOnSurface = palette.paper,
        inversePrimary = palette.paper,
        outline = palette.outline,
        outlineVariant = palette.outlineWeak,
        scrim = palette.ink,
        error = if (dark) WrongDark else Wrong,
        onError = palette.onAccent,
        errorContainer = palette.paperDim,
        onErrorContainer = if (dark) WrongDark else Wrong,
    )
}

private val LightColors = schemeOf(Palette.Light, dark = false)
private val DarkColors = schemeOf(Palette.Dark, dark = true)

/**
 * Whether the app is showing its dark scheme. This follows the player's theme choice, not
 * the system setting, so anything that picks colours by darkness reads this rather than
 * `isSystemInDarkTheme`.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Dynamic colour is deliberately not used: the notation and the correct/wrong annotations need a
 * fixed, high-contrast relationship that a wallpaper-derived palette cannot guarantee.
 */
@Composable
fun KeySightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) Palette.Dark else Palette.Light
    val scale = TypeScale()
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalPalette provides palette,
        LocalTypeScale provides scale,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = materialTypography(scale),
            content = content,
        )
    }
}
