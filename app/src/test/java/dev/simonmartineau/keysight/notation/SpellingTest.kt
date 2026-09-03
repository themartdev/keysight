package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import kotlin.test.Test
import kotlin.test.assertEquals

class SpellingTest {

    @Test
    fun `white keys are natural and black keys are sharps`() {
        val expected = listOf(
            SpelledPitch(Step.C, 0, 4), SpelledPitch(Step.C, 1, 4),
            SpelledPitch(Step.D, 0, 4), SpelledPitch(Step.D, 1, 4),
            SpelledPitch(Step.E, 0, 4),
            SpelledPitch(Step.F, 0, 4), SpelledPitch(Step.F, 1, 4),
            SpelledPitch(Step.G, 0, 4), SpelledPitch(Step.G, 1, 4),
            SpelledPitch(Step.A, 0, 4), SpelledPitch(Step.A, 1, 4),
            SpelledPitch(Step.B, 0, 4),
        )

        assertEquals(expected, (60..71).map { sharpSpelling(Pitch(it)) })
    }

    @Test
    fun `every spelling sounds as the pitch it spells`() {
        Pitch.MIDI_RANGE.forEach { midi ->
            assertEquals(Pitch(midi), sharpSpelling(Pitch(midi)).pitch)
        }
    }
}
