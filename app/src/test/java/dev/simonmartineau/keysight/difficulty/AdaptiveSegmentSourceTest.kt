package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.evaluation.PlayedNote
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.ExerciseGenerator
import dev.simonmartineau.keysight.exercise.segmentSeed
import dev.simonmartineau.keysight.run.CommittedSegment
import dev.simonmartineau.keysight.run.GeneratedSegmentSource
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.SegmentOrigin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AdaptiveSegmentSourceTest {

    private val base = ExerciseConfig.DEFAULT
    private val runConfig = RunConfig.DEFAULT.copy(segmentCount = null)

    /** Every note of [segment] correct on its beat, and no rhythm judgement, which counts as pitch alone. */
    private fun perfect(segment: Segment) = CommittedSegment(
        segment,
        EvaluationResult(5, PitchResult(segment.score.notes.map { NoteOutcome.Correct(it, PlayedNote(it.pitch, 0.0, 0.5, 80, 0L)) })),
    )

    @Test
    fun `the first segments are read at the current level and equal a fixed source's`() = runTest {
        val tracker = DifficultyTracker(InMemoryDifficultyStore(), this)
        tracker.restore()
        val source = AdaptiveSegmentSource(runSeed = 5L, base, runConfig, tracker)

        assertEquals(GeneratedSegmentSource(5L, base).next(12, 1, emptyList()), source.initial(12))
    }

    @Test
    fun `a move changes the configuration of the segments still to come and nothing else`() = runTest {
        val tracker = DifficultyTracker(InMemoryDifficultyStore(), this)
        tracker.restore()
        val source = AdaptiveSegmentSource(runSeed = 5L, base, runConfig, tracker)
        val first = source.initial(12)

        val unmoved = source.next(1, firstIndex = 13, committed = first.take(7).map(::perfect))
        val moved = source.next(2, firstIndex = 14, committed = first.take(8).map(::perfect))

        assertEquals(GeneratedSegmentSource(5L, base).segment(13), unmoved.single())
        val origin = assertIs<SegmentOrigin.Generated>(moved[0].origin)
        assertEquals(3, origin.config.maxInterval)
        assertEquals(segmentSeed(5L, 14), origin.seed)
        assertEquals(ExerciseGenerator.generate(origin.config, origin.seed), moved[0].score)
        assertEquals(base.copy(maxInterval = 3), (moved[1].origin as SegmentOrigin.Generated).config, "one move per window")
        assertEquals(first, GeneratedSegmentSource(5L, base).next(12, 1, emptyList()), "the segments already produced stand")
    }
}
