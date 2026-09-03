package dev.simonmartineau.keysight.ui.practice

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.evaluation.Continuity
import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.evaluation.Pause
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.evaluation.PlayedNote
import dev.simonmartineau.keysight.evaluation.RhythmResult
import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultTextTest {

    private fun played(midi: Int, beat: Double) = PlayedNote(Pitch(midi), beat, null, 80, 0L)

    private val pitch = PitchResult(
        listOf(
            NoteOutcome.Correct(Fixtures.cdef.notes[0], played(60, 0.0)),
            NoteOutcome.Missing(Fixtures.cdef.notes[1]),
        ),
    )

    private fun rhythm(
        phase: Double = 0.0,
        tempoRatio: Double? = 1.0,
        pauses: List<Pause> = emptyList(),
        continuity: Continuity = Continuity.GOOD,
    ) = RhythmResult(emptyList(), phase, tempoRatio, pauses, continuity)

    @Test
    fun `the score line has pitch, rhythm and continuity`() {
        assertEquals("Pitch 50%   Rhythm 0%   Continuity Good", scoreLine(pitch, rhythm()))
        assertEquals("Pitch 50%   Rhythm 0%   Continuity Lost", scoreLine(pitch, rhythm(continuity = Continuity.LOST)))
    }

    @Test
    fun `a result without rhythm only reports pitch`() {
        assertEquals("Pitch 50%", scoreLine(pitch, null))
        assertEquals(emptyList(), remarks(pitch, null))
    }

    @Test
    fun `a clean performance earns no remark`() {
        assertEquals(emptyList(), remarks(pitch, rhythm(phase = 0.05, tempoRatio = 1.03)))
    }

    @Test
    fun `leaning on the beat is remarked past a tenth of a beat`() {
        assertEquals(listOf("Slightly ahead of the beat"), remarks(pitch, rhythm(phase = -0.1)))
        assertEquals(listOf("Slightly behind the beat"), remarks(pitch, rhythm(phase = 0.12)))
    }

    @Test
    fun `tempo drift is remarked past five percent, rounded`() {
        assertEquals(listOf("Drifted 6% fast"), remarks(pitch, rhythm(tempoRatio = 1.06)))
        assertEquals(listOf("Drifted 8% slow"), remarks(pitch, rhythm(tempoRatio = 0.92)))
        assertEquals(emptyList(), remarks(pitch, rhythm(tempoRatio = null)))
    }

    @Test
    fun `pauses and extra notes are counted`() {
        val withExtra = PitchResult(pitch.outcomes + NoteOutcome.Extra(played(70, 1.5)))

        assertEquals(listOf("1 extra note", "1 pause"), remarks(withExtra, rhythm(pauses = listOf(Pause("n2", 0.7)))))
        assertEquals(listOf("2 pauses"), remarks(pitch, rhythm(pauses = listOf(Pause("n2", 0.7), Pause("n3", 0.6)))))
    }

    @Test
    fun `continuity labels`() {
        assertEquals(listOf("Good", "Hesitant", "Lost"), Continuity.entries.map(::continuityLabel))
    }
}
