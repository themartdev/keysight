package dev.simonmartineau.keysight.attempt

import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Score

enum class AttemptStatus { COMPLETED, ABORTED }

/**
 * One attempt as it is kept in history.
 *
 * It is self-contained on purpose: the score and the flash configuration are snapshotted, and
 * [startedAtNanos] anchors the raw event timestamps to the attempt clock, so an old attempt
 * can be re-evaluated without the exercise pack that produced it.
 */
data class AttemptRecord(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val startedAtEpochMillis: Long,
    val startedAtNanos: Long,
    val status: AttemptStatus,
    val abortReason: AbortReason?,
    val config: FlashConfig,
    val score: Score,
    val events: List<MidiEvent>,
) {
    init {
        require((status == AttemptStatus.ABORTED) == (abortReason != null)) {
            "an aborted attempt has a reason and a completed one does not"
        }
    }
}
