package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.SegmentOrigin
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Staff
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class RunDigestTest {

    private val zone: ZoneId = ZoneId.of("America/Toronto")
    private val today: LocalDate = LocalDate.of(2026, 9, 3)

    private fun digest(id: String, startedAt: Long, bars: Int, sessionId: String = "s1") = RunDigest(
        id = id,
        sessionId = sessionId,
        startedAtEpochMillis = startedAt,
        config = RunConfig.DEFAULT,
        keySignature = KeySignature.C_MAJOR,
        hands = Hands.RIGHT,
        barCount = bars,
        evaluations = emptyList(),
    )

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `bars are bucketed by the player's calendar day, oldest first, a missed day counting zero`() {
        val runs = listOf(
            digest("a", at(today.minusDays(2), 23, 59), 4),
            digest("b", at(today.minusDays(1), 0, 1), 8),
            digest("c", at(today, 12), 2),
            digest("d", at(today, 18), 3),
            digest("old", at(today.minusDays(3), 12), 100),
        )

        val days = barsPerDay(runs, days = 3, today = today, zone = zone)

        assertEquals(
            listOf(DayCount(today.minusDays(2), 4), DayCount(today.minusDays(1), 8), DayCount(today, 5)),
            days,
        )
    }

    @Test
    fun `the window opens at local midnight, days minus one days ago`() {
        assertEquals(at(today.minusDays(6), 0), windowStartMillis(7, today, zone))
        assertEquals(at(today, 0), windowStartMillis(1, today, zone))
    }

    @Test
    fun `sessions keep their order and get their runs in the order played`() {
        val newer = SessionRecord("s2", 2_000L, null)
        val older = SessionRecord("s1", 1_000L, 1_500L)
        val empty = SessionRecord("s0", 500L, 600L)
        val runs = listOf(digest("b", 1_200L, 4), digest("a", 1_100L, 4), digest("c", 2_100L, 8, sessionId = "s2"))

        val digests = sessionDigests(listOf(newer, older, empty), runs)

        assertEquals(listOf("s2", "s1", "s0"), digests.map { it.session.id })
        assertEquals(listOf("c"), digests[0].runs.map { it.id })
        assertEquals(listOf("a", "b"), digests[1].runs.map { it.id })
        assertEquals(8, digests[1].barCount)
        assertEquals(0, digests[2].runCount)
    }

    @Test
    fun `hands are read off the staves`() {
        assertEquals(Hands.RIGHT, handsOf(Fixtures.cdef))
        assertEquals(Hands.LEFT, handsOf(Fixtures.cdef.copy(staves = listOf(Staff(Clef.BASS)))))
        assertEquals(Hands.BOTH, handsOf(Fixtures.cdef.copy(staves = listOf(Staff(Clef.TREBLE), Staff(Clef.BASS)))))
    }

    @Test
    fun `a stored run digests to its bars and its judgements`() {
        val bar = Segment(SegmentOrigin.Bundled("cdef"), Fixtures.cdef)
        val stored = HistoryFixtures.stored(HistoryFixtures.record("r1", listOf(bar, bar, bar), RunConfig.DEFAULT), judged = 2)

        val digest = stored.toDigest()

        assertEquals("r1", digest.id)
        assertEquals(HistoryFixtures.session.id, digest.sessionId)
        assertEquals(3, digest.barCount)
        assertEquals(2, digest.evaluations.size)
        assertEquals(stored.record.config, digest.config)
        assertEquals(PooledCounts.of(stored.evaluations), digest.pooled)
    }
}
