package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.evaluation.PlayedNote
import dev.simonmartineau.keysight.evaluation.RhythmResult
import dev.simonmartineau.keysight.evaluation.TimingJudgement
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.Ticks
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One annotation to draw on top of a [PageLayout] after a run.
 *
 * Marks about a notated note refer to it by id, so the renderer finds its anchor and tints
 * its elements. Marks about notes the player added carry their own position: they are not
 * in the layout.
 */
sealed interface NoteMark {

    /** [timing] is null when the result has no rhythm judgement, or the note was on time. */
    data class Correct(val noteId: String, val timing: TimingJudgement? = null) : NoteMark

    /**
     * [played] is where the sounded pitch would sit on the expected note's staff, with the
     * [accidental] the key makes necessary, drawn beside the expected head.
     */
    data class WrongPitch(
        val noteId: String,
        val played: StaffPosition,
        val accidental: Glyph?,
        val timing: TimingJudgement? = null,
    ) : NoteMark

    data class Missing(val noteId: String) : NoteMark

    /**
     * A note the score does not contain, on the [system] and [staff] nearest to what was
     * played, at the x of when it was played and the y of what was played.
     */
    data class Extra(
        val system: Int,
        val staff: Int,
        val x: Double,
        val position: StaffPosition,
        val accidental: Glyph?,
        val ticks: Ticks,
    ) : NoteMark
}

/**
 * Turns the evaluator's outcomes into marks on [page].
 *
 * This is the only place notation meets evaluation, and it goes one way: outcomes in, marks
 * out. Played pitches are spelled as the key spells them. Extras go on the system whose time
 * they fall in and the staff whose middle line is nearest, placed by their onset on that
 * system's time axis; one that lands on an expected head is moved just right of it so both
 * stay legible. Matched notes carry their timing from [rhythm] when it was early or late; on
 * time needs no mark, and neither does a note played too late to count: its bar already
 * shows it missing.
 */
fun noteMarks(page: PageLayout, score: Score, outcomes: List<NoteOutcome>, rhythm: RhythmResult? = null): List<NoteMark> =
    outcomes.mapNotNull { outcome ->
        when (outcome) {
            is NoteOutcome.TooLate -> null
            is NoteOutcome.Correct -> NoteMark.Correct(outcome.expected.id, offBeat(rhythm, outcome.expected.id))
            is NoteOutcome.Missing -> NoteMark.Missing(outcome.expected.id)
            is NoteOutcome.WrongPitch -> {
                val spelling = spelledIn(outcome.played.pitch, score.keySignature)
                NoteMark.WrongPitch(
                    outcome.expected.id,
                    StaffPosition.of(spelling, score.staffOf(outcome.expected).clef),
                    cueAccidental(spelling, score.keySignature),
                    offBeat(rhythm, outcome.expected.id),
                )
            }
            is NoteOutcome.Extra -> {
                val spelling = spelledIn(outcome.played.pitch, score.keySignature)
                val ticks = ticksOf(outcome.played, score)
                val system = page.systems.indexOfLast { ticks >= it.layout.ticks.start }.coerceAtLeast(0)
                val staff = nearestStaff(outcome.played.pitch, score)
                NoteMark.Extra(
                    system = system,
                    staff = staff,
                    x = extraX(page.systems[system].layout, ticks),
                    position = StaffPosition.of(spelling, score.staves[staff].clef),
                    accidental = cueAccidental(spelling, score.keySignature),
                    ticks = ticks,
                )
            }
        }
    }

/** The same over a single system. */
fun noteMarks(layout: SystemLayout, score: Score, outcomes: List<NoteOutcome>, rhythm: RhythmResult? = null): List<NoteMark> =
    noteMarks(PageLayout(listOf(PlacedSystem(layout, 0.0))), score, outcomes, rhythm)

private fun offBeat(rhythm: RhythmResult?, noteId: String): TimingJudgement? =
    rhythm?.timingOf(noteId)?.judgement?.takeIf { it != TimingJudgement.ON_TIME }

private fun ticksOf(played: PlayedNote, score: Score): Ticks =
    Ticks((played.onsetBeat * score.timeSignature.ticksPerBeat.value).roundToInt().coerceAtLeast(0))

/** The staff on which [pitch] sits nearest the middle line; the upper one when tied. */
private fun nearestStaff(pitch: Pitch, score: Score): Int {
    val spelling = spelledIn(pitch, score.keySignature)
    return score.staves.indices.minBy { staff ->
        abs(StaffPosition.of(spelling, score.staves[staff].clef).value - StaffPosition.MIDDLE_LINE.value)
    }
}

private fun extraX(layout: SystemLayout, ticks: Ticks): Double {
    val x = layout.xAtTicks(ticks)
    val overlapped = layout.anchors.values.firstOrNull { abs(it.x - x) < it.headWidth }
    return if (overlapped == null) x else overlapped.x + overlapped.headWidth + Spacing.CUE_GAP
}
