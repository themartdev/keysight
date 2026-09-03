package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Replays hand-built MIDI against the C4 D4 E4 F4 measure at 60 bpm, where the performance
 * starts four seconds after the attempt and each beat is one second.
 */
class PitchEvaluatorTest {

    private val score = Fixtures.cdef
    private val timeline = Fixtures.slowTimeline
    private val startedAt = 0L
    private val performanceStart = 4 * SECOND_NANOS

    private val c4 = Pitch(60)
    private val d4 = Pitch(62)
    private val e4 = Pitch(64)
    private val f4 = Pitch(65)
    private val g4 = Pitch(67)

    /** One quarter note per beat starting at the performance start, each held for half a beat. */
    private fun performance(pitches: List<Pitch>, offsetNanos: Long = 0): List<MidiEvent> =
        pitches.flatMapIndexed { index, pitch ->
            val onset = performanceStart + offsetNanos + index * SECOND_NANOS
            listOf(MidiEvent.noteOn(onset, pitch, 90), MidiEvent.noteOff(onset + SECOND_NANOS / 2, pitch))
        }

    private fun evaluate(events: List<MidiEvent>): PitchResult =
        PerformanceEvaluator.evaluate(score, events, timeline, startedAt).pitch

    private fun kinds(result: PitchResult): List<String> = result.outcomes.map { it::class.simpleName!! }

    @Test
    fun `a perfect performance is all correct`() {
        val result = evaluate(performance(listOf(c4, d4, e4, f4)))

        assertEquals(listOf("Correct", "Correct", "Correct", "Correct"), kinds(result))
        assertEquals(1.0, result.accuracy)
        assertEquals(4, result.expectedCount)
        assertEquals(0, result.extraCount)
    }

    @Test
    fun `a wrong pitch is reported against the note it replaced`() {
        val result = evaluate(performance(listOf(c4, d4, g4, f4)))

        assertEquals(listOf("Correct", "Correct", "WrongPitch", "Correct"), kinds(result))
        val wrong = assertIs<NoteOutcome.WrongPitch>(result.outcomes[2])
        assertEquals("n3", wrong.expected.id)
        assertEquals(g4, wrong.played.pitch)
        assertEquals(0.75, result.accuracy)
    }

    @Test
    fun `a missing note is reported and the rest still match`() {
        val result = evaluate(performance(listOf(c4, d4, f4)))

        assertEquals(listOf("Correct", "Correct", "Missing", "Correct"), kinds(result))
        assertEquals("n3", assertIs<NoteOutcome.Missing>(result.outcomes[2]).expected.id)
        assertEquals(1, result.missingCount)
        assertEquals(0.75, result.accuracy)
    }

    @Test
    fun `an extra note between correct ones is extra, not a wrong pitch`() {
        val result = evaluate(performance(listOf(c4, d4, g4, e4, f4)))

        assertEquals(listOf("Correct", "Correct", "Extra", "Correct", "Correct"), kinds(result))
        assertEquals(1, result.extraCount)
        assertEquals(1.0, result.accuracy)
    }

    @Test
    fun `a repeated note marks the later repeat as extra`() {
        val result = evaluate(performance(listOf(c4, d4, d4, e4, f4)))

        assertEquals(listOf("Correct", "Correct", "Extra", "Correct", "Correct"), kinds(result))
        val correct = assertIs<NoteOutcome.Correct>(result.outcomes[1])
        val extra = assertIs<NoteOutcome.Extra>(result.outcomes[2])
        assertEquals(1.0, correct.played.onsetBeat)
        assertEquals(2.0, extra.played.onsetBeat)
    }

    @Test
    fun `early and late notes are still the right pitches in phase 1`() {
        val early = evaluate(performance(listOf(c4, d4, e4, f4), offsetNanos = -SECOND_NANOS / 4))
        val late = evaluate(performance(listOf(c4, d4, e4, f4), offsetNanos = SECOND_NANOS / 3))

        assertEquals(1.0, early.accuracy)
        assertEquals(1.0, late.accuracy)
        assertEquals(-0.25, assertIs<NoteOutcome.Correct>(early.outcomes[0]).played.onsetBeat)
    }

    @Test
    fun `a pause in the middle does not cost a pitch`() {
        val events = performance(listOf(c4, d4)) + performance(listOf(e4, f4), offsetNanos = 3 * SECOND_NANOS + SECOND_NANOS / 2)

        assertEquals(1.0, evaluate(events).accuracy)
    }

    @Test
    fun `a first note struck too early is not part of the performance`() {
        val result = evaluate(performance(listOf(c4, d4, e4, f4), offsetNanos = -SECOND_NANOS))

        assertEquals(listOf("Missing", "Correct", "Correct", "Correct"), kinds(result))
    }

    @Test
    fun `a last note after capture ended is missing`() {
        val events = performance(listOf(c4, d4, e4)) + performance(listOf(f4), offsetNanos = 5 * SECOND_NANOS + 1)

        assertEquals(listOf("Correct", "Correct", "Correct", "Missing"), kinds(evaluate(events)))
    }

    @Test
    fun `an empty performance is all missing with zero accuracy`() {
        val result = evaluate(emptyList())

        assertEquals(listOf("Missing", "Missing", "Missing", "Missing"), kinds(result))
        assertEquals(0.0, result.accuracy)
    }

    @Test
    fun `an octave error is four wrong pitches`() {
        val result = evaluate(performance(listOf(c4.transposedBy(12), d4.transposedBy(12), e4.transposedBy(12), f4.transposedBy(12))))

        assertEquals(4, result.wrongCount)
        assertEquals(0.0, result.accuracy)
    }

    @Test
    fun `stopping short marks the unplayed tail as missing`() {
        val result = evaluate(performance(listOf(c4, d4)))

        assertEquals(listOf("Correct", "Correct", "Missing", "Missing"), kinds(result))
    }

    @Test
    fun `a stray note before the first correct one is extra`() {
        val result = evaluate(performance(listOf(g4, c4, d4, e4, f4)))

        assertEquals(listOf("Extra", "Correct", "Correct", "Correct", "Correct"), kinds(result))
    }

    @Test
    fun `the evaluator is deterministic`() {
        val events = performance(listOf(c4, g4, d4, e4, f4, f4))

        assertEquals(evaluate(events), evaluate(events))
    }

    @Test
    fun `the result carries the evaluator version`() {
        assertEquals(PerformanceEvaluator.EVALUATOR_VERSION, PerformanceEvaluator.evaluate(score, emptyList(), timeline, startedAt).evaluatorVersion)
    }

    @Test
    fun `accuracy of an empty score is zero rather than undefined`() {
        val empty = Fixtures.oneMeasure()

        assertEquals(0.0, PerformanceEvaluator.evaluate(empty, emptyList(), timeline, startedAt).pitch.accuracy)
    }

    @Test
    fun `alignment handles a score whose notes are not in id order`() {
        val reversed = Fixtures.oneMeasure(
            ScoreNote("b", Fixtures.D4, Ticks.QUARTER, Ticks.QUARTER),
            ScoreNote("a", Fixtures.C4, Ticks.ZERO, Ticks.QUARTER),
        )

        val result = PerformanceEvaluator.evaluate(reversed, performance(listOf(c4, d4)), timeline, startedAt).pitch

        assertEquals(listOf("a", "b"), result.outcomes.map { (it as NoteOutcome.Correct).expected.id })
    }
}
