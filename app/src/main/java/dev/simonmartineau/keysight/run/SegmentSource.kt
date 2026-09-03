package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.evaluation.EvaluationResult

/** A performed segment and its committed judgement. */
data class CommittedSegment(val segment: Segment, val result: EvaluationResult)

/**
 * Where a run's segments come from, by index.
 *
 * The controller keeps [SEGMENTS_AHEAD] segments beyond the one being performed in an
 * open-ended run, one at a time as the cursor advances, so the page always has its next
 * systems laid out and the last, partially filled system is never one the player is reading.
 * A source is addressed by segment index so that the same source serves a run's first
 * segments and its extensions alike, and is told what the run has committed so far, so a
 * source that adapts can read the performance before it writes the next bar.
 */
fun interface SegmentSource {

    /**
     * Segments [firstIndex] to `firstIndex + count - 1`, segment indices counting from 1 after
     * the count-in; [committed] is the run's judged segments in order.
     */
    fun next(count: Int, firstIndex: Int, committed: List<CommittedSegment>): List<Segment>

    companion object {
        /** Enough for the two-system window and the lookahead at up to six measures a system. */
        const val SEGMENTS_AHEAD = 12
    }
}
