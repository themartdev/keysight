package dev.simonmartineau.keysight.evaluation

import kotlin.math.abs

/**
 * Where the player's beat sits relative to the click.
 *
 * Input latency and a player's habit of anticipating or trailing the click shift every note
 * by about the same amount. Scoring against the click directly would call such a player late
 * on every note, so the evaluator first estimates that shift from the performance, with the
 * configured tempo as the prior, and measures timing errors from there. The estimate is
 * bounded: it absorbs a fraction of a beat, never a passage played a whole beat late.
 */
object BeatPhase {

    /** Only notes this close to their beat inform the estimate; the rest are timing errors. */
    const val ON_PULSE_BEATS = 0.5

    /** The largest shift the estimate will absorb. */
    const val MAX_PHASE_BEATS = 0.25

    /**
     * The median of the on-pulse [deviationsBeats], each `played - expected`, clamped to
     * ±[MAX_PHASE_BEATS]; 0 when no note is on the pulse.
     */
    fun estimate(deviationsBeats: List<Double>): Double {
        val onPulse = deviationsBeats.filter { abs(it) <= ON_PULSE_BEATS }.sorted()
        if (onPulse.isEmpty()) return 0.0
        val middle = onPulse.size / 2
        val median = if (onPulse.size % 2 == 1) onPulse[middle] else (onPulse[middle - 1] + onPulse[middle]) / 2
        return median.coerceIn(-MAX_PHASE_BEATS, MAX_PHASE_BEATS)
    }

    /** `played - expected` for every matched note in [outcomes]; missing and extra notes have no deviation. */
    fun deviations(outcomes: List<NoteOutcome>, expectedBeats: Map<String, Double>): List<Double> =
        outcomes.mapNotNull { outcome ->
            when (outcome) {
                is NoteOutcome.Correct -> outcome.played.onsetBeat - expectedBeats.getValue(outcome.expected.id)
                is NoteOutcome.WrongPitch -> outcome.played.onsetBeat - expectedBeats.getValue(outcome.expected.id)
                is NoteOutcome.Missing, is NoteOutcome.Extra -> null
            }
        }
}
