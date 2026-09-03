package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryReaderTest {

    private val segments = listOf(Fixtures.segment("m1", Fixtures.cdef), Fixtures.segment("m2", Fixtures.gfed))
    private val current = HistoryFixtures.stored(HistoryFixtures.record("r1", segments, Fixtures.slowConfig))
    private val stale = HistoryFixtures.stored(HistoryFixtures.record("r2", segments, Fixtures.slowConfig, startedAtEpochMillis = 2_000_000L))
        .let { it.copy(evaluations = it.evaluations.map { result -> result.copy(evaluatorVersion = PerformanceEvaluator.EVALUATOR_VERSION - 1) }) }
    private val other = SessionRecord("s2", startedAtEpochMillis = 5_000_000L, endedAtEpochMillis = 6_000_000L)

    @Test
    fun `sessions come newest first`() = runTest {
        val reader = HistoryReader(InMemoryHistoryStore(listOf(HistoryFixtures.session, other)))

        assertEquals(listOf(other, HistoryFixtures.session), reader.sessions().first())
    }

    @Test
    fun `a stale run is re-evaluated as it is read, stored under the current version, and the old judgement kept`() = runTest {
        val store = InMemoryHistoryStore(listOf(HistoryFixtures.session), listOf(current, stale))
        val reader = HistoryReader(store)

        val runs = reader.runsOf(HistoryFixtures.session.id).first()

        assertEquals(listOf("r1", "r2"), runs.map { it.record.id })
        assertTrue(runs.all { it.isCurrent })
        assertEquals(current.evaluations.map { it.pitch }, runs[1].evaluations.map { it.pitch }, "the same performance gets the same judgement")
        assertEquals(1, store.stored.getValue("r2").size, "written once")
        assertEquals(PerformanceEvaluator.EVALUATOR_VERSION, store.stored.getValue("r2").single().first().evaluatorVersion)
        assertNull(store.stored["r1"], "a current run is not written")
        assertEquals(stale.record, store.run("r2")!!.record, "the raw MIDI and the record are untouched")

        assertTrue(reader.run("r2")!!.isCurrent)
        assertEquals(1, store.stored.getValue("r2").size, "a second read finds the current judgement")
    }

    @Test
    fun `the session summary follows the runs as they land`() = runTest {
        val store = InMemoryHistoryStore(listOf(HistoryFixtures.session), listOf(current))
        val reader = HistoryReader(store)

        assertEquals(1, reader.summaryOf(HistoryFixtures.session).first().runCount)
        store.add(stale)
        assertEquals(2, reader.summaryOf(HistoryFixtures.session).first().runCount)
    }

    @Test
    fun `a run that is not there is null`() = runTest {
        assertNull(HistoryReader(InMemoryHistoryStore()).run("nope"))
    }
}
