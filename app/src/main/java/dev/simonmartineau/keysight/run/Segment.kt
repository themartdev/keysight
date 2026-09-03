package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.score.Score

/**
 * One performed measure of a run and where it came from.
 *
 * Until the generator exists a segment is a bundled exercise adapted to the run's key and
 * staves, so [exerciseId] names it; the generator round adds the seed and parameters. The
 * [score] is the segment on its own, one measure starting at tick 0, which is what gets
 * stored per segment and re-evaluated on its own.
 */
data class Segment(
    val exerciseId: String,
    val score: Score,
) {
    init {
        require(exerciseId.isNotBlank()) { "a segment needs an exercise id" }
        require(score.measureCount == 1) { "$exerciseId: a segment is one measure, not ${score.measureCount}" }
    }
}
