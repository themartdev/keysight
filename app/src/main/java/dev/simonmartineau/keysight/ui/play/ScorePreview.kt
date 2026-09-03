package dev.simonmartineau.keysight.ui.play

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
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.notation.ScoreLayoutEngine
import dev.simonmartineau.keysight.notation.SystemLayout
import dev.simonmartineau.keysight.run.generatedSegment
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.ui.notation.StaffColors
import dev.simonmartineau.keysight.ui.notation.drawSystem
import dev.simonmartineau.keysight.ui.notation.rememberBravura
import dev.simonmartineau.keysight.ui.theme.outcomeColors
import dev.simonmartineau.keysight.ui.theme.palette
import kotlin.math.min

/** How many bars the preview is generated from; the system shows as many as fit its width. */
const val PREVIEW_BARS = 4

/** The largest staff space of a preview, so a tablet gets a comfortable staff rather than a poster. */
private val MaxStaffSpace = 16.dp

/**
 * The first [bars] of a run seeded [seed] and read at [config], chained into one score with
 * no count-in: what the player would see once the run is under way. The generator writes
 * every bar in the same key, meter and staves, so the chain is one score.
 */
fun previewScore(seed: Long, config: ExerciseConfig, bars: Int = PREVIEW_BARS): Score {
    val segments = (1..bars).map { generatedSegment(seed, it, config).score }
    val first = segments.first()
    val measure = first.timeSignature.ticksPerMeasure
    return Score(
        timeSignature = first.timeSignature,
        keySignature = first.keySignature,
        staves = first.staves,
        measureCount = segments.size,
        notes = segments.flatMapIndexed { index, segment ->
            segment.notes.map { note -> note.copy(id = "${index + 1}:${note.id}", onset = note.onset + measure * index) }
        },
    )
}

/**
 * One system of [score], engraved by the layout engine for the box: as many bars as fit its
 * width, at the largest staff space its height allows, centred. Static: no cursor, no mask,
 * no marks. The chrome around it decides the box; this only draws.
 */
@Composable
fun ScorePreview(score: Score, modifier: Modifier = Modifier) {
    val typeface = rememberBravura()
    val palette = MaterialTheme.palette
    val outcome = MaterialTheme.outcomeColors
    val colors = StaffColors(
        ink = palette.ink,
        correct = outcome.correct,
        wrong = outcome.wrong,
        missing = outcome.missing,
        extra = outcome.extra,
        cursor = palette.inkAccent,
    )
    val maxStaffSpace = with(LocalDensity.current) { MaxStaffSpace.toPx() }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val fitted = remember(score, widthPx, heightPx, maxStaffSpace) { fitPreview(score, widthPx, heightPx, maxStaffSpace) }
        Canvas(Modifier.fillMaxSize()) {
            val (layout, staffSpace) = fitted
            val left = (size.width - layout.width * staffSpace).toFloat() / 2
            val top = (size.height - (layout.top - layout.bottom) * staffSpace).toFloat() / 2
            drawSystem(layout, staffSpace, Offset(left, top + (layout.top * staffSpace).toFloat()), typeface, colors)
        }
    }
}

/** The system at the staff space the box's height allows, justified to the box's width. */
private fun fitPreview(score: Score, widthPx: Float, heightPx: Float, maxStaffSpace: Float): Pair<SystemLayout, Float> {
    val natural = ScoreLayoutEngine.layoutSystem(score, 0, targetWidth = null, showTimeSignature = true)
    val byHeight = heightPx / (natural.top - natural.bottom).toFloat()
    val staffSpace = min(byHeight, maxStaffSpace).coerceAtLeast(1f)
    val layout = ScoreLayoutEngine.layoutSystem(score, 0, targetWidth = (widthPx / staffSpace).toDouble(), showTimeSignature = true)
    val byWidth = widthPx / layout.width.toFloat()
    return layout to min(staffSpace, byWidth)
}
