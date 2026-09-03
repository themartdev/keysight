package dev.simonmartineau.keysight.timing

import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.math.floor
import kotlin.math.roundToLong

/** Which part of a run a given instant falls in. */
enum class TimelinePhase {
    /** Segment 0: the metronome is counting in and nothing is expected yet. */
    COUNT_IN,

    /** Segments 1 onwards, one after the other on the same beat line. */
    PERFORMING,

    /** The last segment is over; late notes and releases are still captured. */
    CAPTURE_TAIL,

    ENDED,
}

/**
 * Every scheduled moment of one run, in beats first and nanoseconds on demand.
 *
 * A run is a sequence of segments, one measure each, on one continuous beat line. Beat 0 is
 * the first count-in click and the start of segment 0, the silent measure the metronome counts
 * in; segment k starts at beat `k * beatsPerSegment`. The score's ticks and the run's beats are
 * the same line, so tick 0 is beat 0 and no offset exists between them. Capture runs for
 * [captureTailBeats] past the last segment so a late final note is not lost.
 *
 * Every offset is computed from its absolute beat position, never accumulated, so a long run
 * cannot drift: beat 400 is exactly `400 * 60 s / bpm` after the start, whatever came before.
 */
data class RunTimeline(
    val tempoBpm: Double,
    val timeSignature: TimeSignature,
    /** Segments including the silent segment 0, so at least 2. */
    val segmentCount: Int,
    /** Whether the metronome keeps clicking through the performance, or stops after the count-in. */
    val metronomeThroughout: Boolean,
    val captureTailBeats: Double = DEFAULT_CAPTURE_TAIL_BEATS,
) {
    init {
        require(tempoBpm > 0.0) { "tempoBpm must be positive" }
        require(segmentCount >= 2) { "a run needs the count-in segment and one to perform, not $segmentCount" }
        require(captureTailBeats >= 0.0) { "captureTailBeats must not be negative" }
    }

    val beatsPerSegment: Double get() = timeSignature.beatsPerMeasure.toDouble()

    /** Indices of the segments the player performs: everything after the count-in. */
    val performedSegments: IntRange get() = 1 until segmentCount

    fun segmentStartBeat(segment: Int): Double {
        require(segment in 0..segmentCount) { "no segment $segment in $segmentCount" }
        return segment * beatsPerSegment
    }

    fun segmentEndBeat(segment: Int): Double = segmentStartBeat(segment + 1)

    /** The segment [beat] falls in, clamped to the run: negative beats are segment 0, beats past the end the last. */
    fun segmentAt(beat: Double): Int = floor(beat / beatsPerSegment).toInt().coerceIn(0, segmentCount - 1)

    /** The start of segment 1: the count-in is over and the first notes are due. */
    val performanceStartBeat: Double get() = segmentStartBeat(1)

    /** The end of the last segment, which is the end of the score. */
    val endBeat: Double get() = segmentStartBeat(segmentCount)

    val captureEndBeat: Double get() = endBeat + captureTailBeats

    /** When capture would end if [segment] were the last one performed. */
    fun captureEndBeatAfter(segment: Int): Double = segmentEndBeat(segment) + captureTailBeats

    val performanceStartNanos: Long get() = nanosAtBeat(performanceStartBeat)
    val endNanos: Long get() = nanosAtBeat(endBeat)
    val captureEndNanos: Long get() = nanosAtBeat(captureEndBeat)

    fun captureEndNanosAfter(segment: Int): Long = nanosAtBeat(captureEndBeatAfter(segment))

    /** The beat from which the metronome is silent. */
    val clickEndBeat: Double get() = if (metronomeThroughout) endBeat else performanceStartBeat

    /** Beats on which the metronome sounds. */
    val clickBeats: List<Double>
        get() = generateSequence(0.0) { it + 1.0 }.takeWhile { it < clickEndBeat }.toList()

    /** Nanoseconds from the run start to [beat]. */
    fun nanosAtBeat(beat: Double): Long = (beat * NANOS_PER_MINUTE / tempoBpm).roundToLong()

    /** The beat position [nanosSinceStart] after the run started. */
    fun beatAtNanos(nanosSinceStart: Long): Double = nanosSinceStart * tempoBpm / NANOS_PER_MINUTE

    /** [ticks] of the score as a beat position on the run's line. */
    fun beatsOf(ticks: Ticks): Double = timeSignature.beatsOf(ticks)

    /** The score time at [beat], rounded down to the tick; beats before the start are tick 0. */
    fun ticksAt(beat: Double): Ticks = Ticks(floor(beat * timeSignature.ticksPerBeat.value).toInt().coerceAtLeast(0))

    /**
     * The phase [nanosSinceStart] falls in. Boundaries belong to the phase they open, so the
     * instant segment 1 starts is already [TimelinePhase.PERFORMING].
     */
    fun phaseAt(nanosSinceStart: Long): TimelinePhase = when {
        nanosSinceStart < performanceStartNanos -> TimelinePhase.COUNT_IN
        nanosSinceStart < endNanos -> TimelinePhase.PERFORMING
        nanosSinceStart < captureEndNanos -> TimelinePhase.CAPTURE_TAIL
        else -> TimelinePhase.ENDED
    }

    /** The same run cut after [segmentCount] segments, count-in included: what a stopped run performed. */
    fun truncatedTo(segmentCount: Int): RunTimeline {
        require(segmentCount in 2..this.segmentCount) { "cannot truncate ${this.segmentCount} segments to $segmentCount" }
        return copy(segmentCount = segmentCount)
    }

    companion object {
        private const val NANOS_PER_MINUTE = 60_000_000_000.0

        /** One beat of grace after the last notated beat, for a late final note or release. */
        const val DEFAULT_CAPTURE_TAIL_BEATS = 1.0
    }
}
