package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Staff
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.transposed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RunScoreTest {

    @Test
    fun `segments become measures one to n after a resting measure zero`() {
        val score = runScore(listOf(Fixtures.cdef, Fixtures.gfed))

        assertEquals(3, score.measureCount)
        assertEquals(emptyList(), score.notesInMeasure(0))
        assertEquals(listOf("1:n1", "1:n2", "1:n3", "1:n4"), score.notesInMeasure(1).map { it.id })
        assertEquals(listOf("2:n1", "2:n2", "2:n3", "2:n4"), score.notesInMeasure(2).map { it.id })
        assertEquals(Ticks.quarters(4), score.notesInMeasure(1).first().onset)
        assertEquals(Ticks.quarters(11), score.notesInMeasure(2).last().onset)
        assertEquals(Fixtures.gfed.notes.map { it.spelling }, score.notesInMeasure(2).map { it.spelling })
        assertEquals(Fixtures.cdef.staves, score.staves)
    }

    @Test
    fun `segments must agree on meter, key and staves and be one measure`() {
        val inG = Fixtures.cdef.transposed(KeySignature(1))
        val bass = Fixtures.cdef.copy(staves = listOf(Staff(Clef.BASS)))
        val two = Fixtures.measures(2, ScoreNote("a", Fixtures.C4, Ticks.ZERO, Ticks.WHOLE))

        assertFailsWith<IllegalArgumentException> { runScore(emptyList()) }
        assertFailsWith<IllegalArgumentException> { runScore(listOf(Fixtures.cdef, inG)) }
        assertFailsWith<IllegalArgumentException> { runScore(listOf(Fixtures.cdef, bass)) }
        assertFailsWith<IllegalArgumentException> { runScore(listOf(Fixtures.cdef, two)) }
        assertFailsWith<IllegalArgumentException> { Fixtures.segment("x", two) }
        assertFailsWith<IllegalArgumentException> { SegmentOrigin.Bundled(" ") }
        assertFailsWith<IllegalArgumentException> { SegmentOrigin.Generated(0, 1L, ExerciseConfig.DEFAULT) }
    }

    @Test
    fun `the first measures of a run are what a stopped run performed`() {
        val score = runScore(listOf(Fixtures.cdef, Fixtures.gfed, Fixtures.cdef))

        val cut = score.firstMeasures(2)
        assertEquals(2, cut.measureCount)
        assertEquals(listOf("1:n1", "1:n2", "1:n3", "1:n4"), cut.notes.map { it.id })
        assertSame(score, score.firstMeasures(4))
        assertFailsWith<IllegalArgumentException> { score.firstMeasures(0) }
        assertFailsWith<IllegalArgumentException> { score.firstMeasures(5) }
    }

    @Test
    fun `a context derives its score and timeline from its segments and config`() {
        val context = Fixtures.run(Fixtures.cdef, Fixtures.gfed)

        assertEquals(3, context.timeline.segmentCount)
        assertEquals(3, context.score.measureCount)
        assertEquals(2, context.lastSegment)
        assertEquals(60.0, context.timeline.tempoBpm)
        assertEquals(false, context.timeline.metronomeThroughout)
        assertEquals(true, context.copy(config = context.config.copy(metronome = MetronomeMode.THROUGHOUT)).timeline.metronomeThroughout)

        val performed = context.performed(1)
        assertEquals(2, performed.score.measureCount)
        assertEquals(2, performed.timeline.segmentCount)
        assertEquals(context.score, context.performed(2).score)
        assertFailsWith<IllegalArgumentException> { context.performed(0) }
        assertFailsWith<IllegalArgumentException> { context.performed(3) }
    }

    @Test
    fun `a measure of a score stands on its own, its prefix stripped, its onsets from zero`() {
        val score = runScore(listOf(Fixtures.cdef, Fixtures.gfed))

        assertEquals(Fixtures.cdef, score.measureAsScore(1, idPrefix = "1:"))
        assertEquals(Fixtures.gfed, score.measureAsScore(2, idPrefix = "2:"))
        assertEquals(listOf("2:n1", "2:n2", "2:n3", "2:n4"), score.measureAsScore(2).notes.map { it.id })
        assertEquals(Fixtures.oneMeasure(), score.measureAsScore(0))
        assertEquals(Fixtures.cdef, Fixtures.cdef.measureAsScore(0))
        assertFailsWith<IllegalArgumentException> { score.measureAsScore(3) }
    }

    @Test
    fun `an open-ended context grows as it is extended and keeps its config`() {
        val open = RunContext(listOf(Fixtures.segment("a", Fixtures.cdef)), Fixtures.slowConfig.copy(segmentCount = null))
        assertEquals(true, open.timeline.openEnded)

        val extended = open.extended(listOf(Fixtures.segment("b", Fixtures.gfed), Fixtures.segment("c", Fixtures.cdef)))

        assertEquals(3, extended.lastSegment)
        assertEquals(4, extended.timeline.segmentCount)
        assertEquals(4, extended.score.measureCount)
        assertEquals(listOf("a", "b", "c"), extended.segments.map { (it.origin as SegmentOrigin.Bundled).exerciseId })
        assertSame(open, open.extended(emptyList()))
        assertEquals(listOf(Fixtures.segment("a", Fixtures.cdef), Fixtures.segment("b", Fixtures.gfed)), extended.performed(2).segments)
        assertFailsWith<IllegalArgumentException> { RunContext(listOf(Fixtures.segment("a", Fixtures.cdef)), Fixtures.slowConfig.copy(segmentCount = 2)) }
    }
}
