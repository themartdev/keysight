package dev.simonmartineau.keysight.ui.notation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.notation.NoteMark
import dev.simonmartineau.keysight.notation.StaffLayout
import dev.simonmartineau.keysight.ui.theme.outcomeColors

/**
 * The largest staff space, so a tablet gets a comfortably large staff rather than a
 * poster-sized one.
 */
private val MaxStaffSpace = 20.dp

/**
 * Draws [layout] as large as fits the space it is given, centred, never stretched: the
 * measure keeps its engraved proportions and simply scales with the screen.
 */
@Composable
fun Staff(layout: StaffLayout, modifier: Modifier = Modifier, marks: List<NoteMark> = emptyList()) {
    val typeface = rememberBravura()
    val outcome = MaterialTheme.outcomeColors
    val colors = StaffColors(
        ink = MaterialTheme.colorScheme.onSurface,
        correct = outcome.correct,
        wrong = outcome.wrong,
        missing = outcome.missing,
        extra = outcome.extra,
    )
    val maxStaffSpace = with(LocalDensity.current) { MaxStaffSpace.toPx() }

    Canvas(modifier.fillMaxSize()) {
        val staffSpace = minOf(size.height / layout.height, size.width / layout.width, maxStaffSpace.toDouble()).toFloat()
        val left = (size.width - layout.width * staffSpace).toFloat() / 2
        val top = (size.height - layout.height * staffSpace).toFloat() / 2
        val origin = Offset(left, top + (layout.top * staffSpace).toFloat())
        drawStaff(layout, staffSpace, origin, typeface, colors, marks)
    }
}
