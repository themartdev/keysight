package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.RunStatus
import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredRunTest {

    private val segments = listOf(Fixtures.segment("m1", Fixtures.cdef), Fixtures.segment("m2", Fixtures.gfed), Fixtures.segment("m3", Fixtures.cdef))

    private val record = HistoryFixtures.record("r1", segments, Fixtures.slowConfig) { note -> if (note.id == "2:n1") Pitch(70) else note.pitch }

    @Test
    fun `a completed run at the current version is current, and its evaluation is the run view the summary reads`() {
        val run = HistoryFixtures.stored(record)

        assertTrue(run.isCurrent)
        assertEquals(3, run.judgedSegments)
        assertEquals(11, run.evaluation.pitch.correctCount)
        assertEquals(1, run.evaluation.pitch.wrongCount)
        assertEquals(run.evaluations.last().rhythm!!.phaseBeats, run.evaluation.phaseBeats)
        assertEquals(listOf(2), run.evaluation.weakestSegments())
    }

    @Test
    fun `a judgement at an older version, or a missing one, makes the run stale`() {
        val current = HistoryFixtures.stored(record)
        val older = current.copy(evaluations = current.evaluations.map { it.copy(evaluatorVersion = PerformanceEvaluator.EVALUATOR_VERSION - 1) })
        val partial = current.copy(evaluations = current.evaluations.take(2))
        val none = current.copy(evaluations = emptyList())

        assertFalse(older.isCurrent)
        assertFalse(partial.isCurrent)
        assertFalse(none.isCurrent, "a run migrated without its judgements is judged afresh")
    }

    @Test
    fun `re-evaluating replays every commit from the segments and raw MIDI, at the current version`() {
        val current = HistoryFixtures.stored(record)
        val older = current.copy(evaluations = current.evaluations.map { it.copy(evaluatorVersion = 1) })

        val judged = older.reevaluated()

        assertEquals(current.evaluations, judged.evaluations)
        assertEquals(record, judged.record, "the record is untouched")
        assertTrue(judged.isCurrent)
    }

    @Test
    fun `an aborted run is judged only on the bars the live run committed`() {
        val aborted = HistoryFixtures.record("r2", segments, Fixtures.slowConfig, status = RunStatus.ABORTED, reason = AbortReason.MIDI_DISCONNECTED)
        val stored = HistoryFixtures.stored(aborted, judged = 1).let { it.copy(evaluations = it.evaluations.map { result -> result.copy(evaluatorVersion = 1) }) }

        assertEquals(1, stored.judgedSegments)
        assertFalse(stored.isCurrent)
        assertEquals(1, stored.reevaluated().evaluations.size)
        assertTrue(stored.reevaluated().isCurrent)
        assertTrue(HistoryFixtures.stored(aborted, judged = 0).isCurrent, "an abort during the first bar left nothing to judge")
    }

    @Test
    fun `a run cannot carry more judgements than segments`() {
        val current = HistoryFixtures.stored(record)

        assertFailsWith<IllegalArgumentException> { current.copy(evaluations = current.evaluations + current.evaluations.first()) }
    }
}
