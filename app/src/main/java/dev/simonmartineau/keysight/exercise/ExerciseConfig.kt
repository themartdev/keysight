package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Staff
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** A note value the generator may write. Nothing shorter than an eighth until the layout draws a second beam. */
@Serializable
enum class NoteValue(val ticks: Ticks) {
    WHOLE(Ticks.WHOLE),
    HALF(Ticks.HALF),
    QUARTER(Ticks.QUARTER),
    EIGHTH(Ticks.EIGHTH),
}

/** One event of a rhythm: a note of [value], or with [rest] the same span of silence. */
data class RhythmEvent(val value: NoteValue, val rest: Boolean = false) {
    val ticks: Ticks get() = value.ticks

    override fun toString(): String = if (rest) "${value.name} rest" else value.name
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
 * a repeated note is always allowed. With [rests] the rhythm may hold silence between its
 * notes, a rest of one of the note values at a time. With [accidentals] one note of the bar
 * is a chromatic neighbour, see [chromaticNeighbour]: a note of the walk raised or lowered
 * by a semitone so that it resolves by step to the note after it.
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
    val noteValues: Set<NoteValue> = DEFAULT_NOTE_VALUES,
    val rests: Boolean = false,
    val accidentals: Boolean = false,
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
     * Every readable way to fill one measure with the allowed values, longest value first at
     * every position, so the vocabulary is enumerated in one fixed order: in 4/4 with wholes
     * to quarters, whole; half half; half quarter quarter; quarter half quarter; quarter
     * quarter half; four quarters. Readable means [mayStartAt]: only a note shorter than the
     * beat starts off the beat, so eighths come in pairs that fill a beat and nothing is
     * syncopated. With [rests] the same enumeration also tries a rest at every value, after
     * the note of it, where [mayRestAt] allows; without them it is exactly the old list.
     */
    val rhythms: List<List<RhythmEvent>>
        get() = rhythmsFilling(noteValues, timeSignature, rests)

    /**
     * Whether a note of [duration] may start at [onset]: on a beat, or anywhere when it is
     * shorter than the beat. Syncopation, a longer note off the beat, is a rung of its own
     * for later; until then every note at least a beat long lands on the pulse.
     */
    fun mayStartAt(duration: Ticks, onset: Ticks): Boolean = mayStartAt(duration, onset, timeSignature)

    /**
     * Whether a rest of [duration] may start at [onset]: never first in the measure, never
     * right after another rest, and only at a multiple of its own length, so an eighth rest
     * sits on either half of a beat, a quarter rest on a beat, and a half rest on beat 1 or
     * 3 of 4/4, never across the middle of the measure. That is also how the layout splits
     * silence into rests, so every rest the generator writes is drawn as one glyph.
     */
    fun mayRestAt(duration: Ticks, onset: Ticks, afterRest: Boolean): Boolean = mayRestAt(duration, onset, afterRest, timeSignature)

    companion object {
        /** Wider than an octave is never a sight-reading interval for this trainer. */
        const val MAX_INTERVAL = 7

        /** The five-finger positions on C: the first hand position a pianist learns. */
        val DEFAULT_RIGHT_HAND_RANGE = PitchRange(SpelledPitch(Step.C, octave = 4), SpelledPitch(Step.G, octave = 4))
        val DEFAULT_LEFT_HAND_RANGE = PitchRange(SpelledPitch(Step.C, octave = 3), SpelledPitch(Step.G, octave = 3))

        /** Wholes to quarters: where every player starts, and what every configuration stored before eighths meant. */
        val DEFAULT_NOTE_VALUES: Set<NoteValue> = setOf(NoteValue.WHOLE, NoteValue.HALF, NoteValue.QUARTER)

        val DEFAULT = ExerciseConfig(KeySignature.C_MAJOR, Hands.RIGHT)
    }
}

/**
 * Every way to fill a measure of [timeSignature] with [values] in which only a note shorter
 * than the beat starts off the beat, longest value first at every position, and with [rests]
 * also a rest of each value where [ExerciseConfig.mayRestAt] allows, tried after the note of
 * it; empty when nothing fills the measure.
 */
fun rhythmsFilling(values: Set<NoteValue>, timeSignature: TimeSignature, rests: Boolean): List<List<RhythmEvent>> {
    val measure = timeSignature.ticksPerMeasure
    val ordered = values.sortedByDescending { it.ticks }
    val result = ArrayList<List<RhythmEvent>>()
    fun fill(prefix: List<RhythmEvent>, filled: Ticks) {
        if (filled == measure) {
            result += prefix
            return
        }
        for (value in ordered) {
            if (filled + value.ticks > measure) continue
            if (mayStartAt(value.ticks, filled, timeSignature)) fill(prefix + RhythmEvent(value), filled + value.ticks)
            if (rests && mayRestAt(value.ticks, filled, prefix.lastOrNull()?.rest ?: true, timeSignature)) {
                fill(prefix + RhythmEvent(value, rest = true), filled + value.ticks)
            }
        }
    }
    fill(emptyList(), Ticks.ZERO)
    return result
}

/**
 * The altered note that may stand where [note] does when [next] follows it: [note] raised or
 * lowered by a semitone so that it sits a semitone from [next] and resolves to it by a letter
 * step, a raised note upward and a lowered note downward. Null when [note] is not one letter
 * and one whole tone from [next]: E to F and B to C are semitones already, so raising the one
 * or lowering the other would spell a white key as a black one. Stated in C major, where the
 * generator writes and the constraints are checked; in the key the spelling follows from
 * transposition, so a natural appears exactly where the key signature alters the letter.
 */
fun chromaticNeighbour(note: SpelledPitch, next: SpelledPitch): SpelledPitch? {
    if (note.alteration != 0 || next.alteration != 0) return null
    val letters = next.diatonicIndex - note.diatonicIndex
    if (abs(letters) != 1 || note.pitch.semitonesTo(next.pitch) != 2 * letters) return null
    return note.copy(alteration = letters)
}

/** The vocabulary's readability rule, [ExerciseConfig.mayStartAt]. */
private fun mayStartAt(duration: Ticks, onset: Ticks, timeSignature: TimeSignature): Boolean =
    duration < timeSignature.ticksPerBeat || onset.value % timeSignature.ticksPerBeat.value == 0

/** The vocabulary's rule for rests, [ExerciseConfig.mayRestAt]; [afterRest] is true at the start of the measure too. */
private fun mayRestAt(duration: Ticks, onset: Ticks, afterRest: Boolean, timeSignature: TimeSignature): Boolean =
    !afterRest && onset > Ticks.ZERO && duration < timeSignature.ticksPerMeasure &&
        mayStartAt(duration, onset, timeSignature) && onset.value % duration.value == 0
