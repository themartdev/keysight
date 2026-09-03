package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.evaluation.PlayedNote
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.Ticks
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One annotation to draw on top of a [StaffLayout] after an attempt.
 *
 * Marks about a notated note refer to it by id, so the renderer finds its anchor and tints
 * its elements. Marks about notes the player added carry their own position: they are not
 * in the layout.
 */
sealed interface NoteMark {

    data class Correct(val noteId: String) : NoteMark

    /** [played] is where the sounded pitch would sit, with its accidental, drawn beside the expected head. */
    data class WrongPitch(val noteId: String, val played: StaffPosition, val playedAlteration: Int) : NoteMark

    data class Missing(val noteId: String) : NoteMark

    /** A note the score does not contain, at the x of when it was played and the y of what was played. */
    data class Extra(val x: Double, val position: StaffPosition, val alteration: Int) : NoteMark
}

/**
 * Turns the evaluator's outcomes into marks on [layout].
 *
 * This is the only place notation meets evaluation, and it goes one way: outcomes in, marks
 * out. Extras are placed by their onset on the layout's time axis; one that lands on an
 * expected head is moved just right of it so both stay legible.
 */
fun noteMarks(layout: StaffLayout, score: Score, outcomes: List<NoteOutcome>): List<NoteMark> =
    outcomes.map { outcome ->
        when (outcome) {
            is NoteOutcome.Correct -> NoteMark.Correct(outcome.expected.id)
            is NoteOutcome.Missing -> NoteMark.Missing(outcome.expected.id)
            is NoteOutcome.WrongPitch -> {
                val spelling = sharpSpelling(outcome.played.pitch)
                NoteMark.WrongPitch(outcome.expected.id, StaffPosition.of(spelling, score.clef), spelling.alteration)
            }
            is NoteOutcome.Extra -> {
                val spelling = sharpSpelling(outcome.played.pitch)
                NoteMark.Extra(extraX(layout, score, outcome.played), StaffPosition.of(spelling, score.clef), spelling.alteration)
            }
        }
    }

private fun extraX(layout: StaffLayout, score: Score, played: PlayedNote): Double {
    val ticks = (played.onsetBeat * score.timeSignature.ticksPerBeat.value).roundToInt().coerceAtLeast(0)
    val x = layout.xAtTicks(Ticks(ticks))
    val overlapped = layout.anchors.values.firstOrNull { abs(it.x - x) < it.headWidth }
    return if (overlapped == null) x else overlapped.x + overlapped.headWidth + Spacing.CUE_GAP
}
