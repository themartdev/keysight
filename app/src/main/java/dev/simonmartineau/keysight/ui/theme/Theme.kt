package dev.simonmartineau.keysight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Paper,
    secondary = Amber,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    error = Wrong,
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Ink,
    secondary = AmberLight,
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    error = WrongDark,
)

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
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = KeySightTypography,
            content = content,
        )
    }
}
