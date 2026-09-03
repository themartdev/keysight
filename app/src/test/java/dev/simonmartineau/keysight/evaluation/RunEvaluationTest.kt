package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunEvaluationTest {

    private val notes = Fixtures.cdef.notes

    private fun played(midi: Int, beat: Double) = PlayedNote(Pitch(midi), beat, beat + 0.5, velocity = 80, onsetNanos = 0L)

    private fun timing(id: String, judgement: TimingJudgement) = NoteTiming(id, 0.0, 0.0, 0.0, judgement)

    private fun result(
        outcomes: List<NoteOutcome>,
        timings: List<NoteTiming> = emptyList(),
        tempoRatio: Double? = 1.0,
        pauses: List<Pause> = emptyList(),
        continuity: Continuity = Continuity.GOOD,
    ) = EvaluationResult(4, PitchResult(outcomes), RhythmResult(timings, 0.0, tempoRatio, pauses, continuity))

    private val perfect = result(notes.map { NoteOutcome.Correct(it, played(it.pitch.midiNoteNumber, 0.0)) }, notes.map { timing(it.id, TimingJudgement.ON_TIME) })
    private val oneWrong = result(listOf(NoteOutcome.WrongPitch(notes[0], played(61, 0.0))) + notes.drop(1).map { NoteOutcome.Correct(it, played(it.pitch.midiNoteNumber, 0.0)) }, tempoRatio = 1.1, continuity = Continuity.HESITANT)
    private val oneLate = result(notes.map { NoteOutcome.Correct(it, played(it.pitch.midiNoteNumber, 0.0)) }, listOf(timing("n1", TimingJudgement.LATE)), pauses = listOf(Pause("n1", 0.6)))
    private val allMissing = result(notes.map { NoteOutcome.Missing(it) }, tempoRatio = null, continuity = Continuity.LOST)

    @Test
    fun `the run's pitch is every committed outcome in order`() {
        val evaluation = RunEvaluation(listOf(perfect, oneWrong), phaseBeats = 0.1)

        assertEquals(2, evaluation.committedCount)
        assertEquals(perfect.pitch.outcomes + oneWrong.pitch.outcomes, evaluation.pitch.outcomes)
        assertEquals(7, evaluation.pitch.correctCount)
        assertEquals(8, evaluation.pitch.expectedCount)
    }

    @Test
    fun `the run's rhythm merges the segments, keeps the final phase and takes the worst continuity`() {
        val rhythm = RunEvaluation(listOf(perfect, oneWrong, oneLate), phaseBeats = 0.1).rhythm!!

        assertEquals(5, rhythm.timings.size)
        assertEquals(0.1, rhythm.phaseBeats)
        assertEquals((1.0 + 1.1 + 1.0) / 3, rhythm.tempoRatio!!, 1e-9)
        assertEquals(listOf(Pause("n1", 0.6)), rhythm.pauses)
        assertEquals(Continuity.HESITANT, rhythm.continuity)
        assertEquals(Continuity.LOST, RunEvaluation(listOf(perfect, allMissing), 0.0).rhythm!!.continuity)
        assertNull(RunEvaluation(listOf(allMissing), 0.0).rhythm!!.tempoRatio)
    }

    @Test
    fun `nothing committed is empty`() {
        assertEquals(0, RunEvaluation.EMPTY.committedCount)
        assertEquals(0, RunEvaluation.EMPTY.pitch.expectedCount)
        assertNull(RunEvaluation.EMPTY.rhythm)
        assertEquals(emptyList(), RunEvaluation.EMPTY.weakestSegments())
        assertNull(RunEvaluation(listOf(EvaluationResult(1, PitchResult(emptyList()))), 0.0).rhythm)
    }

    @Test
    fun `the weakest bars are the imperfect ones, worst first, at most three`() {
        val evaluation = RunEvaluation(listOf(perfect, oneWrong, perfect, allMissing, oneLate, oneWrong), phaseBeats = 0.0)

        assertEquals(listOf(4, 2, 6), evaluation.weakestSegments())
        assertEquals(listOf(4, 2, 6, 5), evaluation.weakestSegments(limit = 10))
        assertEquals(emptyList(), RunEvaluation(listOf(perfect, perfect), 0.0).weakestSegments())
    }
}
