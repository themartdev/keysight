package dev.simonmartineau.keysight.ui.notation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.notation.Mask
import dev.simonmartineau.keysight.notation.NoteMark
import dev.simonmartineau.keysight.notation.PageLayout
import dev.simonmartineau.keysight.notation.ScoreLayoutEngine
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.ui.theme.outcomeColors
import kotlin.math.min

/** Systems on a page at a time: the one being read and the next, so lookahead across the boundary works. */
const val SYSTEMS_PER_PAGE = 2

/**
 * The largest staff space, so a tablet gets a comfortably large staff rather than a
 * poster-sized one.
 */
private val MaxStaffSpace = 20.dp

/** A whole run laid out for a box, and the staff space it is drawn at. */
class FittedRun(val page: PageLayout, val staffSpace: Float)

@Composable
private fun staffColors(): StaffColors {
    val outcome = MaterialTheme.outcomeColors
    return StaffColors(
        ink = MaterialTheme.colorScheme.onSurface,
        correct = outcome.correct,
        wrong = outcome.wrong,
        missing = outcome.missing,
        extra = outcome.extra,
        cursor = MaterialTheme.colorScheme.primary,
    )
}

/**
 * The page of a run: the [SYSTEMS_PER_PAGE] systems around the time [focusTicks], as large as
 * fit the space, [mask] applied, a cursor at [cursorTicks] when there is one.
 *
 * The whole run is laid out once for the box; the window is the page turn, which moves a
 * system at a time as the focus enters the next system. Everything here is derived from the
 * arguments on every frame, so the caller decides what the beat means and this only draws.
 */
@Composable
fun RunPage(
    score: Score,
    modifier: Modifier = Modifier,
    mask: Mask = Mask.NONE,
    focusTicks: Ticks = Ticks.ZERO,
    cursorTicks: Ticks? = null,
    marks: (PageLayout) -> List<NoteMark> = { emptyList() },
) {
    val typeface = rememberBravura()
    val colors = staffColors()
    val maxStaffSpace = with(LocalDensity.current) { MaxStaffSpace.toPx() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val fitted = remember(score, widthPx, heightPx, maxStaffSpace) { fitRun(score, widthPx, heightPx, maxStaffSpace) }
        val pageMarks = remember(fitted, marks) { marks(fitted.page) }

        Canvas(Modifier.fillMaxSize()) {
            val page = fitted.page
            val staffSpace = fitted.staffSpace
            val window = page.window(page.systemAt(focusTicks), SYSTEMS_PER_PAGE)
            val first = page.systems[window.first]
            val left = (size.width - page.width * staffSpace).toFloat() / 2
            val top = (size.height - page.heightOf(window) * staffSpace).toFloat() / 2
            val origin = Offset(left, top + (first.layout.top * staffSpace).toFloat())
            val cursor = cursorTicks?.let(page::cursorAt)?.takeIf { it.system in window }
            drawPage(page, window, staffSpace, origin, typeface, colors, pageMarks, mask, cursor)
        }
    }
}

/**
 * The whole run at the staff space its pages were read at, every system top to bottom in a
 * vertical scroll, annotated with [marks]: the summary shows exactly what was read. The fit
 * is to the box, so in the box the run was read in the staff is the size it was; [above] and
 * [below] scroll with the page and take no room from the fit.
 */
@Composable
fun RunSummaryPage(
    score: Score,
    modifier: Modifier = Modifier,
    marks: (PageLayout) -> List<NoteMark> = { emptyList() },
    above: @Composable ColumnScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val typeface = rememberBravura()
    val colors = staffColors()
    val density = LocalDensity.current
    val maxStaffSpace = with(density) { MaxStaffSpace.toPx() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val fitted = remember(score, widthPx, heightPx, maxStaffSpace) { fitRun(score, widthPx, heightPx, maxStaffSpace) }
        val pageMarks = remember(fitted, marks) { marks(fitted.page) }
        val page = fitted.page
        val staffSpace = fitted.staffSpace
        val pageHeight = with(density) { (page.height * staffSpace).toFloat().toDp() }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            above()
            Canvas(Modifier.fillMaxWidth().height(pageHeight)) {
                val left = (size.width - page.width * staffSpace).toFloat() / 2
                val origin = Offset(left, (page.top * staffSpace).toFloat())
                drawPage(page, page.systems.indices, staffSpace, origin, typeface, colors, pageMarks)
            }
            below()
        }
    }
}

/**
 * The staff space comes first, from the height [SYSTEMS_PER_PAGE] systems need and the cap;
 * the systems are then justified to the width that leaves. When the tallest window turns out
 * taller than the box the staff space shrinks to fit and the run is laid out once more at the
 * wider measure.
 */
private fun fitRun(score: Score, widthPx: Float, heightPx: Float, maxStaffSpace: Float): FittedRun {
    val systemHeight = ScoreLayoutEngine.systemHeight(score.staves.size)
    val windowHeight = SYSTEMS_PER_PAGE * systemHeight + (SYSTEMS_PER_PAGE - 1) * ScoreLayoutEngine.SYSTEM_GAP
    var staffSpace = min(maxStaffSpace, heightPx / windowHeight.toFloat()).coerceAtLeast(1f)
    var page = ScoreLayoutEngine.layoutPage(score, widthPx / staffSpace.toDouble())
    val tallest = page.systems.indices.maxOf { page.heightOf(page.window(it, SYSTEMS_PER_PAGE)) }
    val overflow = min(widthPx / (page.width * staffSpace), heightPx / (tallest * staffSpace))
    if (overflow < 1.0) {
        staffSpace = (staffSpace * overflow).toFloat().coerceAtLeast(1f)
        page = ScoreLayoutEngine.layoutPage(score, widthPx / staffSpace.toDouble())
    }
    return FittedRun(page, staffSpace)
}
