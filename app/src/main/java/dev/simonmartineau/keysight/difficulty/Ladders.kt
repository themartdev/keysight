package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.exercise.NoteValue
import dev.simonmartineau.keysight.exercise.PitchRange
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step

/** The ranges of both hands at one rung, spelled in C: the same width each, so they move together. */
data class HandRanges(val right: PitchRange, val left: PitchRange) {
    init {
        require(right.indices.count() == left.indices.count()) { "$right and $left are not the same width" }
    }

    /** Notes per hand, in letters. */
    val width: Int get() = right.indices.count()
}

/**
 * The rungs of every dimension, easiest first, in one place.
 *
 * The lookahead ladder is the run configuration's. The interval ladder skips sevenths, which
 * are rarer in sight-reading material than the octave that follows them. The range ladder
 * grows each hand from its five-finger position, upward first for the right hand and
 * downward first for the left, every rung holding a tone of the tonic triad so the held
 * note of a hands-together bar always has one. The rhythm ladder adds one shorter value per
 * rung, eighths last; sixteenths and dots are rungs for when the layout draws them. The rests
 * ladder is off then on: a rest is a silence of one of the note values in play. So is the
 * accidentals ladder: one chromatic neighbour per bar, or none.
 */
object Ladders {

    val LOOKAHEAD: Ladder<Double> = Ladder(RunConfig.LOOKAHEAD_LADDER_BEATS) { -it }

    val INTERVAL: Ladder<Int> = Ladder(listOf(1, 2, 3, 4, 5, 7)) { it.toDouble() }

    val RANGE: Ladder<HandRanges> = Ladder(
        listOf(
            HandRanges(range(Step.C, 4, Step.G, 4), range(Step.C, 3, Step.G, 3)),
            HandRanges(range(Step.C, 4, Step.A, 4), range(Step.B, 2, Step.G, 3)),
            HandRanges(range(Step.C, 4, Step.C, 5), range(Step.A, 2, Step.A, 3)),
            HandRanges(range(Step.B, 3, Step.D, 5), range(Step.G, 2, Step.B, 3)),
            HandRanges(range(Step.A, 3, Step.E, 5), range(Step.F, 2, Step.C, 4)),
        ),
    ) { it.width.toDouble() }

    /** Ranked by the shortest value: the shorter it is, the more notes a bar holds. */
    val RHYTHM: Ladder<Set<NoteValue>> = Ladder(
        listOf(
            setOf(NoteValue.WHOLE, NoteValue.HALF),
            setOf(NoteValue.WHOLE, NoteValue.HALF, NoteValue.QUARTER),
            setOf(NoteValue.WHOLE, NoteValue.HALF, NoteValue.QUARTER, NoteValue.EIGHTH),
        ),
    ) { -it.minOf { value -> value.ticks.value }.toDouble() }

    val RESTS: Ladder<Boolean> = Ladder(listOf(false, true)) { if (it) 1.0 else 0.0 }

    val ACCIDENTALS: Ladder<Boolean> = Ladder(listOf(false, true)) { if (it) 1.0 else 0.0 }

    private fun range(lowStep: Step, lowOctave: Int, highStep: Step, highOctave: Int) =
        PitchRange(SpelledPitch(lowStep, octave = lowOctave), SpelledPitch(highStep, octave = highOctave))
}
