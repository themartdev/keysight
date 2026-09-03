package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.timing.AttemptTimeline

/**
 * Scores one attempt from its score and its raw MIDI.
 *
 * The evaluator is deterministic: it has no clock and no state, so the same stored attempt
 * always yields the same result, and [EVALUATOR_VERSION] changes whenever the judgement would.
 */
object PerformanceEvaluator {

    /** Version 1: pitch correctness by order-based alignment. */
    const val EVALUATOR_VERSION = 1

    /** How far ahead of beat 0 a note may land and still count as the first note. */
    const val EARLY_GRACE_BEATS = 0.5

    fun evaluate(
        score: Score,
        events: List<MidiEvent>,
        timeline: AttemptTimeline,
        startedAtNanos: Long,
    ): EvaluationResult {
        val played = PlayedNotes.extract(events, timeline, startedAtNanos, EARLY_GRACE_BEATS)
        val outcomes = PitchAlignment.align(score.notesInPerformanceOrder, played)
        return EvaluationResult(EVALUATOR_VERSION, PitchResult(outcomes))
    }
}
