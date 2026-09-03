package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Score

enum class RunStatus { COMPLETED, ABORTED }

/**
 * One run as it is kept in history.
 *
 * It is self-contained on purpose: the performed [segments], each with its score, and the run
 * configuration are snapshotted, and [startedAtNanos] anchors the raw event timestamps to the
 * run clock, so an old run can be re-evaluated without the content that produced it. The run's
 * [score] is rebuilt from the segments the way it was read.
 */
data class RunRecord(
    val id: String,
    val sessionId: String,
    val startedAtEpochMillis: Long,
    val startedAtNanos: Long,
    val status: RunStatus,
    val abortReason: AbortReason?,
    val config: RunConfig,
    val segments: List<Segment>,
    val events: List<MidiEvent>,
) {
    init {
        require((status == RunStatus.ABORTED) == (abortReason != null)) {
            "an aborted run has a reason and a completed one does not"
        }
        require(segments.isNotEmpty()) { "a run keeps the segments it performed" }
    }

    val score: Score get() = runScore(segments.map { it.score })
}
