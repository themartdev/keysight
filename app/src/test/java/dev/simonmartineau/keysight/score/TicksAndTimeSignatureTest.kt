package dev.simonmartineau.keysight.score

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TicksAndTimeSignatureTest {

    @Test
    fun `note values are exact multiples of the quarter`() {
        assertEquals(Ticks(3840), Ticks.WHOLE)
        assertEquals(Ticks(1920), Ticks.HALF)
        assertEquals(Ticks(960), Ticks.QUARTER)
        assertEquals(Ticks(480), Ticks.EIGHTH)
        assertEquals(Ticks(240), Ticks.SIXTEENTH)
        assertEquals(Ticks.WHOLE, Ticks.quarters(4))
    }

    @Test
    fun `dots and tuplets stay exact`() {
        assertEquals(Ticks(1440), Ticks.QUARTER.dotted())
        assertEquals(Ticks(320), Ticks.QUARTER.divided(3))
        assertEquals(Ticks(192), Ticks.QUARTER.divided(5))
        assertEquals(Ticks.QUARTER, Ticks.QUARTER.divided(3) * 3)
    }

    @Test
    fun `ticks arithmetic and ordering`() {
        assertEquals(Ticks.HALF, Ticks.QUARTER + Ticks.QUARTER)
        assertEquals(Ticks.EIGHTH, Ticks.QUARTER - Ticks.EIGHTH)
        assertEquals(true, Ticks.EIGHTH < Ticks.QUARTER)
        assertFailsWith<IllegalArgumentException> { Ticks.EIGHTH - Ticks.QUARTER }
    }

    @Test
    fun `a beat is the beat unit of the time signature`() {
        assertEquals(Ticks.QUARTER, TimeSignature.FOUR_FOUR.ticksPerBeat)
        assertEquals(Ticks.WHOLE, TimeSignature.FOUR_FOUR.ticksPerMeasure)
        assertEquals(Ticks.QUARTER, TimeSignature.THREE_FOUR.ticksPerBeat)
        assertEquals(Ticks.quarters(3), TimeSignature.THREE_FOUR.ticksPerMeasure)
        assertEquals(Ticks.EIGHTH, TimeSignature(6, 8).ticksPerBeat)
        assertEquals(Ticks.quarters(3), TimeSignature(6, 8).ticksPerMeasure)
    }

    @Test
    fun `beat positions come out of ticks exactly`() {
        assertEquals(1.5, TimeSignature.FOUR_FOUR.beatsOf(Ticks.QUARTER.dotted()))
        assertEquals(3.0, TimeSignature(6, 8).beatsOf(Ticks.QUARTER.dotted()))
        assertEquals("4/4", TimeSignature.FOUR_FOUR.toString())
    }

    @Test
    fun `unusual beat units are rejected`() {
        assertFailsWith<IllegalArgumentException> { TimeSignature(4, 3) }
        assertFailsWith<IllegalArgumentException> { TimeSignature(0, 4) }
    }
}
