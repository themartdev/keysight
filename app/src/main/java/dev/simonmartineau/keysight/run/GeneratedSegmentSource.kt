package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.ExerciseGenerator
import dev.simonmartineau.keysight.exercise.segmentSeed

/**
 * A run's segments from the generator at one fixed [config]: segment k is generated from the
 * seed [segmentSeed] derives from [runSeed] and k, so one run seed reproduces the whole run
 * and every segment carries what reproduces it alone.
 */
class GeneratedSegmentSource(
    private val runSeed: Long,
    private val config: ExerciseConfig,
) : SegmentSource {

    override fun next(count: Int, firstIndex: Int, committed: List<CommittedSegment>): List<Segment> {
        require(count > 0) { "count must be positive" }
        return (firstIndex until firstIndex + count).map(::segment)
    }

    fun segment(index: Int): Segment = generatedSegment(runSeed, index, config)
}

/** Segment [index] of the run seeded [runSeed], read at [config]: its seed depends on the run and the index alone. */
fun generatedSegment(runSeed: Long, index: Int, config: ExerciseConfig): Segment {
    val seed = segmentSeed(runSeed, index)
    return Segment(
        origin = SegmentOrigin.Generated(ExerciseGenerator.GENERATOR_VERSION, seed, config),
        score = ExerciseGenerator.generate(config, seed),
    )
}
