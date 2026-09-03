package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Staff
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlinx.serialization.Serializable

/** A note value the generator may write. Nothing shorter than a quarter until the layout draws flags. */
@Serializable
enum class NoteValue(val ticks: Ticks) {
    WHOLE(Ticks.WHOLE),
    HALF(Ticks.HALF),
    QUARTER(Ticks.QUARTER),
}

/**
 * The notes one hand may be asked to read, as written in C major: every natural letter from
 * [lowest] to [highest] inclusive. Transposition moves the range with the passage.
 */
@Serializable
data class PitchRange(val lowest: SpelledPitch, val highest: SpelledPitch) {
    init {
        require(lowest.alteration == 0 && highest.alteration == 0) { "a range is spelled in C major, without accidentals" }
        require(lowest.diatonicIndex <= highest.diatonicIndex) { "$lowest is above $highest" }
    }

    val indices: IntRange get() = lowest.diatonicIndex..highest.diatonicIndex

    operator fun contains(spelling: SpelledPitch): Boolean = spelling.diatonicIndex in indices

    override fun toString(): String = "$lowest..$highest"
}

/** What the hand not carrying the melody does when both hands are on the page. */
enum class Accompaniment(val label: String) {
    /** The other staff rests: one hand reads at a time, the melody changing staff from bar to bar. */
    NONE("One hand at a time"),

    /** The other hand holds one note of the tonic triad for the whole bar under, or over, the melody. */
    HELD_NOTE("Hands together"),
}

/**
 * What the generator produces: the musical side of difficulty, as opposed to the exposure the
 * [dev.simonmartineau.keysight.run.RunConfig] controls.
 *
 * Every field is a dimension the difficulty controller may move on its own, and each one is
 * proven by the generator's constraint tests before it is offered. The ranges are spelled in
 * C major because the generator writes in C and transposes; a range therefore names a hand
 * position, not a register, and moves with the key. [maxInterval] is the largest distance
 * between consecutive melody notes, in letters: 1 is stepwise, 2 allows thirds, 4 fifths;
 * a repeated note is always allowed.
 *
 * Only [keySignature], [hands] and [accompaniment] are exposed in the settings; the rest are
 * the difficulty controller's, laid over them by its
 * [dev.simonmartineau.keysight.difficulty.MusicalLevel].
 */
@Serializable
data class ExerciseConfig(
    val keySignature: KeySignature,
    val hands: Hands,
    val accompaniment: Accompaniment = Accompaniment.NONE,
    val rightHandRange: PitchRange = DEFAULT_RIGHT_HAND_RANGE,
    val leftHandRange: PitchRange = DEFAULT_LEFT_HAND_RANGE,
    val noteValues: Set<NoteValue> = NoteValue.entries.toSet(),
    val maxInterval: Int = 2,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
) {
    init {
        require(maxInterval in 0..MAX_INTERVAL) { "maxInterval must be within 0..$MAX_INTERVAL, was $maxInterval" }
        require(noteValues.isNotEmpty()) { "a config needs at least one note value" }
        require(accompaniment == Accompaniment.NONE || hands == Hands.BOTH) { "$accompaniment needs both hands" }
        require(accompaniment != Accompaniment.HELD_NOTE || NoteValue.entries.any { it.ticks == timeSignature.ticksPerMeasure }) {
            "a held note must be one plain value: a measure of $timeSignature is not"
        }
        require(rhythms.isNotEmpty()) { "no rhythm fills a measure of $timeSignature with $noteValues" }
    }

    /** The staves the score is written on: one for a single hand, treble over bass for both. */
    val staves: List<Staff>
        get() = when (hands) {
            Hands.RIGHT -> listOf(Staff(Clef.TREBLE))
            Hands.LEFT -> listOf(Staff(Clef.BASS))
            Hands.BOTH -> listOf(Staff(Clef.TREBLE), Staff(Clef.BASS))
        }

    /** The range of the hand that reads [clef]. */
    fun rangeOf(clef: Clef): PitchRange = when (clef) {
        Clef.TREBLE -> rightHandRange
        Clef.BASS -> leftHandRange
    }

    /**
     * Every way to fill one measure with the allowed values, longest value first at every
     * position, so the vocabulary is enumerated in one fixed order: in 4/4 with every value,
     * whole; half half; half quarter quarter; quarter half quarter; quarter quarter half;
     * four quarters.
     */
    val rhythms: List<List<NoteValue>>
        get() = rhythmsFilling(noteValues, timeSignature.ticksPerMeasure)

    companion object {
        /** Wider than an octave is never a sight-reading interval for this trainer. */
        const val MAX_INTERVAL = 7

        /** The five-finger positions on C: the first hand position a pianist learns. */
        val DEFAULT_RIGHT_HAND_RANGE = PitchRange(SpelledPitch(Step.C, octave = 4), SpelledPitch(Step.G, octave = 4))
        val DEFAULT_LEFT_HAND_RANGE = PitchRange(SpelledPitch(Step.C, octave = 3), SpelledPitch(Step.G, octave = 3))

        val DEFAULT = ExerciseConfig(KeySignature.C_MAJOR, Hands.RIGHT)
    }
}

/** Every way to fill [measure] with [values], longest value first at every position; empty when none does. */
fun rhythmsFilling(values: Set<NoteValue>, measure: Ticks): List<List<NoteValue>> {
    val ordered = values.sortedByDescending { it.ticks }
    val result = ArrayList<List<NoteValue>>()
    fun fill(prefix: List<NoteValue>, filled: Ticks) {
        if (filled == measure) {
            result += prefix
            return
        }
        for (value in ordered) {
            if (filled + value.ticks <= measure) fill(prefix + value, filled + value.ticks)
        }
    }
    fill(emptyList(), Ticks.ZERO)
    return result
}
