package dev.simonmartineau.keysight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.simonmartineau.keysight.R

/** One sans, three weights. */
val WorkSans = FontFamily(
    Font(R.font.work_sans_regular, FontWeight.Normal),
    Font(R.font.work_sans_medium, FontWeight.Medium),
    Font(R.font.work_sans_semibold, FontWeight.SemiBold),
)

/** Tabular figures, for numbers that sit in a column. */
const val TABULAR_FIGURES = "tnum"

private fun workSans(size: TextUnit, weight: FontWeight, tracking: TextUnit = 0.em, lineHeight: TextUnit = TextUnit.Unspecified, features: String? = null) =
    TextStyle(
        fontFamily = WorkSans,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = tracking,
        lineHeight = lineHeight,
        fontFeatureSettings = features,
    )

/**
 * The type scale of `docs/design.md`, by role. Nothing is below 11sp except the two
 * uppercase labels, which their tracking keeps legible. [label], [button] and [buttonQuiet]
 * are written in capitals: the style carries the tracking, the component uppercases the text.
 * [paramValue] is the one value the parameter grid shows, named here so it has one source.
 */
@Immutable
class TypeScale(
    /** "Good evening", "History", "Settings". */
    val screenTitle: TextStyle = workSans(22.sp, FontWeight.Medium, tracking = (-0.015).em),
    /** The mode's name on Play. */
    val paneTitle: TextStyle = workSans(20.sp, FontWeight.Medium, tracking = (-0.01).em),
    /** The resume line on Home. */
    val lead: TextStyle = workSans(19.sp, FontWeight.Medium, tracking = (-0.01).em, lineHeight = 26.6.sp),
    /** The dashboard numbers. */
    val numeral: TextStyle = workSans(28.sp, FontWeight.Medium, tracking = (-0.02).em, features = TABULAR_FIGURES),
    /** Descriptions, table cells. */
    val body: TextStyle = workSans(13.sp, FontWeight.Normal, lineHeight = 19.5.sp),
    /** MIDI status, the last-run line. */
    val meta: TextStyle = workSans(12.sp, FontWeight.Normal),
    /** Axis labels, sub-labels. */
    val micro: TextStyle = workSans(11.sp, FontWeight.Normal),
    /** Section headings, rail labels; uppercase. */
    val label: TextStyle = workSans(9.5.sp, FontWeight.SemiBold, tracking = 0.15.em),
    /** Filled buttons; uppercase. */
    val button: TextStyle = workSans(12.5.sp, FontWeight.SemiBold, tracking = 0.13.em),
    /** Outlined buttons, chips; uppercase. */
    val buttonQuiet: TextStyle = workSans(11.5.sp, FontWeight.Medium, tracking = 0.12.em),
    /** The value in a parameter cell. */
    val paramValue: TextStyle = workSans(13.5.sp, FontWeight.Medium),
)

val LocalTypeScale = staticCompositionLocalOf { TypeScale() }

/** The type scale in force. */
val MaterialTheme.type: TypeScale
    @Composable
    get() = LocalTypeScale.current

/**
 * The scale as Material's roles, so a Material component that picks its own style still
 * sets Work Sans at a size of the scale. Screens read [type], not this.
 */
internal fun materialTypography(scale: TypeScale) = Typography(
    displayLarge = scale.numeral,
    displayMedium = scale.numeral,
    displaySmall = scale.numeral,
    headlineLarge = scale.screenTitle,
    headlineMedium = scale.screenTitle,
    headlineSmall = scale.paneTitle,
    titleLarge = scale.screenTitle,
    titleMedium = scale.paneTitle,
    titleSmall = scale.lead,
    bodyLarge = scale.body,
    bodyMedium = scale.body,
    bodySmall = scale.meta,
    labelLarge = scale.button,
    labelMedium = scale.buttonQuiet,
    labelSmall = scale.label,
)
