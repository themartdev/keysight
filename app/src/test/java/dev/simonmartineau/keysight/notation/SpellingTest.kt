package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpellingTest {

    @Test
    fun `in C white keys are natural and black keys are sharps`() {
        val expected = listOf(
            SpelledPitch(Step.C, 0, 4), SpelledPitch(Step.C, 1, 4),
            SpelledPitch(Step.D, 0, 4), SpelledPitch(Step.D, 1, 4),
            SpelledPitch(Step.E, 0, 4),
            SpelledPitch(Step.F, 0, 4), SpelledPitch(Step.F, 1, 4),
            SpelledPitch(Step.G, 0, 4), SpelledPitch(Step.G, 1, 4),
            SpelledPitch(Step.A, 0, 4), SpelledPitch(Step.A, 1, 4),
            SpelledPitch(Step.B, 0, 4),
        )

        assertEquals(expected, (60..71).map { spelledIn(Pitch(it), KeySignature.C_MAJOR) })
    }

    @Test
    fun `notes of the key are spelled as the key spells them`() {
        assertEquals(SpelledPitch(Step.B, -1, 4), spelledIn(Pitch(70), KeySignature(-1)))
        assertEquals(SpelledPitch(Step.E, 1, 4), spelledIn(Pitch(65), KeySignature(6)))
        assertEquals(SpelledPitch(Step.C, -1, 5), spelledIn(Pitch(71), KeySignature(-7)))
        assertEquals(SpelledPitch(Step.B, 1, 3), spelledIn(Pitch(60), KeySignature(7)))
    }

    @Test
    fun `notes outside the key lean the way the key does`() {
        assertEquals(SpelledPitch(Step.D, -1, 4), spelledIn(Pitch(61), KeySignature(-1)))
        assertEquals(SpelledPitch(Step.D, 1, 4), spelledIn(Pitch(63), KeySignature(1)))
        assertEquals(SpelledPitch(Step.E, 0, 4), spelledIn(Pitch(64), KeySignature(-3)))
    }

    @Test
    fun `every spelling sounds as the pitch it spells in every key`() {
        KeySignature.ALL.forEach { key ->
            Pitch.MIDI_RANGE.forEach { midi ->
                assertEquals(Pitch(midi), spelledIn(Pitch(midi), key).pitch, "$midi in $key")
            }
        }
    }

    @Test
    fun `a cue needs an accidental only when it disagrees with the key`() {
        assertNull(cueAccidental(SpelledPitch(Step.B, -1, 4), KeySignature(-1)))
        assertEquals(Glyph.ACCIDENTAL_NATURAL, cueAccidental(SpelledPitch(Step.B, 0, 4), KeySignature(-1)))
        assertEquals(Glyph.ACCIDENTAL_SHARP, cueAccidental(SpelledPitch(Step.F, 1, 4), KeySignature.C_MAJOR))
        assertNull(cueAccidental(SpelledPitch(Step.F, 0, 4), KeySignature.C_MAJOR))
    }
}
