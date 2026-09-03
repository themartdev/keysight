package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.run.CommittedSegment
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.SegmentSource
import dev.simonmartineau.keysight.run.generatedSegment

/**
 * A run's segments from the generator at the level the controller decides as the run goes on.
 *
 * Every segment is generated from the run seed and its index exactly as a fixed source would,
 * so a move changes only the configuration stored beside the segment, never its seed, and
 * every segment reproduces from what it stores. Segments already produced are never touched:
 * the controller is consulted only for the ones still to come.
 */
class AdaptiveSegmentSource(
    private val runSeed: Long,
    private val base: ExerciseConfig,
    private val runConfig: RunConfig,
    private val tracker: DifficultyTracker,
) : SegmentSource {

    /** Segments 1 to [count], read at the current level with no decision. */
    fun initial(count: Int): List<Segment> {
        val config = tracker.configFor(base)
        return (1..count).map { generatedSegment(runSeed, it, config) }
    }

    override fun next(count: Int, firstIndex: Int, committed: List<CommittedSegment>): List<Segment> {
        require(count > 0) { "count must be positive" }
        val evidence = committed.map { evidenceOf(runConfig, it.segment, it.result) }
        return (firstIndex until firstIndex + count).map { index ->
            generatedSegment(runSeed, index, tracker.nextSegment(runConfig, base, evidence))
        }
    }
}
