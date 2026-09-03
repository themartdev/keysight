package dev.simonmartineau.keysight.evaluation

import kotlin.math.abs

/**
 * Where the player's beat sits relative to the click.
 *
 * Input latency and a player's habit of anticipating or trailing the click shift every note
 * by about the same amount. Scoring against the click directly would call such a player late
 * on every note, so the evaluator first estimates that shift from the performance, with the
 * configured tempo as the prior, and measures timing errors from there.
 *
 * Over a run the phase is re-estimated at every commit from the residuals of the segment just
 * played, with the previous phase as the prior and a bounded [step] per segment: a pulse that
 * drifts slowly is followed, a note that jumps off the pulse is not. The estimate absorbs a
 * fraction of a beat at a time, never a passage played a whole beat late.
 */
object BeatPhase {

    /** Only notes this close to their beat inform the estimate; the rest are timing errors. */
    const val ON_PULSE_BEATS = 0.5

    /** The largest shift the first estimate, and the whole estimate under a click, will absorb. */
    const val MAX_PHASE_BEATS = 0.25

    /** The most the phase moves per segment while the metronome sounds through the run: the pulse is given, not drifting. */
    const val MAX_STEP_WITH_CLICK_BEATS = 0.02

    /** The most the phase moves per segment with no click after the count-in, enough to follow a few percent of drift. */
    const val MAX_STEP_WITHOUT_CLICK_BEATS = 0.125

    /**
     * The median of the on-pulse [deviationsBeats], each `played - expected`, clamped to
     * ±[MAX_PHASE_BEATS]; 0 when no note is on the pulse. The estimate of a run's first segment.
     */
    fun estimate(deviationsBeats: List<Double>): Double = step(0.0, deviationsBeats, MAX_PHASE_BEATS)

    /**
     * [previous] moved towards the median of the on-pulse [residualsBeats], each
     * `played - expected - previous`, by at most [maxStepBeats]; [previous] when no note is on
     * the pulse.
     */
    fun step(previous: Double, residualsBeats: List<Double>, maxStepBeats: Double): Double {
        require(maxStepBeats >= 0.0) { "maxStepBeats must not be negative" }
        val onPulse = residualsBeats.filter { abs(it) <= ON_PULSE_BEATS }.sorted()
        if (onPulse.isEmpty()) return previous
        val middle = onPulse.size / 2
        val median = if (onPulse.size % 2 == 1) onPulse[middle] else (onPulse[middle - 1] + onPulse[middle]) / 2
        return previous + median.coerceIn(-maxStepBeats, maxStepBeats)
    }

    /** `played - expected` for every matched note in [outcomes]; missing, extra and too-late notes have no deviation. */
    fun deviations(outcomes: List<NoteOutcome>, expectedBeats: Map<String, Double>): List<Double> =
        outcomes.mapNotNull { outcome ->
            when (outcome) {
                is NoteOutcome.Correct -> outcome.played.onsetBeat - expectedBeats.getValue(outcome.expected.id)
                is NoteOutcome.WrongPitch -> outcome.played.onsetBeat - expectedBeats.getValue(outcome.expected.id)
                is NoteOutcome.Missing, is NoteOutcome.Extra, is NoteOutcome.TooLate -> null
            }
        }
}
