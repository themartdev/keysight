package dev.simonmartineau.keysight.timing

import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val SECOND_NANOS = 1_000_000_000L

class AttemptTimelineTest {

    private fun timeline(
        tempoBpm: Double = 60.0,
        previewDurationBeats: Double = 2.0,
        countInMeasures: Int = 1,
        metronomeDuringAttempt: Boolean = false,
        exerciseBeats: Double = 4.0,
    ) = AttemptTimeline.of(
        config = FlashConfig(
            tempoBpm = tempoBpm,
            timeSignature = TimeSignature.FOUR_FOUR,
            countInMeasures = countInMeasures,
            previewDurationBeats = previewDurationBeats,
            metronomeDuringAttempt = metronomeDuringAttempt,
        ),
        exerciseBeats = exerciseBeats,
    )

    @Test
    fun `four beat count-in with two beat preview lays out one beat per second at 60 bpm`() {
        val timeline = timeline()

        assertEquals(2.0, timeline.previewStartBeat)
        assertEquals(4.0, timeline.performanceStartBeat)
        assertEquals(8.0, timeline.performanceEndBeat)

        assertEquals(2 * SECOND_NANOS, timeline.previewStartNanos)
        assertEquals(4 * SECOND_NANOS, timeline.performanceStartNanos)
        assertEquals(8 * SECOND_NANOS, timeline.performanceEndNanos)
    }

    @Test
    fun `the notation disappears on the same instant the performance begins`() {
        val timeline = timeline(previewDurationBeats = 1.0)

        assertEquals(timeline.performanceStartBeat, timeline.countInBeats)
        assertEquals(timeline.performanceStartNanos, timeline.nanosAtBeat(timeline.countInBeats))
    }

    @Test
    fun `a full count-in preview shows the notation from the very first click`() {
        assertEquals(0.0, timeline(previewDurationBeats = 4.0).previewStartBeat)
    }

    @Test
    fun `preview durations shorter than a beat are placed inside the last beat`() {
        val timeline = timeline(tempoBpm = 120.0, previewDurationBeats = 0.5)

        assertEquals(3.5, timeline.previewStartBeat)
        // 120 bpm is 500 ms per beat, so three and a half beats is 1.75 s.
        assertEquals(1_750_000_000L, timeline.previewStartNanos)
    }

    @Test
    fun `beat offsets are absolute so a long attempt cannot accumulate drift`() {
        val timeline = timeline(tempoBpm = 72.0, exerciseBeats = 64.0)

        // Every offset is computed from its beat number, never by adding one beat at a time.
        val beat64 = timeline.nanosAtBeat(64.0)
        val stepwise = generateSequence(0L) { it + timeline.nanosAtBeat(1.0) }.elementAt(64)
        assertTrue(
            beat64 != stepwise,
            "at 72 bpm a beat is not a whole number of nanoseconds, so this test is only " +
                "meaningful if accumulating differs from computing directly",
        )
        assertEquals(53_333_333_333L, beat64)
    }

    @Test
    fun `beat and nanosecond conversions round-trip`() {
        val timeline = timeline(tempoBpm = 96.0)

        assertEquals(5.0, timeline.beatAtNanos(timeline.nanosAtBeat(5.0)), 1e-6)
    }

    @Test
    fun `the metronome stops at the performance by default`() {
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), timeline().clickBeats)
    }

    @Test
    fun `the metronome can be left running through the attempt`() {
        val clicks = timeline(metronomeDuringAttempt = true).clickBeats

        assertEquals((0..7).map { it.toDouble() }, clicks)
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
