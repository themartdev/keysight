package dev.simonmartineau.keysight.score

import dev.simonmartineau.keysight.Fixtures
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScoreTest {

    @Test
    fun `a one measure score in four four is four beats long`() {
        assertEquals(4.0, Fixtures.cdef.totalBeats)
        assertEquals(Ticks.WHOLE, Fixtures.cdef.totalTicks)
    }

    @Test
    fun `notes are ordered by onset then pitch`() {
        val score = Fixtures.oneMeasure(
            note("b", Fixtures.E4, Ticks.ZERO),
            note("c", Fixtures.C4, Ticks.HALF),
            note("a", Fixtures.C4, Ticks.ZERO),
        )

        assertEquals(listOf("a", "b", "c"), score.notesInPerformanceOrder.map { it.id })
    }

    @Test
    fun `notes that start together are one chord`() {
        val score = Fixtures.oneMeasure(
            note("a", Fixtures.C4, Ticks.ZERO),
            note("b", Fixtures.E4, Ticks.ZERO),
            note("c", Fixtures.G4, Ticks.HALF),
        )

        assertEquals(listOf(listOf("a", "b"), listOf("c")), score.chordsInPerformanceOrder.map { chord -> chord.map { it.id } })
    }

    @Test
    fun `notes cannot run past the end of the score`() {
        assertFailsWith<IllegalArgumentException> {
            Fixtures.oneMeasure(note("a", Fixtures.C4, Ticks.quarters(3), Ticks.HALF))
        }
    }

    @Test
    fun `notes in one voice cannot overlap unless they start together`() {
        assertFailsWith<IllegalArgumentException> {
            Fixtures.oneMeasure(
                note("a", Fixtures.C4, Ticks.ZERO, Ticks.HALF),
                note("b", Fixtures.D4, Ticks.QUARTER),
            )
        }
    }

    @Test
    fun `different voices may overlap`() {
        Fixtures.oneMeasure(
            note("a", Fixtures.C4, Ticks.ZERO, Ticks.HALF, voice = 0),
            note("b", Fixtures.D4, Ticks.QUARTER, voice = 1),
        )
    }

    @Test
    fun `note ids are unique within a score`() {
        assertFailsWith<IllegalArgumentException> {
            Fixtures.oneMeasure(note("a", Fixtures.C4, Ticks.ZERO), note("a", Fixtures.D4, Ticks.QUARTER))
        }
    }

    @Test
    fun `a note must have a positive duration`() {
        assertFailsWith<IllegalArgumentException> { note("a", Fixtures.C4, Ticks.ZERO, Ticks.ZERO) }
    }

    @Test
    fun `a score survives a JSON round trip`() {
        val json = Json.encodeToString(Score.serializer(), Fixtures.cdef)

        assertEquals(Fixtures.cdef, Json.decodeFromString(Score.serializer(), json))
    }

    private fun note(
        id: String,
        spelling: SpelledPitch,
        onset: Ticks,
        duration: Ticks = Ticks.QUARTER,
        voice: Int = 0,
    ) = ScoreNote(id = id, spelling = spelling, onset = onset, duration = duration, voice = voice)
}
