package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals

class RunRecordTest {

    @Test
    fun `a record's timeline is the run as performed, the count-in and its segments, closed`() {
        val context = Fixtures.run(Fixtures.cdef, Fixtures.gfed, Fixtures.cdef, config = Fixtures.slowConfig.copy(metronome = MetronomeMode.THROUGHOUT, segmentCount = null))
        val record = RunRecord("r", "s", 0L, 0L, RunStatus.ABORTED, AbortReason.BACKGROUNDED, context.config, context.segments.take(2), emptyList())

        assertEquals(context.performed(2).timeline, record.timeline)
        assertEquals(3, record.timeline.segmentCount)
        assertEquals(true, record.timeline.metronomeThroughout)
        assertEquals(false, record.timeline.openEnded)
    }
}
