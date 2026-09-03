package dev.simonmartineau.keysight.settings

import dev.simonmartineau.keysight.difficulty.MusicalLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotesLadderTest {

    @Test
    fun `the ladder starts where every player starts and climbs one dimension a rung`() {
        val levels = NotesLadder.LEVELS

        assertEquals(MusicalLevel.DEFAULT, levels.first())
        assertTrue(levels.size > 5, "a rung per step of every dimension")
        levels.zipWithNext().forEach { (easier, harder) ->
            val change = easier.changeTo(harder)!!
            assertEquals(1, change.dimensions.size, "$easier to $harder moves one dimension")
            assertTrue(change.harder, "$easier to $harder is a step up")
            assertTrue(harder.isConsistent)
        }
    }

    @Test
    fun `the top rung has every dimension at its top`() {
        val top = NotesLadder.LEVELS.last()

        assertTrue(top.accidentals)
        assertTrue(top.rests)
        assertEquals(top.noteValues, NotesLadder.LEVELS.maxBy { it.noteValues.size }.noteValues)
        assertEquals(top.maxInterval, NotesLadder.LEVELS.maxOf { it.maxInterval })
    }

    @Test
    fun `a level on the ladder is found exactly, one off it rounds down`() {
        NotesLadder.LEVELS.forEachIndexed { index, level -> assertEquals(index, NotesLadder.indexOf(level)) }

        val second = NotesLadder.LEVELS[1]
        val between = second.copy(rests = true)
        assertTrue(between !in NotesLadder.LEVELS)
        assertEquals(1, NotesLadder.indexOf(between))
    }

    @Test
    fun `the short label names the interval and the rhythm, and the extras only once on`() {
        assertEquals("Up to thirds · quarter notes", NotesLadder.shortLabel(MusicalLevel.DEFAULT))
        assertEquals(
            "Up to thirds · quarter notes · with rests · with accidentals",
            NotesLadder.shortLabel(MusicalLevel.DEFAULT.copy(rests = true, accidentals = true)),
        )
    }

    @Test
    fun `the hand-picked level reaches the generator and the adapt flag starts off`() {
        val settings = InMemoryRunSettings()
        assertEquals(false, settings.adaptEnabled.value)
        settings.setAdaptEnabled(true)
        assertEquals(true, settings.adaptEnabled.value)

        val content = ContentConfig.DEFAULT.copy(level = NotesLadder.LEVELS.last())
        assertEquals(NotesLadder.LEVELS.last(), MusicalLevel.of(content.exerciseConfig))
        assertEquals(content.keySignature, content.exerciseConfig.keySignature)
    }
}
