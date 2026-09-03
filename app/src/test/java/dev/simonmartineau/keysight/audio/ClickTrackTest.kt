package dev.simonmartineau.keysight.audio

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.timing.AttemptTimeline
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClickTrackTest {

    private fun timeline(tempoBpm: Double = 60.0, metronomeDuringAttempt: Boolean = false) = AttemptTimeline.of(
        Fixtures.slowConfig.copy(tempoBpm = tempoBpm, metronomeDuringAttempt = metronomeDuringAttempt),
        Fixtures.cdef,
    )

    @Test
    fun `clicks sit on the frames of their absolute beats`() {
        val track = ClickTrack(48_000, timeline())

        assertEquals(listOf(0L, 48_000L, 96_000L, 144_000L), track.clicks.map { it.frame })
    }

    @Test
    fun `an awkward tempo and sample rate still round each click independently`() {
        val timeline = timeline(tempoBpm = 72.0)
        val track = ClickTrack(44_100, timeline)

        val expected = (0..3).map { beat -> (timeline.nanosAtBeat(beat.toDouble()) * 44_100 / 1e9).roundToLong() }
        assertEquals(expected, track.clicks.map { it.frame })
        assertEquals(36_750L, track.clicks[1].frame)
    }

    @Test
    fun `the downbeat is accented`() {
        val track = ClickTrack(48_000, timeline(metronomeDuringAttempt = true))

        assertEquals(listOf(true, false, false, false, true, false, false, false), track.clicks.map { it.accented })
    }

    @Test
    fun `the metronome stops at the performance unless asked to continue`() {
        assertEquals(4, ClickTrack(48_000, timeline()).clicks.size)
        assertEquals(8, ClickTrack(48_000, timeline(metronomeDuringAttempt = true)).clicks.size)
    }

    @Test
    fun `rendering in odd chunks equals rendering in one go`() {
        val track = ClickTrack(48_000, timeline())
        val total = (track.endFrame + 1000).toInt()
        val whole = ShortArray(total).also { track.render(0, it) }

        val chunked = ShortArray(total)
        val chunk = ShortArray(37)
        var frame = 0
        while (frame < total) {
            val count = minOf(37, total - frame)
            track.render(frame.toLong(), chunk, count)
            chunk.copyInto(chunked, frame, 0, count)
            frame += count
        }

        assertContentEquals(whole, chunked)
    }

    @Test
    fun `between clicks and before the start there is silence`() {
        val track = ClickTrack(48_000, timeline())
        val clickLength = track.clicks[0].samples.size

        val before = ShortArray(100).also { track.render(-100, it) }
        assertTrue(before.all { it == 0.toShort() })

        val between = ShortArray(1000).also { track.render(clickLength + 10L, it) }
        assertTrue(between.all { it == 0.toShort() })

        val after = ShortArray(1000).also { track.render(track.endFrame, it) }
        assertTrue(after.all { it == 0.toShort() })
    }

    @Test
    fun `a chunk straddling a click carries the click's samples`() {
        val track = ClickTrack(48_000, timeline())
        val click = track.clicks[1]
        val chunk = ShortArray(64).also { track.render(click.frame - 32, it) }

        assertTrue(chunk.take(32).all { it == 0.toShort() })
        assertContentEquals(click.samples.take(32).toShortArray(), chunk.drop(32).toShortArray())
    }

    @Test
    fun `the end frame is the tail of the last click`() {
        val track = ClickTrack(48_000, timeline())

        assertEquals(144_000L + track.clicks.last().samples.size, track.endFrame)
        val silent = ClickTrack(48_000, timeline(), accent = ShortArray(0), beat = ShortArray(0))
        assertEquals(144_000L, silent.endFrame)
    }

    @Test
    fun `a two measure count-in accents each measure`() {
        val timeline = AttemptTimeline.of(FlashConfig(60.0, countInMeasures = 2, previewDurationBeats = 2.0, metronomeDuringAttempt = false), Fixtures.cdef)

        assertEquals(listOf(0, 4), ClickTrack(48_000, timeline).clicks.withIndex().filter { it.value.accented }.map { it.index })
    }
}
