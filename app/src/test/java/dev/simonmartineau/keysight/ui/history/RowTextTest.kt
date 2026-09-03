package dev.simonmartineau.keysight.ui.history

import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.history.PooledCounts
import dev.simonmartineau.keysight.history.RunDigest
import dev.simonmartineau.keysight.history.SessionDigest
import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.ui.play.lastRunLine
import dev.simonmartineau.keysight.ui.play.lengthValue
import dev.simonmartineau.keysight.ui.play.lookaheadValue
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class RowTextTest {

    private val zone = ZoneOffset.UTC
    private val now = LocalDate.of(2026, 9, 3).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(daysAgo: Long, hour: Int) = LocalDate.of(2026, 9, 3).minusDays(daysAgo).atTime(hour, 42).atZone(zone).toInstant().toEpochMilli()

    private fun digest(config: RunConfig = RunConfig.DEFAULT, hands: Hands = Hands.RIGHT, startedAt: Long = at(0, 18)) =
        RunDigest("r", "s", startedAt, config, KeySignature(1), hands, 4, emptyList())

    @Test
    fun `when a session was, relative to now`() {
        assertEquals("Today 18:42", whenLabel(at(0, 18), now, zone, Locale.UK))
        assertEquals("Yesterday 09:42", whenLabel(at(1, 9), now, zone, Locale.UK))
        assertEquals("Monday 09:42", whenLabel(at(3, 9), now, zone, Locale.UK))
        assertEquals("27 Aug 2026", whenLabel(at(7, 9), now, zone, Locale.UK))
    }

    @Test
    fun `what a run and a session were`() {
        assertEquals("Flash 4 beats · G major · right hand", whatLabel(digest()))
        assertEquals("Read ahead · G major · both hands", whatLabel(digest(RunConfig.DEFAULT.copy(mode = VisibilityMode.READ_AHEAD), Hands.BOTH)))
        assertEquals("No runs", whatLabel(SessionDigest(SessionRecord("s", 0L, null), emptyList())))
    }

    @Test
    fun `the accuracy column, and nothing where nothing was judged`() {
        assertEquals("91 · 84", accuracyLabel(PooledCounts(91, 100, 84, 100)))
        assertEquals("50", accuracyLabel(PooledCounts(1, 2, 0, 0)))
        assertEquals("", accuracyLabel(PooledCounts.NONE))
    }

    @Test
    fun `the play screen's values`() {
        assertEquals("No run in this mode yet", lastRunLine(null, now))
        assertEquals("Last run: Today 18:42", lastRunLine(digest(), now, zone))
        assertEquals("8 bars", lengthValue(8))
        assertEquals("Open, until Stop", lengthValue(null))
        assertEquals("1 beat", lookaheadValue(1.0))
        assertEquals("1.5 beats", lookaheadValue(1.5))
    }
}
