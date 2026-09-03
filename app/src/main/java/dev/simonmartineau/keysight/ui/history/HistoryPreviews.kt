package dev.simonmartineau.keysight.ui.history

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.history.StoredRun
import dev.simonmartineau.keysight.history.summarise
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunRecord
import dev.simonmartineau.keysight.run.RunStatus
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.generatedSegment
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.ui.theme.KeySightTheme

/**
 * Hand-built history for the Android Studio previews: two sessions of generated runs, each
 * performed by a synthetic player through the real evaluator, so the summaries, the marks
 * and the level lines are what a phone would show.
 */
private object PreviewHistory {

    private const val DAY_MILLIS = 86_400_000L
    private val today = 1_788_400_000_000L

    private val config = RunConfig(tempoBpm = 80.0, metronome = MetronomeMode.COUNT_IN_ONLY, mode = VisibilityMode.FLASH, lookaheadBeats = 4.0, segmentCount = 4)
    private val thirds = ExerciseConfig.DEFAULT.copy(keySignature = KeySignature(1))
    private val fourths = thirds.copy(maxInterval = 3)

    val current = SessionRecord("current", today, endedAtEpochMillis = null)
    val yesterday = SessionRecord("yesterday", today - DAY_MILLIS, today - DAY_MILLIS + 1_500_000L)

    /**
     * A run [played] through the evaluator: every note struck a shade late unless [played]
     * says which pitch to strike instead, or null to leave it out.
     */
    private fun run(
        id: String,
        session: SessionRecord,
        minutesIn: Long,
        segments: List<Segment>,
        config: RunConfig,
        status: RunStatus = RunStatus.COMPLETED,
        reason: AbortReason? = null,
        played: (ScoreNote) -> Pitch? = { it.pitch },
    ): StoredRun {
        val record = RunRecord(
            id = id,
            sessionId = session.id,
            startedAtEpochMillis = session.startedAtEpochMillis + minutesIn * 60_000L,
            startedAtNanos = 0L,
            status = status,
            abortReason = reason,
            config = config.copy(segmentCount = config.segmentCount?.let { segments.size }),
            segments = segments,
            events = emptyList(),
            seed = 7L,
        )
        val timeline = record.timeline
        val events = record.score.notes.flatMap { note ->
            val pitch = played(note) ?: return@flatMap emptyList()
            val on = timeline.nanosAtBeat(timeline.beatsOf(note.onset) + 0.04)
            listOf(MidiEvent.noteOn(on, pitch, 90), MidiEvent.noteOff(on + timeline.nanosAtBeat(0.4), pitch))
        }
        val performed = record.copy(events = events)
        val judged = PerformanceEvaluator.evaluate(performed.score, timeline, 0L, events).segments
        return StoredRun(performed, if (status == RunStatus.COMPLETED) judged else judged.dropLast(1))
    }

    private fun bars(count: Int, level: (Int) -> ExerciseConfig) = (1..count).map { generatedSegment(7L, it, level(it)) }

    /** Four bars at thirds, one wrong note and one dropped in bar 2, played correctly otherwise. */
    val first = run("r1", current, 0, bars(4) { thirds }, config) { note ->
        when (note.id) {
            "2:n2" -> Pitch(note.pitch.midiNoteNumber + 1)
            "2:n3" -> null
            else -> note.pitch
        }
    }

    /** An open-ended run the controller moved up to fourths from bar 3, stopped after bar 6, clean. */
    val moved = run("r2", current, 4, bars(6) { if (it >= 3) fourths else thirds }, config.copy(segmentCount = null))

    /** The next run at three beats ahead, aborted in bar 3 by the keyboard. */
    val aborted = run("r3", current, 9, bars(3) { fourths }, config.copy(lookaheadBeats = 3.0), RunStatus.ABORTED, AbortReason.MIDI_DISCONNECTED)

    val earlier = run("r0", yesterday, 0, bars(4) { ExerciseConfig.DEFAULT.copy(hands = Hands.LEFT) }, config.copy(mode = VisibilityMode.OPEN_SCORE))

    val sessions = listOf(current, yesterday)

    val currentSummary = summarise(current, listOf(first, moved, aborted))

    val yesterdaySummary = summarise(yesterday, listOf(earlier))
}

@Composable
private fun PreviewHistoryScreen(expanded: String?, summary: dev.simonmartineau.keysight.history.SessionSummary?, sessions: List<SessionRecord>? = PreviewHistory.sessions) {
    KeySightTheme {
        HistoryContent(
            sessions = sessions,
            currentSessionId = PreviewHistory.current.id,
            expanded = expanded,
            summary = summary,
            onToggle = {},
            onOpenRun = {},
            onBack = {},
        )
    }
}

@Preview(name = "History, loading", showBackground = true)
@Composable
private fun HistoryLoadingPreview() = PreviewHistoryScreen(expanded = null, summary = null, sessions = null)

@Preview(name = "History, nothing recorded", showBackground = true)
@Composable
private fun HistoryEmptyPreview() = PreviewHistoryScreen(expanded = null, summary = null, sessions = emptyList())

@Preview(name = "History, sessions collapsed", showBackground = true)
@Composable
private fun HistoryCollapsedPreview() = PreviewHistoryScreen(expanded = null, summary = null)

@Preview(name = "History, this session expanded and loading", showBackground = true)
@Composable
private fun HistoryExpandedLoadingPreview() = PreviewHistoryScreen(expanded = PreviewHistory.current.id, summary = null)

@Preview(name = "History, this session: moves, an aborted run, weakest bars", showBackground = true, heightDp = 900)
@Composable
private fun HistoryExpandedPreview() = PreviewHistoryScreen(expanded = PreviewHistory.current.id, summary = PreviewHistory.currentSummary)

@Preview(name = "History, this session, dark", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryExpandedDarkPreview() = PreviewHistoryScreen(expanded = PreviewHistory.current.id, summary = PreviewHistory.currentSummary)

@Preview(name = "History, an earlier session in the open score, clean", showBackground = true)
@Composable
private fun HistoryEarlierPreview() = PreviewHistoryScreen(expanded = PreviewHistory.yesterday.id, summary = PreviewHistory.yesterdaySummary)

@Preview(name = "Run page", showBackground = true)
@Composable
private fun RunPagePreview() {
    KeySightTheme { RunContent(RunPageState.Loaded(PreviewHistory.first), onBack = {}) }
}

@Preview(name = "Run page, level moved within the run", showBackground = true)
@Composable
private fun RunPageMovedPreview() {
    KeySightTheme { RunContent(RunPageState.Loaded(PreviewHistory.moved), onBack = {}) }
}

@Preview(name = "Run page, aborted", showBackground = true)
@Composable
private fun RunPageAbortedPreview() {
    KeySightTheme { RunContent(RunPageState.Loaded(PreviewHistory.aborted), onBack = {}) }
}

@Preview(name = "Run page, loading", showBackground = true)
@Composable
private fun RunPageLoadingPreview() {
    KeySightTheme { RunContent(RunPageState.Loading, onBack = {}) }
}

@Preview(name = "Run page, missing", showBackground = true)
@Composable
private fun RunPageMissingPreview() {
    KeySightTheme { RunContent(RunPageState.Missing, onBack = {}) }
}
