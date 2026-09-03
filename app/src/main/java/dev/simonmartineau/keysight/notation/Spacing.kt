package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Ticks
import kotlin.math.log2
import kotlin.math.max

/**
 * Horizontal room a note takes, measured from the left edge of its head to the left edge of
 * the next one.
 *
 * Space grows with the logarithm of the duration, as engraved music does: an eighth gets one
 * unit of gap after its head, a quarter two, a half three and a whole four. A floor keeps
 * every column wide enough for the result screen to draw a played pitch beside the head,
 * accidental included, without running into the next column.
 */
object Spacing {

    /** Gap between heads per doubling of the duration, in staff spaces. */
    const val GAP = 1.6

    /** Gap between an accidental and the head it precedes, in staff spaces. */
    const val ACCIDENTAL_GAP = 0.25

    /** Scale of the notehead drawn beside an expected note to show what was played instead. */
    const val CUE_SCALE = 0.7

    /** Gap between an expected head and the cue head beside it. */
    const val CUE_GAP = 0.25

    val MIN_ADVANCE: Double = run {
        val head = BravuraMetrics.of(Glyph.NOTEHEAD_BLACK).width
        val sharp = BravuraMetrics.of(Glyph.ACCIDENTAL_SHARP).width
        head + CUE_GAP + sharp + ACCIDENTAL_GAP + head * CUE_SCALE + CUE_GAP
    }

    fun advanceFor(duration: Ticks, headWidth: Double): Double {
        require(duration > Ticks.ZERO) { "duration must be positive" }
        val doublings = 1.0 + log2(duration.value.toDouble() / Ticks.EIGHTH.value)
        return max(MIN_ADVANCE, headWidth + GAP * max(doublings, 0.0))
    }
}
