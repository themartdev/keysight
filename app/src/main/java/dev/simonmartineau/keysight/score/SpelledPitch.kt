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

    override fun toString(): String = "$step${ACCIDENTALS.getValue(alteration)}$octave"
}

private val ACCIDENTALS = mapOf(-2 to "bb", -1 to "b", 0 to "", 1 to "#", 2 to "##")
