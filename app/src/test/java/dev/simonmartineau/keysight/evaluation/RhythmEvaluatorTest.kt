package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rhythm end to end, against the one-segment run of the C4 D4 E4 F4 measure at 60 bpm: the
 * notes are due four seconds after the run starts and each beat is one second, so seconds
 * from the performance start are beats of the bar.
 */
class RhythmEvaluatorTest {

    private val score = Fixtures.slowScore
    private val timeline = Fixtures.slowTimeline
    private val performanceStart = 4 * SECOND_NANOS

    private val c4 = Pitch(60)
    private val d4 = Pitch(62)
    private val e4 = Pitch(64)
    private val f4 = Pitch(65)
    private val g4 = Pitch(67)
    private val a4 = Pitch(69)
    private val b4 = Pitch(71)
    private val c5 = Pitch(72)

    /** Each pitch struck at its beat, in seconds from the performance start, held for a quarter beat. */
    private fun performance(vararg notes: Pair<Pitch, Double>): List<MidiEvent> =
        notes.flatMap { (pitch, beat) ->
            val onset = performanceStart + (beat * SECOND_NANOS).toLong()
            listOf(MidiEvent.noteOn(onset, pitch, 90), MidiEvent.noteOff(onset + SECOND_NANOS / 4, pitch))
        }

    private fun evaluate(events: List<MidiEvent>): RunEvaluation =
        PerformanceEvaluator.evaluate(score, timeline, startedAtNanos = 0L, events = events)

    @Test
    fun `right notes at the wrong times score full pitch and no rhythm`() {
        val result = evaluate(performance(c4 to 0.0, d4 to 0.5, e4 to 1.0, f4 to 1.5))

        assertEquals(1.0, result.pitch.accuracy)
        val rhythm = result.rhythm!!
        assertEquals(0.0, rhythm.accuracy)
        assertEquals(4, rhythm.matchedCount)
        assertEquals(2.0, rhythm.tempoRatio!!, 1e-9)
    }

    @Test
    fun `wrong notes on the beat score no pitch and full rhythm`() {
        val result = evaluate(performance(g4 to 0.0, a4 to 1.0, b4 to 2.0, c5 to 3.0))

        assertEquals(0.0, result.pitch.accuracy)
        assertEquals(4, result.pitch.wrongCount)
        val rhythm = result.rhythm!!
        assertEquals(1.0, rhythm.accuracy)
        assertEquals(Continuity.GOOD, rhythm.continuity)
    }

    @Test
    fun `a uniformly late performance is on time after its phase`() {
        val result = evaluate(performance(c4 to 0.06, d4 to 1.06, e4 to 2.06, f4 to 3.06))

        val rhythm = result.rhythm!!
        assertEquals(0.06, rhythm.phaseBeats, 1e-9)
        assertEquals(1.0, rhythm.accuracy)
        rhythm.timings.forEach { assertEquals(0.0, it.errorBeats, 1e-9) }
        assertEquals(1.0, rhythm.tempoRatio!!, 1e-9)
    }

    @Test
    fun `timings are on the run's beat line`() {
        val rhythm = evaluate(performance(c4 to 0.0, d4 to 1.0, e4 to 2.0, f4 to 3.0)).rhythm!!

        assertEquals(listOf(4.0, 5.0, 6.0, 7.0), rhythm.timings.map { it.expectedBeat })
        assertEquals(listOf(4.0, 5.0, 6.0, 7.0), rhythm.timings.map { it.playedBeat })
    }

    @Test
    fun `a hesitation is a pause and costs continuity`() {
        val result = evaluate(performance(c4 to 0.0, d4 to 1.0, e4 to 3.0, f4 to 4.0))

        assertEquals(1.0, result.pitch.accuracy)
        val rhythm = result.rhythm!!
        assertEquals(listOf("1:n3"), rhythm.pauses.map { it.beforeNoteId })
        assertEquals(1.0, rhythm.pauses.single().extraBeats, 1e-9)
        assertEquals(0.5, rhythm.accuracy)
        assertEquals(Continuity.HESITANT, rhythm.continuity)
        assertEquals(0.0, rhythm.phaseBeats)
    }

    @Test
    fun `timing tells which repeat of a note was skipped`() {
        val repeated = Fixtures.run(
            Fixtures.oneMeasure(
                ScoreNote("r1", Fixtures.C4, Ticks.ZERO, Ticks.QUARTER),
                ScoreNote("r2", Fixtures.C4, Ticks.QUARTER, Ticks.QUARTER),
                ScoreNote("r3", Fixtures.C4, Ticks.quarters(2), Ticks.QUARTER),
                ScoreNote("r4", Fixtures.C4, Ticks.quarters(3), Ticks.QUARTER),
            ),
        )

        val result = PerformanceEvaluator.evaluate(repeated.score, repeated.timeline, 0L, performance(c4 to 0.0, c4 to 1.0, c4 to 3.0))

        assertEquals(listOf("Correct", "Correct", "Missing", "Correct"), result.pitch.outcomes.map { it::class.simpleName })
        assertEquals(1.0, result.rhythm!!.accuracy)
    }

    @Test
    fun `an empty performance has no rhythm to speak of`() {
        val rhythm = evaluate(emptyList()).rhythm!!

        assertEquals(0, rhythm.matchedCount)
        assertEquals(0.0, rhythm.accuracy)
        assertEquals(Continuity.LOST, rhythm.continuity)
        assertEquals(null, rhythm.tempoRatio)
    }

    @Test
    fun `the evaluator is deterministic and versioned`() {
        val events = performance(c4 to 0.1, g4 to 0.9, e4 to 2.4, f4 to 3.0)

        assertEquals(evaluate(events), evaluate(events))
        assertEquals(PerformanceEvaluator.EVALUATOR_VERSION, evaluate(events).segments.single().evaluatorVersion)
        assertEquals(5, PerformanceEvaluator.EVALUATOR_VERSION)
        assertTrue(evaluate(events).rhythm != null)
    }
}
