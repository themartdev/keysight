package dev.simonmartineau.keysight.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.simonmartineau.keysight.attempt.AbortReason
import dev.simonmartineau.keysight.attempt.AttemptRecord
import dev.simonmartineau.keysight.attempt.AttemptStatus
import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.midi.MidiEvent
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The history implementation against a real Room database. Run from Android Studio. */
@RunWith(AndroidJUnit4::class)
class RoomAttemptHistoryTest {

    private lateinit var database: KeySightDatabase
    private lateinit var history: RoomAttemptHistory
    private var now = 1_000L

    private val score = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        keySignature = KeySignature.C_MAJOR,
        measureCount = 1,
        notes = listOf(ScoreNote("n1", SpelledPitch(Step.C, octave = 4), Ticks.ZERO, Ticks.WHOLE)),
    )

    private fun record(id: String, sessionId: String, status: AttemptStatus = AttemptStatus.COMPLETED, reason: AbortReason? = null) = AttemptRecord(
        id = id,
        sessionId = sessionId,
        exerciseId = "exercise-1",
        startedAtEpochMillis = 5,
        startedAtNanos = 10_000_000_000,
        status = status,
        abortReason = reason,
        config = FlashConfig.DEFAULT,
        score = score,
        events = listOf(MidiEvent.noteOn(14_000_000_000, Pitch.C4, 90), MidiEvent.noteOff(15_000_000_000, Pitch.C4)),
    )

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, KeySightDatabase::class.java).build()
        history = RoomAttemptHistory(database, wallClock = { now }, ids = { "session-1" })
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun sessionsOpenAndClose() = runTest {
        val id = history.startSession()
        assertEquals("session-1", id)
        assertNull(database.sessionDao().byId(id)!!.endedAtEpochMillis)

        now = 2_000L
        history.endSession(id)

        assertEquals(2_000L, database.sessionDao().byId(id)!!.endedAtEpochMillis)
    }

    @Test
    fun aCompletedAttemptIsStoredWithItsEvaluation() = runTest {
        val session = history.startSession()
        val record = record("attempt-1", session)
        val evaluation = PerformanceEvaluator.evaluate(score, record.events, AttemptTimeline.of(record.config, score), record.startedAtNanos)

        history.record(record, evaluation)

        val dao = database.attemptDao()
        assertEquals(record, dao.byId("attempt-1")!!.toRecord(dao.midiEventsFor("attempt-1")))
        assertEquals(evaluation, dao.latestEvaluationFor("attempt-1")!!.toResult())
    }

    @Test
    fun anAbortedAttemptIsStoredWithoutAnEvaluation() = runTest {
        val session = history.startSession()

        history.record(record("attempt-2", session, AttemptStatus.ABORTED, AbortReason.BACKGROUNDED), evaluation = null)

        val dao = database.attemptDao()
        val stored = dao.byId("attempt-2")
        assertNotNull(stored)
        assertEquals(AbortReason.BACKGROUNDED, stored!!.abortReason)
        assertNull(dao.latestEvaluationFor("attempt-2"))
        assertEquals(2, dao.midiEventsFor("attempt-2").size)
    }
}
