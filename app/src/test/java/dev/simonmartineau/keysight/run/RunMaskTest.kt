package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.notation.Glyph
import dev.simonmartineau.keysight.notation.GlyphElement
import dev.simonmartineau.keysight.notation.Mask
import dev.simonmartineau.keysight.notation.Role
import dev.simonmartineau.keysight.notation.ScoreLayoutEngine
import dev.simonmartineau.keysight.notation.TickRange
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Staff
import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The mask of a three-segment run, at every beat that matters, for each preset. */
class RunMaskTest {

    private val run = Fixtures.run(Fixtures.cdef, Fixtures.gfed, Fixtures.cdef)
    private val timeline = run.timeline
    private val measure = Ticks.quarters(4)

    private fun segment(k: Int) = TickRange(measure * k, measure * (k + 1))

    private fun hiddenSegments(policy: VisibilityPolicy, beat: Double, lastSegment: Int = 3): List<Int> =
        (1..3).filter { runMask(timeline, policy, beat, lastSegment).hidden.contains(segment(it)) }

    @Test
    fun `flash with one beat of lookahead reveals each bar a beat early and hides it as it starts`() {
        val policy = VisibilityPolicy.flash(1.0)

        assertEquals(listOf(1, 2, 3), hiddenSegments(policy, 0.0))
        assertEquals(listOf(1, 2, 3), hiddenSegments(policy, 2.999))
        assertEquals(listOf(2, 3), hiddenSegments(policy, 3.0))
        assertEquals(listOf(1, 2, 3), hiddenSegments(policy, 4.0))
        assertEquals(listOf(1, 3), hiddenSegments(policy, 7.0))
        assertEquals(listOf(2, 3), hiddenSegments(policy, 8.0))
        assertEquals(listOf(3), hiddenSegments(policy, 12.0))
        assertEquals(emptyList(), hiddenSegments(policy, 16.0))
    }

    @Test
    fun `read ahead hides only the bar being played`() {
        val policy = VisibilityPolicy.READ_AHEAD

        assertEquals(emptyList(), hiddenSegments(policy, 0.0))
        assertEquals(listOf(1), hiddenSegments(policy, 4.0))
        assertEquals(listOf(2), hiddenSegments(policy, 11.999))
        assertEquals(listOf(3), hiddenSegments(policy, 12.0))
        assertEquals(emptyList(), hiddenSegments(policy, 16.0))
    }

    @Test
    fun `open score hides nothing`() {
        listOf(-1.0, 0.0, 4.0, 9.5, 16.0, 17.0).forEach { beat ->
            assertEquals(Mask.NONE, runMask(timeline, VisibilityPolicy.OPEN_SCORE, beat), "beat $beat")
        }
    }

    @Test
    fun `segment 0 is never hidden so the count-in reads as a measure of rest`() {
        val layout = ScoreLayoutEngine.layoutSystem(run.score, 0, null, showTimeSignature = true)
        val rest = layout.elements.single { it.role == Role.REST }
        assertNull(rest.ticks, "a whole-measure rest carries no tick, so no mask can hide it")

        listOf(VisibilityPolicy.flash(0.25), VisibilityPolicy.READ_AHEAD).forEach { policy ->
            listOf(-1.0, 0.0, 2.0, 4.0).forEach { beat ->
                assertTrue(!runMask(timeline, policy, beat).hides(rest), "$policy at $beat")
            }
        }
    }

    @Test
    fun `a stopped run hides the segments it will not reach`() {
        assertEquals(listOf(3), hiddenSegments(VisibilityPolicy.OPEN_SCORE, 6.0, lastSegment = 2))
        assertEquals(listOf(2, 3), hiddenSegments(VisibilityPolicy.READ_AHEAD, 3.0, lastSegment = 1))
        assertEquals(listOf(1, 2, 3), hiddenSegments(VisibilityPolicy.READ_AHEAD, 4.0, lastSegment = 1))
        assertFailsWith<IllegalArgumentException> { runMask(timeline, VisibilityPolicy.OPEN_SCORE, 0.0, lastSegment = 0) }
        assertFailsWith<IllegalArgumentException> { runMask(timeline, VisibilityPolicy.OPEN_SCORE, 0.0, lastSegment = 4) }
    }

    @Test
    fun `before the start a bounded lookahead hides everything and an unbounded one shows everything`() {
        assertEquals(listOf(1, 2, 3), (1..3).filter { runMaskBeforeStart(timeline, VisibilityPolicy.flash(4.0)).hidden.contains(segment(it)) })
        assertEquals(Mask.NONE, runMaskBeforeStart(timeline, VisibilityPolicy.READ_AHEAD))
        assertEquals(Mask.NONE, runMaskBeforeStart(timeline, VisibilityPolicy.OPEN_SCORE))
    }

    @Test
    fun `the mask hides exactly the notes of the hidden bars on the engraved page`() {
        val page = ScoreLayoutEngine.layoutPage(run.score, null)
        val elements = page.systems.single().layout.elements
        val mask = runMask(timeline, VisibilityPolicy.READ_AHEAD, beat = 9.0)

        val hidden = elements.filter(mask::hides).mapNotNull { it.noteId }.toSet()
        assertEquals(setOf("2:n1", "2:n2", "2:n3", "2:n4"), hidden)
    }

    /** A rest inside a bar tells which beat is silent, so it is hidden with the bar; a staff silent through the bar keeps its whole rest. */
    @Test
    fun `a hidden bar hides the rests inside it and keeps a whole-measure rest`() {
        val withRest = Fixtures.oneMeasure(
            ScoreNote("n1", Fixtures.C4, Ticks.ZERO, Ticks.QUARTER),
            ScoreNote("n2", Fixtures.E4, Ticks.HALF, Ticks.HALF),
        ).copy(staves = listOf(Staff(Clef.TREBLE), Staff(Clef.BASS)))
        val run = Fixtures.run(withRest, withRest)
        val layout = ScoreLayoutEngine.layoutSystem(run.score, 0, null, showTimeSignature = true)
        val rests = layout.elements.filterIsInstance<GlyphElement>().filter { it.role == Role.REST }
        val inner = rests.filter { it.glyph == Glyph.REST_QUARTER }
        val whole = rests.filter { it.glyph == Glyph.REST_WHOLE }
        assertEquals(2, inner.size, "a quarter rest in each performed bar")
        assertEquals(4, whole.size, "the count-in on both staves and the resting bass staff of each bar")

        val flash = VisibilityPolicy.flash(1.0)
        // Bar 1 is being played and bar 2 is one beat ahead, inside the lookahead.
        val playingBarOne = runMask(run.timeline, flash, beat = 7.5)
        assertTrue(playingBarOne.hides(inner[0]), "the rest of the bar being played is hidden with its notes")
        assertTrue(!playingBarOne.hides(inner[1]), "the next bar's rest shows with the next bar")
        whole.forEach { assertTrue(!playingBarOne.hides(it), "a whole-measure rest stays") }
        assertTrue(!runMask(run.timeline, VisibilityPolicy.OPEN_SCORE, beat = 5.0).hides(inner[0]))
    }
}
