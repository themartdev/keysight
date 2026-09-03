package dev.simonmartineau.keysight.timing

import dev.simonmartineau.keysight.attempt.FlashConfig
import kotlin.math.roundToLong

/**
 * Every scheduled moment of one flash attempt, expressed first in beats and then converted to
 * nanoseconds on demand.
 *
 * Offsets are always computed from an absolute beat position rather than accumulated, so a long
 * session cannot drift: beat 40 is exactly `40 * 60s / bpm` after the attempt started, however
 * many beats came before it.
 *
 * The count-in and the performance are one continuous beat timeline. Beat 0 is the first
 * count-in click; the performance begins at [performanceStartBeat], which is also the instant
 * the notation disappears.
 */
data class AttemptTimeline(
    val tempoBpm: Double,
    val countInBeats: Double,
    val previewDurationBeats: Double,
    val performanceBeats: Double,
    val metronomeDuringAttempt: Boolean,
) {
    init {
        require(tempoBpm > 0.0) { "tempoBpm must be positive" }
        require(countInBeats > 0.0) { "countInBeats must be positive" }
        require(performanceBeats > 0.0) { "performanceBeats must be positive" }
        require(previewDurationBeats > 0.0) { "previewDurationBeats must be positive" }
        require(previewDurationBeats <= countInBeats) {
            "previewDurationBeats ($previewDurationBeats) cannot exceed the count-in " +
                "($countInBeats beats); the notation must be gone by the time playing starts"
        }
    }

    /** When the notation appears. Equals 0 when the preview covers the whole count-in. */
    val previewStartBeat: Double get() = countInBeats - previewDurationBeats

    /** When the notation disappears and the player starts, in one step. */
    val performanceStartBeat: Double get() = countInBeats

    val performanceEndBeat: Double get() = countInBeats + performanceBeats

    /** Beats on which the metronome sounds, given [metronomeDuringAttempt]. */
    val clickBeats: List<Double>
        get() {
            val lastClick = if (metronomeDuringAttempt) performanceEndBeat else countInBeats
            return generateSequence(0.0) { it + 1.0 }
                .takeWhile { it < lastClick }
                .toList()
        }

    /** Nanoseconds from attempt start to [beat]. */
    fun nanosAtBeat(beat: Double): Long = (beat * NANOS_PER_MINUTE / tempoBpm).roundToLong()

    /** The beat position [nanosSinceStart] after the attempt started. */
    fun beatAtNanos(nanosSinceStart: Long): Double =
        nanosSinceStart * tempoBpm / NANOS_PER_MINUTE

    val previewStartNanos: Long get() = nanosAtBeat(previewStartBeat)
    val performanceStartNanos: Long get() = nanosAtBeat(performanceStartBeat)
    val performanceEndNanos: Long get() = nanosAtBeat(performanceEndBeat)

    companion object {
        private const val NANOS_PER_MINUTE = 60_000_000_000.0

        /**
         * Builds the timeline for [config] applied to an exercise [exerciseBeats] beats long.
         */
        fun of(config: FlashConfig, exerciseBeats: Double): AttemptTimeline = AttemptTimeline(
            tempoBpm = config.tempoBpm,
            countInBeats = (config.countInMeasures * config.timeSignature.beatsPerMeasure)
                .toDouble(),
            previewDurationBeats = config.previewDurationBeats,
            performanceBeats = exerciseBeats,
            metronomeDuringAttempt = config.metronomeDuringAttempt,
        )
    }
}
