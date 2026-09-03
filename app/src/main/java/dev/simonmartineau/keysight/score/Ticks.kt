package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/**
 * Musical time, as an integer count of ticks.
 *
 * Notation is authored in exact fractions of a whole note, and doubles cannot hold a triplet
 * exactly, so beat positions in the score are integers at a fixed resolution. [PER_QUARTER] is
 * 960, which divides evenly by 2^6, 3 and 5, so every plain, dotted, triplet and quintuplet value
 * down to a 64th note is exact.
 *
 * Wall-clock beats (the flash configuration, the timeline) stay as doubles; the bridge between
 * the two is [TimeSignature.ticksPerBeat].
 */
@Serializable
@JvmInline
value class Ticks(val value: Int) : Comparable<Ticks> {

    init {
        require(value >= 0) { "ticks must not be negative, was $value" }
    }

    operator fun plus(other: Ticks): Ticks = Ticks(value + other.value)

    operator fun minus(other: Ticks): Ticks = Ticks(value - other.value)

    operator fun times(factor: Int): Ticks = Ticks(value * factor)

    /** This value lengthened by half, as a dot does in notation. */
    fun dotted(): Ticks {
        require(value % 2 == 0) { "$value ticks cannot be dotted exactly" }
        return Ticks(value + value / 2)
    }

    /** A whole-number ratio of this value, for tuplets: `QUARTER.divided(3)` is one triplet. */
    fun divided(by: Int): Ticks {
        require(by > 0) { "divisor must be positive" }
        require(value % by == 0) { "$value ticks cannot be divided by $by exactly" }
        return Ticks(value / by)
    }

    override fun compareTo(other: Ticks): Int = value.compareTo(other.value)

    override fun toString(): String = "${value}t"

    companion object {
        const val PER_QUARTER = 960

        val ZERO = Ticks(0)
        val WHOLE = Ticks(PER_QUARTER * 4)
        val HALF = Ticks(PER_QUARTER * 2)
        val QUARTER = Ticks(PER_QUARTER)
        val EIGHTH = Ticks(PER_QUARTER / 2)
        val SIXTEENTH = Ticks(PER_QUARTER / 4)

        fun quarters(count: Int): Ticks = Ticks(PER_QUARTER * count)
    }
}
