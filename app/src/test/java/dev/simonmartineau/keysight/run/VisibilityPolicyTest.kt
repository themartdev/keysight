package dev.simonmartineau.keysight.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Every preset against one segment spanning beats 8 to 12. */
class VisibilityPolicyTest {

    private val start = 8.0
    private val end = 12.0

    private fun visibility(policy: VisibilityPolicy, vararg beats: Double): List<Boolean> =
        beats.map { policy.isVisible(it, start, end) }

    @Test
    fun `flash shows the notes for the lookahead, hides them as the bar starts and brings them back after`() {
        val policy = VisibilityPolicy.flash(2.0)

        assertEquals(
            listOf(false, false, true, true, false, false, false, true, true),
            visibility(policy, 0.0, 5.999, 6.0, 7.999, 8.0, 10.0, 11.999, 12.0, 100.0),
        )
    }

    @Test
    fun `the notes disappear exactly on the beat the bar starts, whatever the lookahead`() {
        RunConfig.LOOKAHEAD_LADDER_BEATS.forEach { lookahead ->
            val policy = VisibilityPolicy.flash(lookahead)
            assertEquals(listOf(true, false), visibility(policy, start - 1e-9, start), "lookahead $lookahead")
            assertEquals(listOf(false, true), visibility(policy, start - lookahead - 1e-9, start - lookahead), "lookahead $lookahead")
        }
    }

    @Test
    fun `read ahead shows everything except the bar being played`() {
        val policy = VisibilityPolicy.READ_AHEAD

        assertEquals(
            listOf(true, true, true, false, false, false, true, true),
            visibility(policy, -100.0, 0.0, 7.999, 8.0, 10.0, 11.999, 12.0, 100.0),
        )
    }

    @Test
    fun `open score shows the notes at every beat`() {
        val policy = VisibilityPolicy.OPEN_SCORE

        assertEquals(List(6) { true }, visibility(policy, -100.0, 0.0, 8.0, 10.0, 12.0, 100.0))
    }

    @Test
    fun `the presets are the plan's table`() {
        assertEquals(VisibilityPolicy(1.5, hideWhilePlaying = true, showAfter = true), VisibilityPolicy.flash(1.5))
        assertEquals(VisibilityPolicy(null, hideWhilePlaying = true, showAfter = true), VisibilityPolicy.READ_AHEAD)
        assertEquals(VisibilityPolicy(null, hideWhilePlaying = false, showAfter = true), VisibilityPolicy.OPEN_SCORE)
        assertEquals(VisibilityPolicy.flash(0.25), RunConfig.DEFAULT.copy(lookaheadBeats = 0.25).policy)
        assertEquals(VisibilityPolicy.READ_AHEAD, RunConfig.DEFAULT.copy(mode = VisibilityMode.READ_AHEAD).policy)
        assertEquals(VisibilityPolicy.OPEN_SCORE, RunConfig.DEFAULT.copy(mode = VisibilityMode.OPEN_SCORE).policy)
    }

    @Test
    fun `a lookahead must be positive`() {
        assertFailsWith<IllegalArgumentException> { VisibilityPolicy.flash(0.0) }
        assertFailsWith<IllegalArgumentException> { RunConfig.DEFAULT.copy(lookaheadBeats = -1.0) }
    }

    @Test
    fun `the default configuration is on the ladder and offers a whole bar`() {
        assertEquals(4.0, RunConfig.DEFAULT.lookaheadBeats)
        assertEquals(listOf(4.0, 3.0, 2.0, 1.5, 1.0, 0.75, 0.5, 0.25), RunConfig.LOOKAHEAD_LADDER_BEATS)
    }
}
