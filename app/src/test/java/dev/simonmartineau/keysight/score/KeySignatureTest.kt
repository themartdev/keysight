package dev.simonmartineau.keysight.score

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KeySignatureTest {

    @Test
    fun `sharps and flats come in fifths order`() {
        assertEquals(emptyList(), KeySignature.C_MAJOR.alteredSteps)
        assertEquals(listOf(Step.F, Step.C, Step.G), KeySignature(3).alteredSteps)
        assertEquals(listOf(Step.B, Step.E), KeySignature(-2).alteredSteps)
        assertEquals(Step.entries.size, KeySignature(7).alteredSteps.size)
        assertEquals(Step.entries.size, KeySignature(-7).alteredSteps.size)
    }

    @Test
    fun `every step is altered as its key says`() {
        val d = KeySignature(2)
        assertEquals(1, d.alterationOf(Step.F))
        assertEquals(1, d.alterationOf(Step.C))
        assertEquals(0, d.alterationOf(Step.G))
        val eFlat = KeySignature(-3)
        assertEquals(-1, eFlat.alterationOf(Step.B))
        assertEquals(-1, eFlat.alterationOf(Step.A))
        assertEquals(0, eFlat.alterationOf(Step.D))
    }

    @Test
    fun `the tonic walks the circle of fifths`() {
        val tonics = KeySignature.ALL.map { it.tonicStep to it.tonicAlteration }
        val expected = listOf(
            Step.C to 0, Step.G to 0, Step.D to 0, Step.A to 0, Step.E to 0, Step.B to 0, Step.F to 1, Step.C to 1,
            Step.F to 0, Step.B to -1, Step.E to -1, Step.A to -1, Step.D to -1, Step.G to -1, Step.C to -1,
        )
        assertEquals(expected, tonics)
        assertEquals(6, KeySignature(6).tonicPitchClass)
        assertEquals(11, KeySignature(-7).tonicPitchClass)
    }

    @Test
    fun `names`() {
        assertEquals("C major", KeySignature.C_MAJOR.majorName)
        assertEquals("F♯ major", KeySignature(6).majorName)
        assertEquals("B♭ major", KeySignature(-2).majorName)
        assertEquals(15, KeySignature.ALL.size)
        assertEquals(15, KeySignature.ALL.map { it.majorName }.toSet().size)
    }

    @Test
    fun `eight accidentals is not a key`() {
        assertFailsWith<IllegalArgumentException> { KeySignature(8) }
    }
}
