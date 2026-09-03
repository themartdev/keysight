package dev.simonmartineau.keysight.ui.home

import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.history.PooledCounts
import dev.simonmartineau.keysight.history.RunDigest
import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.settings.ContentConfig
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeDataTest {

    private val today = LocalDate.of(2026, 9, 3)
    private val zone = ZoneOffset.UTC

    private fun digest(id: String, daysAgo: Long, bars: Int, sessionId: String) = RunDigest(
        id = id,
        sessionId = sessionId,
        startedAtEpochMillis = today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        config = RunConfig.DEFAULT,
        keySignature = KeySignature.C_MAJOR,
        hands = Hands.RIGHT,
        barCount = bars,
        evaluations = emptyList(),
    )

    private fun session(id: String, daysAgo: Long) = SessionRecord(id, today.minusDays(daysAgo).atTime(11, 0).atZone(zone).toInstant().toEpochMilli(), null)

    @Test
    fun `no history is said, not shown as zeros`() {
        val dashboard = dashboardOf(emptyList(), emptyList(), today, zone)

        assertFalse(dashboard.hasHistory)
        assertEquals(PooledCounts.NONE, dashboard.week)
        assertTrue(dashboard.recent.isEmpty())
    }

    @Test
    fun `the week is the last seven days and the recent sessions are the newest three with a run`() {
        val sessions = (0L..4L).map { session("s$it", it) } + session("empty", 0)
        val runs = listOf(
            digest("a", 0, 4, "s0"),
            digest("b", 6, 8, "s1"),
            digest("c", 7, 100, "s2"),
            digest("d", 3, 2, "s3"),
            digest("e", 4, 1, "s4"),
        )

        val dashboard = dashboardOf(sessions, runs, today, zone)

        assertTrue(dashboard.hasHistory)
        assertEquals(15, dashboard.weekBars, "the run seven days ago is outside the window")
        assertEquals(DASHBOARD_DAYS, dashboard.days.size)
        assertEquals(listOf(8, 0, 1, 2, 0, 0, 4), dashboard.days.map { it.bars })
        assertEquals(listOf("s0", "s1", "s2"), dashboard.recent.map { it.session.id })
    }

    @Test
    fun `the words`() {
        val config = RunConfig.DEFAULT.copy(mode = VisibilityMode.READ_AHEAD, tempoBpm = 72.0, segmentCount = 8, metronome = MetronomeMode.COUNT_IN_ONLY)
        val content = ContentConfig(KeySignature.C_MAJOR, Hands.RIGHT)

        assertEquals("Read ahead · C major · right hand", resumeLead(config, content))
        assertEquals("8 bars at 72 bpm, count-in only. Up to thirds, quarter notes.", resumeBody(config, MusicalLevel.DEFAULT))
        assertEquals("Open run at 72 bpm, click throughout. Up to thirds, quarter notes.", resumeBody(config.copy(segmentCount = null, metronome = MetronomeMode.THROUGHOUT), MusicalLevel.DEFAULT))
        assertEquals("Good morning", greeting(9))
        assertEquals("Good afternoon", greeting(12))
        assertEquals("Good evening", greeting(18))
    }

    @Test
    fun `the quick starts are the other modes and the other hands`() {
        val config = RunConfig.DEFAULT.copy(mode = VisibilityMode.READ_AHEAD)

        assertEquals(
            listOf(QuickStart.Mode(VisibilityMode.FLASH), QuickStart.Mode(VisibilityMode.OPEN_SCORE), QuickStart.WithHands(Hands.BOTH)),
            quickStarts(config, ContentConfig(KeySignature.C_MAJOR, Hands.RIGHT)),
        )
        assertEquals(QuickStart.WithHands(Hands.RIGHT), quickStarts(config, ContentConfig(KeySignature.C_MAJOR, Hands.BOTH)).last())
    }
}
