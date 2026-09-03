package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.ScoreNote

/** What happened to one expected or played note. */
enum class NoteOutcomeType {
    /** The expected pitch was played. */
    CORRECT,

    /** Something was played in this slot, but not the notated pitch. */
    WRONG_PITCH,

    /** The notated pitch was never played. */
    MISSING,

    /** The player sounded a note the score does not contain. */
    EXTRA,
}

/**
 * One line of the annotated score: an expected note, what the player did about it, or both.
 *
 * [expected] is null for an [NoteOutcomeType.EXTRA] outcome and [playedPitch] is null for a
 * [NoteOutcomeType.MISSING] one; every other outcome carries both.
 */
data class NoteOutcome(
    val type: NoteOutcomeType,
    val expected: ScoreNote?,
    val playedPitch: Pitch?,
    val playedAtNanos: Long?,
) {
    init {
        when (type) {
            NoteOutcomeType.MISSING -> require(expected != null && playedPitch == null) {
                "a missing note has an expected note and nothing played"
            }
            NoteOutcomeType.EXTRA -> require(expected == null && playedPitch != null) {
                "an extra note has a played pitch and no expected note"
            }
            NoteOutcomeType.CORRECT, NoteOutcomeType.WRONG_PITCH ->
                require(expected != null && playedPitch != null) {
                    "$type pairs an expected note with a played pitch"
                }
        }
    }
}

/** Phase 1 scoring: which notated pitches were sounded, and what was sounded instead. */
data class PitchResult(val outcomes: List<NoteOutcome>) {

    val expectedCount: Int get() = outcomes.count { it.expected != null }

    val correctCount: Int get() = outcomes.count { it.type == NoteOutcomeType.CORRECT }

    val extraCount: Int get() = outcomes.count { it.type == NoteOutcomeType.EXTRA }

    /** Fraction of notated pitches played correctly, 0.0 to 1.0. */
    val accuracy: Double
        get() = if (expectedCount == 0) 0.0 else correctCount.toDouble() / expectedCount
}

/**
 * The evaluated outcome of one attempt.
 *
 * [evaluatorVersion] is stored alongside the result so that attempts scored by an older
 * evaluator can be recognised and recomputed from their retained MIDI.
 */
data class EvaluationResult(
    val evaluatorVersion: Int,
    val pitch: PitchResult,
)
