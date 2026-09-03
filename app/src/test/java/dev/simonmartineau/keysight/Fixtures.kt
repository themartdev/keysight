package dev.simonmartineau.keysight

import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunContext
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.SegmentOrigin
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import dev.simonmartineau.keysight.timing.RunTimeline

const val SECOND_NANOS = 1_000_000_000L

/** Hand-built content shared by the tests. */
object Fixtures {

    val C4 = SpelledPitch(Step.C, octave = 4)
    val D4 = SpelledPitch(Step.D, octave = 4)
    val E4 = SpelledPitch(Step.E, octave = 4)
    val F4 = SpelledPitch(Step.F, octave = 4)
    val G4 = SpelledPitch(Step.G, octave = 4)

    /** One measure of 4/4: C4 D4 E4 F4 as quarter notes. */
    val cdef: Score = oneMeasure(
        ScoreNote("n1", C4, Ticks.ZERO, Ticks.QUARTER),
        ScoreNote("n2", D4, Ticks.QUARTER, Ticks.QUARTER),
        ScoreNote("n3", E4, Ticks.quarters(2), Ticks.QUARTER),
        ScoreNote("n4", F4, Ticks.quarters(3), Ticks.QUARTER),
    )

    /** One measure of 4/4: G4 F4 E4 D4 as quarter notes. */
    val gfed: Score = oneMeasure(
        ScoreNote("n1", G4, Ticks.ZERO, Ticks.QUARTER),
        ScoreNote("n2", F4, Ticks.QUARTER, Ticks.QUARTER),
        ScoreNote("n3", E4, Ticks.quarters(2), Ticks.QUARTER),
        ScoreNote("n4", D4, Ticks.quarters(3), Ticks.QUARTER),
    )

    /** 60 bpm so that one beat is exactly one second; Flash with two beats of lookahead; one performed segment. */
    val slowConfig = RunConfig(
        tempoBpm = 60.0,
        metronome = MetronomeMode.COUNT_IN_ONLY,
        mode = VisibilityMode.FLASH,
        lookaheadBeats = 2.0,
        segmentCount = 1,
    )

    /** The one-segment run of [cdef]: the count-in is beats 0 to 4, the notes are beats 4 to 8, capture ends at 9. */
    val slowRun: RunContext = run(cdef)

    /** [cdef] as measure 1 of a run, its notes named `1:n1` to `1:n4`, measure 0 resting. */
    val slowScore: Score = slowRun.score

    val slowTimeline: RunTimeline = slowRun.timeline

    /** A run of [segments], each one measure, at [config]. */
    fun run(vararg segments: Score, config: RunConfig = slowConfig): RunContext =
        RunContext(segments.mapIndexed { index, score -> segment("segment-${index + 1}", score) }, config.copy(segmentCount = segments.size))

    /** A segment of bundled content named [exerciseId]. */
    fun segment(exerciseId: String, score: Score) = Segment(SegmentOrigin.Bundled(exerciseId), score)

    fun oneMeasure(vararg notes: ScoreNote, timeSignature: TimeSignature = TimeSignature.FOUR_FOUR) =
        measures(1, *notes, timeSignature = timeSignature)

    /** [measureCount] measures of 4/4 in C major on a treble staff. */
    fun measures(measureCount: Int, vararg notes: ScoreNote, timeSignature: TimeSignature = TimeSignature.FOUR_FOUR) = Score(
        timeSignature = timeSignature,
        keySignature = KeySignature.C_MAJOR,
        measureCount = measureCount,
        notes = notes.toList(),
    )
}
