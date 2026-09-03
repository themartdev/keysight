package dev.simonmartineau.keysight.timing

import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class RunTimelineTest {

    /** 60 bpm in 4/4: one beat per second, four seconds per segment. */
    private fun timeline(
        tempoBpm: Double = 60.0,
        segmentCount: Int = 3,
        metronomeThroughout: Boolean = false,
        timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    ) = RunTimeline(tempoBpm, timeSignature, segmentCount, metronomeThroughout)

    @Test
    fun `segments follow each other on one beat line from the count-in`() {
        val timeline = timeline()

        assertEquals(4.0, timeline.beatsPerSegment)
        assertEquals(listOf(0.0, 4.0, 8.0, 12.0), (0..3).map(timeline::segmentStartBeat))
        assertEquals(8.0, timeline.segmentEndBeat(1))
        assertEquals(1..2, timeline.performedSegments)
        assertEquals(4.0, timeline.performanceStartBeat)
        assertEquals(12.0, timeline.endBeat)
        assertEquals(13.0, timeline.captureEndBeat)
        assertEquals(9.0, timeline.captureEndBeatAfter(1))
    }

    @Test
    fun `the reference timeline lays out one beat per second`() {
        val timeline = timeline()

        assertEquals(4 * SECOND_NANOS, timeline.performanceStartNanos)
        assertEquals(12 * SECOND_NANOS, timeline.endNanos)
        assertEquals(13 * SECOND_NANOS, timeline.captureEndNanos)
        assertEquals(9 * SECOND_NANOS, timeline.captureEndNanosAfter(1))
    }

    @Test
    fun `the segment at a beat is clamped to the run`() {
        val timeline = timeline()

        assertEquals(0, timeline.segmentAt(-1.0))
        assertEquals(0, timeline.segmentAt(3.999))
        assertEquals(1, timeline.segmentAt(4.0))
        assertEquals(2, timeline.segmentAt(11.5))
        assertEquals(2, timeline.segmentAt(12.0))
        assertEquals(2, timeline.segmentAt(100.0))
    }

    @Test
    fun `three four has three beats per segment`() {
        val timeline = timeline(timeSignature = TimeSignature.THREE_FOUR)

        assertEquals(3.0, timeline.performanceStartBeat)
        assertEquals(9.0, timeline.endBeat)
        assertEquals(1, timeline.segmentAt(5.9))
    }

    @Test
    fun `phases start inclusive and end exclusive`() {
        val timeline = timeline()

        assertEquals(TimelinePhase.COUNT_IN, timeline.phaseAt(0))
        assertEquals(TimelinePhase.COUNT_IN, timeline.phaseAt(4 * SECOND_NANOS - 1))
        assertEquals(TimelinePhase.PERFORMING, timeline.phaseAt(4 * SECOND_NANOS))
        assertEquals(TimelinePhase.PERFORMING, timeline.phaseAt(12 * SECOND_NANOS - 1))
        assertEquals(TimelinePhase.CAPTURE_TAIL, timeline.phaseAt(12 * SECOND_NANOS))
        assertEquals(TimelinePhase.ENDED, timeline.phaseAt(13 * SECOND_NANOS))
    }

    @Test
    fun `beat offsets are absolute so a long run cannot accumulate drift`() {
        val timeline = timeline(tempoBpm = 72.0, segmentCount = 100)

        val direct = timeline.nanosAtBeat(400.0)
        val accumulated = generateSequence(0L) { it + timeline.nanosAtBeat(1.0) }.elementAt(400)

        assertEquals(333_333_333_333L, direct)
        assertNotEquals(direct, accumulated, "at 72 bpm a beat is not a whole number of nanoseconds")
    }

    @Test
    fun `beat and nanosecond conversions round-trip`() {
        val timeline = timeline(tempoBpm = 96.0)

        assertEquals(5.0, timeline.beatAtNanos(timeline.nanosAtBeat(5.0)), 1e-6)
    }

    @Test
    fun `score ticks and run beats are one line`() {
        val timeline = timeline()

        assertEquals(1.5, timeline.beatsOf(Ticks.QUARTER.dotted()))
        assertEquals(Ticks.quarters(4), timeline.ticksAt(4.0))
        assertEquals(Ticks(4 * 960 + 480), timeline.ticksAt(4.5))
        assertEquals(Ticks(959), timeline.ticksAt(0.9999))
        assertEquals(Ticks.ZERO, timeline.ticksAt(-1.0))
    }

    @Test
    fun `the metronome stops at the performance by default`() {
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), timeline().clickBeats)
        assertEquals(4.0, timeline().clickEndBeat)
    }

    @Test
    fun `the metronome can be left running through the run`() {
        val throughout = timeline(metronomeThroughout = true)

        assertEquals((0..11).map { it.toDouble() }, throughout.clickBeats)
        assertEquals(throughout.endBeat, throughout.clickEndBeat)
    }

    @Test
    fun `a truncated run ends where its last kept segment does`() {
        val cut = timeline(segmentCount = 5).truncatedTo(3)

        assertEquals(3, cut.segmentCount)
        assertEquals(12.0, cut.endBeat)
        assertEquals(13.0, cut.captureEndBeat)
        assertFailsWith<IllegalArgumentException> { timeline(segmentCount = 3).truncatedTo(4) }
        assertFailsWith<IllegalArgumentException> { timeline(segmentCount = 3).truncatedTo(1) }
    }

    @Test
    fun `a run needs a count-in and something to perform`() {
        assertFailsWith<IllegalArgumentException> { timeline(segmentCount = 1) }
        assertFailsWith<IllegalArgumentException> { timeline(tempoBpm = 0.0) }
    }
}
