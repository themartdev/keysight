package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step

/**
 * Where a note sits on the staff, counted in diatonic steps above the bottom line.
 *
 * Lines are the even values 0, 2, 4, 6, 8 and spaces the odd ones, so the middle line is 4
 * and one step up is always one letter name up. In treble clef E4 is 0 and C4 is -2, the
 * first ledger line below. Vertical layout works in staff spaces, [y], which is half the
 * position.
 */
@JvmInline
value class StaffPosition(val value: Int) : Comparable<StaffPosition> {

    /** Height above the bottom staff line, in staff spaces. */
    val y: Double get() = value / 2.0

    val isLine: Boolean get() = value % 2 == 0

    /** True below the middle line: those notes carry their stem on the right, pointing up. */
    val stemUp: Boolean get() = value < MIDDLE_LINE.value

    /**
     * The ledger lines a notehead at this position needs, nearest the staff first. A note on
     * a ledger line needs that line; a note in the space beyond it needs it too.
     */
    val ledgerLines: List<StaffPosition>
        get() = when {
            value <= FIRST_LEDGER_BELOW -> (FIRST_LEDGER_BELOW downTo value step 2).map(::StaffPosition)
            value >= FIRST_LEDGER_ABOVE -> (FIRST_LEDGER_ABOVE..value step 2).map(::StaffPosition)
            else -> emptyList()
        }

    override fun compareTo(other: StaffPosition): Int = value.compareTo(other.value)

    operator fun plus(steps: Int): StaffPosition = StaffPosition(value + steps)

    override fun toString(): String = "pos$value"

    companion object {
        val BOTTOM_LINE = StaffPosition(0)
        val MIDDLE_LINE = StaffPosition(4)
        val TOP_LINE = StaffPosition(8)

        private const val FIRST_LEDGER_BELOW = -2
        private const val FIRST_LEDGER_ABOVE = 10

        /** Diatonic index of the note on each clef's bottom line: E4 for treble, G2 for bass. */
        private fun bottomLineIndex(clef: Clef): Int = when (clef) {
            Clef.TREBLE -> diatonicIndex(Step.E, octave = 4)
            Clef.BASS -> diatonicIndex(Step.G, octave = 2)
        }

        /** The accidental never moves a note: F sharp sits where F does. */
        fun of(spelling: SpelledPitch, clef: Clef): StaffPosition =
            StaffPosition(diatonicIndex(spelling.step, spelling.octave) - bottomLineIndex(clef))

        private fun diatonicIndex(step: Step, octave: Int): Int = octave * Step.entries.size + step.ordinal
    }
}
