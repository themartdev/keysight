package dev.simonmartineau.keysight.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.AbortReason
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The history implementation against a real Room database. Run from Android Studio. */
@RunWith(AndroidJUnit4::class)
class RoomRunHistoryTest {

    private lateinit var database: KeySightDatabase
    private lateinit var history: RoomRunHistory
    private var now = 1_000L

    private val measure = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        keySignature = KeySignature.C_MAJOR,
        measureCount = 1,
        notes = listOf(ScoreNote("n1", SpelledPitch(Step.C, octave = 4), Ticks.ZERO, Ticks.WHOLE)),
    )

    private val run = RunContext(listOf(Segment("exercise-1", measure)), RunConfig.DEFAULT.copy(segmentCount = 1))

    private fun record(id: String, sessionId: String, status: RunStatus = RunStatus.COMPLETED, reason: AbortReason? = null) = RunRecord(
        id = id,
        sessionId = sessionId,
        exerciseIds = listOf("exercise-1"),
        startedAtEpochMillis = 5,
        startedAtNanos = 10_000_000_000,
        status = status,
        abortReason = reason,
        config = run.config,
        score = run.score,
        events = listOf(MidiEvent.noteOn(14_000_000_000, Pitch.C4, 90), MidiEvent.noteOff(15_000_000_000, Pitch.C4)),
    )

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, KeySightDatabase::class.java).build()
        history = RoomRunHistory(database, wallClock = { now }, ids = { "session-1" })
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
    fun aCompletedRunIsStoredWithItsEvaluation() = runTest {
        val session = history.startSession()
        val record = record("run-1", session)
        val evaluation = PerformanceEvaluator.evaluate(record.score, record.events, run.timeline, record.startedAtNanos)

        history.record(record, evaluation)

        val dao = database.attemptDao()
        assertEquals(record, dao.byId("run-1")!!.toRecord(dao.midiEventsFor("run-1")))
        assertEquals(evaluation, dao.latestEvaluationFor("run-1")!!.toResult())
    }

    @Test
    fun anAbortedRunIsStoredWithoutAnEvaluation() = runTest {
        val session = history.startSession()

        history.record(record("run-2", session, RunStatus.ABORTED, AbortReason.BACKGROUNDED), evaluation = null)

        val dao = database.attemptDao()
        val stored = dao.byId("run-2")
        assertNotNull(stored)
        assertEquals(AbortReason.BACKGROUNDED, stored!!.abortReason)
        assertNull(dao.latestEvaluationFor("run-2"))
        assertEquals(2, dao.midiEventsFor("run-2").size)
    }
}
