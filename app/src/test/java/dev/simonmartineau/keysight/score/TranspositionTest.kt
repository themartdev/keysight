package dev.simonmartineau.keysight.score

import dev.simonmartineau.keysight.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    /** The ten chromatic neighbours of C major, each with the diatonic note it resolves to. */
    private val neighbours: List<Pair<SpelledPitch, SpelledPitch>> = listOf(
        Step.C to Step.D, Step.D to Step.E, Step.F to Step.G, Step.G to Step.A, Step.A to Step.B,
    ).flatMap { (lower, upper) ->
        listOf(
            SpelledPitch(lower, 1, 4) to SpelledPitch(upper, octave = 4),
            SpelledPitch(upper, -1, 4) to SpelledPitch(lower, octave = 4),
        )
    }

    private fun pair(altered: SpelledPitch, resolution: SpelledPitch) = Fixtures.oneMeasure(
        ScoreNote("a", altered, Ticks.ZERO, Ticks.QUARTER),
        ScoreNote("r", resolution, Ticks.QUARTER, Ticks.QUARTER),
    )

    @Test
    fun `a double is respelled as the plain accidental of the neighbouring letter`() {
        val aSharp = pair(SpelledPitch(Step.A, 1, 4), SpelledPitch(Step.B, octave = 4))
        assertEquals(listOf("G4", "G#4"), spellings(aSharp.transposed(KeySignature(3))), "F double sharp in A is G natural")
        assertEquals(listOf("G4", "G#4"), spellings(pair(SpelledPitch(Step.D, 1, 4), Fixtures.E4).transposed(KeySignature(4))), "in E the raised second, F double sharp, is G natural before G sharp")

        val gFlat = pair(SpelledPitch(Step.G, -1, 4), Fixtures.F4)
        assertEquals(listOf("A4", "Ab4"), spellings(gFlat.transposed(KeySignature(-3))), "B double flat in E flat is A natural")
        assertEquals(listOf("Ab4", "G4"), spellings(pair(SpelledPitch(Step.E, -1, 4), Fixtures.D4).transposed(KeySignature(-1))), "in F the lowered third is A flat, the letter plain")

        assertEquals(listOf("B4", "C5"), spellings(pair(SpelledPitch(Step.F, 1, 4), Fixtures.G4).transposed(KeySignature(-1))), "the raised fourth of F is a natural")
        assertEquals(listOf("B3", "C4"), spellings(pair(SpelledPitch(Step.C, 1, 4), Fixtures.D4).transposed(KeySignature(-2))), "so is the raised tonic of B flat")
        assertEquals(listOf("E#4", "F#4"), spellings(pair(SpelledPitch(Step.D, 1, 4), Fixtures.E4).transposed(KeySignature(2))), "a plain sharp on a white key is kept")
        assertEquals(listOf("Cb5", "Bb4"), spellings(pair(SpelledPitch(Step.G, -1, 4), Fixtures.F4).transposed(KeySignature(-1))), "so is a plain flat")
    }

    @Test
    fun `every chromatic neighbour keeps its semitone to its resolution in every key, with a plain accidental`() {
        KeySignature.ALL.forEach { key ->
            neighbours.forEach { (altered, resolution) ->
                val inKey = pair(altered, resolution).transposed(key)
                val (a, r) = inKey.notes
                assertEquals(altered.pitch.semitonesTo(resolution.pitch), a.pitch.semitonesTo(r.pitch), "$key: $altered to $resolution became ${a.spelling} to ${r.spelling}")
                assertEquals(resolution.pitch.semitonesTo(r.pitch), altered.pitch.semitonesTo(a.pitch), "$key: both notes move by the same shift")
                assertTrue(a.spelling.alteration in -1..1, "$key: ${a.spelling} is a double")
                assertEquals(key.alterationOf(r.spelling.step), r.spelling.alteration, "$key: the resolution is in the key")
                val letters = r.spelling.diatonicIndex - a.spelling.diatonicIndex
                assertTrue(letters == altered.alteration || letters == 0, "$key: ${a.spelling} to ${r.spelling} is $letters letters")
                val back = inKey.transposed(KeySignature.C_MAJOR)
                assertEquals(listOf(altered.pitch, resolution.pitch), back.notes.map { it.pitch }, "$key: the pitches come back")
                if (letters != 0) assertEquals(listOf(altered, resolution), back.notes.map { it.spelling }, "$key: with no double avoided the spelling comes back")
            }
        }
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
