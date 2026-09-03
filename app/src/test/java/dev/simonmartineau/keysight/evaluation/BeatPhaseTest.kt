package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals

class BeatPhaseTest {

    @Test
    fun `the phase is the median lean of the notes on the pulse`() {
        assertEquals(0.05, BeatPhase.estimate(listOf(0.05, 0.02, 0.09)), 1e-9)
        assertEquals(0.04, BeatPhase.estimate(listOf(0.02, 0.06)), 1e-9)
        assertEquals(-0.1, BeatPhase.estimate(listOf(-0.1, -0.1, -0.1, 0.4)), 1e-9)
    }

    @Test
    fun `notes off the pulse do not pull the estimate`() {
        assertEquals(0.0, BeatPhase.estimate(listOf(0.0, 0.0, 1.0, 1.0)), 1e-9)
        assertEquals(0.02, BeatPhase.estimate(listOf(0.02, -0.8, 0.7)), 1e-9)
    }

    @Test
    fun `the estimate is bounded`() {
        assertEquals(BeatPhase.MAX_PHASE_BEATS, BeatPhase.estimate(listOf(0.4, 0.45, 0.5)))
        assertEquals(-BeatPhase.MAX_PHASE_BEATS, BeatPhase.estimate(listOf(-0.4, -0.3)))
    }

    @Test
    fun `no note on the pulse means no phase`() {
        assertEquals(0.0, BeatPhase.estimate(emptyList()))
        assertEquals(0.0, BeatPhase.estimate(listOf(2.0, -1.0)))
    }

    @Test
    fun `only matched notes have a deviation`() {
        val expected = Fixtures.cdef.notes
        val beats = expected.associate { it.id to Fixtures.cdef.timeSignature.beatsOf(it.onset) }
        fun played(midi: Int, beat: Double) = PlayedNote(Pitch(midi), beat, null, 80, 0L)
        val outcomes = listOf(
            NoteOutcome.Correct(expected[0], played(60, 0.1)),
            NoteOutcome.WrongPitch(expected[1], played(61, 0.9)),
            NoteOutcome.Missing(expected[2]),
            NoteOutcome.Extra(played(70, 2.5)),
            NoteOutcome.Correct(expected[3], played(65, 3.0)),
        )

        val deviations = BeatPhase.deviations(outcomes, beats)

        assertEquals(3, deviations.size)
        assertEquals(0.1, deviations[0], 1e-9)
        assertEquals(-0.1, deviations[1], 1e-9)
        assertEquals(0.0, deviations[2], 1e-9)
    }
}
