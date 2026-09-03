package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.evaluation.RunEvaluation
import dev.simonmartineau.keysight.run.RunRecord
import dev.simonmartineau.keysight.run.RunStatus

/**
 * A run as history reads it: the stored [record] and the judgement of each of its segments in
 * order, index k - 1 for segment k. A completed run has one judgement per segment; an
 * aborted run has one for every segment the live run had committed, and none for the bar the
 * abort landed in, which was never judged.
 *
 * The judgements are at whatever evaluator version stored them. [isCurrent] says whether they
 * are the ones the app would make today, and [reevaluated] makes those from the record's
 * segments and raw MIDI, which is enough on its own.
 */
data class StoredRun(
    val record: RunRecord,
    val evaluations: List<EvaluationResult>,
) {
    init {
        require(evaluations.size <= record.segments.size) { "${evaluations.size} judgements for ${record.segments.size} segments" }
    }

    /** The run's judgement as the summary reads it, the running phase being the last commit's. */
    val evaluation: RunEvaluation
        get() = RunEvaluation(evaluations, phaseBeats = evaluations.lastOrNull()?.rhythm?.phaseBeats ?: 0.0)

    /** How many segments the run is judged on: every one of a completed run, the committed ones of an aborted run. */
    val judgedSegments: Int
        get() = if (record.status == RunStatus.COMPLETED) record.segments.size else evaluations.size

    /** Whether every judgement is the current evaluator's and every judged segment has one. */
    val isCurrent: Boolean
        get() = evaluations.size == judgedSegments && evaluations.all { it.evaluatorVersion == PerformanceEvaluator.EVALUATOR_VERSION }

    /**
     * The same run judged by the current evaluator, replaying every commit from the stored
     * segments and raw MIDI: what a live run showed, at today's rules. The record is untouched.
     */
    fun reevaluated(): StoredRun {
        val replay = PerformanceEvaluator.evaluate(record.score, record.timeline, record.startedAtNanos, record.events)
        return copy(evaluations = replay.segments.take(judgedSegments))
    }
}
