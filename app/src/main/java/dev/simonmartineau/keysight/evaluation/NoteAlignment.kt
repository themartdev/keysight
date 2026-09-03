package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.score.ScoreNote
import kotlin.math.abs

/** A notated note with its onset on the performance beat line, as the aligner needs it. */
data class ExpectedNote(val note: ScoreNote, val beat: Double)

/**
 * Aligns the expected notes with the played ones by edit distance over pitch and time, one
 * chord at a time.
 *
 * Expected notes with the same beat are one chord, whichever staff they are on, and played
 * notes struck within [CHORD_SPREAD_BEATS] of each other are one chord too, so both hands are
 * judged as one stream. The outer alignment walks the two chord sequences: leaving out an
 * expected chord costs [MISSING_COST] per note, a played chord nobody asked for costs
 * [EXTRA_COST] per note, and matching two chords costs the inner alignment of their notes,
 * both sorted by pitch: a pair costs 0 if the pitch is right and [WRONG_PITCH_COST] if it is
 * not, plus [TIME_COST_PER_BEAT] per beat between when the note was played and when it was
 * due after the player's beat phase, and a note left unpaired on either side costs as a
 * missing or an extra one.
 *
 * Pitch is the stronger evidence: a right pitch up to two beats off is still that note,
 * played early or late, and only one further away than that becomes a missing note and an
 * extra one; a wrong pitch on the beat is a substitution rather than a missing-plus-extra
 * pair, which is what "beat 3: expected F4, played G4" means; and when a repeated note is
 * dropped, the timing says which repeat went missing, which pitch order alone cannot.
 *
 * Among equally cheap explanations, the walk from the start of the passage prefers, in order,
 * a chord with a correct note in it, an extra chord, a chord of wrong pitches and a missing
 * chord. So a repeated note marks the later repeat as the extra one, a stray note between two
 * correct ones is extra rather than a wrong pitch, and a performance that stops short marks
 * its unplayed tail as missing. A single note per chord is the monophonic alignment exactly.
 * One hand lagging the other by more than the spread is a missing note and an extra one;
 * tolerating that is a later evaluator version, if the union alignment proves insufficient.
 * The result is fully determined by the two sequences and the phase.
 */
object NoteAlignment {

    const val MISSING_COST = 1.0
    const val EXTRA_COST = 1.0
    const val WRONG_PITCH_COST = 1.0

    /** Cost per beat between when a note was played and when it was due; below [WRONG_PITCH_COST] so pitch wins. */
    const val TIME_COST_PER_BEAT = 0.5

    /**
     * Notes struck this close together were meant together. A quarter of a beat is 200 ms at
     * 75 bpm, wider than two hands ever spread on purpose, and shorter than any note value
     * the generator writes; it must stay shorter than the shortest one.
     */
    const val CHORD_SPREAD_BEATS = 0.25

    private const val EPSILON = 1e-9

    fun align(expected: List<ExpectedNote>, played: List<PlayedNote>, phaseBeats: Double = 0.0): List<NoteOutcome> {
        val expectedChords = expected.groupBy { it.beat }.entries.sortedBy { it.key }.map { (_, notes) -> notes.sortedBy { it.note.pitch } }
        val playedChords = chordsOf(played)
        val n = expectedChords.size
        val m = playedChords.size

        val substitutions = HashMap<Pair<Int, Int>, Inner>()
        fun substitution(i: Int, j: Int): Inner = substitutions.getOrPut(i to j) { alignChord(expectedChords[i], playedChords[j], phaseBeats) }

        // cost[i][j] is the cheapest explanation of expectedChords[i..] against playedChords[j..].
        val cost = Array(n + 1) { DoubleArray(m + 1) }
        for (i in n downTo 0) cost[i][m] = (i until n).sumOf { expectedChords[it].size * MISSING_COST }
        for (j in m downTo 0) cost[n][j] = (j until m).sumOf { playedChords[it].size * EXTRA_COST }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                cost[i][j] = minOf(
                    cost[i + 1][j + 1] + substitution(i, j).cost,
                    cost[i + 1][j] + expectedChords[i].size * MISSING_COST,
                    cost[i][j + 1] + playedChords[j].size * EXTRA_COST,
                )
            }
        }

        fun equalCost(a: Double, b: Double) = abs(a - b) <= EPSILON

        val outcomes = ArrayList<NoteOutcome>(expected.size + played.size)
        var i = 0
        var j = 0
        while (i < n || j < m) {
            val here = cost[i][j]
            val both = i < n && j < m
            val inner = if (both) substitution(i, j) else null
            val substitutes = inner != null && equalCost(cost[i + 1][j + 1] + inner.cost, here)
            when {
                substitutes && inner!!.hasCorrect -> {
                    outcomes += inner.outcomes
                    i++
                    j++
                }
                j < m && equalCost(cost[i][j + 1] + playedChords[j].size * EXTRA_COST, here) -> {
                    playedChords[j].forEach { outcomes += NoteOutcome.Extra(it) }
                    j++
                }
                substitutes -> {
                    outcomes += inner!!.outcomes
                    i++
                    j++
                }
                else -> {
                    expectedChords[i].forEach { outcomes += NoteOutcome.Missing(it.note) }
                    i++
                }
            }
        }
        return outcomes
    }

    /**
     * The played notes as chords: in onset order, a note joins the open chord when it starts
     * within [CHORD_SPREAD_BEATS] of the chord's first note and its pitch is not in it yet, so
     * a repeated note struck twice in quick succession is two notes, not a chord of one.
     */
    fun chordsOf(played: List<PlayedNote>): List<List<PlayedNote>> {
        val chords = ArrayList<MutableList<PlayedNote>>()
        for (note in played.sortedWith(compareBy({ it.onsetBeat }, { it.pitch }))) {
            val open = chords.lastOrNull()
            if (open != null && note.onsetBeat - open.first().onsetBeat <= CHORD_SPREAD_BEATS && open.none { it.pitch == note.pitch }) {
                open += note
            } else {
                chords += mutableListOf(note)
            }
        }
        return chords.map { chord -> chord.sortedBy { it.pitch } }
    }

    private class Inner(val cost: Double, val outcomes: List<NoteOutcome>) {
        val hasCorrect: Boolean get() = outcomes.any { it is NoteOutcome.Correct }
    }

    /** The monophonic edit distance over one expected chord and one played chord, both sorted by pitch. */
    private fun alignChord(expected: List<ExpectedNote>, played: List<PlayedNote>, phaseBeats: Double): Inner {
        val n = expected.size
        val m = played.size

        fun pair(i: Int, j: Int): Double {
            val pitch = if (expected[i].note.pitch == played[j].pitch) 0.0 else WRONG_PITCH_COST
            return pitch + abs(played[j].onsetBeat - expected[i].beat - phaseBeats) * TIME_COST_PER_BEAT
        }

        val cost = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 0..n) cost[i][m] = (n - i) * MISSING_COST
        for (j in 0..m) cost[n][j] = (m - j) * EXTRA_COST
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                cost[i][j] = minOf(cost[i + 1][j + 1] + pair(i, j), cost[i + 1][j] + MISSING_COST, cost[i][j + 1] + EXTRA_COST)
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
                samePitch && equalCost(cost[i + 1][j + 1] + pair(i, j), here) -> {
                    outcomes += NoteOutcome.Correct(expected[i].note, played[j])
                    i++
                    j++
                }
                j < m && equalCost(cost[i][j + 1] + EXTRA_COST, here) -> {
                    outcomes += NoteOutcome.Extra(played[j])
                    j++
                }
                both && equalCost(cost[i + 1][j + 1] + pair(i, j), here) -> {
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
        return Inner(cost[0][0], outcomes)
    }
}
