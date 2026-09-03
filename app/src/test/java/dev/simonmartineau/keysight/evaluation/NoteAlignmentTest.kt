package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
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

    // Chords: two hands, the left on staff 1 a whole note under the right's quarters.

    private val c3 = Pitch(48)
    private val e4 = Pitch(64)

    private val twoHands: List<ExpectedNote> = listOf(
        ExpectedNote(ScoreNote("r1", Fixtures.C4, Ticks.ZERO, Ticks.QUARTER), 0.0),
        ExpectedNote(ScoreNote("r2", Fixtures.D4, Ticks.QUARTER, Ticks.QUARTER), 1.0),
        ExpectedNote(ScoreNote("l1", SpelledPitch(Step.C, octave = 3), Ticks.ZERO, Ticks.HALF, voice = 1, staff = 1), 0.0),
    )

    private fun struck(vararg notes: Pair<Pitch, Double>): List<PlayedNote> =
        notes.map { (pitch, beat) -> PlayedNote(pitch, beat, beat + 0.5, velocity = 80, onsetNanos = (beat * 1e9).toLong()) }

    private fun byId(outcomes: List<NoteOutcome>): Map<String, String> = outcomes.filterNot { it is NoteOutcome.Extra }.associate { outcome ->
        val id = when (outcome) {
            is NoteOutcome.Correct -> outcome.expected.id
            is NoteOutcome.WrongPitch -> outcome.expected.id
            is NoteOutcome.Missing -> outcome.expected.id
            is NoteOutcome.TooLate -> outcome.expected.id
            is NoteOutcome.Extra -> error("filtered")
        }
        id to outcome::class.simpleName!!
    }

    @Test
    fun `notes struck together against notes due together are judged as one chord`() {
        val outcomes = NoteAlignment.align(twoHands, struck(c4 to 0.0, c3 to 0.05, d4 to 1.0))

        assertEquals(mapOf("l1" to "Correct", "r1" to "Correct", "r2" to "Correct"), byId(outcomes))
        assertEquals(listOf("Correct", "Correct", "Correct"), kinds(outcomes))
        assertEquals("l1", assertIs<NoteOutcome.Correct>(outcomes[0]).expected.id, "a chord's outcomes come lowest pitch first")
    }

    @Test
    fun `within a chord a wrong pitch is the note nearest in pitch and a dropped one is missing`() {
        val wrongLeft = NoteAlignment.align(twoHands, struck(c4 to 0.0, Pitch(47) to 0.02, d4 to 1.0))
        assertEquals(mapOf("l1" to "WrongPitch", "r1" to "Correct", "r2" to "Correct"), byId(wrongLeft))
        assertEquals(c4, assertIs<NoteOutcome.Correct>(wrongLeft.first { it is NoteOutcome.Correct }).played.pitch)

        val droppedLeft = NoteAlignment.align(twoHands, struck(c4 to 0.0, d4 to 1.0))
        assertEquals(mapOf("l1" to "Missing", "r1" to "Correct", "r2" to "Correct"), byId(droppedLeft))
        assertEquals(0, droppedLeft.count { it is NoteOutcome.Extra })

        val fatFinger = NoteAlignment.align(twoHands, struck(c4 to 0.0, c3 to 0.0, e4 to 0.03, d4 to 1.0))
        assertEquals(mapOf("l1" to "Correct", "r1" to "Correct", "r2" to "Correct"), byId(fatFinger))
        assertEquals(e4, assertIs<NoteOutcome.Extra>(fatFinger.single { it is NoteOutcome.Extra }).played.pitch)
    }

    @Test
    fun `a hand lagging by more than the spread is a missing note and an extra one, as deferred`() {
        val lagging = NoteAlignment.align(twoHands, struck(c4 to 0.0, c3 to 0.4, d4 to 1.0))

        assertEquals(mapOf("l1" to "Missing", "r1" to "Correct", "r2" to "Correct"), byId(lagging))
        assertEquals(c3, assertIs<NoteOutcome.Extra>(lagging.single { it is NoteOutcome.Extra }).played.pitch)
    }

    @Test
    fun `played chords are grouped by the spread, never holding the same pitch twice`() {
        val chords = NoteAlignment.chordsOf(struck(c4 to 0.0, c3 to 0.2, c4 to 0.24, d4 to 0.5, e4 to 0.5))

        assertEquals(listOf(listOf(c3, c4), listOf(c4), listOf(d4, e4)), chords.map { chord -> chord.map { it.pitch } })
        assertEquals(listOf(0.0, 0.24, 0.5), chords.map { chord -> chord.minOf { it.onsetBeat } })
    }

    @Test
    fun `a whole chord played late is still that chord up to the same distance as a single note`() {
        val late = NoteAlignment.align(twoHands.take(3).filter { it.beat == 0.0 }, struck(c4 to 1.5, c3 to 1.5))
        assertEquals(listOf("Correct", "Correct"), kinds(late))

        val tooLate = NoteAlignment.align(twoHands.filter { it.beat == 0.0 }, struck(c4 to 5.0, c3 to 5.0))
        assertEquals(setOf("Extra", "Missing"), kinds(tooLate).toSet())
        assertEquals(4, tooLate.size)
    }
}
