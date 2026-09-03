package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The alignment on its own, with beats given directly. The evaluator tests cover it end to end. */
class NoteAlignmentTest {

    private val c4 = Pitch(60)
    private val d4 = Pitch(62)

    private fun expected(vararg beats: Double, spelling: SpelledPitch = Fixtures.C4): List<ExpectedNote> =
        beats.mapIndexed { index, beat ->
            ExpectedNote(ScoreNote("n${index + 1}", spelling, Ticks.quarters(index), Ticks.QUARTER), beat)
        }

    private fun played(pitch: Pitch, vararg beats: Double): List<PlayedNote> =
        beats.map { PlayedNote(pitch, it, it + 0.5, velocity = 80, onsetNanos = 0L) }

    private fun kinds(outcomes: List<NoteOutcome>) = outcomes.map { it::class.simpleName!! }

    @Test
    fun `a right pitch a beat late is still that note`() {
        val outcomes = NoteAlignment.align(expected(0.0), played(c4, 1.0))

        assertEquals(listOf("Correct"), kinds(outcomes))
    }

    @Test
    fun `a right pitch two beats late is still that note, but five beats late is not`() {
        assertEquals(listOf("Correct"), kinds(NoteAlignment.align(expected(0.0), played(c4, 2.0))))
        assertEquals(listOf("Extra", "Missing"), kinds(NoteAlignment.align(expected(0.0), played(c4, 5.0))))
    }

    @Test
    fun `a shifted passage keeps its pitches rather than becoming wrong notes`() {
        val outcomes = NoteAlignment.align(expected(0.0, 1.0, 2.0, 3.0, spelling = Fixtures.D4), played(d4, 1.0, 2.0, 3.0))

        assertEquals(listOf("Missing", "Correct", "Correct", "Correct"), kinds(outcomes))
    }

    @Test
    fun `a wrong pitch on the beat is a substitution`() {
        val outcomes = NoteAlignment.align(expected(0.0), played(d4, 0.0))

        assertEquals(listOf("WrongPitch"), kinds(outcomes))
    }

    @Test
    fun `timing decides which repeat of a note went missing`() {
        val outcomes = NoteAlignment.align(expected(0.0, 1.0, 2.0, 3.0), played(c4, 0.0, 1.0, 3.0))

        assertEquals(listOf("Correct", "Correct", "Missing", "Correct"), kinds(outcomes))
        assertEquals("n3", assertIs<NoteOutcome.Missing>(outcomes[2]).expected.id)
        assertEquals(3.0, assertIs<NoteOutcome.Correct>(outcomes[3]).played.onsetBeat)
    }

    @Test
    fun `a note halfway between two beats goes to the earlier one, ties preferring a match first`() {
        val outcomes = NoteAlignment.align(expected(0.0, 1.0), played(c4, 0.5))

        assertEquals(listOf("Correct", "Missing"), kinds(outcomes))
    }

    @Test
    fun `the phase moves the beats the player is measured against`() {
        val early = NoteAlignment.align(expected(0.0, 1.0), played(c4, 0.5), phaseBeats = -0.3)
        val late = NoteAlignment.align(expected(0.0, 1.0), played(c4, 0.5), phaseBeats = 0.3)

        assertEquals(listOf("Missing", "Correct"), kinds(early))
        assertEquals(listOf("Correct", "Missing"), kinds(late))
    }

    @Test
    fun `a stray note between two correct ones is extra rather than a wrong pitch`() {
        val outcomes = NoteAlignment.align(expected(0.0, 1.0), played(c4, 0.0) + played(d4, 0.5) + played(c4, 1.0))

        assertEquals(listOf("Correct", "Extra", "Correct"), kinds(outcomes))
    }

    @Test
    fun `nothing played is all missing and nothing expected is all extra`() {
        assertEquals(listOf("Missing", "Missing"), kinds(NoteAlignment.align(expected(0.0, 1.0), emptyList())))
        assertEquals(listOf("Extra"), kinds(NoteAlignment.align(emptyList(), played(c4, 0.0))))
    }
}
