package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Score

enum class RunStatus { COMPLETED, ABORTED }

/**
 * One run as it is kept in history.
 *
 * It is self-contained on purpose: the score as performed and the run configuration are
 * snapshotted, and [startedAtNanos] anchors the raw event timestamps to the run clock, so an
 * old run can be re-evaluated without the content that produced it. [exerciseIds] names the
 * bundled measures the segments came from, in order.
 *
 * Until schema version 3 brings run and segment tables, a record is stored as one attempt
 * row: the run's score is the attempt's score and its config snapshot is the run's.
 */
data class RunRecord(
    val id: String,
    val sessionId: String,
    val exerciseIds: List<String>,
    val startedAtEpochMillis: Long,
    val startedAtNanos: Long,
    val status: RunStatus,
    val abortReason: AbortReason?,
    val config: RunConfig,
    val score: Score,
    val events: List<MidiEvent>,
) {
    init {
        require((status == RunStatus.ABORTED) == (abortReason != null)) {
            "an aborted run has a reason and a completed one does not"
        }
        require(exerciseIds.isNotEmpty()) { "a run names the exercises it was made of" }
    }
}
