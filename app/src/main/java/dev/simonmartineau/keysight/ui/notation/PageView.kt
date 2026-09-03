package dev.simonmartineau.keysight.ui.notation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.notation.Mask
import dev.simonmartineau.keysight.notation.NoteMark
import dev.simonmartineau.keysight.notation.PageLayout
import dev.simonmartineau.keysight.notation.ScoreLayoutEngine
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.ui.theme.outcomeColors
import kotlin.math.min

/**
 * The largest staff space, so a tablet gets a comfortably large staff rather than a
 * poster-sized one.
 */
private val MaxStaffSpace = 20.dp

/** A page laid out for a box, and the staff space it is drawn at. */
private class FittedPage(val page: PageLayout, val staffSpace: Float)

/**
 * Engraves [score] as a page of systems as large as fits the space it is given, centred.
 *
 * The staff space comes first, from the height one system needs and the cap; the systems
 * are then justified to the width that leaves. When the page turns out taller than the box
 * the staff space shrinks to fit and the page is laid out once more at the wider measure.
 * [marks] is computed from the finished layout, since marks are placed on it.
 */
@Composable
fun Page(
    score: Score,
    modifier: Modifier = Modifier,
    mask: Mask = Mask.NONE,
    marks: (PageLayout) -> List<NoteMark> = { emptyList() },
) {
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

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val fitted = remember(score, widthPx, heightPx, maxStaffSpace) { fit(score, widthPx, heightPx, maxStaffSpace) }
        val pageMarks = remember(fitted, marks) { marks(fitted.page) }

        Canvas(Modifier.fillMaxSize()) {
            val page = fitted.page
            val staffSpace = fitted.staffSpace
            val left = (size.width - page.width * staffSpace).toFloat() / 2
            val top = (size.height - page.height * staffSpace).toFloat() / 2
            val origin = Offset(left, top + (page.top * staffSpace).toFloat())
            drawPage(page, staffSpace, origin, typeface, colors, pageMarks, mask)
        }
    }
}

private fun fit(score: Score, widthPx: Float, heightPx: Float, maxStaffSpace: Float): FittedPage {
    val systemHeight = ScoreLayoutEngine.systemHeight(score.staves.size)
    var staffSpace = min(maxStaffSpace, heightPx / systemHeight.toFloat()).coerceAtLeast(1f)
    var page = ScoreLayoutEngine.layoutPage(score, widthPx / staffSpace.toDouble())
    val overflow = min(widthPx / (page.width * staffSpace), heightPx / (page.height * staffSpace))
    if (overflow < 1.0) {
        staffSpace = (staffSpace * overflow).toFloat().coerceAtLeast(1f)
        page = ScoreLayoutEngine.layoutPage(score, widthPx / staffSpace.toDouble())
    }
    return FittedPage(page, staffSpace)
}
