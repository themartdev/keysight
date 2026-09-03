package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.Hand
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Staff
import dev.simonmartineau.keysight.score.transposed
import kotlin.math.abs
import kotlin.random.Random

/**
 * A bundled exercise as the player has asked to read it: in [key], on the staves [hands]
 * calls for.
 *
 * The bundled content is a single voice on one staff. With both hands it goes on one staff of
 * the grand staff, picked at random, and the other staff rests: the alignment is monophonic
 * until the generator round, so this is how both clefs stay live without two-hand chords.
 * When the voice moves to a clef it was not written for, it moves by whole octaves so that its
 * median note sits nearest the middle line; on its own clef it stays exactly where the player
 * knows it.
 */
fun Exercise.adaptedTo(key: KeySignature, hands: Hands, random: Random): Exercise {
    require(score.staves.size == 1) { "$id: adaptation handles single-staff content, not ${score.staves.size} staves" }
    val staves = when (hands) {
        Hands.RIGHT -> listOf(Staff(Clef.TREBLE))
        Hands.LEFT -> listOf(Staff(Clef.BASS))
        Hands.BOTH -> listOf(Staff(Clef.TREBLE), Staff(Clef.BASS))
    }
    val staff = if (hands == Hands.BOTH) random.nextInt(staves.size) else 0
    val clef = staves[staff].clef
    val transposed = score.transposed(key)
    val octaves = if (clef == score.staves.single().clef) 0 else centringOctaves(transposed.notes.map { it.spelling.diatonicIndex }, clef)
    val hand = if (clef == Clef.BASS) Hand.LEFT else Hand.RIGHT
    return copy(
        score = transposed.copy(
            staves = staves,
            notes = transposed.notes.map { it.copy(spelling = it.spelling.octavesUp(octaves), staff = staff, hand = hand) },
        ),
    )
}

/** The whole-octave shift that puts the median of [diatonicIndices] nearest [clef]'s middle line; the smaller shift when tied. */
private fun centringOctaves(diatonicIndices: List<Int>, clef: Clef): Int {
    if (diatonicIndices.isEmpty()) return 0
    val sorted = diatonicIndices.sorted()
    val middle = sorted.size / 2
    val median = if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
    return (-4..4).minWith(compareBy({ abs(median + it * 7 - clef.middleLineIndex) }, { abs(it) }))
}
