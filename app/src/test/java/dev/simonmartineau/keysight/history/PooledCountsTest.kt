package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.SegmentOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PooledCountsTest {

    private val bar = Segment(SegmentOrigin.Bundled("cdef"), Fixtures.cdef)

    /** Two clean bars, eight of eight. */
    private val clean = HistoryFixtures.stored(HistoryFixtures.record("clean", listOf(bar, bar), RunConfig.DEFAULT))

    /** One bar with nothing played, none of four. */
    private val silent = HistoryFixtures.stored(HistoryFixtures.record("silent", listOf(bar), RunConfig.DEFAULT) { null })

    @Test
    fun `a pool is the sum over the sum, not a mean of percentages`() {
        val pooled = PooledCounts.of(clean.evaluations + silent.evaluations)

        assertEquals(8, pooled.correctCount)
        assertEquals(12, pooled.expectedCount)
        assertEquals(8.0 / 12.0, pooled.pitchAccuracy!!, 1e-9)
    }

    @Test
    fun `nothing judged is no accuracy, not zero`() {
        assertNull(PooledCounts.NONE.pitchAccuracy)
        assertNull(PooledCounts.NONE.rhythmAccuracy)
        assertNull(PooledCounts.of(silent.evaluations).rhythmAccuracy, "no note matched, so no timing was judged")
        assertEquals(0.0, PooledCounts.of(silent.evaluations).pitchAccuracy)
    }

    @Test
    fun `adding pools adds every count, and the session summary reads the same pool`() {
        val sum = PooledCounts.of(clean.evaluations) + PooledCounts.of(silent.evaluations)

        assertEquals(PooledCounts.of(clean.evaluations + silent.evaluations), sum)
        assertEquals(sum, summarise(HistoryFixtures.session, listOf(clean, silent)).pooled)
    }
}
