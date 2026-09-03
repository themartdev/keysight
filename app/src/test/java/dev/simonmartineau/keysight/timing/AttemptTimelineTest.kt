package dev.simonmartineau.keysight.timing

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AttemptTimelineTest {

    private fun timeline(
        tempoBpm: Double = 60.0,
        previewDurationBeats: Double = 2.0,
        countInMeasures: Int = 1,
        metronomeDuringAttempt: Boolean = false,
    ) = AttemptTimeline.of(
        FlashConfig(
            tempoBpm = tempoBpm,
            countInMeasures = countInMeasures,
            previewDurationBeats = previewDurationBeats,
            metronomeDuringAttempt = metronomeDuringAttempt,
        ),
        Fixtures.cdef,
    )

    @Test
    fun `the reference timeline lays out one beat per second`() {
        val timeline = timeline()

        assertEquals(2.0, timeline.previewStartBeat)
        assertEquals(4.0, timeline.performanceStartBeat)
        assertEquals(8.0, timeline.performanceEndBeat)
        assertEquals(9.0, timeline.captureEndBeat)

        assertEquals(2 * SECOND_NANOS, timeline.previewStartNanos)
        assertEquals(4 * SECOND_NANOS, timeline.performanceStartNanos)
        assertEquals(8 * SECOND_NANOS, timeline.performanceEndNanos)
        assertEquals(9 * SECOND_NANOS, timeline.captureEndNanos)
    }

    @Test
    fun `the notation disappears on the same instant the performance begins`() {
        val timeline = timeline(previewDurationBeats = 1.0)

        assertEquals(timeline.countInBeats, timeline.performanceStartBeat)
        assertEquals(TimelinePhase.PREVIEW, timeline.phaseAt(timeline.performanceStartNanos - 1))
        assertEquals(TimelinePhase.PERFORMING, timeline.phaseAt(timeline.performanceStartNanos))
    }

    @Test
    fun `a full count-in preview shows the notation from the first click`() {
        val timeline = timeline(previewDurationBeats = 4.0)

        assertEquals(0.0, timeline.previewStartBeat)
        assertEquals(TimelinePhase.PREVIEW, timeline.phaseAt(0))
    }

    @Test
    fun `preview durations shorter than a beat land inside the last beat`() {
        val timeline = timeline(tempoBpm = 120.0, previewDurationBeats = 0.5)

        assertEquals(3.5, timeline.previewStartBeat)
        assertEquals(1_750_000_000L, timeline.previewStartNanos)
    }

    @Test
    fun `beat offsets are absolute so a long attempt cannot accumulate drift`() {
        val timeline = timeline(tempoBpm = 72.0)

        val direct = timeline.nanosAtBeat(64.0)
        val accumulated = generateSequence(0L) { it + timeline.nanosAtBeat(1.0) }.elementAt(64)

        assertEquals(53_333_333_333L, direct)
        assertNotEquals(direct, accumulated, "at 72 bpm a beat is not a whole number of nanoseconds")
    }

    @Test
    fun `beat and nanosecond conversions round-trip`() {
        val timeline = timeline(tempoBpm = 96.0)

        assertEquals(5.0, timeline.beatAtNanos(timeline.nanosAtBeat(5.0)), 1e-6)
    }

    @Test
    fun `score ticks map onto beats of the score's meter`() {
        assertEquals(1.5, timeline().beatsOf(Ticks.QUARTER.dotted()))
    }

    @Test
    fun `phases start inclusive and end exclusive`() {
        val timeline = timeline()

        assertEquals(TimelinePhase.COUNT_IN, timeline.phaseAt(0))
        assertEquals(TimelinePhase.COUNT_IN, timeline.phaseAt(2 * SECOND_NANOS - 1))
        assertEquals(TimelinePhase.PREVIEW, timeline.phaseAt(2 * SECOND_NANOS))
        assertEquals(TimelinePhase.PERFORMING, timeline.phaseAt(4 * SECOND_NANOS))
        assertEquals(TimelinePhase.PERFORMING, timeline.phaseAt(8 * SECOND_NANOS - 1))
        assertEquals(TimelinePhase.CAPTURE_TAIL, timeline.phaseAt(8 * SECOND_NANOS))
        assertEquals(TimelinePhase.ENDED, timeline.phaseAt(9 * SECOND_NANOS))
    }

    @Test
    fun `the next boundary walks every boundary exactly once`() {
        val timeline = timeline()
        val visited = generateSequence(timeline.nextBoundaryAfter(-1)) { timeline.nextBoundaryAfter(it) }.toList()

        assertEquals(listOf(2, 4, 8, 9).map { it * SECOND_NANOS }, visited)
        assertNull(timeline.nextBoundaryAfter(9 * SECOND_NANOS))
    }

    @Test
    fun `the metronome stops at the performance by default`() {
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), timeline().clickBeats)
    }

    @Test
    fun `the metronome can be left running through the attempt`() {
        assertEquals((0..7).map { it.toDouble() }, timeline(metronomeDuringAttempt = true).clickBeats)
    }

    @Test
    fun `the clicks end where the beat indicator goes dark`() {
        assertEquals(4.0, timeline().clickEndBeat)
        assertEquals(8.0, timeline(metronomeDuringAttempt = true).clickEndBeat)
        assertEquals(timeline(metronomeDuringAttempt = true).performanceEndBeat, timeline(metronomeDuringAttempt = true).clickEndBeat)
    }

    @Test
    fun `a two measure count-in doubles the available preview`() {
        val timeline = timeline(countInMeasures = 2, previewDurationBeats = 6.0)

        assertEquals(8.0, timeline.countInBeats)
        assertEquals(2.0, timeline.previewStartBeat)
    }

    @Test
    fun `a preview longer than the count-in is rejected`() {
        assertFailsWith<IllegalArgumentException> { timeline(previewDurationBeats = 5.0) }
    }
}
