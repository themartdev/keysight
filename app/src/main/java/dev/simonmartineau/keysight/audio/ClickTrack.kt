package dev.simonmartineau.keysight.audio

import dev.simonmartineau.keysight.timing.RunTimeline
import kotlin.math.roundToLong

/**
 * The metronome's audio, laid out on a frame line.
 *
 * Every click sits at the frame that corresponds to its absolute beat position on the
 * timeline, so the spacing is exact whatever chunk size the writer uses and however many clicks
 * came before. [render] fills any window of that line on demand, working out from the timeline
 * which clicks touch the window rather than walking a list, so an open-ended run's click has
 * no end to run out of; the writer thread never decides when a click happens, it only keeps
 * the buffer full.
 *
 * Frame 0 is the run start, the first count-in click.
 */
class ClickTrack(
    val sampleRate: Int,
    val timeline: RunTimeline,
    private val accent: ShortArray = ClickSynth.render(sampleRate, ClickSynth.ACCENT),
    private val beat: ShortArray = ClickSynth.render(sampleRate, ClickSynth.BEAT),
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
    }

    class Click(val beat: Long, val frame: Long, val accented: Boolean, val samples: ShortArray) {
        val endFrame: Long get() = frame + samples.size
    }

    private val longestClick = maxOf(accent.size, beat.size)

    /** The click on beat [beat], whether or not the metronome sounds there. */
    fun click(beat: Long): Click {
        val accented = beat % timeline.timeSignature.beatsPerMeasure == 0L
        return Click(beat, frameAtNanos(timeline.nanosAtBeat(beat.toDouble())), accented, if (accented) accent else this.beat)
    }

    /** The clicks that sound and have at least one sample in `[fromFrame, toFrame)`. */
    fun clicksIn(fromFrame: Long, toFrame: Long): List<Click> {
        if (toFrame <= fromFrame) return emptyList()
        val beats = timeline.clicksIn(beatAtFrame(fromFrame - longestClick) - 1.0, beatAtFrame(toFrame) + 1.0)
        return beats.map(::click).filter { it.frame < toFrame && it.endFrame > fromFrame }
    }

    /** The first frame after which the track is silent for good; null when it never is. */
    val endFrame: Long?
        get() {
            val end = timeline.clickEndBeat
            if (end.isInfinite()) return null
            val last = timeline.clicksIn(0.0, end).last
            return if (last < 0) 0L else click(last).endFrame
        }

    fun frameAtNanos(nanos: Long): Long = (nanos.toDouble() * sampleRate / NANOS_PER_SECOND).roundToLong()

    private fun beatAtFrame(frame: Long): Double = timeline.beatAtNanos((frame.toDouble() * NANOS_PER_SECOND / sampleRate).roundToLong())

    /**
     * Fills [into] with the [count] frames starting at [fromFrame]. Frames before 0 and after
     * the last click are silence; overlapping clicks are summed and clamped.
     */
    fun render(fromFrame: Long, into: ShortArray, count: Int = into.size) {
        require(count in 0..into.size) { "count $count does not fit ${into.size}" }
        into.fill(0, 0, count)
        val toFrame = fromFrame + count
        for (click in clicksIn(fromFrame, toFrame)) {
            val start = maxOf(click.frame, fromFrame)
            val end = minOf(click.endFrame, toFrame)
            for (frame in start until end) {
                val i = (frame - fromFrame).toInt()
                val mixed = into[i] + click.samples[(frame - click.frame).toInt()]
                into[i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
