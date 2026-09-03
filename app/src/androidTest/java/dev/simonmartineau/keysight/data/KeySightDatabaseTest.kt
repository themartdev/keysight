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
 * Smoke test for the practice-history schema, on a device: a run and its raw MIDI go in
 * together and come back unchanged, evaluations are keyed by evaluator version, and deleting a
 * session takes everything recorded in it along.
 */
@RunWith(AndroidJUnit4::class)
class KeySightDatabaseTest {

    private lateinit var database: KeySightDatabase

    private val measure = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        keySignature = KeySignature.C_MAJOR,
        measureCount = 1,
        notes = listOf(ScoreNote("n1", SpelledPitch(Step.C, octave = 4), Ticks.ZERO, Ticks.WHOLE)),
    )

    private val run = RunContext(listOf(Segment("exercise-1", measure)), RunConfig.DEFAULT.copy(segmentCount = 1))

    private val record = RunRecord(
        id = "run-1",
        sessionId = "session-1",
        exerciseIds = listOf("exercise-1"),
        startedAtEpochMillis = 1_700_000_000_000,
        startedAtNanos = 10_000_000_000,
        status = RunStatus.COMPLETED,
        abortReason = null,
        config = run.config,
        score = run.score,
        events = listOf(
            MidiEvent.noteOn(14_000_000_000, Pitch.C4, 90),
            MidiEvent.noteOff(15_000_000_000, Pitch.C4),
        ),
    )

    private fun evaluate() = PerformanceEvaluator.evaluate(record.score, record.events, run.timeline, record.startedAtNanos)

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
    fun aRunComesBackWithItsRawMidi() = runTest {
        val dao = database.attemptDao()

        dao.insertAttemptWithEvents(record.toEntity(), record.toMidiEventEntities())

        val stored = dao.byId("run-1")!!.toRecord(dao.midiEventsFor("run-1"))
        assertEquals(record, stored)
    }

    @Test
    fun evaluationsAreKeyedByEvaluatorVersion() = runTest {
        val dao = database.attemptDao()
        dao.insertAttemptWithEvents(record.toEntity(), record.toMidiEventEntities())
        val evaluation = evaluate()

        dao.upsertEvaluation(evaluation.toEntity("run-1", evaluatedAtEpochMillis = 1))
        dao.upsertEvaluation(evaluation.copy(evaluatorVersion = evaluation.evaluatorVersion + 1).toEntity("run-1", evaluatedAtEpochMillis = 2))

        assertEquals(2, dao.evaluationsFor("run-1").size)
        assertEquals(evaluation.evaluatorVersion + 1, dao.latestEvaluationFor("run-1")!!.evaluatorVersion)
        assertEquals(evaluation, dao.evaluationsFor("run-1").first().toResult())
    }

    @Test
    fun deletingASessionCascades() = runTest {
        val dao = database.attemptDao()
        dao.insertAttemptWithEvents(record.toEntity(), record.toMidiEventEntities())
        dao.upsertEvaluation(evaluate().toEntity("run-1", evaluatedAtEpochMillis = 1))

        database.sessionDao().delete("session-1")

        assertNull(dao.byId("run-1"))
        assertEquals(emptyList<Any>(), dao.midiEventsFor("run-1"))
        assertEquals(emptyList<Any>(), dao.evaluationsFor("run-1"))
    }
}
