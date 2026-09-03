package dev.simonmartineau.keysight.ui.practice

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.difficulty.Decision
import dev.simonmartineau.keysight.difficulty.DifficultyState
import dev.simonmartineau.keysight.difficulty.Dimension
import dev.simonmartineau.keysight.difficulty.Direction
import dev.simonmartineau.keysight.difficulty.Ladders
import dev.simonmartineau.keysight.difficulty.Move
import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.difficulty.Position
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.evaluation.Continuity
import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.evaluation.NoteTiming
import dev.simonmartineau.keysight.evaluation.Pause
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.evaluation.PlayedNote
import dev.simonmartineau.keysight.evaluation.RhythmResult
import dev.simonmartineau.keysight.evaluation.RunEvaluation
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.TimingJudgement
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunContext
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.generatedSegment
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.Staff
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultTextTest {

    private fun played(midi: Int, beat: Double) = PlayedNote(Pitch(midi), beat, null, 80, 0L)

    private val pitch = PitchResult(
        listOf(
            NoteOutcome.Correct(Fixtures.cdef.notes[0], played(60, 0.0)),
            NoteOutcome.Missing(Fixtures.cdef.notes[1]),
        ),
    )

    private fun rhythm(
        phase: Double = 0.0,
        tempoRatio: Double? = 1.0,
        pauses: List<Pause> = emptyList(),
        continuity: Continuity = Continuity.GOOD,
        timings: List<NoteTiming> = emptyList(),
    ) = RhythmResult(timings, phase, tempoRatio, pauses, continuity)

    private fun timing(judgement: TimingJudgement, error: Double = 0.0) =
        NoteTiming("n1", 0.0, error, error, judgement)

    private val oneLate = listOf(timing(TimingJudgement.LATE, 0.3))

    @Test
    fun `the score line has pitch, rhythm and continuity`() {
        assertEquals("Pitch 50%   Rhythm 0%   Continuity Good", scoreLine(pitch, rhythm()))
        assertEquals("Pitch 50%   Rhythm 0%   Continuity Lost", scoreLine(pitch, rhythm(continuity = Continuity.LOST)))
    }

    @Test
    fun `a result without rhythm only reports pitch`() {
        assertEquals("Pitch 50%", scoreLine(pitch, null))
        assertEquals(emptyList(), remarks(pitch, null))
    }

    @Test
    fun `a clean performance earns no remark`() {
        assertEquals(emptyList(), remarks(pitch, rhythm(phase = 0.05, tempoRatio = 1.03)))
    }

    @Test
    fun `leaning on the beat is remarked past a tenth of a beat when a timing mark needs explaining`() {
        assertEquals(listOf("Slightly ahead of the beat"), remarks(pitch, rhythm(phase = -0.1, timings = oneLate)))
        assertEquals(listOf("Slightly behind the beat"), remarks(pitch, rhythm(phase = 0.12, timings = oneLate)))
    }

    @Test
    fun `a lean with every note on its own pulse earns no remark`() {
        val onTime = listOf(timing(TimingJudgement.ON_TIME), timing(TimingJudgement.ON_TIME, 0.05))

        assertEquals(emptyList(), remarks(pitch, rhythm(phase = 0.25, timings = onTime)))
        assertEquals(emptyList(), remarks(pitch, rhythm(phase = -0.25, timings = onTime)))
        assertEquals(emptyList(), remarks(pitch, rhythm(phase = 0.25)))
    }

    @Test
    fun `tempo drift is remarked past five percent, rounded`() {
        assertEquals(listOf("Drifted 6% fast"), remarks(pitch, rhythm(tempoRatio = 1.06)))
        assertEquals(listOf("Drifted 8% slow"), remarks(pitch, rhythm(tempoRatio = 0.92)))
        assertEquals(emptyList(), remarks(pitch, rhythm(tempoRatio = null)))
    }

    @Test
    fun `pauses and extra notes are counted`() {
        val withExtra = PitchResult(pitch.outcomes + NoteOutcome.Extra(played(70, 1.5)))

        assertEquals(listOf("1 extra note", "1 pause"), remarks(withExtra, rhythm(pauses = listOf(Pause("n2", 0.7)))))
        assertEquals(listOf("2 pauses"), remarks(pitch, rhythm(pauses = listOf(Pause("n2", 0.7), Pause("n3", 0.6)))))
    }

    @Test
    fun `continuity labels`() {
        assertEquals(listOf("Good", "Hesitant", "Lost"), Continuity.entries.map(::continuityLabel))
    }

    @Test
    fun `the header says what the run was`() {
        val config = Fixtures.slowConfig
        val score = Fixtures.cdef

        assertEquals("8 bars   Flash 2 beats   C major   right hand", summaryHeader(config, score, 8))
        assertEquals("1 bar   Flash 1 beat   G major   left hand", summaryHeader(config.copy(lookaheadBeats = 1.0), score.copy(keySignature = KeySignature(1), staves = listOf(Staff(Clef.BASS))), 1))
        assertEquals("3 bars   Read ahead   C major   both hands", summaryHeader(config.copy(mode = VisibilityMode.READ_AHEAD), score.copy(staves = listOf(Staff(Clef.TREBLE), Staff(Clef.BASS))), 3))
        assertEquals("12 bars   Open score   C major   right hand", summaryHeader(config.copy(mode = VisibilityMode.OPEN_SCORE, segmentCount = null), score, 12))
    }

    @Test
    fun `the weakest bars are named once there is more than one bar and one went wrong`() {
        val clean = EvaluationResult(4, PitchResult(listOf(NoteOutcome.Correct(Fixtures.cdef.notes[0], played(60, 0.0)))), rhythm(timings = listOf(timing(TimingJudgement.ON_TIME))))
        val flawed = EvaluationResult(4, pitch, rhythm())

        assertEquals("Weakest bar: 2", weakestBarsLine(RunEvaluation(listOf(clean, flawed, clean), 0.0)))
        assertEquals("Weakest bars: 1, 3", weakestBarsLine(RunEvaluation(listOf(flawed, clean, flawed), 0.0)))
        assertNull(weakestBarsLine(RunEvaluation(listOf(clean, clean), 0.0)))
        assertNull(weakestBarsLine(RunEvaluation(listOf(flawed), 0.0)))
    }

    @Test
    fun `the level line reads the first bar's configuration, and bundled content has none`() {
        val generated = RunContext(listOf(generatedSegment(1L, 1, ExerciseConfig.DEFAULT)), RunConfig.DEFAULT.copy(segmentCount = 1), seed = 1L)

        assertEquals("Up to thirds, five notes, quarter notes, no rests.", levelLine(generated))
        assertNull(levelLine(Fixtures.slowRun))
    }

    @Test
    fun `a level change is named at the bar it reached, harder or easier`() {
        val thirds = ExerciseConfig.DEFAULT
        val fourths = thirds.copy(maxInterval = 3)
        val halves = fourths.copy(noteValues = Ladders.RHYTHM.easiest)
        val rests = fourths.copy(rests = true)
        val segments = listOf(thirds, thirds, fourths, fourths, halves, halves, fourths, rests, fourths).mapIndexed { index, config -> generatedSegment(1L, index + 1, config) }

        assertEquals(
            listOf("Harder from bar 3: up to fourths", "Easier from bar 5: half notes", "Harder from bar 7: quarter notes", "Harder from bar 8: with rests", "Easier from bar 9: no rests"),
            levelChangeLines(segments),
        )
        assertEquals(emptyList(), levelChangeLines(segments.take(2)))
        assertEquals(emptyList(), levelChangeLines(Fixtures.slowRun.segments + segments.take(1)))
    }

    @Test
    fun `the next run's move is named, the lookahead in beats and the music by its rung`() {
        val config = RunConfig.DEFAULT.copy(lookaheadBeats = 1.0)
        val lookahead = Decision(Position(config, DifficultyState(lastMoved = Dimension.LOOKAHEAD)), Move(Dimension.LOOKAHEAD, Direction.UP))
        val range = Decision(Position(config, DifficultyState(MusicalLevel.DEFAULT.copy(rightHandRange = Ladders.RANGE.rungs[1].right, leftHandRange = Ladders.RANGE.rungs[1].left), Dimension.RANGE)), Move(Dimension.RANGE, Direction.DOWN))

        val rests = Decision(Position(config, DifficultyState(MusicalLevel.DEFAULT.copy(rests = true), Dimension.RESTS)), Move(Dimension.RESTS, Direction.UP))

        assertEquals("Harder next run: 1 beat ahead", nextRunLine(lookahead))
        assertEquals("Easier next run: a sixth", nextRunLine(range))
        assertEquals("Harder next run: with rests", nextRunLine(rests))
        assertNull(nextRunLine(Decision(lookahead.position, null)))
        assertEquals("0.75 beats ahead", lookaheadLabel(0.75))
    }
}
