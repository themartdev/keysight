package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.NoteValue
import dev.simonmartineau.keysight.exercise.PitchRange
import kotlinx.serialization.Serializable
import kotlin.math.min

/**
 * The difference between two levels: the dimensions that moved, in walk order, whether the
 * first of them got harder, and the level reached. [what] names the rungs reached, "up to
 * fourths", the way a move is announced.
 */
data class LevelChange(val dimensions: List<Dimension>, val harder: Boolean, val after: MusicalLevel) {
    val what: String get() = dimensions.joinToString(", ") { after.label(it) }
}

/**
 * The controller's position on the musical dimensions: the generator fields it owns, as
 * values rather than rung indices, so a stored level keeps its meaning when a ladder is
 * reshaped. [applyTo] lays them over the player's own choices (key, hands, accompaniment,
 * meter), and [of] reads them back from any configuration, which is how a segment's stored
 * configuration says what level it was read at. [rests] and [accidentals] default so a level
 * stored before their dimensions reads as it was.
 */
@Serializable
data class MusicalLevel(
    val maxInterval: Int,
    val rightHandRange: PitchRange,
    val leftHandRange: PitchRange,
    val noteValues: Set<NoteValue>,
    val rests: Boolean = false,
    val accidentals: Boolean = false,
) {
    /** Notes per hand, the narrower hand counting: an interval must fit inside it. */
    val width: Int get() = min(rightHandRange.indices.count(), leftHandRange.indices.count())

    val shortestValue: NoteValue get() = noteValues.minBy { it.ticks }

    val ranges: HandRanges get() = HandRanges(rightHandRange, leftHandRange)

    /**
     * Whether the level makes sense on its own: the largest interval fits inside the range,
     * and with accidentals there is a step for a chromatic neighbour to resolve by.
     */
    val isConsistent: Boolean get() = maxInterval < width && (!accidentals || maxInterval >= 1)

    fun applyTo(base: ExerciseConfig): ExerciseConfig = base.copy(
        maxInterval = maxInterval,
        rightHandRange = rightHandRange,
        leftHandRange = leftHandRange,
        noteValues = noteValues,
        rests = rests,
        accidentals = accidentals,
    )

    /** Where the level stands on [dimension], increasing with difficulty. */
    fun rank(dimension: Dimension): Double = when (dimension) {
        Dimension.INTERVAL -> maxInterval.toDouble()
        Dimension.RANGE -> width.toDouble()
        Dimension.RHYTHM -> -shortestValue.ticks.value.toDouble()
        Dimension.RESTS -> if (rests) 1.0 else 0.0
        Dimension.ACCIDENTALS -> if (accidentals) 1.0 else 0.0
        Dimension.LOOKAHEAD -> error("the lookahead is not a musical dimension")
    }

    /** The rung of [dimension] in words: "up to thirds", "five notes", "quarter notes", "no rests", "no accidentals". */
    fun label(dimension: Dimension): String = when (dimension) {
        Dimension.INTERVAL -> intervalLabel(maxInterval)
        Dimension.RANGE -> rangeLabel(width)
        Dimension.RHYTHM -> rhythmLabel(shortestValue)
        Dimension.RESTS -> restsLabel(rests)
        Dimension.ACCIDENTALS -> accidentalsLabel(accidentals)
        Dimension.LOOKAHEAD -> error("the lookahead is not a musical dimension")
    }

    /** "Up to thirds, five notes, quarter notes, no rests, no accidentals." */
    val description: String
        get() = MUSICAL_DIMENSIONS.joinToString(", ", postfix = ".") { label(it) }.replaceFirstChar { it.uppercase() }

    /** What changed from this level to [after], or null when nothing did. */
    fun changeTo(after: MusicalLevel): LevelChange? {
        val changed = MUSICAL_DIMENSIONS.filter { rank(it) != after.rank(it) }
        if (changed.isEmpty()) return null
        return LevelChange(changed, harder = after.rank(changed.first()) > rank(changed.first()), after = after)
    }

    companion object {
        /** The dimensions a level has a position on, in walk order. */
        val MUSICAL_DIMENSIONS: List<Dimension> = Dimension.entries.filter { it.movesWithinRun }

        fun of(config: ExerciseConfig): MusicalLevel =
            MusicalLevel(config.maxInterval, config.rightHandRange, config.leftHandRange, config.noteValues, config.rests, config.accidentals)

        /** The generator's defaults: where every player starts. */
        val DEFAULT: MusicalLevel = of(ExerciseConfig.DEFAULT)

        fun intervalLabel(letters: Int): String = when (letters) {
            0 -> "repeated notes only"
            1 -> "steps only"
            2 -> "up to thirds"
            3 -> "up to fourths"
            4 -> "up to fifths"
            5 -> "up to sixths"
            6 -> "up to sevenths"
            7 -> "up to octaves"
            else -> "up to $letters letters"
        }

        fun rangeLabel(width: Int): String = when (width) {
            1 -> "one note"
            2 -> "two notes"
            3 -> "three notes"
            4 -> "four notes"
            5 -> "five notes"
            6 -> "a sixth"
            7 -> "a seventh"
            8 -> "an octave"
            9 -> "a ninth"
            10 -> "a tenth"
            11 -> "an eleventh"
            12 -> "a twelfth"
            15 -> "two octaves"
            else -> "$width notes"
        }

        fun rhythmLabel(shortest: NoteValue): String = when (shortest) {
            NoteValue.WHOLE -> "whole notes"
            NoteValue.HALF -> "half notes"
            NoteValue.QUARTER -> "quarter notes"
            NoteValue.EIGHTH -> "eighth notes"
        }

        fun restsLabel(rests: Boolean): String = if (rests) "with rests" else "no rests"

        fun accidentalsLabel(accidentals: Boolean): String = if (accidentals) "with accidentals" else "no accidentals"
    }
}
