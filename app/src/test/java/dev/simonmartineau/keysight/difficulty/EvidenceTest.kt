package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.evaluation.Continuity
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.evaluation.NoteTiming
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.evaluation.PlayedNote
import dev.simonmartineau.keysight.evaluation.RhythmResult
import dev.simonmartineau.keysight.evaluation.TimingJudgement
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.generatedSegment
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EvidenceTest {

    private val notes = Fixtures.cdef.notes

    private fun played(midi: Int) = PlayedNote(Pitch(midi), 0.0, 0.5, velocity = 80, onsetNanos = 0L)

    private fun timing(id: String, judgement: TimingJudgement) = NoteTiming(id, 0.0, 0.0, 0.0, judgement)

    /** Two correct, one wrong, one missing, one extra; of the three matched, two on time. */
    private val result = EvaluationResult(
        5,
        PitchResult(
            listOf(
                NoteOutcome.Correct(notes[0], played(60)),
                NoteOutcome.Correct(notes[1], played(62)),
                NoteOutcome.WrongPitch(notes[2], played(63)),
                NoteOutcome.Missing(notes[3]),
                NoteOutcome.Extra(played(70)),
            ),
        ),
        RhythmResult(
            listOf(timing("n1", TimingJudgement.ON_TIME), timing("n2", TimingJudgement.LATE), timing("n3", TimingJudgement.ON_TIME)),
            phaseBeats = 0.0,
            tempoRatio = 1.0,
            pauses = emptyList(),
            continuity = Continuity.GOOD,
        ),
    )

    private val config = ExerciseConfig.DEFAULT
    private val runConfig = RunConfig.DEFAULT

    private fun evidence(correct: Int, expected: Int, onTime: Int, matched: Int, config: ExerciseConfig? = this.config, runConfig: RunConfig = this.runConfig) =
        SegmentEvidence(runConfig.exposure, config, correct, expected, onTime, matched)

    @Test
    fun `evidence is the score line's counts and the state the segment was read at`() {
        val segment = generatedSegment(1L, 1, config)

        val evidence = evidenceOf(runConfig, segment, result)

        assertEquals(SegmentEvidence(runConfig.exposure, config, correctCount = 2, expectedCount = 4, onTimeCount = 2, matchedCount = 3), evidence)
        assertNull(evidenceOf(runConfig, Fixtures.segment("m01", Fixtures.cdef), result).config)
        assertEquals(0, evidenceOf(runConfig, segment, result.copy(rhythm = null)).matchedCount)
    }

    @Test
    fun `the exposure is the run's presentation without its length, and the lookahead only counts in Flash`() {
        assertEquals(runConfig.exposure, runConfig.copy(segmentCount = null).exposure)
        assertEquals(runConfig.copy(mode = VisibilityMode.READ_AHEAD).exposure, runConfig.copy(mode = VisibilityMode.READ_AHEAD, lookaheadBeats = 1.0).exposure)
        assertEquals(false, runConfig.exposure == runConfig.copy(lookaheadBeats = 3.0).exposure)
        assertEquals(false, runConfig.exposure == runConfig.copy(tempoBpm = 84.0).exposure)
    }

    @Test
    fun `the window is the most recent evidence at the current state, stopping at the first that differs`() {
        val other = config.copy(hands = Hands.LEFT)
        val evidence = listOf(
            evidence(4, 4, 4, 4),
            evidence(4, 4, 4, 4, config = other),
            evidence(3, 4, 3, 3),
            evidence(2, 4, 2, 2),
            evidence(4, 4, 4, 4),
        )

        assertEquals(evidence.takeLast(3), trailingWindow(evidence, runConfig.exposure, config, limit = 8))
        assertEquals(evidence.takeLast(2), trailingWindow(evidence, runConfig.exposure, config, limit = 2))
        assertEquals(emptyList(), trailingWindow(evidence, runConfig.copy(lookaheadBeats = 3.0).exposure, config, limit = 8))
        assertEquals(emptyList(), trailingWindow(evidence, runConfig.exposure, config.copy(keySignature = KeySignature(1)), limit = 8))
        assertEquals(emptyList(), trailingWindow(evidence + evidence(4, 4, 4, 4, config = null), runConfig.exposure, config, limit = 8))
    }

    @Test
    fun `success is the smaller of pooled pitch and pooled rhythm, pitch alone when nothing matched`() {
        assertEquals(0.5, successOf(listOf(evidence(4, 4, 2, 4), evidence(4, 4, 2, 4))))
        assertEquals(0.75, successOf(listOf(evidence(3, 4, 3, 3), evidence(3, 4, 3, 3))))
        assertEquals(0.5, successOf(listOf(evidence(2, 4, 0, 0))))
        assertEquals(0.0, successOf(emptyList()))
        assertEquals(1.0, successOf(listOf(evidence(4, 4, 4, 4))))
    }
}
