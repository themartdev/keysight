package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpacingTest {

    private val head = BravuraMetrics.of(Glyph.NOTEHEAD_BLACK).width

    private fun advance(duration: Ticks) = Spacing.advanceFor(duration, head)

    @Test
    fun `longer notes get more room, one gap per doubling`() {
        assertEquals(head + 2 * Spacing.GAP, advance(Ticks.QUARTER), 1e-9)
        assertEquals(head + 3 * Spacing.GAP, advance(Ticks.HALF), 1e-9)
        assertEquals(head + 4 * Spacing.GAP, advance(Ticks.WHOLE), 1e-9)
        assertTrue(advance(Ticks.QUARTER) < advance(Ticks.QUARTER.dotted()))
        assertTrue(advance(Ticks.QUARTER.dotted()) < advance(Ticks.HALF))
    }

    @Test
    fun `short notes never get less than the floor`() {
        assertEquals(Spacing.MIN_ADVANCE, advance(Ticks.EIGHTH))
        assertEquals(Spacing.MIN_ADVANCE, advance(Ticks.SIXTEENTH))
        assertTrue(Spacing.MIN_ADVANCE < advance(Ticks.QUARTER))
    }

    @Test
    fun `the floor fits a head, an accidental and a cue head`() {
        val sharp = BravuraMetrics.of(Glyph.ACCIDENTAL_SHARP).width
        assertTrue(Spacing.MIN_ADVANCE >= head + Spacing.CUE_GAP + sharp + Spacing.ACCIDENTAL_GAP + head * Spacing.CUE_SCALE)
    }

    @Test
    fun `a whole note's wider head widens its column`() {
        val whole = BravuraMetrics.of(Glyph.NOTEHEAD_WHOLE).width
        assertEquals(advance(Ticks.WHOLE) + (whole - head), Spacing.advanceFor(Ticks.WHOLE, whole), 1e-9)
    }

    @Test
    fun `a zero duration is rejected`() {
        assertFailsWith<IllegalArgumentException> { advance(Ticks.ZERO) }
    }
}
