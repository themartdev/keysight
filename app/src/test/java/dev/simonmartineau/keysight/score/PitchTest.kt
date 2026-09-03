package dev.simonmartineau.keysight.score

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PitchTest {

    @Test
    fun `middle C is MIDI 60 in octave 4`() {
        assertEquals(60, Pitch.C4.midiNoteNumber)
        assertEquals(4, Pitch.C4.octave)
        assertEquals(0, Pitch.C4.pitchClass)
        assertEquals("C4", Pitch.C4.toString())
    }

    @Test
    fun `A0 and C8 are the ends of a piano`() {
        assertEquals("A0", Pitch(21).toString())
        assertEquals("C8", Pitch(108).toString())
    }

    @Test
    fun `intervals are signed semitone distances`() {
        val g4 = Pitch(67)

        assertEquals(7, Pitch.C4.semitonesTo(g4))
        assertEquals(-7, g4.semitonesTo(Pitch.C4))
        assertEquals(g4, Pitch.C4.transposedBy(7))
        assertTrue(Pitch.C4 < g4)
    }

    @Test
    fun `note numbers outside MIDI are rejected`() {
        assertFailsWith<IllegalArgumentException> { Pitch(-1) }
        assertFailsWith<IllegalArgumentException> { Pitch(128) }
    }

    @Test
    fun `a pitch serialises as its note number`() {
        assertEquals("60", Json.encodeToString(Pitch.serializer(), Pitch.C4))
        assertEquals(Pitch.C4, Json.decodeFromString(Pitch.serializer(), "60"))
    }
}
