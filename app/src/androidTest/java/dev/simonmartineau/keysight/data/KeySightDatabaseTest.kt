package dev.simonmartineau.keysight.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.simonmartineau.keysight.data.entity.AttemptEntity
import dev.simonmartineau.keysight.data.entity.MidiEventEntity
import dev.simonmartineau.keysight.data.entity.SessionEntity
import dev.simonmartineau.keysight.midi.MidiEventType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the practice history schema: an attempt and its raw MIDI go in together and
 * come back out unchanged, which is the property re-evaluation depends on.
 */
@RunWith(AndroidJUnit4::class)
class KeySightDatabaseTest {

    private lateinit var database: KeySightDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, KeySightDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun anAttemptKeepsItsRawMidi() = runTest {
        val sessionDao = database.sessionDao()
        val attemptDao = database.attemptDao()

        sessionDao.insert(SessionEntity("session-1", startedAtEpochMillis = 0, endedAtEpochMillis = null))
        attemptDao.insertAttemptWithEvents(
            attempt = AttemptEntity(
                id = "attempt-1",
                sessionId = "session-1",
                exerciseId = "exercise-1",
                startedAtEpochMillis = 0,
                tempoBpm = 72.0,
                previewDurationBeats = 4.0,
                configSnapshot = "{}",
            ),
            events = listOf(
                MidiEventEntity(
                    attemptId = "attempt-1",
                    timestampNanos = 1_000,
                    offsetFromAttemptStartNanos = 500,
                    type = MidiEventType.NOTE_ON,
                    channel = 0,
                    midiNoteNumber = 60,
                    velocity = 90,
                ),
            ),
        )

        val stored = attemptDao.midiEventsFor("attempt-1")

        assertEquals(1, stored.size)
        assertEquals(MidiEventType.NOTE_ON, stored.single().type)
        assertEquals(60, stored.single().midiNoteNumber)
    }
}
