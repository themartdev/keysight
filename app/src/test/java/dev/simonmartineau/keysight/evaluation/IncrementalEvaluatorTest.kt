package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.GeneratedSegmentSource
import dev.simonmartineau.keysight.run.RunContext
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Committing segment by segment from the trailing window, against the three-segment run of
 * C4 D4 E4 F4, G4 F4 E4 D4, C4 D4 E4 F4 at 60 bpm: the count-in is beats 0 to 4, the bars are
 * beats 4 to 8, 8 to 12 and 12 to 16, and segment k commits at beat 4k + 5.
 */
class IncrementalEvaluatorTest {

    private val run = Fixtures.run(Fixtures.cdef, Fixtures.gfed, Fixtures.cdef)
    private val score = run.score
    private val timeline = run.timeline

    private val c4 = Pitch(60)
    private val d4 = Pitch(62)
    private val e4 = Pitch(64)
    private val f4 = Pitch(65)
    private val g4 = Pitch(67)
    private val a4 = Pitch(69)

    /** The notes of the whole run, one per beat from beat 4, as pitch to beat. */
    private val perfect: List<Pair<Pitch, Double>> =
        listOf(c4, d4, e4, f4, g4, f4, e4, d4, c4, d4, e4, f4).mapIndexed { index, pitch -> pitch to 4.0 + index }

    /** Each pitch struck at its run beat, held a quarter beat. */
    private fun performance(notes: List<Pair<Pitch, Double>>): List<MidiEvent> =
        notes.flatMap { (pitch, beat) ->
            val onset = (beat * SECOND_NANOS).toLong()
            listOf(MidiEvent.noteOn(onset, pitch, 90), MidiEvent.noteOff(onset + SECOND_NANOS / 4, pitch))
        }

    private fun evaluate(notes: List<Pair<Pitch, Double>>, context: RunContext = run): RunEvaluation =
        PerformanceEvaluator.evaluate(context.score, context.timeline, startedAtNanos = 0L, events = performance(notes))

    private fun kinds(result: EvaluationResult) = result.pitch.outcomes.map { it::class.simpleName!! }

    private fun ids(result: EvaluationResult) = result.pitch.outcomes.map { outcome ->
        when (outcome) {
            is NoteOutcome.Correct -> outcome.expected.id
            is NoteOutcome.WrongPitch -> outcome.expected.id
            is NoteOutcome.Missing -> outcome.expected.id
            is NoteOutcome.TooLate -> outcome.expected.id
            is NoteOutcome.Extra -> "extra@${outcome.played.onsetBeat}"
        }
    }

    /** One alignment of the whole run at phase 0, cut by the segment each expected note or extra belongs to. */
    private fun unlimitedContext(notes: List<Pair<Pitch, Double>>): Map<Int, List<NoteOutcome>> {
        val expected = score.notesInPerformanceOrder.map { ExpectedNote(it, timeline.beatsOf(it.onset)) }
        val played = PlayedNotes.extract(performance(notes), timeline, 0L, PerformanceEvaluator.EARLY_GRACE_BEATS)
        return NoteAlignment.align(expected, played, phaseBeats = 0.0).groupBy { outcome ->
            when (outcome) {
                is NoteOutcome.Extra -> timeline.segmentAt(outcome.played.onsetBeat)
                else -> score.measureOf(outcome.let { it as? NoteOutcome.Correct }?.expected?.onset ?: (outcome as? NoteOutcome.WrongPitch)?.expected?.onset ?: (outcome as NoteOutcome.Missing).expected.onset)
            }
        }
    }

    private fun assertMatchesUnlimitedContext(notes: List<Pair<Pitch, Double>>) {
        val oracle = unlimitedContext(notes)
        val evaluation = evaluate(notes)
        assertEquals(3, evaluation.committedCount)
        evaluation.segments.forEachIndexed { index, result ->
            assertEquals(oracle[index + 1].orEmpty(), result.pitch.outcomes, "segment ${index + 1}")
        }
    }

    @Test
    fun `a perfect run commits every bar correct`() {
        val evaluation = evaluate(perfect)

        assertEquals(listOf(4, 4, 4), evaluation.segments.map { it.pitch.correctCount })
        assertEquals(1.0, evaluation.pitch.accuracy)
        assertEquals(1.0, evaluation.rhythm!!.accuracy)
        assertEquals(0.0, evaluation.phaseBeats)
        assertMatchesUnlimitedContext(perfect)
    }

    @Test
    fun `wrong, missing, extra, repeated, early, late and paused notes are judged as with unlimited context`() {
        val wrong = perfect.toMutableList().also { it[5] = a4 to 9.0 }
        val missing = perfect.filterIndexed { index, _ -> index != 6 }
        val extra = perfect + (a4 to 10.5)
        val repeated = perfect + (d4 to 5.4)
        val early = perfect.map { (pitch, beat) -> pitch to beat - 0.2 }
        val late = perfect.mapIndexed { index, (pitch, beat) -> pitch to if (index == 9) beat + 0.4 else beat }
        val paused = perfect.mapIndexed { index, (pitch, beat) -> pitch to if (index >= 10) beat + 1.0 else beat }

        listOf(wrong, missing, extra, repeated, early, late, paused).forEach(::assertMatchesUnlimitedContext)
        assertEquals(listOf("Correct", "WrongPitch", "Correct", "Correct"), kinds(evaluate(wrong).segments[1]))
        assertEquals(listOf("Correct", "Correct", "Missing", "Correct"), kinds(evaluate(missing).segments[1]))
        assertEquals(1, evaluate(extra).segments[1].pitch.extraCount)
        assertEquals(listOf(Pause("3:n3", 1.0)), evaluate(paused).segments[2].rhythm!!.pauses.map { it.copy(extraBeats = Math.round(it.extraBeats * 1000) / 1000.0) })
    }

    @Test
    fun `the last note of a bar played after its commit is too late, not an extra of the next bar`() {
        // The player hesitates across the barline: bar 1 ends on an eighth note due at beat 7.5, played 1.6
        // beats late, after the tail, and bar 2 starts late behind it and catches up. Bar 2 has no F4 of its own.
        val shortEnd = Fixtures.oneMeasure(
            ScoreNote("n1", Fixtures.C4, Ticks.ZERO, Ticks.QUARTER),
            ScoreNote("n2", Fixtures.D4, Ticks.QUARTER, Ticks.QUARTER),
            ScoreNote("n3", Fixtures.E4, Ticks.quarters(2), Ticks.QUARTER),
            ScoreNote("n4", Fixtures.F4, Ticks.quarters(3) + Ticks.EIGHTH, Ticks.EIGHTH),
        )
        val gege = Fixtures.oneMeasure(
            ScoreNote("n1", Fixtures.G4, Ticks.ZERO, Ticks.QUARTER),
            ScoreNote("n2", Fixtures.E4, Ticks.QUARTER, Ticks.QUARTER),
            ScoreNote("n3", Fixtures.G4, Ticks.quarters(2), Ticks.QUARTER),
            ScoreNote("n4", Fixtures.E4, Ticks.quarters(3), Ticks.QUARTER),
        )
        val context = Fixtures.run(shortEnd, gege, Fixtures.cdef)
        val notes = listOf(c4 to 4.0, d4 to 5.0, e4 to 6.0, f4 to 9.1, g4 to 9.4, e4 to 10.2, g4 to 10.8, e4 to 11.4) +
            listOf(c4, d4, e4, f4).mapIndexed { index, pitch -> pitch to 12.0 + index }

        val evaluation = evaluate(notes, context)

        assertEquals(listOf("Correct", "Correct", "Correct", "Missing"), kinds(evaluation.segments[0]))
        val second = evaluation.segments[1]
        assertEquals(0, second.pitch.extraCount)
        assertEquals(4, second.pitch.correctCount)
        assertEquals(4, second.pitch.expectedCount)
        val tooLate = assertIs<NoteOutcome.TooLate>(second.pitch.outcomes.single { it is NoteOutcome.TooLate })
        assertEquals("1:n4", tooLate.expected.id)
        assertEquals(9.1, tooLate.played.onsetBeat)
        assertEquals(12, evaluation.pitch.expectedCount)
        assertEquals(0, evaluation.pitch.extraCount)
        assertNull(second.rhythm!!.timingOf("1:n4"))
        assertEquals(TimingJudgement.LATE, second.rhythm!!.timingOf("2:n1")!!.judgement)
    }

    @Test
    fun `a note more than two beats late is an extra, as it would be with unlimited context`() {
        val notes = perfect.mapIndexed { index, (pitch, beat) -> pitch to if (index == 3) 9.2 else beat }

        val evaluation = evaluate(notes)

        assertEquals(listOf("Correct", "Correct", "Correct", "Missing"), kinds(evaluation.segments[0]))
        assertEquals(1, evaluation.segments[1].pitch.extraCount)
        assertMatchesUnlimitedContext(notes)
    }

    @Test
    fun `the downbeat of the next bar played in the tail of this one is that downbeat`() {
        val notes = perfect.mapIndexed { index, (pitch, beat) -> pitch to if (index == 4) 7.9 else beat }

        val evaluation = evaluate(notes)

        assertEquals(0, evaluation.segments[0].pitch.extraCount)
        assertEquals(4, evaluation.segments[0].pitch.correctCount)
        assertEquals("2:n1", ids(evaluation.segments[1]).first())
        assertEquals(7.9, assertIs<NoteOutcome.Correct>(evaluation.segments[1].pitch.outcomes.first()).played.onsetBeat)
        assertMatchesUnlimitedContext(notes)
    }

    @Test
    fun `a stray note in a bar's tail waits for the next bar's commit`() {
        val notes = perfect + (a4 to 8.5)

        val evaluation = evaluate(notes)

        assertEquals(0, evaluation.segments[0].pitch.extraCount)
        assertEquals(1, evaluation.segments[1].pitch.extraCount)
        assertEquals(1, evaluation.pitch.extraCount)
    }

    @Test
    fun `every played note has exactly one outcome across the commits`() {
        val messy = perfect + listOf(a4 to 8.5, g4 to 6.3, e4 to 16.4) + perfect.mapIndexed { index, (pitch, beat) -> pitch to if (index == 7) 13.1 else beat }.drop(7).take(1)

        val evaluation = evaluate(messy)

        val played = evaluation.pitch.outcomes.mapNotNull { it.played }.map { it.onsetNanos to it.pitch }
        assertEquals(played.size, played.toSet().size, "no played note counted twice")
        assertEquals(messy.size, played.size, "no played note lost")
        assertEquals(12, evaluation.pitch.expectedCount)
    }

    @Test
    fun `a commit sees only what had arrived by its tail`() {
        val held = listOf(MidiEvent.noteOn(4 * SECOND_NANOS, c4, 90), MidiEvent.noteOff(12 * SECOND_NANOS, c4))

        val evaluation = PerformanceEvaluator.evaluate(score, timeline, 0L, held)

        val first = assertIs<NoteOutcome.Correct>(evaluation.segments[0].pitch.outcomes.first())
        assertNull(first.played.releaseBeat, "the key was still down when bar 1 was committed")
    }

    @Test
    fun `committing one bar at a time equals evaluating the run in one go`() {
        val events = performance(perfect + (a4 to 8.5))
        var evaluation = RunEvaluation.EMPTY
        for (segment in 1..3) {
            val seen = events.filter { it.timestampNanos <= timeline.captureEndNanosAfter(segment) + 3 * SECOND_NANOS }
            evaluation = PerformanceEvaluator.commit(evaluation, score, timeline, 0L, seen, segment, lastSegment = 3)
        }

        assertEquals(PerformanceEvaluator.evaluate(score, timeline, 0L, events), evaluation)
        assertEquals(evaluation, PerformanceEvaluator.evaluate(score, timeline, 0L, events.shuffled()))
    }

    @Test
    fun `a stopped run commits its last bar without a bar after it and takes the tail's extras`() {
        val notes = perfect.take(8) + (a4 to 12.5)

        val evaluation = (1..2).fold(RunEvaluation.EMPTY) { evaluation, segment ->
            PerformanceEvaluator.commit(evaluation, score, timeline, 0L, performance(notes), segment, lastSegment = 2)
        }

        assertEquals(2, evaluation.committedCount)
        assertEquals(1, evaluation.segments[1].pitch.extraCount)
        assertEquals(8, evaluation.pitch.correctCount)
    }

    @Test
    fun `a slow drift without the click is followed and a jump is not`() {
        val drifting = perfect.mapIndexed { index, (pitch, beat) -> pitch to beat + 0.1 * (index / 4 + 1) }
        val followed = evaluate(drifting)
        assertEquals(listOf(0.1, 0.2, 0.3), followed.segments.map { Math.round(it.rhythm!!.phaseBeats * 1000) / 1000.0 })
        assertEquals(1.0, followed.rhythm!!.accuracy)

        val jump = perfect.mapIndexed { index, (pitch, beat) -> pitch to if (index >= 4) beat + 0.7 else beat }
        val notFollowed = evaluate(jump)
        assertEquals(listOf(0.0, 0.0, 0.0), notFollowed.segments.map { it.rhythm!!.phaseBeats })
        assertEquals(8, notFollowed.rhythm!!.lateCount)

        val smallJump = perfect.mapIndexed { index, (pitch, beat) -> pitch to if (index >= 4) beat + 0.4 else beat }
        val stepped = evaluate(smallJump)
        assertEquals(listOf(0.0, 0.125, 0.25), stepped.segments.map { it.rhythm!!.phaseBeats })
        assertEquals(TimingJudgement.LATE, stepped.segments[1].rhythm!!.timings.first().judgement)
    }

    @Test
    fun `under the click the phase barely moves and stays within the latency bound`() {
        val clicked = RunContext(run.segments, run.config.copy(metronome = MetronomeMode.THROUGHOUT))
        val drifting = perfect.mapIndexed { index, (pitch, beat) -> pitch to beat + 0.2 * (index / 4 + 1) }

        val evaluation = evaluate(drifting, clicked)

        assertEquals(listOf(0.2, 0.22, 0.24), evaluation.segments.map { Math.round(it.rhythm!!.phaseBeats * 1000) / 1000.0 })
        assertEquals(listOf(0, 4, 4), evaluation.segments.map { it.rhythm!!.lateCount })
        val far = evaluate(perfect.map { (pitch, beat) -> pitch to beat + 0.45 }, clicked)
        assertTrue(far.segments.all { it.rhythm!!.phaseBeats <= BeatPhase.MAX_PHASE_BEATS })
    }

    @Test
    fun `both hands are committed together, each outcome naming its staff`() {
        // A generated hands-together run in C: the melody on one staff, a held triad tone on the other.
        val config = ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE)
        val context = RunContext(GeneratedSegmentSource(runSeed = 11L, config).next(3, firstIndex = 1, committed = emptyList()), Fixtures.slowConfig.copy(segmentCount = 3), seed = 11L)
        val notes = context.score.notes.map { it.pitch to context.timeline.beatsOf(it.onset) }
        val leftLate = notes.map { (pitch, beat) -> pitch to if (pitch < Pitch(60)) beat + 0.15 else beat }

        val evaluation = evaluate(leftLate, context)

        assertEquals(context.score.notes.size, evaluation.pitch.correctCount)
        assertEquals(0, evaluation.pitch.extraCount)
        assertEquals(1.0, evaluation.pitch.accuracy)
        evaluation.segments.forEachIndexed { index, result ->
            val staves = result.pitch.outcomes.map { (it as NoteOutcome.Correct).expected.staff }.toSet()
            assertEquals(setOf(0, 1), staves, "segment ${index + 1} judges both staves")
        }

        val leftDropped = notes.filter { (pitch, _) -> pitch >= Pitch(60) }
        val dropped = evaluate(leftDropped, context)
        val missing = dropped.pitch.outcomes.filterIsInstance<NoteOutcome.Missing>()
        assertEquals(context.score.notes.count { it.pitch < Pitch(60) }, missing.size)
        assertTrue(missing.all { it.expected.staff == 1 && it.expected.hand == dev.simonmartineau.keysight.score.Hand.LEFT })
        assertEquals(0, dropped.pitch.extraCount)
    }

    @Test
    fun `commits are versioned and refuse to skip a bar`() {
        val evaluation = evaluate(perfect)

        assertTrue(evaluation.segments.all { it.evaluatorVersion == PerformanceEvaluator.EVALUATOR_VERSION })
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            PerformanceEvaluator.commit(RunEvaluation.EMPTY, score, timeline, 0L, emptyList(), segment = 2, lastSegment = 3)
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            PerformanceEvaluator.commit(RunEvaluation.EMPTY, score, timeline, 0L, emptyList(), segment = 1, lastSegment = 4)
        }
    }
}
