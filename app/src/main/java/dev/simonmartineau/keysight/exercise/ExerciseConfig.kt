package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.Hand
import dev.simonmartineau.keysight.score.PitchRange

/**
 * The musical dimensions an exercise may span.
 *
 * These are internal knobs, not user-facing settings: they describe what a piece of content is
 * allowed to contain, so that selection and adaptation can move one dimension at a time. None of
 * them are exposed in the V1 UI.
 */
data class ExerciseConfig(
    val measureCount: Int,
    val clef: Clef,
    val keySignatureFifths: Int,
    val pitchRange: PitchRange,
    val noteValues: Set<NoteValue>,
    val rests: Boolean,
    val maximumIntervalSemitones: Int,
    val chordSize: Int,
    val hand: Hand,
    val polyphony: Int,
    val rhythmicPatterns: Set<RhythmicPatternId>,
) {
    init {
        require(measureCount > 0) { "measureCount must be positive" }
        require(noteValues.isNotEmpty()) { "at least one note value must be allowed" }
        require(chordSize >= 1) { "chordSize must be at least 1" }
        require(polyphony >= 1) { "polyphony must be at least 1" }
        require(maximumIntervalSemitones >= 0) { "maximumIntervalSemitones must not be negative" }
    }
}

/** Notated durations, named by their length relative to a whole note. */
enum class NoteValue(val quarterNotes: Double) {
    WHOLE(4.0),
    HALF(2.0),
    QUARTER(1.0),
    EIGHTH(0.5),
    SIXTEENTH(0.25),
}

/**
 * Identifies a rhythmic figure a generator or selector may use, for example "half-two-quarters".
 * The vocabulary is defined by the content pack rather than by code, so this stays a plain id.
 */
@JvmInline
value class RhythmicPatternId(val value: String)
