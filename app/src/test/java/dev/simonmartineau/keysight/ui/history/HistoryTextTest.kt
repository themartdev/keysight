package dev.simonmartineau.keysight.ui.history

import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.history.SessionLevel
import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.history.SessionSummary
import dev.simonmartineau.keysight.run.AbortReason
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryTextTest {

    private val zone = ZoneId.of("America/Toronto")
    private val session = SessionRecord("s1", startedAtEpochMillis = 1_788_400_000_000L, endedAtEpochMillis = null)

    private fun summary(
        correct: Int = 0,
        expected: Int = 0,
        onTime: Int = 0,
        matched: Int = 0,
        start: SessionLevel? = null,
        end: SessionLevel? = start,
    ) = SessionSummary(session, emptyList(), correct, expected, onTime, matched, start, end, emptyList(), emptyList())

    @Test
    fun `dates and times are in the player's zone`() {
        assertEquals("Wed 2 Sep 2026, 21:46", dateTimeLabel(session.startedAtEpochMillis, zone, Locale.ENGLISH))
        assertEquals("21:46", timeLabel(session.startedAtEpochMillis, zone, Locale.ENGLISH))
        assertEquals("Thu 3 Sep 2026, 01:46", dateTimeLabel(session.startedAtEpochMillis, ZoneId.of("UTC"), Locale.ENGLISH))
        assertEquals("This session", sessionTitle(session, "s1", zone, Locale.ENGLISH))
        assertEquals("Wed 2 Sep 2026, 21:46", sessionTitle(session, "other", zone, Locale.ENGLISH))
    }

    @Test
    fun `the score line pools pitch and rhythm, rhythm only when something matched`() {
        assertEquals("Pitch 75%   Rhythm 50%", sessionScoreLine(summary(correct = 3, expected = 4, onTime = 1, matched = 2)))
        assertEquals("Pitch 100%", sessionScoreLine(summary(correct = 4, expected = 4)))
        assertNull(sessionScoreLine(summary()))
    }

    @Test
    fun `the counts line`() {
        assertEquals("No runs yet", sessionCountsLine(summary()))
        assertEquals("1 run", runsLabel(1))
        assertEquals("3 runs", runsLabel(3))
    }

    @Test
    fun `the level is one line when it held and two when it moved`() {
        val thirds = SessionLevel(4.0, MusicalLevel.DEFAULT)
        val fourths = SessionLevel(3.0, MusicalLevel.DEFAULT.copy(maxInterval = 3))

        assertEquals(listOf("Level: 4 beats ahead, up to thirds, five notes, quarter notes, no rests, no accidentals."), sessionLevelLines(summary(start = thirds)))
        assertEquals(
            listOf("Started: 4 beats ahead, up to thirds, five notes, quarter notes, no rests, no accidentals.", "Ended: 3 beats ahead, up to fourths, five notes, quarter notes, no rests, no accidentals."),
            sessionLevelLines(summary(start = thirds, end = fourths)),
        )
        assertEquals(emptyList(), sessionLevelLines(summary()))
        assertEquals(listOf("Level: 3 beats ahead."), sessionLevelLines(summary(start = SessionLevel(null, null), end = SessionLevel(3.0, null))))
    }

    @Test
    fun `why a run stopped`() {
        assertEquals("Stopped early: the keyboard disconnected.", stoppedLine(AbortReason.MIDI_DISCONNECTED))
    }
}
