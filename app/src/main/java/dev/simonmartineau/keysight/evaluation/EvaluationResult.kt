package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.score.ScoreNote
import kotlinx.serialization.Serializable

/** What happened to one expected note, or to one note that should not have been played. */
@Serializable
sealed interface NoteOutcome {

    /** The notated pitch was sounded. */
    @Serializable
    data class Correct(val expected: ScoreNote, override val played: PlayedNote) : NoteOutcome

    /** Something was played in this note's place, but not its pitch. */
    @Serializable
    data class WrongPitch(val expected: ScoreNote, override val played: PlayedNote) : NoteOutcome

    /** The notated pitch was never played. */
    @Serializable
    data class Missing(val expected: ScoreNote) : NoteOutcome {
        override val played: PlayedNote? get() = null
    }

    /** The player sounded a note the score does not contain. */
    @Serializable
    data class Extra(override val played: PlayedNote) : NoteOutcome

    /**
     * A note of an earlier segment, sounded only after that segment had been committed with
     * the note [Missing]. It is neither correct nor an extra of the segment it arrived in; it
     * is kept so that every played note has exactly one outcome.
     */
    @Serializable
    data class TooLate(val expected: ScoreNote, override val played: PlayedNote) : NoteOutcome

    /** The note the player sounded, when the outcome is about one. */
    val played: PlayedNote?
}

/** Phase 1 scoring: which notated pitches were sounded, and what was sounded instead. */
@Serializable
data class PitchResult(val outcomes: List<NoteOutcome>) {

    val correctCount: Int get() = outcomes.count { it is NoteOutcome.Correct }
    val wrongCount: Int get() = outcomes.count { it is NoteOutcome.WrongPitch }
    val missingCount: Int get() = outcomes.count { it is NoteOutcome.Missing }
    val extraCount: Int get() = outcomes.count { it is NoteOutcome.Extra }

    /** The notated notes judged here: correct, wrong or missing. */
    val expectedCount: Int get() = correctCount + wrongCount + missingCount

    /** Fraction of notated pitches played correctly, 0.0 to 1.0. */
    val accuracy: Double
        get() = if (expectedCount == 0) 0.0 else correctCount.toDouble() / expectedCount
}

/**
 * The evaluated outcome of one segment.
 *
 * [evaluatorVersion] travels with the result so that segments scored by an older evaluator
 * can be recognised and re-scored from their retained MIDI. [rhythm] is null only for results
 * stored by evaluator version 1, which did not score it.
 */
@Serializable
data class EvaluationResult(
    val evaluatorVersion: Int,
    val pitch: PitchResult,
    val rhythm: RhythmResult? = null,
) {
    /** Whether anything went wrong: a wrong, missing or extra note, or a note early or late. */
    val hasFault: Boolean
        get() = pitch.accuracy < 1.0 || pitch.extraCount > 0 || (rhythm?.accuracy ?: 1.0) < 1.0
}
