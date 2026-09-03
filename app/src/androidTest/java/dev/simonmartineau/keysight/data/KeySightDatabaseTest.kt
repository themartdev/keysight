package dev.simonmartineau.keysight.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.simonmartineau.keysight.attempt.AttemptRecord
import dev.simonmartineau.keysight.attempt.AttemptStatus
import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.data.entity.SessionEntity
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import dev.simonmartineau.keysight.timing.AttemptTimeline
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the practice-history schema, on a device: an attempt and its raw MIDI go in
 * together and come back unchanged, evaluations are keyed by evaluator version, and deleting a
 * session takes everything recorded in it along.
 */
@RunWith(AndroidJUnit4::class)
class KeySightDatabaseTest {

    private lateinit var database: KeySightDatabase

    private val score = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        clef = Clef.TREBLE,
        keySignature = KeySignature.C_MAJOR,
        measureCount = 1,
        notes = listOf(ScoreNote("n1", SpelledPitch(Step.C, octave = 4), Ticks.ZERO, Ticks.WHOLE)),
    )

    private val record = AttemptRecord(
        id = "attempt-1",
        sessionId = "session-1",
        exerciseId = "exercise-1",
        startedAtEpochMillis = 1_700_000_000_000,
        startedAtNanos = 10_000_000_000,
        status = AttemptStatus.COMPLETED,
        abortReason = null,
        config = FlashConfig.DEFAULT,
        score = score,
        events = listOf(
            MidiEvent.noteOn(14_000_000_000, Pitch.C4, 90),
            MidiEvent.noteOff(15_000_000_000, Pitch.C4),
        ),
    )

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
    fun anAttemptComesBackWithItsRawMidi() = runTest {
        val dao = database.attemptDao()

        dao.insertAttemptWithEvents(record.toEntity(), record.toMidiEventEntities())

        val stored = dao.byId("attempt-1")!!.toRecord(dao.midiEventsFor("attempt-1"))
        assertEquals(record, stored)
    }

    @Test
    fun evaluationsAreKeyedByEvaluatorVersion() = runTest {
        val dao = database.attemptDao()
        dao.insertAttemptWithEvents(record.toEntity(), record.toMidiEventEntities())
        val timeline = AttemptTimeline.of(record.config, record.score)
        val evaluation = PerformanceEvaluator.evaluate(record.score, record.events, timeline, record.startedAtNanos)

        dao.upsertEvaluation(evaluation.toEntity("attempt-1", evaluatedAtEpochMillis = 1))
        dao.upsertEvaluation(evaluation.copy(evaluatorVersion = 2).toEntity("attempt-1", evaluatedAtEpochMillis = 2))

        assertEquals(2, dao.evaluationsFor("attempt-1").size)
        assertEquals(2, dao.latestEvaluationFor("attempt-1")!!.evaluatorVersion)
        assertEquals(evaluation, dao.evaluationsFor("attempt-1").first().toResult())
    }

    @Test
    fun deletingASessionCascades() = runTest {
        val dao = database.attemptDao()
        dao.insertAttemptWithEvents(record.toEntity(), record.toMidiEventEntities())
        dao.upsertEvaluation(
            PerformanceEvaluator.evaluate(record.score, record.events, AttemptTimeline.of(record.config, record.score), record.startedAtNanos)
                .toEntity("attempt-1", evaluatedAtEpochMillis = 1),
        )

        database.sessionDao().delete("session-1")

        assertNull(dao.byId("attempt-1"))
        assertEquals(emptyList<Any>(), dao.midiEventsFor("attempt-1"))
        assertEquals(emptyList<Any>(), dao.evaluationsFor("attempt-1"))
    }
}
