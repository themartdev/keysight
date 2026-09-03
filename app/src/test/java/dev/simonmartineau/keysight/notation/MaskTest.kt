package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaskTest {

    private val layout = ScoreLayoutEngine.layoutSystem(Fixtures.cdef, 0, null, showTimeSignature = true)

    @Test
    fun `a range holds its start and not its end`() {
        val range = TickRange(Ticks.QUARTER, Ticks.HALF)

        assertTrue(Ticks.QUARTER in range)
        assertTrue(Ticks(1919) in range)
        assertFalse(Ticks.HALF in range)
        assertFalse(Ticks.ZERO in range)
        assertFailsWith<IllegalArgumentException> { TickRange(Ticks.HALF, Ticks.HALF) }
    }

    @Test
    fun `none hides nothing and all hides every note`() {
        assertTrue(layout.elements.none(Mask.NONE::hides))
        val hidden = layout.elements.filter(Mask.ALL::hides)
        assertEquals(layout.elements.filter { it.ticks != null }, hidden)
        assertEquals(setOf(Role.NOTEHEAD, Role.STEM, Role.LEDGER), hidden.map { it.role }.toSet())
    }

    @Test
    fun `a mask hides the notes in its ranges and leaves the structure`() {
        val mask = Mask(listOf(TickRange(Ticks.QUARTER, Ticks.quarters(3))))

        val hiddenNotes = layout.elements.filter(mask::hides).map { it.noteId }.toSet()
        assertEquals(setOf("n2", "n3"), hiddenNotes)
        assertTrue(layout.elements.filter { it.role == Role.STAFF_LINE || it.role == Role.CLEF || it.role == Role.BARLINE }.none(mask::hides))
    }
}
