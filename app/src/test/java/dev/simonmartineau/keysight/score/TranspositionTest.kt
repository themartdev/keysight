package dev.simonmartineau.keysight.score

import dev.simonmartineau.keysight.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TranspositionTest {

    private fun spellings(score: Score) = score.notes.map { it.spelling.toString() }

    @Test
    fun `C to G moves every letter by the smaller interval, down a fourth`() {
        val g = Fixtures.cdef.transposed(KeySignature(1))

        assertEquals(KeySignature(1), g.keySignature)
        assertEquals(listOf("G3", "A3", "B3", "C4"), spellings(g))
        assertEquals(listOf("n1", "n2", "n3", "n4"), g.notes.map { it.id })
    }

    @Test
    fun `the key's own accidentals are taken from the signature`() {
        val b = Fixtures.oneMeasure(ScoreNote("n", SpelledPitch(Step.B, octave = 4), Ticks.ZERO, Ticks.QUARTER))

        assertEquals(listOf("F#4"), spellings(b.transposed(KeySignature(1))))
        assertEquals(listOf("A4"), spellings(b.transposed(KeySignature(-2))))
    }

    @Test
    fun `an accidental against the key keeps its meaning`() {
        val raisedFourth = Fixtures.oneMeasure(ScoreNote("n", SpelledPitch(Step.F, alteration = 1, octave = 4), Ticks.ZERO, Ticks.QUARTER))

        assertEquals(listOf("C#4"), spellings(raisedFourth.transposed(KeySignature(1))))
        assertEquals(listOf("B4"), spellings(raisedFourth.transposed(KeySignature(-1))))
        assertEquals(listOf("G#4"), spellings(raisedFourth.transposed(KeySignature(2))))
    }

    @Test
    fun `at most a tritone either way, a tritone going the way of the circle`() {
        val c4 = Fixtures.oneMeasure(ScoreNote("n", Fixtures.C4, Ticks.ZERO, Ticks.QUARTER))

        assertEquals(listOf("A3"), spellings(c4.transposed(KeySignature(3))))
        assertEquals(listOf("F#4"), spellings(c4.transposed(KeySignature(6))))
        assertEquals(listOf("Gb3"), spellings(c4.transposed(KeySignature(-6))))
        assertEquals(listOf("Cb4"), spellings(c4.transposed(KeySignature(-7))))
        assertEquals(listOf("C#4"), spellings(c4.transposed(KeySignature(7))))
    }

    @Test
    fun `there and back is the identity, and the same key is the same score`() {
        KeySignature.ALL.forEach { key ->
            assertEquals(Fixtures.cdef, Fixtures.cdef.transposed(key).transposed(KeySignature.C_MAJOR), "$key")
            assertEquals(Fixtures.cdef.notes.map { it.pitch.transposedBy(0) }.size, Fixtures.cdef.transposed(key).notes.size)
        }
        assertSame(Fixtures.cdef, Fixtures.cdef.transposed(KeySignature.C_MAJOR))
    }

    @Test
    fun `every note moves by the same number of semitones`() {
        KeySignature.ALL.forEach { key ->
            val shifts = Fixtures.cdef.transposed(key).notes.zip(Fixtures.cdef.notes) { after, before -> before.pitch.semitonesTo(after.pitch) }
            assertEquals(1, shifts.toSet().size, "$key: $shifts")
        }
    }
}
