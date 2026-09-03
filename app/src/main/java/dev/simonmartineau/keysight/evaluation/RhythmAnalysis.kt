package dev.simonmartineau.keysight.evaluation

import kotlin.math.abs

/**
 * Turns an alignment into rhythm judgements.
 *
 * Every matched note is timed against its beat after the player's phase; consecutive matched
 * notes that arrive further apart than the score says are a pause; the slope of played beats
 * against expected beats is the player's tempo; and continuity summarises whether the pulse
 * held. All thresholds are in beats, so they scale with the tempo.
 */
object RhythmAnalysis {

    /** A note within this of its beat is on time. At 72 bpm it is about 100 ms either way. */
    const val ON_TIME_TOLERANCE_BEATS = 0.125

    /** A note further than this from its beat has left the pulse. */
    const val OFF_PULSE_BEATS = 0.5

    /** A gap between matched notes this much longer than notated is a pause. */
    const val PAUSE_MIN_BEATS = 0.5

    fun analyse(outcomes: List<NoteOutcome>, expectedBeats: Map<String, Double>, phaseBeats: Double): RhythmResult {
        val timings = outcomes.mapNotNull { outcome ->
            val (note, played) = when (outcome) {
                is NoteOutcome.Correct -> outcome.expected to outcome.played
                is NoteOutcome.WrongPitch -> outcome.expected to outcome.played
                is NoteOutcome.Missing, is NoteOutcome.Extra, is NoteOutcome.TooLate -> return@mapNotNull null
            }
            val expected = expectedBeats.getValue(note.id)
            val error = played.onsetBeat - expected - phaseBeats
            NoteTiming(
                noteId = note.id,
                expectedBeat = expected,
                playedBeat = played.onsetBeat,
                errorBeats = error,
                judgement = when {
                    abs(error) <= ON_TIME_TOLERANCE_BEATS -> TimingJudgement.ON_TIME
                    error < 0 -> TimingJudgement.EARLY
                    else -> TimingJudgement.LATE
                },
            )
        }

        val pauses = timings.zipWithNext().mapNotNull { (earlier, later) ->
            val excess = (later.playedBeat - earlier.playedBeat) - (later.expectedBeat - earlier.expectedBeat)
            if (excess > PAUSE_MIN_BEATS) Pause(later.noteId, excess) else null
        }

        return RhythmResult(
            timings = timings,
            phaseBeats = phaseBeats,
            tempoRatio = tempoRatio(timings),
            pauses = pauses,
            continuity = continuity(timings, pauses),
        )
    }

    /**
     * Least-squares slope of played beat over expected beat is the length of the player's beat
     * in configured beats; its inverse is the tempo ratio. Null with fewer than two distinct
     * expected beats, or if the fit is degenerate.
     */
    private fun tempoRatio(timings: List<NoteTiming>): Double? {
        if (timings.map { it.expectedBeat }.distinct().size < 2) return null
        val meanExpected = timings.sumOf { it.expectedBeat } / timings.size
        val meanPlayed = timings.sumOf { it.playedBeat } / timings.size
        val covariance = timings.sumOf { (it.expectedBeat - meanExpected) * (it.playedBeat - meanPlayed) }
        val variance = timings.sumOf { (it.expectedBeat - meanExpected) * (it.expectedBeat - meanExpected) }
        val slope = covariance / variance
        return if (slope > 0.0) 1.0 / slope else null
    }

    private fun continuity(timings: List<NoteTiming>, pauses: List<Pause>): Continuity {
        if (timings.isEmpty()) return Continuity.LOST
        val onPulse = timings.count { abs(it.errorBeats) <= OFF_PULSE_BEATS }
        return when {
            pauses.size >= 2 || onPulse * 2 < timings.size -> Continuity.LOST
            pauses.isNotEmpty() || onPulse < timings.size -> Continuity.HESITANT
            else -> Continuity.GOOD
        }
    }
}
