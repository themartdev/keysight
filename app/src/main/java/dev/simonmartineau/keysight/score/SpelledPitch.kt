package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/** The seven letter names, with their distance in semitones above C. */
enum class Step(val semitonesAboveC: Int) {
    C(0), D(2), E(4), F(5), G(7), A(9), B(11),
}

/**
 * A pitch as it is written: letter, accidental and octave.
 *
 * F sharp and G flat sound the same but are drawn differently, and a renderer needs to know
 * which one the author meant. The score model therefore keeps the spelling, and everything
 * that only cares about what sounds reads [pitch].
 *
 * @param alteration semitones added by the accidental: -2 double flat, -1 flat, 0 natural,
 *   1 sharp, 2 double sharp. Matches MusicXML `alter`.
 * @param octave scientific pitch notation octave of the letter, so B#3 sounds as C4.
 */
@Serializable
data class SpelledPitch(val step: Step, val alteration: Int = 0, val octave: Int) {

    init {
        require(alteration in -2..2) { "alteration must be within -2..2, was $alteration" }
    }

    val pitch: Pitch
        get() = Pitch((octave + 1) * 12 + step.semitonesAboveC + alteration)

    /**
     * The letter's place on the endless diatonic ladder, one per letter name, so that a step
     * up is always one more whatever the accidental. C4 is 28.
     */
    val diatonicIndex: Int get() = diatonicIndex(step, octave)

    /** The same letter and accidental, [octaves] higher. */
    fun octavesUp(octaves: Int): SpelledPitch = copy(octave = octave + octaves)

    override fun toString(): String = "$step${ACCIDENTALS.getValue(alteration)}$octave"

    companion object {
        /**
         * The spelling of [step] altered by [alteration] that sounds as [pitch], if there is
         * one: B sharp for C4 is B#3, C flat for B3 is Cb4.
         */
        fun of(pitch: Pitch, step: Step, alteration: Int): SpelledPitch? {
            val letterMidi = pitch.midiNoteNumber - step.semitonesAboveC - alteration
            if (Math.floorMod(letterMidi, 12) != 0) return null
            return SpelledPitch(step, alteration, letterMidi / 12 - 1)
        }
    }
}

fun diatonicIndex(step: Step, octave: Int): Int = octave * Step.entries.size + step.ordinal

private val ACCIDENTALS = mapOf(-2 to "bb", -1 to "b", 0 to "", 1 to "#", 2 to "##")
