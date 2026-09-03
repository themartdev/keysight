package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/**
 * A notated time signature.
 *
 * Everywhere else in the app a "beat" means one [beatUnit], because that is what the metronome
 * clicks and what the preview duration is measured in. [ticksPerBeat] converts score positions
 * to beats.
 */
@Serializable
data class TimeSignature(val beatsPerMeasure: Int, val beatUnit: Int) {

    init {
        require(beatsPerMeasure > 0) { "beatsPerMeasure must be positive" }
        require(beatUnit in ALLOWED_BEAT_UNITS) { "beatUnit must be one of $ALLOWED_BEAT_UNITS" }
    }

    val ticksPerBeat: Ticks get() = Ticks(Ticks.PER_QUARTER * 4 / beatUnit)

    val ticksPerMeasure: Ticks get() = ticksPerBeat * beatsPerMeasure

    /** [ticks] as a number of beats of this signature. */
    fun beatsOf(ticks: Ticks): Double = ticks.value.toDouble() / ticksPerBeat.value

    override fun toString(): String = "$beatsPerMeasure/$beatUnit"

    companion object {
        private val ALLOWED_BEAT_UNITS = setOf(1, 2, 4, 8, 16)

        val FOUR_FOUR = TimeSignature(4, 4)
        val THREE_FOUR = TimeSignature(3, 4)
    }
}
