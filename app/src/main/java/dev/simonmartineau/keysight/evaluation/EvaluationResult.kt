package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.score.ScoreNote
import kotlinx.serialization.Serializable

/** What happened to one expected note, or to one note that should not have been played. */
@Serializable
sealed interface NoteOutcome {

    /** The notated pitch was sounded. */
    @Serializable
    data class Correct(val expected: ScoreNote, val played: PlayedNote) : NoteOutcome

    /** Something was played in this note's place, but not its pitch. */
    @Serializable
    data class WrongPitch(val expected: ScoreNote, val played: PlayedNote) : NoteOutcome

    /** The notated pitch was never played. */
    @Serializable
    data class Missing(val expected: ScoreNote) : NoteOutcome

    /** The player sounded a note the score does not contain. */
    @Serializable
    data class Extra(val played: PlayedNote) : NoteOutcome
}

/** Phase 1 scoring: which notated pitches were sounded, and what was sounded instead. */
@Serializable
data class PitchResult(val outcomes: List<NoteOutcome>) {

    val expectedCount: Int get() = outcomes.count { it !is NoteOutcome.Extra }
    val correctCount: Int get() = outcomes.count { it is NoteOutcome.Correct }
    val wrongCount: Int get() = outcomes.count { it is NoteOutcome.WrongPitch }
    val missingCount: Int get() = outcomes.count { it is NoteOutcome.Missing }
    val extraCount: Int get() = outcomes.count { it is NoteOutcome.Extra }

    /** Fraction of notated pitches played correctly, 0.0 to 1.0. */
    val accuracy: Double
        get() = if (expectedCount == 0) 0.0 else correctCount.toDouble() / expectedCount
}

/**
 * The evaluated outcome of one attempt.
 *
 * [evaluatorVersion] travels with the result so that attempts scored by an older evaluator can
 * be recognised and re-scored from their retained MIDI.
 */
@Serializable
data class EvaluationResult(
    val evaluatorVersion: Int,
    val pitch: PitchResult,
)
