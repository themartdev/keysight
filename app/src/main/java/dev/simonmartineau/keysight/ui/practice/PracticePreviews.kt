package dev.simonmartineau.keysight.ui.practice

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.evaluation.BeatPhase
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.evaluation.PlayedNote
import dev.simonmartineau.keysight.evaluation.RhythmAnalysis
import dev.simonmartineau.keysight.evaluation.RunEvaluation
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.notation.Mask
import dev.simonmartineau.keysight.notation.noteMarks
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunContext
import dev.simonmartineau.keysight.run.RunState
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.runMask
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Staff
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.settings.ThemeMode
import dev.simonmartineau.keysight.ui.notation.RunPage
import dev.simonmartineau.keysight.ui.notation.RunSummaryPage
import dev.simonmartineau.keysight.ui.theme.KeySightTheme

/**
 * Hand-built states for the Android Studio previews, one per thing the stage can show, so
 * the engraving can be eyeballed without a keyboard or a device.
 */
private object PreviewData {

    private fun oneMeasure(clef: Clef, vararg notes: ScoreNote, key: KeySignature = KeySignature.C_MAJOR) = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        keySignature = key,
        staves = listOf(Staff(clef)),
        measureCount = 1,
        notes = notes.toList(),
    )

    /** C4 D4 E4 F4, the shape of the bundled content. */
    val cdef: Score = oneMeasure(
        Clef.TREBLE,
        ScoreNote("n1", SpelledPitch(Step.C, octave = 4), Ticks.ZERO, Ticks.QUARTER),
        ScoreNote("n2", SpelledPitch(Step.D, octave = 4), Ticks.QUARTER, Ticks.QUARTER),
        ScoreNote("n3", SpelledPitch(Step.E, octave = 4), Ticks.quarters(2), Ticks.QUARTER),
        ScoreNote("n4", SpelledPitch(Step.F, octave = 4), Ticks.quarters(3), Ticks.QUARTER),
    )

    /** G4 F4 E4 D4, the way down. */
    val gfed: Score = oneMeasure(
        Clef.TREBLE,
        ScoreNote("n1", SpelledPitch(Step.G, octave = 4), Ticks.ZERO, Ticks.QUARTER),
        ScoreNote("n2", SpelledPitch(Step.F, octave = 4), Ticks.QUARTER, Ticks.QUARTER),
        ScoreNote("n3", SpelledPitch(Step.E, octave = 4), Ticks.quarters(2), Ticks.QUARTER),
        ScoreNote("n4", SpelledPitch(Step.D, octave = 4), Ticks.quarters(3), Ticks.QUARTER),
    )

    /** A half note then two quarters. */
    val halfThenSteps: Score = oneMeasure(
        Clef.TREBLE,
        ScoreNote("n1", SpelledPitch(Step.E, octave = 4), Ticks.ZERO, Ticks.HALF),
        ScoreNote("n2", SpelledPitch(Step.F, octave = 4), Ticks.HALF, Ticks.QUARTER),
        ScoreNote("n3", SpelledPitch(Step.G, octave = 4), Ticks.quarters(3), Ticks.QUARTER),
    )

    /** Ledger lines both ways, both stem directions, a half note and an accidental. */
    val wide: Score = oneMeasure(
        Clef.TREBLE,
        ScoreNote("w1", SpelledPitch(Step.A, octave = 3), Ticks.ZERO, Ticks.HALF),
        ScoreNote("w2", SpelledPitch(Step.B, octave = 5), Ticks.HALF, Ticks.QUARTER),
        ScoreNote("w3", SpelledPitch(Step.F, alteration = 1, octave = 4), Ticks.quarters(3), Ticks.QUARTER),
    )

    /** The same shape in bass clef. */
    val bass: Score = oneMeasure(
        Clef.BASS,
        ScoreNote("b1", SpelledPitch(Step.C, octave = 3), Ticks.ZERO, Ticks.QUARTER),
        ScoreNote("b2", SpelledPitch(Step.E, octave = 3), Ticks.QUARTER, Ticks.QUARTER),
        ScoreNote("b3", SpelledPitch(Step.G, octave = 3), Ticks.quarters(2), Ticks.HALF),
    )

    /** E flat major with a natural against the key, then the flat restored in the same measure. */
    val flatKey: Score = oneMeasure(
        Clef.TREBLE,
        ScoreNote("f1", SpelledPitch(Step.E, alteration = -1, octave = 4), Ticks.ZERO, Ticks.QUARTER),
        ScoreNote("f2", SpelledPitch(Step.E, octave = 4), Ticks.QUARTER, Ticks.QUARTER),
        ScoreNote("f3", SpelledPitch(Step.E, alteration = -1, octave = 4), Ticks.quarters(2), Ticks.QUARTER),
        ScoreNote("f4", SpelledPitch(Step.B, alteration = -1, octave = 4), Ticks.quarters(3), Ticks.QUARTER),
        key = KeySignature(-3),
    )

    /** One measure of a grand staff in G major with the voice on [staff] and the other hand resting. */
    private fun grandStaffMeasure(staff: Int, vararg steps: Step): Score = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        keySignature = KeySignature(1),
        staves = listOf(Staff(Clef.TREBLE), Staff(Clef.BASS)),
        measureCount = 1,
        notes = steps.mapIndexed { i, step ->
            ScoreNote("g$i", SpelledPitch(step, octave = if (staff == 0) 4 else 2), Ticks.quarters(i), Ticks.QUARTER, staff = staff)
        },
    )

    val config = RunConfig(tempoBpm = 80.0, metronome = MetronomeMode.COUNT_IN_ONLY, mode = VisibilityMode.FLASH, lookaheadBeats = 4.0, segmentCount = 6)

    val content = ContentConfig(KeySignature.C_MAJOR, Hands.RIGHT)

    /** Six bars on one staff: three systems on a phone. */
    val run = RunContext(
        segments = listOf(cdef, gfed, halfThenSteps, cdef, halfThenSteps, gfed).mapIndexed { i, score -> Segment("m$i", score) },
        config = config,
    )

    val grandStaffRun = RunContext(
        segments = listOf(
            Segment("g1", grandStaffMeasure(1, Step.G, Step.A, Step.B, Step.C)),
            Segment("g2", grandStaffMeasure(0, Step.D, Step.C, Step.B, Step.A)),
            Segment("g3", grandStaffMeasure(0, Step.G, Step.A, Step.B, Step.C)),
            Segment("g4", grandStaffMeasure(1, Step.C, Step.B, Step.A, Step.G)),
        ),
        config = config.copy(segmentCount = 4),
    )

    val connection: MidiConnection = MidiConnection.Connected("Preview keyboard")

    private fun played(midi: Int, onsetBeat: Double) =
        PlayedNote(Pitch(midi), onsetBeat, onsetBeat + 0.4, velocity = 90, onsetNanos = 0L)

    /** The first performed bar of [run]: one of each outcome, correct, wrong (F sharp for D), missing, an extra A between beats, correct. */
    private val firstBar = run.score.notes.take(4)

    private val firstBarOutcomes: List<NoteOutcome> = listOf(
        NoteOutcome.Correct(firstBar[0], played(60, 4.02)),
        NoteOutcome.WrongPitch(firstBar[1], played(66, 4.8)),
        NoteOutcome.Missing(firstBar[2]),
        NoteOutcome.Extra(played(69, 6.6)),
        NoteOutcome.Correct(firstBar[3], played(65, 7.7)),
    )

    private val expectedBeats = run.score.notes.associate { it.id to run.timeline.beatsOf(it.onset) }

    /** One committed result per bar: the first with one of each outcome, the rest played a touch late. */
    private fun segmentResult(segment: Int): EvaluationResult {
        val outcomes = if (segment == 1) firstBarOutcomes else run.score.notesInMeasure(segment).map {
            NoteOutcome.Correct(it, played(it.pitch.midiNoteNumber, run.timeline.beatsOf(it.onset) + 0.05))
        }
        val phase = BeatPhase.estimate(BeatPhase.deviations(outcomes, expectedBeats))
        return EvaluationResult(PerformanceEvaluator.EVALUATOR_VERSION, PitchResult(outcomes), RhythmAnalysis.analyse(outcomes, expectedBeats, phase))
    }

    /** The whole run committed: one early, one late, one pause in bar 1. */
    val evaluation = RunEvaluation((1..run.lastSegment).map(::segmentResult), phaseBeats = 0.05)

    /** Bars 1 and 2 committed, the cursor in bar 3. */
    val partlyCommitted = RunEvaluation(evaluation.segments.take(2), phaseBeats = 0.05)

    val ready = RunState.Ready(run)

    val countingIn = RunState.CountingIn(run, startedAtNanos = 0L, captured = emptyList())

    val performing = RunState.Performing(run, startedAtNanos = 0L, captured = emptyList())

    val performingWithMarks = performing.copy(evaluation = partlyCommitted)

    val stopping = performing.copy(stopAfter = 2)

    val grandStaffPerforming = RunState.Performing(grandStaffRun, startedAtNanos = 0L, captured = emptyList())

    val summary = RunState.Summary(
        run,
        startedAtNanos = 0L,
        captured = emptyList(),
        lastSegment = run.lastSegment,
        evaluation = evaluation,
    )

    val aborted = RunState.Aborted(run, AbortReason.MIDI_DISCONNECTED, startedAtNanos = 0L, captured = emptyList(), lastSegment = 3, evaluation = partlyCommitted)

    val actions = PracticeActions(
        start = {},
        stop = {},
        next = {},
        retry = {},
        setMode = {},
        setLookaheadBeats = {},
        setTempo = {},
        setMetronome = {},
        setSegmentCount = {},
        setKey = {},
        setHands = {},
        setTheme = {},
    )
}

@Composable
private fun PreviewScreen(state: RunState?, config: RunConfig = PreviewData.config) {
    KeySightTheme {
        PracticeContent(
            state = state,
            connection = PreviewData.connection,
            config = config,
            content = PreviewData.content,
            theme = ThemeMode.SYSTEM,
            loadError = null,
            actions = PreviewData.actions,
        )
    }
}

@Preview(name = "Ready, Flash", showBackground = true)
@Composable
private fun ReadyPreview() = PreviewScreen(PreviewData.ready)

@Preview(name = "Ready, Open score", showBackground = true)
@Composable
private fun ReadyOpenScorePreview() {
    val config = PreviewData.config.copy(mode = VisibilityMode.OPEN_SCORE)
    PreviewScreen(RunState.Ready(PreviewData.run.copy(config = config)), config)
}

@Preview(name = "Counting in", showBackground = true)
@Composable
private fun CountingInPreview() = PreviewScreen(PreviewData.countingIn)

@Preview(name = "Performing", showBackground = true)
@Composable
private fun PerformingPreview() = PreviewScreen(PreviewData.performing)

@Preview(name = "Performing, bars 1 and 2 marked", showBackground = true)
@Composable
private fun PerformingWithMarksPreview() = PreviewScreen(PreviewData.performingWithMarks)

@Preview(name = "Performing, stopping after bar 2", showBackground = true)
@Composable
private fun StoppingPreview() = PreviewScreen(PreviewData.stopping)

@Preview(name = "Performing, grand staff", showBackground = true)
@Composable
private fun GrandStaffPreview() = PreviewScreen(PreviewData.grandStaffPerforming)

@Preview(name = "Performing, grand staff, landscape", showBackground = true, widthDp = 800, heightDp = 360)
@Composable
private fun GrandStaffLandscapePreview() = PreviewScreen(PreviewData.grandStaffPerforming)

@Preview(name = "Summary", showBackground = true)
@Composable
private fun SummaryPreview() = PreviewScreen(PreviewData.summary)

@Preview(name = "Summary, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SummaryDarkPreview() = PreviewScreen(PreviewData.summary)

@Preview(name = "Summary, tablet", showBackground = true, widthDp = 1000, heightDp = 700)
@Composable
private fun SummaryTabletPreview() = PreviewScreen(PreviewData.summary)

@Preview(name = "Aborted", showBackground = true)
@Composable
private fun AbortedPreview() = PreviewScreen(PreviewData.aborted)

@Composable
private fun PagePreview(score: Score, mask: Mask = Mask.NONE, cursorTicks: Ticks? = null, height: Int = 220) {
    KeySightTheme {
        Surface {
            Box(Modifier.width(360.dp).height(height.dp)) {
                RunPage(score, mask = mask, focusTicks = cursorTicks ?: Ticks.ZERO, cursorTicks = cursorTicks)
            }
        }
    }
}

@Preview(name = "Page: ledger lines, stems, accidental")
@Composable
private fun WidePagePreview() = PagePreview(PreviewData.wide)

@Preview(name = "Page: bass clef")
@Composable
private fun BassPagePreview() = PagePreview(PreviewData.bass)

@Preview(name = "Page: E flat major with a natural")
@Composable
private fun FlatKeyPagePreview() = PagePreview(PreviewData.flatKey)

@Preview(name = "Page: Flash, one beat ahead, cursor in bar 2")
@Composable
private fun FlashPagePreview() {
    val run = PreviewData.run
    val beat = 9.0
    PagePreview(
        run.score,
        mask = runMask(run.timeline, run.config.copy(lookaheadBeats = 1.0).policy, beat),
        cursorTicks = run.timeline.ticksAt(beat),
        height = 360,
    )
}

@Preview(name = "Page: Read ahead, page turned to systems 2 and 3")
@Composable
private fun ReadAheadTurnedPagePreview() {
    val run = PreviewData.run
    val beat = 13.5
    PagePreview(
        run.score,
        mask = runMask(run.timeline, VisibilityMode.READ_AHEAD.let { run.config.copy(mode = it) }.policy, beat),
        cursorTicks = run.timeline.ticksAt(beat),
        height = 360,
    )
}

@Preview(name = "Page: annotated summary, scrolling")
@Composable
private fun AnnotatedSummaryPreview() {
    KeySightTheme {
        Surface {
            Box(Modifier.width(360.dp).height(360.dp)) {
                RunSummaryPage(PreviewData.run.score) { page ->
                    noteMarks(page, PreviewData.run.score, PreviewData.evaluation.pitch.outcomes, PreviewData.evaluation.rhythm)
                }
            }
        }
    }
}
