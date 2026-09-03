package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.timing.RunTimeline

enum class RunStatus { COMPLETED, ABORTED }

/**
 * One run as it is kept in history.
 *
 * It is self-contained on purpose: the performed [segments], each with its score, and the run
 * configuration are snapshotted, and [startedAtNanos] anchors the raw event timestamps to the
 * run clock, so an old run can be re-evaluated without the content that produced it. The run's
 * [score] is rebuilt from the segments the way it was read, and [timeline] is the run as
 * performed, the count-in and the stored segments, closed, on the beat line the raw MIDI was
 * captured on, so replaying the evaluator over a record judges exactly what the live run
 * judged. [seed] is the run seed the segments were generated from, null for a run of bundled
 * content.
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
    val seed: Long? = null,
) {
    init {
        require((status == RunStatus.ABORTED) == (abortReason != null)) {
            "an aborted run has a reason and a completed one does not"
        }
        require(segments.isNotEmpty()) { "a run keeps the segments it performed" }
    }

    val score: Score get() = runScore(segments.map { it.score })

    val timeline: RunTimeline
        get() = RunTimeline(
            tempoBpm = config.tempoBpm,
            timeSignature = score.timeSignature,
            segmentCount = segments.size + 1,
            metronomeThroughout = config.metronome == MetronomeMode.THROUGHOUT,
        )
}
