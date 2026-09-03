package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaffPositionTest {

    private fun treble(step: Step, octave: Int, alteration: Int = 0) =
        StaffPosition.of(SpelledPitch(step, alteration, octave), Clef.TREBLE)

    private fun bass(step: Step, octave: Int) = StaffPosition.of(SpelledPitch(step, octave = octave), Clef.BASS)

    @Test
    fun `treble clef counts up from E4 on the bottom line`() {
        assertEquals(StaffPosition(0), treble(Step.E, 4))
        assertEquals(StaffPosition(2), treble(Step.G, 4))
        assertEquals(StaffPosition.MIDDLE_LINE, treble(Step.B, 4))
        assertEquals(StaffPosition.TOP_LINE, treble(Step.F, 5))
        assertEquals(StaffPosition(-2), treble(Step.C, 4))
        assertEquals(StaffPosition(10), treble(Step.A, 5))
    }

    @Test
    fun `bass clef counts up from G2 on the bottom line`() {
        assertEquals(StaffPosition(0), bass(Step.G, 2))
        assertEquals(StaffPosition(4), bass(Step.D, 3))
        assertEquals(StaffPosition(6), bass(Step.F, 3))
        assertEquals(StaffPosition(10), bass(Step.C, 4))
    }

    @Test
    fun `an accidental does not move the note`() {
        assertEquals(treble(Step.F, 4), treble(Step.F, 4, alteration = 1))
        assertEquals(treble(Step.B, 4), treble(Step.B, 4, alteration = -1))
    }

    @Test
    fun `y is half the position`() {
        assertEquals(-1.0, StaffPosition(-2).y)
        assertEquals(0.5, StaffPosition(1).y)
        assertEquals(4.0, StaffPosition.TOP_LINE.y)
    }

    @Test
    fun `stems go up below the middle line only`() {
        assertTrue(treble(Step.A, 4).stemUp)
        assertFalse(treble(Step.B, 4).stemUp)
        assertFalse(treble(Step.C, 5).stemUp)
    }

    @Test
    fun `ledger lines are the even positions from the staff out to the note`() {
        assertEquals(emptyList(), treble(Step.E, 4).ledgerLines)
        assertEquals(emptyList(), treble(Step.D, 4).ledgerLines)
        assertEquals(listOf(StaffPosition(-2)), treble(Step.C, 4).ledgerLines)
        assertEquals(listOf(StaffPosition(-2)), treble(Step.B, 3).ledgerLines)
        assertEquals(listOf(StaffPosition(-2), StaffPosition(-4)), treble(Step.A, 3).ledgerLines)
        assertEquals(emptyList(), treble(Step.G, 5).ledgerLines)
        assertEquals(listOf(StaffPosition(10)), treble(Step.A, 5).ledgerLines)
        assertEquals(listOf(StaffPosition(10)), treble(Step.B, 5).ledgerLines)
        assertEquals(listOf(StaffPosition(10), StaffPosition(12)), treble(Step.C, 6).ledgerLines)
    }
}
