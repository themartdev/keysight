package dev.simonmartineau.keysight.audio

import dev.simonmartineau.keysight.timing.AttemptTimeline
import kotlin.math.roundToLong

/**
 * The metronome's audio, laid out on a frame line.
 *
 * Every click sits at the frame that corresponds to its absolute beat position on the
 * timeline, so the spacing is exact whatever chunk size the writer uses and however many clicks
 * came before. [render] fills any window of that line on demand; the writer thread never
 * decides when a click happens, it only keeps the buffer full.
 *
 * Frame 0 is the attempt start, the first count-in click.
 */
class ClickTrack(
    val sampleRate: Int,
    val timeline: AttemptTimeline,
    accent: ShortArray = ClickSynth.render(sampleRate, ClickSynth.ACCENT),
    beat: ShortArray = ClickSynth.render(sampleRate, ClickSynth.BEAT),
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
    }

    class Click(val frame: Long, val accented: Boolean, val samples: ShortArray) {
        val endFrame: Long get() = frame + samples.size
    }

    val clicks: List<Click> = timeline.clickBeats.map { beatPosition ->
        val index = beatPosition.roundToLong()
        val accented = index % timeline.timeSignature.beatsPerMeasure == 0L
        Click(
            frame = frameAtNanos(timeline.nanosAtBeat(beatPosition)),
            accented = accented,
            samples = if (accented) accent else beat,
        )
    }

    /** The first frame after which the track is silent for good. */
    val endFrame: Long = clicks.maxOfOrNull { it.endFrame } ?: 0L

    fun frameAtNanos(nanos: Long): Long = (nanos.toDouble() * sampleRate / NANOS_PER_SECOND).roundToLong()

    /**
     * Fills [into] with the [count] frames starting at [fromFrame]. Frames before 0 and after
     * [endFrame] are silence; overlapping clicks are summed and clamped.
     */
    fun render(fromFrame: Long, into: ShortArray, count: Int = into.size) {
        require(count in 0..into.size) { "count $count does not fit ${into.size}" }
        into.fill(0, 0, count)
        val toFrame = fromFrame + count
        for (click in clicks) {
            if (click.endFrame <= fromFrame || click.frame >= toFrame) continue
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
