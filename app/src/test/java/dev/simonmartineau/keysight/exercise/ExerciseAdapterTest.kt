package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.Hand
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Staff
import dev.simonmartineau.keysight.score.Ticks
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExerciseAdapterTest {

    private val random = Random(7)

    /** m01 of the pack: C4 four times. */
    private val repeatedC = Exercise(
        "m01",
        Fixtures.oneMeasure(*(0 until 4).map { ScoreNote("n$it", Fixtures.C4, Ticks.quarters(it), Ticks.QUARTER) }.toTypedArray()),
        musicalDifficulty = 1,
    )

    private fun spellings(exercise: Exercise) = exercise.score.notes.map { it.spelling.toString() }

    @Test
    fun `the right hand keeps the treble content where it was written`() {
        val adapted = repeatedC.adaptedTo(KeySignature.C_MAJOR, Hands.RIGHT, random)

        assertEquals(repeatedC, adapted)
        assertEquals(listOf("D4", "E4", "F#4", "G4"), spellings(Fixtures.exercise.adaptedTo(KeySignature(2), Hands.RIGHT, random)))
    }

    @Test
    fun `the left hand moves the content down to sit on the bass staff`() {
        val adapted = repeatedC.adaptedTo(KeySignature.C_MAJOR, Hands.LEFT, random)

        assertEquals(listOf(Staff(Clef.BASS)), adapted.score.staves)
        assertEquals(List(4) { "C3" }, spellings(adapted))
        adapted.score.notes.forEach { note ->
            assertEquals(0, note.staff)
            assertEquals(Hand.LEFT, note.hand)
        }
        assertEquals(listOf("C3", "D3", "E3", "F3"), spellings(Fixtures.exercise.adaptedTo(KeySignature.C_MAJOR, Hands.LEFT, random)))
    }

    @Test
    fun `both hands is a grand staff with the voice on one staff`() {
        val staves = (0 until 20).map { repeatedC.adaptedTo(KeySignature(1), Hands.BOTH, random) }

        staves.forEach { adapted ->
            assertEquals(listOf(Staff(Clef.TREBLE), Staff(Clef.BASS)), adapted.score.staves)
            val staff = adapted.score.notes.map { it.staff }.toSet().single()
            // G3 sits two ledger lines under the treble staff and in the top space of the bass one; neither moves it.
            assertEquals(List(4) { "G3" }, spellings(adapted))
            assertEquals(if (staff == 0) Hand.RIGHT else Hand.LEFT, adapted.score.notes.first().hand)
            assertEquals(repeatedC.id, adapted.id)
            assertEquals(repeatedC.musicalDifficulty, adapted.musicalDifficulty)
        }
        assertEquals(setOf(0, 1), staves.map { it.score.notes.first().staff }.toSet())
    }

    @Test
    fun `only single-staff content is adapted`() {
        val grand = repeatedC.copy(score = repeatedC.score.copy(staves = listOf(Staff(Clef.TREBLE), Staff(Clef.BASS))))

        assertFailsWith<IllegalArgumentException> { grand.adaptedTo(KeySignature.C_MAJOR, Hands.RIGHT, random) }
    }
}
