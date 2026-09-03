package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Replays hand-built MIDI against the one-segment run of the C4 D4 E4 F4 measure at 60 bpm:
 * the count-in is the first four seconds, the notes are due at seconds 4 to 7, and each beat
 * is one second, so run beats are seconds.
 */
class PitchEvaluatorTest {

    private val score = Fixtures.slowScore
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
        PerformanceEvaluator.evaluate(score, timeline, startedAt, events).pitch

    private fun kinds(result: PitchResult): List<String> = result.outcomes.map { it::class.simpleName!! }

    /**
     * Eighths are half a beat apart, twice the chord spread, so a pair is two notes whether
     * played in time or rushed to just outside the spread; a dropped one is missing on its own.
     */
    @Test
    fun `consecutive eighths are two notes and never one chord`() {
        val eighths = Fixtures.oneMeasure(
            ScoreNote("n1", Fixtures.C4, Ticks.ZERO, Ticks.EIGHTH),
            ScoreNote("n2", Fixtures.D4, Ticks.EIGHTH, Ticks.EIGHTH),
            ScoreNote("n3", Fixtures.E4, Ticks.QUARTER, Ticks.QUARTER),
            ScoreNote("n4", Fixtures.F4, Ticks.HALF, Ticks.HALF),
        )
        val run = Fixtures.run(eighths)
        fun played(vararg notes: Pair<Pitch, Double>): RunEvaluation = PerformanceEvaluator.evaluate(
            run.score,
            run.timeline,
            startedAt,
            notes.flatMap { (pitch, beat) ->
                val onset = performanceStart + (beat * SECOND_NANOS).toLong()
                listOf(MidiEvent.noteOn(onset, pitch, 90), MidiEvent.noteOff(onset + SECOND_NANOS / 4, pitch))
            },
        )

        val inTime = played(c4 to 0.0, d4 to 0.5, e4 to 1.0, f4 to 2.0)
        assertEquals(listOf("Correct", "Correct", "Correct", "Correct"), kinds(inTime.pitch))
        assertEquals(List(4) { TimingJudgement.ON_TIME }, inTime.rhythm!!.timings.map { it.judgement })

        val rushed = played(c4 to 0.0, d4 to 0.3, e4 to 1.0, f4 to 2.0)
        assertEquals(listOf("Correct", "Correct", "Correct", "Correct"), kinds(rushed.pitch))
        assertEquals(
            listOf(TimingJudgement.ON_TIME, TimingJudgement.EARLY, TimingJudgement.ON_TIME, TimingJudgement.ON_TIME),
            rushed.rhythm!!.timings.map { it.judgement },
        )

        val dropped = played(c4 to 0.0, e4 to 1.0, f4 to 2.0)
        assertEquals(listOf("Correct", "Missing", "Correct", "Correct"), kinds(dropped.pitch))
        assertEquals(5, PerformanceEvaluator.EVALUATOR_VERSION, "no judgement changed for eighths")
    }

    /**
     * A rest is no expected note: silence through it is right, a note in it is an extra, and
     * the gap it makes between two notes is notated, not a pause. A real hesitation after it
     * is still a pause.
     */
    @Test
    fun `a rest expects nothing, so silence is right, a note in it is extra and the gap is not a pause`() {
        val withRest = Fixtures.oneMeasure(
            ScoreNote("n1", Fixtures.C4, Ticks.ZERO, Ticks.QUARTER),
            ScoreNote("n2", Fixtures.E4, Ticks.HALF, Ticks.HALF),
        )
        val run = Fixtures.run(withRest)
        fun played(vararg notes: Pair<Pitch, Double>): RunEvaluation = PerformanceEvaluator.evaluate(
            run.score,
            run.timeline,
            startedAt,
            notes.flatMap { (pitch, beat) ->
                val onset = performanceStart + (beat * SECOND_NANOS).toLong()
                listOf(MidiEvent.noteOn(onset, pitch, 90), MidiEvent.noteOff(onset + SECOND_NANOS / 4, pitch))
            },
        )

        val silent = played(c4 to 0.0, e4 to 2.0)
        assertEquals(listOf("Correct", "Correct"), kinds(silent.pitch))
        assertEquals(1.0, silent.pitch.accuracy)
        val rhythm = silent.rhythm!!
        assertEquals(listOf(TimingJudgement.ON_TIME, TimingJudgement.ON_TIME), rhythm.timings.map { it.judgement })
        assertEquals(emptyList(), rhythm.pauses, "the silence of a rest is notated")
        assertEquals(Continuity.GOOD, rhythm.continuity)
        assertEquals(1.0, rhythm.tempoRatio!!, 1e-9)

        val filled = played(c4 to 0.0, d4 to 1.0, e4 to 2.0)
        assertEquals(listOf("Correct", "Extra", "Correct"), kinds(filled.pitch))
        assertEquals(1, filled.pitch.extraCount)
        assertEquals(emptyList(), filled.rhythm!!.pauses)

        val hesitant = played(c4 to 0.0, e4 to 2.7)
        assertEquals(listOf("Correct", "Correct"), kinds(hesitant.pitch))
        assertEquals(listOf("1:n2"), hesitant.rhythm!!.pauses.map { it.beforeNoteId }, "a hesitation after the rest is still a pause")
        assertEquals(Continuity.HESITANT, hesitant.rhythm!!.continuity)
        assertEquals(5, PerformanceEvaluator.EVALUATOR_VERSION, "no judgement changed for rests")
    }

    /**
     * The evaluator compares MIDI numbers, so an accidental is just another pitch: a written
     * B natural in F played as B natural is correct, played as the key's B flat is a wrong
     * pitch, and played E sharp for F is the same note.
     */
    @Test
    fun `an altered note is correct at its own pitch and a wrong pitch at its diatonic neighbour`() {
        val inF = Fixtures.oneMeasure(
            ScoreNote("n1", SpelledPitch(Step.C, octave = 5), Ticks.ZERO, Ticks.QUARTER),
            ScoreNote("n2", SpelledPitch(Step.B, octave = 4), Ticks.QUARTER, Ticks.QUARTER),
            ScoreNote("n3", SpelledPitch(Step.C, octave = 5), Ticks.HALF, Ticks.QUARTER),
            ScoreNote("n4", SpelledPitch(Step.E, alteration = 1, octave = 4), Ticks.quarters(3), Ticks.QUARTER),
        ).copy(keySignature = KeySignature(-1))
        val run = Fixtures.run(inF)
        fun played(vararg midi: Int): PitchResult = PerformanceEvaluator.evaluate(
            run.score,
            run.timeline,
            startedAt,
            midi.flatMapIndexed { index, note ->
                val onset = performanceStart + index * SECOND_NANOS
                listOf(MidiEvent.noteOn(onset, Pitch(note), 90), MidiEvent.noteOff(onset + SECOND_NANOS / 2, Pitch(note)))
            },
        ).pitch

        assertEquals(listOf("Correct", "Correct", "Correct", "Correct"), kinds(played(72, 71, 72, 65)), "B natural and E sharp, which is F")
        val flat = played(72, 70, 72, 65)
        assertEquals(listOf("Correct", "WrongPitch", "Correct", "Correct"), kinds(flat), "the key's B flat is not the written B natural")
        assertEquals(Pitch(70), assertIs<NoteOutcome.WrongPitch>(flat.outcomes[1]).played.pitch)
        assertEquals(listOf("Correct", "Correct", "Correct", "WrongPitch"), kinds(played(72, 71, 72, 64)), "E natural is not E sharp")
        assertEquals(5, PerformanceEvaluator.EVALUATOR_VERSION, "no judgement changed for accidentals")
    }

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
        assertEquals("1:n3", wrong.expected.id)
        assertEquals(g4, wrong.played.pitch)
        assertEquals(0.75, result.accuracy)
    }

    @Test
    fun `a missing note is reported and the rest still match`() {
        val result = evaluate(performance(listOf(c4, d4, f4)))

        assertEquals(listOf("Correct", "Correct", "Missing", "Correct"), kinds(result))
        assertEquals("1:n3", assertIs<NoteOutcome.Missing>(result.outcomes[2]).expected.id)
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
        assertEquals(5.0, correct.played.onsetBeat)
        assertEquals(6.0, extra.played.onsetBeat)
    }

    @Test
    fun `early and late notes are still the right pitches in phase 1`() {
        val early = evaluate(performance(listOf(c4, d4, e4, f4), offsetNanos = -SECOND_NANOS / 4))
        val late = evaluate(performance(listOf(c4, d4, e4, f4), offsetNanos = SECOND_NANOS / 3))

        assertEquals(1.0, early.accuracy)
        assertEquals(1.0, late.accuracy)
        assertEquals(3.75, assertIs<NoteOutcome.Correct>(early.outcomes[0]).played.onsetBeat)
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
    fun `a two segment run is one performance across the barline`() {
        val run = Fixtures.run(Fixtures.cdef, Fixtures.gfed)
        val events = performance(listOf(c4, d4, e4, f4, g4, f4, e4, d4))

        val result = PerformanceEvaluator.evaluate(run.score, run.timeline, startedAt, events).pitch

        assertEquals(8, result.expectedCount)
        assertEquals(1.0, result.accuracy)
        assertEquals("2:n1", assertIs<NoteOutcome.Correct>(result.outcomes[4]).expected.id)
    }

    @Test
    fun `the evaluator is deterministic`() {
        val events = performance(listOf(c4, g4, d4, e4, f4, f4))

        assertEquals(evaluate(events), evaluate(events))
    }

    @Test
    fun `the result carries the evaluator version`() {
        assertEquals(PerformanceEvaluator.EVALUATOR_VERSION, PerformanceEvaluator.evaluate(score, timeline, startedAt, emptyList()).segments.single().evaluatorVersion)
    }

    @Test
    fun `accuracy of an empty score is zero rather than undefined`() {
        val empty = Fixtures.run(Fixtures.oneMeasure())

        assertEquals(0.0, PerformanceEvaluator.evaluate(empty.score, empty.timeline, startedAt, emptyList()).pitch.accuracy)
    }

    @Test
    fun `alignment handles a score whose notes are not in id order`() {
        val reversed = Fixtures.run(
            Fixtures.oneMeasure(
                ScoreNote("b", Fixtures.D4, Ticks.QUARTER, Ticks.QUARTER),
                ScoreNote("a", Fixtures.C4, Ticks.ZERO, Ticks.QUARTER),
            ),
        )

        val result = PerformanceEvaluator.evaluate(reversed.score, reversed.timeline, startedAt, performance(listOf(c4, d4))).pitch

        assertEquals(listOf("1:a", "1:b"), result.outcomes.map { (it as NoteOutcome.Correct).expected.id })
    }
}
