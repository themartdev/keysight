package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.score.ScoreNote

/**
 * Aligns the expected pitch sequence with the played one by edit distance.
 *
 * A matching pitch costs nothing; a wrong pitch, a missing note and an extra note cost one
 * each, so a substitution is always preferred over a missing-plus-extra pair, which is what
 * "beat 3: expected F4, played G4" means.
 *
 * Among equally cheap explanations, the walk from the start of the passage prefers, in order,
 * a match, an extra note, a wrong pitch and a missing note. So a repeated note marks the later
 * repeat as the extra one, a stray note between two correct ones is extra rather than a wrong
 * pitch, and a performance that stops short marks its unplayed tail as missing. The result is
 * fully determined by the two sequences.
 *
 * Order-based alignment is exact for monophonic passages. Chords will need onset-aware
 * matching, which is a later evaluator version.
 */
object PitchAlignment {

    fun align(expected: List<ScoreNote>, played: List<PlayedNote>): List<NoteOutcome> {
        val n = expected.size
        val m = played.size

        // cost[i][j] is the edit distance between expected[i..] and played[j..].
        val cost = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) cost[i][m] = n - i
        for (j in 0..m) cost[n][j] = m - j
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                val substitution = cost[i + 1][j + 1] + if (expected[i].pitch == played[j].pitch) 0 else 1
                val missing = cost[i + 1][j] + 1
                val extra = cost[i][j + 1] + 1
                cost[i][j] = minOf(substitution, missing, extra)
            }
        }

        val outcomes = ArrayList<NoteOutcome>(n + m)
        var i = 0
        var j = 0
        while (i < n || j < m) {
            val here = cost[i][j]
            val samePitch = i < n && j < m && expected[i].pitch == played[j].pitch
            when {
                samePitch && cost[i + 1][j + 1] == here -> {
                    outcomes += NoteOutcome.Correct(expected[i], played[j])
                    i++
                    j++
                }
                j < m && cost[i][j + 1] + 1 == here -> {
                    outcomes += NoteOutcome.Extra(played[j])
                    j++
                }
                i < n && j < m && cost[i + 1][j + 1] + 1 == here -> {
                    outcomes += NoteOutcome.WrongPitch(expected[i], played[j])
                    i++
                    j++
                }
                else -> {
                    outcomes += NoteOutcome.Missing(expected[i])
                    i++
                }
            }
        }
        return outcomes
    }
}
