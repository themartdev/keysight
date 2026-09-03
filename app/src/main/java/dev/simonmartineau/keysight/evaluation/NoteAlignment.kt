package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.score.ScoreNote
import kotlin.math.abs

/** A notated note with its onset on the performance beat line, as the aligner needs it. */
data class ExpectedNote(val note: ScoreNote, val beat: Double)

/**
 * Aligns the expected note sequence with the played one by edit distance over pitch and time.
 *
 * Matching a played note to an expected one costs 0 if the pitch is right and 1 if it is not,
 * plus half a point per beat between when it was played and when it was due, after the
 * player's beat phase. A missing note and an extra note cost 1 each. Pitch is the stronger
 * evidence: a right pitch up to two beats off is still that note, played early or late, and
 * only one further away than that becomes a missing note and an extra one; a wrong pitch on
 * the beat is a substitution rather than a missing-plus-extra pair, which is what "beat 3:
 * expected F4, played G4" means; and when a repeated note is dropped, the timing says which
 * repeat went missing, which pitch order alone cannot.
 *
 * Among equally cheap explanations, the walk from the start of the passage prefers, in order,
 * a match, an extra note, a wrong pitch and a missing note. So a repeated note marks the later
 * repeat as the extra one, a stray note between two correct ones is extra rather than a wrong
 * pitch, and a performance that stops short marks its unplayed tail as missing. The result is
 * fully determined by the two sequences and the phase.
 *
 * Both sequences are walked in order, which is exact for monophonic passages. Chords will
 * need onset-grouped matching, which is a later evaluator version.
 */
object NoteAlignment {

    const val MISSING_COST = 1.0
    const val EXTRA_COST = 1.0
    const val WRONG_PITCH_COST = 1.0

    /** Cost per beat between when a note was played and when it was due; below [WRONG_PITCH_COST] so pitch wins. */
    const val TIME_COST_PER_BEAT = 0.5

    private const val EPSILON = 1e-9

    fun align(expected: List<ExpectedNote>, played: List<PlayedNote>, phaseBeats: Double = 0.0): List<NoteOutcome> {
        val n = expected.size
        val m = played.size

        fun substitution(i: Int, j: Int): Double {
            val pitch = if (expected[i].note.pitch == played[j].pitch) 0.0 else WRONG_PITCH_COST
            return pitch + abs(played[j].onsetBeat - expected[i].beat - phaseBeats) * TIME_COST_PER_BEAT
        }

        // cost[i][j] is the cheapest explanation of expected[i..] against played[j..].
        val cost = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 0..n) cost[i][m] = (n - i) * MISSING_COST
        for (j in 0..m) cost[n][j] = (m - j) * EXTRA_COST
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                cost[i][j] = minOf(
                    cost[i + 1][j + 1] + substitution(i, j),
                    cost[i + 1][j] + MISSING_COST,
                    cost[i][j + 1] + EXTRA_COST,
                )
            }
        }

        fun equalCost(a: Double, b: Double) = abs(a - b) <= EPSILON

        val outcomes = ArrayList<NoteOutcome>(n + m)
        var i = 0
        var j = 0
        while (i < n || j < m) {
            val here = cost[i][j]
            val both = i < n && j < m
            val samePitch = both && expected[i].note.pitch == played[j].pitch
            when {
                samePitch && equalCost(cost[i + 1][j + 1] + substitution(i, j), here) -> {
                    outcomes += NoteOutcome.Correct(expected[i].note, played[j])
                    i++
                    j++
                }
                j < m && equalCost(cost[i][j + 1] + EXTRA_COST, here) -> {
                    outcomes += NoteOutcome.Extra(played[j])
                    j++
                }
                both && equalCost(cost[i + 1][j + 1] + substitution(i, j), here) -> {
                    outcomes += NoteOutcome.WrongPitch(expected[i].note, played[j])
                    i++
                    j++
                }
                else -> {
                    outcomes += NoteOutcome.Missing(expected[i].note)
                    i++
                }
            }
        }
        return outcomes
    }
}
