package dev.simonmartineau.keysight.attempt

import dev.simonmartineau.keysight.score.TimeSignature
import kotlinx.serialization.Serializable

/**
 * How the flash mechanic behaves, independent of what the player is reading.
 *
 * Preview difficulty and notation difficulty are separate variables on purpose: a trivial
 * passage shown for half a beat and a hard passage shown for four beats are both difficult, and
 * the app needs to move one without moving the other.
 *
 * The count-in is measured in measures, and a measure is whatever the exercise's time signature
 * says it is; the configuration deliberately does not carry a time signature of its own that
 * could disagree with the score.
 */
@Serializable
data class FlashConfig(
    val tempoBpm: Double,
    val countInMeasures: Int,
    val previewDurationBeats: Double,
    val metronomeDuringAttempt: Boolean,
) {
    init {
        require(tempoBpm > 0.0) { "tempoBpm must be positive" }
        require(countInMeasures > 0) { "countInMeasures must be positive" }
        require(previewDurationBeats > 0.0) { "previewDurationBeats must be positive" }
    }

    fun countInBeats(timeSignature: TimeSignature): Double =
        (countInMeasures * timeSignature.beatsPerMeasure).toDouble()

    companion object {
        /**
         * The easiest configuration: notation visible for the whole count-in, and a metronome
         * that establishes the pulse then gets out of the way.
         */
        val DEFAULT = FlashConfig(
            tempoBpm = 72.0,
            countInMeasures = 1,
            previewDurationBeats = 4.0,
            metronomeDuringAttempt = false,
        )

        /** The preview ladder the difficulty controller will walk, easiest first. */
        val PREVIEW_LADDER_BEATS = listOf(4.0, 3.0, 2.0, 1.5, 1.0, 0.75, 0.5, 0.25)
    }
}
