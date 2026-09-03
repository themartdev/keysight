package dev.simonmartineau.keysight.timing

import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.math.roundToLong

/** Which part of an attempt a given instant falls in. */
enum class TimelinePhase {
    /** The metronome is counting in and the notation is not on screen yet. */
    COUNT_IN,

    /** Still counting in, notation visible. */
    PREVIEW,

    /** The notation is gone and the player is performing the passage. */
    PERFORMING,

    /** The passage is over; late notes and releases are still captured. */
    CAPTURE_TAIL,

    ENDED,
}

/**
 * Every scheduled moment of one flash attempt, in beats first and nanoseconds on demand.
 *
 * The count-in and the performance are one continuous beat line: beat 0 is the first count-in
 * click, the notation disappears and the performance starts on the same beat, and capture runs
 * for [captureTailBeats] past the last notated beat so a late final note is not lost.
 *
 * Every offset is computed from its absolute beat position, never accumulated, so repeated
 * attempts cannot drift: beat 40 is exactly `40 * 60 s / bpm` after the start, whatever came
 * before it.
 */
data class AttemptTimeline(
    val tempoBpm: Double,
    val timeSignature: TimeSignature,
    val countInBeats: Double,
    val previewDurationBeats: Double,
    val performanceBeats: Double,
    val metronomeDuringAttempt: Boolean,
    val captureTailBeats: Double = DEFAULT_CAPTURE_TAIL_BEATS,
) {
    init {
        require(tempoBpm > 0.0) { "tempoBpm must be positive" }
        require(countInBeats > 0.0) { "countInBeats must be positive" }
        require(performanceBeats > 0.0) { "performanceBeats must be positive" }
        require(captureTailBeats >= 0.0) { "captureTailBeats must not be negative" }
        require(previewDurationBeats > 0.0) { "previewDurationBeats must be positive" }
        require(previewDurationBeats <= countInBeats) {
            "previewDurationBeats ($previewDurationBeats) cannot exceed the count-in " +
                "($countInBeats beats): the notation must be gone by the time playing starts"
        }
    }

    /** When the notation appears; 0 when the preview covers the whole count-in. */
    val previewStartBeat: Double get() = countInBeats - previewDurationBeats

    /** When the notation disappears and the performance begins, in one step. */
    val performanceStartBeat: Double get() = countInBeats

    val performanceEndBeat: Double get() = countInBeats + performanceBeats

    val captureEndBeat: Double get() = performanceEndBeat + captureTailBeats

    val previewStartNanos: Long get() = nanosAtBeat(previewStartBeat)
    val performanceStartNanos: Long get() = nanosAtBeat(performanceStartBeat)
    val performanceEndNanos: Long get() = nanosAtBeat(performanceEndBeat)
    val captureEndNanos: Long get() = nanosAtBeat(captureEndBeat)

    /** Beats on which the metronome sounds. */
    val clickBeats: List<Double>
        get() {
            val lastClick = if (metronomeDuringAttempt) performanceEndBeat else countInBeats
            return generateSequence(0.0) { it + 1.0 }.takeWhile { it < lastClick }.toList()
        }

    /** Nanoseconds from the attempt start to [beat]. */
    fun nanosAtBeat(beat: Double): Long = (beat * NANOS_PER_MINUTE / tempoBpm).roundToLong()

    /** The beat position [nanosSinceStart] after the attempt started. */
    fun beatAtNanos(nanosSinceStart: Long): Double = nanosSinceStart * tempoBpm / NANOS_PER_MINUTE

    /** [ticks] of the exercise as a beat position relative to the performance start. */
    fun beatsOf(ticks: Ticks): Double = timeSignature.beatsOf(ticks)

    /**
     * The phase [nanosSinceStart] falls in. Boundaries belong to the phase they open, so the
     * instant the performance starts is already [TimelinePhase.PERFORMING].
     */
    fun phaseAt(nanosSinceStart: Long): TimelinePhase = when {
        nanosSinceStart < previewStartNanos -> TimelinePhase.COUNT_IN
        nanosSinceStart < performanceStartNanos -> TimelinePhase.PREVIEW
        nanosSinceStart < performanceEndNanos -> TimelinePhase.PERFORMING
        nanosSinceStart < captureEndNanos -> TimelinePhase.CAPTURE_TAIL
        else -> TimelinePhase.ENDED
    }

    /** The next phase boundary strictly after [nanosSinceStart], or null once capture ended. */
    fun nextBoundaryAfter(nanosSinceStart: Long): Long? =
        boundaryNanos.firstOrNull { it > nanosSinceStart }

    private val boundaryNanos: List<Long>
        get() = listOf(previewStartNanos, performanceStartNanos, performanceEndNanos, captureEndNanos)

    companion object {
        private const val NANOS_PER_MINUTE = 60_000_000_000.0

        /** One beat of grace after the last notated beat, for a late final note or release. */
        const val DEFAULT_CAPTURE_TAIL_BEATS = 1.0

        /** The timeline for [config] applied to [score]; the count-in is measured in the score's meter. */
        fun of(config: FlashConfig, score: Score): AttemptTimeline = AttemptTimeline(
            tempoBpm = config.tempoBpm,
            timeSignature = score.timeSignature,
            countInBeats = config.countInBeats(score.timeSignature),
            previewDurationBeats = config.previewDurationBeats,
            performanceBeats = score.totalBeats,
            metronomeDuringAttempt = config.metronomeDuringAttempt,
        )
    }
}
