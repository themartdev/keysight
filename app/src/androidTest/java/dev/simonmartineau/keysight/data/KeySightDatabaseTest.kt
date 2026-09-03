package dev.simonmartineau.keysight.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.simonmartineau.keysight.data.entity.SessionEntity
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunContext
import dev.simonmartineau.keysight.run.RunRecord
import dev.simonmartineau.keysight.run.RunStatus
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.SegmentOrigin
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the practice-history schema, on a device: a run, its segments and its raw
 * MIDI go in together and come back unchanged, evaluations are keyed by segment and evaluator
 * version, and deleting a session takes everything recorded in it along.
 */
@RunWith(AndroidJUnit4::class)
class KeySightDatabaseTest {

    private lateinit var database: KeySightDatabase

    private fun measure(id: String, step: Step) = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        keySignature = KeySignature.C_MAJOR,
        measureCount = 1,
        notes = listOf(ScoreNote(id, SpelledPitch(step, octave = 4), Ticks.ZERO, Ticks.WHOLE)),
    )

    private val segments = listOf(Segment(SegmentOrigin.Bundled("exercise-1"), measure("n1", Step.C)), Segment(SegmentOrigin.Bundled("exercise-2"), measure("n1", Step.D)))

    private val run = RunContext(segments, RunConfig.DEFAULT.copy(segmentCount = 2))

    private val record = RunRecord(
        id = "run-1",
        sessionId = "session-1",
        startedAtEpochMillis = 1_700_000_000_000,
        startedAtNanos = 10_000_000_000,
        status = RunStatus.COMPLETED,
        abortReason = null,
        config = run.config,
        segments = segments,
        events = listOf(
            MidiEvent.noteOn(14_000_000_000, Pitch.C4, 90),
            MidiEvent.noteOff(15_000_000_000, Pitch.C4),
        ),
    )

    private fun evaluate() = PerformanceEvaluator.evaluate(record.score, run.timeline, record.startedAtNanos, record.events)

    @Before
    fun createDatabase() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, KeySightDatabase::class.java).build()
        database.sessionDao().insert(SessionEntity("session-1", startedAtEpochMillis = 0, endedAtEpochMillis = null))
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aRunComesBackWithItsSegmentsAndRawMidi() = runTest {
        val dao = database.runDao()

        dao.insertRunWithSegmentsAndEvents(record.toEntity(), record.toSegmentEntities(), record.toMidiEventEntities())

        val stored = dao.byId("run-1")!!.toRecord(dao.segmentsFor("run-1"), dao.midiEventsFor("run-1"))
        assertEquals(record, stored)
    }

    @Test
    fun evaluationsAreKeyedBySegmentAndEvaluatorVersion() = runTest {
        val dao = database.runDao()
        dao.insertRunWithSegmentsAndEvents(record.toEntity(), record.toSegmentEntities(), record.toMidiEventEntities())
        val evaluation = evaluate()
        val first = evaluation.segments[0]
        val second = evaluation.segments[1]

        dao.upsertEvaluations(listOf(first.toEntity("run-1:1", 1), second.toEntity("run-1:2", 1)))
        dao.upsertEvaluations(listOf(first.copy(evaluatorVersion = first.evaluatorVersion + 1).toEntity("run-1:1", 2)))

        assertEquals(2, dao.evaluationsFor("run-1:1").size)
        assertEquals(first.evaluatorVersion + 1, dao.latestEvaluationFor("run-1:1")!!.evaluatorVersion)
        assertEquals(first, dao.evaluationsFor("run-1:1").first().toResult())
        assertEquals(listOf(first.evaluatorVersion + 1, second.evaluatorVersion), dao.latestEvaluationsForRun("run-1").map { it.evaluatorVersion })
        assertEquals(second, dao.latestEvaluationsForRun("run-1")[1].toResult())
    }

    @Test
    fun deletingASessionCascades() = runTest {
        val dao = database.runDao()
        dao.insertRunWithSegmentsAndEvents(record.toEntity(), record.toSegmentEntities(), record.toMidiEventEntities())
        dao.upsertEvaluations(listOf(evaluate().segments[0].toEntity("run-1:1", 1)))

        database.sessionDao().delete("session-1")

        assertNull(dao.byId("run-1"))
        assertEquals(emptyList<Any>(), dao.segmentsFor("run-1"))
        assertEquals(emptyList<Any>(), dao.midiEventsFor("run-1"))
        assertEquals(emptyList<Any>(), dao.evaluationsFor("run-1:1"))
    }
}
