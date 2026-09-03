package dev.simonmartineau.keysight

import dev.simonmartineau.keysight.attempt.AttemptContext
import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.exercise.Exercise
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import dev.simonmartineau.keysight.timing.AttemptTimeline

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

    val exercise = Exercise(id = "cdef", score = cdef, musicalDifficulty = 1)

    /** 60 bpm so that one beat is exactly one second; two beats of preview. */
    val slowConfig = FlashConfig(
        tempoBpm = 60.0,
        countInMeasures = 1,
        previewDurationBeats = 2.0,
        metronomeDuringAttempt = false,
    )

    val slowTimeline: AttemptTimeline = AttemptTimeline.of(slowConfig, cdef)

    val slowContext = AttemptContext(exercise, slowConfig)

    fun oneMeasure(vararg notes: ScoreNote, timeSignature: TimeSignature = TimeSignature.FOUR_FOUR) = Score(
        timeSignature = timeSignature,
        clef = Clef.TREBLE,
        keySignature = KeySignature.C_MAJOR,
        measureCount = 1,
        notes = notes.toList(),
    )
}
