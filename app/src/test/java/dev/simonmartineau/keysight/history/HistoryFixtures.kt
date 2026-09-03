package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunRecord
import dev.simonmartineau.keysight.run.RunStatus
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.ScoreNote

/** Stored runs performed by a synthetic player through the real evaluator. */
object HistoryFixtures {

    val session = SessionRecord("s1", startedAtEpochMillis = 1_000_000L, endedAtEpochMillis = null)

    /**
     * A record of [segments] whose raw MIDI is every note struck a shade late at the pitch
     * [played] returns, or left out when it returns null; no judgement yet.
     */
    fun record(
        id: String,
        segments: List<Segment>,
        config: RunConfig,
        startedAtEpochMillis: Long = 1_000_000L,
        sessionId: String = session.id,
        status: RunStatus = RunStatus.COMPLETED,
        reason: AbortReason? = null,
        played: (ScoreNote) -> Pitch? = { it.pitch },
    ): RunRecord {
        val silent = RunRecord(id, sessionId, startedAtEpochMillis, startedAtNanos = 0L, status, reason, config.copy(segmentCount = config.segmentCount?.let { segments.size }), segments, emptyList())
        val timeline = silent.timeline
        val events = silent.score.notes.flatMap { note ->
            val pitch = played(note) ?: return@flatMap emptyList()
            val on = timeline.nanosAtBeat(timeline.beatsOf(note.onset) + 0.04)
            listOf(MidiEvent.noteOn(on, pitch, 90), MidiEvent.noteOff(on + timeline.nanosAtBeat(0.4), pitch))
        }
        return silent.copy(events = events)
    }

    /** [record] judged by the current evaluator on every segment, or on [judged] of them. */
    fun stored(record: RunRecord, judged: Int = record.segments.size): StoredRun =
        StoredRun(record, PerformanceEvaluator.evaluate(record.score, record.timeline, record.startedAtNanos, record.events).segments.take(judged))
}
