package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/**
 * Which clef a staff carries. Each clef fixes the note on the staff's bottom line: E4 for
 * treble, G2 for bass; everything vertical about notation follows from that one fact.
 */
enum class Clef(val bottomLineStep: Step, val bottomLineOctave: Int) {
    TREBLE(Step.E, 4),
    BASS(Step.G, 2),
    ;

    /** Diatonic index of the note on the bottom line, see [SpelledPitch.diatonicIndex]. */
    val bottomLineIndex: Int get() = diatonicIndex(bottomLineStep, bottomLineOctave)

    /** Diatonic index of the note on the middle line, four steps up from the bottom one. */
    val middleLineIndex: Int get() = bottomLineIndex + 4
}

/** One staff of a system. A single-hand score has one; a grand staff has a treble one over a bass one. */
@Serializable
data class Staff(val clef: Clef)

/** Which hand a note is written for. */
enum class Hand { LEFT, RIGHT }
