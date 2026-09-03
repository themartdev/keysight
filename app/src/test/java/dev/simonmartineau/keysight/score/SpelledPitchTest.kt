package dev.simonmartineau.keysight.score

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpelledPitchTest {

    @Test
    fun `natural notes sound at their MIDI numbers`() {
        assertEquals(Pitch(60), SpelledPitch(Step.C, octave = 4).pitch)
        assertEquals(Pitch(69), SpelledPitch(Step.A, octave = 4).pitch)
        assertEquals(Pitch(21), SpelledPitch(Step.A, octave = 0).pitch)
    }

    @Test
    fun `accidentals shift the sounding pitch but keep the letter`() {
        assertEquals(Pitch(66), SpelledPitch(Step.F, alteration = 1, octave = 4).pitch)
        assertEquals(Pitch(66), SpelledPitch(Step.G, alteration = -1, octave = 4).pitch)
        assertEquals("F#4", SpelledPitch(Step.F, alteration = 1, octave = 4).toString())
        assertEquals("Gb4", SpelledPitch(Step.G, alteration = -1, octave = 4).toString())
    }

    @Test
    fun `the octave belongs to the letter, not the sounding pitch`() {
        // B sharp 3 sounds as C4, and C flat 4 sounds as B3.
        assertEquals(Pitch(60), SpelledPitch(Step.B, alteration = 1, octave = 3).pitch)
        assertEquals(Pitch(59), SpelledPitch(Step.C, alteration = -1, octave = 4).pitch)
        assertEquals("Cb4", SpelledPitch(Step.C, alteration = -1, octave = 4).toString())
    }

    @Test
    fun `double accidentals are the limit`() {
        assertEquals(Pitch(62), SpelledPitch(Step.C, alteration = 2, octave = 4).pitch)
        assertFailsWith<IllegalArgumentException> { SpelledPitch(Step.C, alteration = 3, octave = 4) }
    }

    @Test
    fun `a spelling survives a JSON round trip`() {
        val spelled = SpelledPitch(Step.E, alteration = -1, octave = 5)
        val json = Json.encodeToString(SpelledPitch.serializer(), spelled)

        assertEquals(spelled, Json.decodeFromString(SpelledPitch.serializer(), json))
    }
}
