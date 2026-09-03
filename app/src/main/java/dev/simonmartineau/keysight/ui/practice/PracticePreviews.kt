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
import dev.simonmartineau.keysight.attempt.AbortReason
import dev.simonmartineau.keysight.attempt.AttemptContext
import dev.simonmartineau.keysight.attempt.AttemptState
import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.evaluation.PlayedNote
import dev.simonmartineau.keysight.exercise.Exercise
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.notation.ScoreLayoutEngine
import dev.simonmartineau.keysight.notation.noteMarks
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import dev.simonmartineau.keysight.settings.ThemeMode
import dev.simonmartineau.keysight.ui.notation.Staff
import dev.simonmartineau.keysight.ui.theme.KeySightTheme

/**
 * Hand-built states for the Android Studio previews, one per thing the stage can show, so
 * the engraving can be eyeballed without a keyboard or a device.
 */
private object PreviewData {

    private fun oneMeasure(clef: Clef, vararg notes: ScoreNote) = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        clef = clef,
        keySignature = KeySignature.C_MAJOR,
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

    /** Ledger lines both ways, both stem directions, a half note and an accidental. */
    val wide: Score = oneMeasure(
        Clef.TREBLE,
        ScoreNote("w1", SpelledPitch(Step.A, octave = 3), Ticks.ZERO, Ticks.HALF),
        ScoreNote("w2", SpelledPitch(Step.B, octave = 5), Ticks.HALF, Ticks.QUARTER),
        ScoreNote("w3", SpelledPitch(Step.F, alteration = 1, octave = 4), Ticks.quarters(3), Ticks.QUARTER),
    )

    /** The same shape in bass clef, for Milestone 5's eyes. */
    val bass: Score = oneMeasure(
        Clef.BASS,
        ScoreNote("b1", SpelledPitch(Step.C, octave = 3), Ticks.ZERO, Ticks.QUARTER),
        ScoreNote("b2", SpelledPitch(Step.E, octave = 3), Ticks.QUARTER, Ticks.QUARTER),
        ScoreNote("b3", SpelledPitch(Step.G, octave = 3), Ticks.quarters(2), Ticks.HALF),
    )

    val config = FlashConfig(tempoBpm = 80.0, countInMeasures = 1, previewDurationBeats = 4.0, metronomeDuringAttempt = false)

    val context = AttemptContext(Exercise("preview-cdef", cdef, musicalDifficulty = 1), config)

    val connection: MidiConnection = MidiConnection.Connected("Preview keyboard")

    private fun played(midi: Int, onsetBeat: Double) =
        PlayedNote(Pitch(midi), onsetBeat, onsetBeat + 0.4, velocity = 90, onsetNanos = 0L)

    /** One of each outcome: correct, wrong (F sharp for D), missing, an extra A between beats, correct. */
    val outcomes: List<NoteOutcome> = listOf(
        NoteOutcome.Correct(cdef.notes[0], played(60, 0.02)),
        NoteOutcome.WrongPitch(cdef.notes[1], played(66, 1.0)),
        NoteOutcome.Missing(cdef.notes[2]),
        NoteOutcome.Extra(played(69, 2.6)),
        NoteOutcome.Correct(cdef.notes[3], played(65, 3.0)),
    )

    val ready = AttemptState.Ready(context)

    fun countingIn(notationVisible: Boolean) =
        AttemptState.CountingIn(context, startedAtNanos = 0L, notationVisible = notationVisible, captured = emptyList())

    val performing = AttemptState.Performing(context, startedAtNanos = 0L, captured = emptyList())

    val evaluating = AttemptState.Evaluating(context, startedAtNanos = 0L, captured = emptyList())

    val result = AttemptState.Result(
        context,
        startedAtNanos = 0L,
        captured = emptyList(),
        evaluation = EvaluationResult(PerformanceEvaluator.EVALUATOR_VERSION, PitchResult(outcomes)),
    )

    val aborted = AttemptState.Aborted(context, AbortReason.MIDI_DISCONNECTED, startedAtNanos = 0L, captured = emptyList())

    val actions = PracticeActions(
        start = {},
        cancel = {},
        next = {},
        retry = {},
        setPreviewBeats = {},
        setTempo = {},
        setMetronomeDuringAttempt = {},
        setTheme = {},
    )
}

@Composable
private fun PreviewScreen(state: AttemptState?) {
    KeySightTheme {
        PracticeContent(
            state = state,
            connection = PreviewData.connection,
            config = PreviewData.config,
            theme = ThemeMode.SYSTEM,
            loadError = null,
            actions = PreviewData.actions,
        )
    }
}

@Preview(name = "Ready", showBackground = true)
@Composable
private fun ReadyPreview() = PreviewScreen(PreviewData.ready)

@Preview(name = "Counting in, notation shown", showBackground = true)
@Composable
private fun CountingInVisiblePreview() = PreviewScreen(PreviewData.countingIn(notationVisible = true))

@Preview(name = "Counting in, notation hidden", showBackground = true)
@Composable
private fun CountingInHiddenPreview() = PreviewScreen(PreviewData.countingIn(notationVisible = false))

@Preview(name = "Performing", showBackground = true)
@Composable
private fun PerformingPreview() = PreviewScreen(PreviewData.performing)

@Preview(name = "Evaluating", showBackground = true)
@Composable
private fun EvaluatingPreview() = PreviewScreen(PreviewData.evaluating)

@Preview(name = "Result", showBackground = true)
@Composable
private fun ResultPreview() = PreviewScreen(PreviewData.result)

@Preview(name = "Result, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ResultDarkPreview() = PreviewScreen(PreviewData.result)

@Preview(name = "Result, tablet", showBackground = true, widthDp = 1000, heightDp = 700)
@Composable
private fun ResultTabletPreview() = PreviewScreen(PreviewData.result)

@Preview(name = "Aborted", showBackground = true)
@Composable
private fun AbortedPreview() = PreviewScreen(PreviewData.aborted)

@Composable
private fun StaffPreview(score: Score, outcomes: List<NoteOutcome> = emptyList()) {
    val layout = ScoreLayoutEngine.layout(score)
    KeySightTheme {
        Surface {
            Box(Modifier.width(360.dp).height(220.dp)) {
                Staff(layout, marks = noteMarks(layout, score, outcomes))
            }
        }
    }
}

@Preview(name = "Staff: ledger lines, stems, accidental")
@Composable
private fun WideStaffPreview() = StaffPreview(PreviewData.wide)

@Preview(name = "Staff: bass clef")
@Composable
private fun BassStaffPreview() = StaffPreview(PreviewData.bass)

@Preview(name = "Staff: annotated")
@Composable
private fun AnnotatedStaffPreview() = StaffPreview(PreviewData.cdef, PreviewData.outcomes)
