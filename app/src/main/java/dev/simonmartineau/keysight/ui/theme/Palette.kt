package dev.simonmartineau.keysight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The design system's colours for one theme: the six surfaces and inks, and the alpha ramp
 * over [ink] that stands in for every grey. Dark is the same structure inverted and its ramp
 * one step (0.04) stronger, since thin light lines on ink read fainter than dark lines on
 * paper. Chrome reads these; the judgement colours live in [outcomeColors].
 */
@Immutable
class Palette(
    /** Panels, the rail, cards. */
    val paper: Color,
    /** The page behind panels. */
    val ground: Color,
    /** A selected row, a pressed keycap. */
    val paperDim: Color,
    /** Text, notation, staff lines. */
    val ink: Color,
    /** Filled buttons, the active rail mark, the cursor. */
    val inkAccent: Color,
    /** The label on [inkAccent]. */
    val onAccent: Color,
    /** Added to every step of the ramp except full ink. */
    private val rampShift: Float,
) {
    /** [ink] at [alpha] plus the theme's shift, the way every grey is made. */
    fun inkAt(alpha: Float): Color = ink.copy(alpha = (alpha + rampShift).coerceAtMost(1f))

    /** Primary text, notation. */
    val onSurface: Color get() = ink

    /** Secondary text, values in a row. */
    val onSurfaceMuted: Color get() = inkAt(0.66f)

    /** Section labels, axis labels. */
    val onSurfaceFaint: Color get() = inkAt(0.55f)

    /** The outlined button's border. */
    val outline: Color get() = inkAt(0.34f)

    /** Hairlines with weight, disabled borders. */
    val outlineWeak: Color get() = inkAt(0.18f)

    /** Row separators, panel edges. */
    val hairline: Color get() = inkAt(0.10f)

    /** Inactive chart bars, rail marks. */
    val fill: Color get() = inkAt(0.16f)

    companion object {
        val Light = Palette(
            paper = Paper,
            ground = Ground,
            paperDim = PaperDim,
            ink = Ink,
            inkAccent = InkAccent,
            onAccent = OnAccent,
            rampShift = 0f,
        )

        val Dark = Palette(
            paper = DarkPaper,
            ground = DarkGround,
            paperDim = DarkDim,
            ink = DarkInk,
            inkAccent = DarkAccent,
            onAccent = DarkOnAccent,
            rampShift = 0.04f,
        )
    }
}

val LocalPalette = staticCompositionLocalOf { Palette.Light }

/** The palette of the theme in force. */
val MaterialTheme.palette: Palette
    @Composable
    get() = LocalPalette.current
