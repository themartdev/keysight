package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.score.Score

/** Where a segment's measure came from: enough to reproduce it, stored beside the score itself. */
sealed interface SegmentOrigin {

    /** A measure of the bundled content of the rounds before the generator, named by its id. */
    data class Bundled(val exerciseId: String) : SegmentOrigin {
        init {
            require(exerciseId.isNotBlank()) { "a bundled segment needs an exercise id" }
        }
    }

    /** The generator at [generatorVersion] given [config] and [seed] produces this measure again. */
    data class Generated(val generatorVersion: Int, val seed: Long, val config: ExerciseConfig) : SegmentOrigin {
        init {
            require(generatorVersion > 0) { "generatorVersion must be positive" }
        }
    }
}

/**
 * One performed measure of a run and where it came from.
 *
 * The [score] is the segment on its own, one measure starting at tick 0, which is what gets
 * stored per segment and re-evaluated on its own; the [origin] is stored beside it because
 * generators change and history must stand without them.
 */
data class Segment(
    val origin: SegmentOrigin,
    val score: Score,
) {
    init {
        require(score.measureCount == 1) { "$origin: a segment is one measure, not ${score.measureCount}" }
    }
}
