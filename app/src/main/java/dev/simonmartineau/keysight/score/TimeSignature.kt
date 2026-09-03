package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/**
 * A notated time signature.
 *
 * Everywhere else in the app a "beat" means one unit of [beatUnit], because that is what the
 * metronome clicks and what `previewDurationBeats` is measured in. [quarterNotesPerBeat] is the
 * bridge to durations expressed in quarter notes.
 */
@Serializable
data class TimeSignature(val beatsPerMeasure: Int, val beatUnit: Int) {

    init {
        require(beatsPerMeasure > 0) { "beatsPerMeasure must be positive" }
        require(beatUnit in ALLOWED_BEAT_UNITS) { "beatUnit must be one of $ALLOWED_BEAT_UNITS" }
    }

    val quarterNotesPerBeat: Double get() = 4.0 / beatUnit

    override fun toString(): String = "$beatsPerMeasure/$beatUnit"

    companion object {
        private val ALLOWED_BEAT_UNITS = setOf(1, 2, 4, 8, 16)

        val FOUR_FOUR = TimeSignature(4, 4)
    }
}
