package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.score.Score

/**
 * One unseen passage the player is asked to flash-read.
 *
 * V1 exercises are authored offline and bundled with the app: [notationAssetPath] points at the
 * pre-rendered SVG, and [score] is the matching canonical data. Keeping both in the same object
 * is what lets the renderer stay dumb and the evaluator stay independent of notation.
 */
data class Exercise(
    val id: String,
    val score: Score,
    val notationAssetPath: String,
    val musicalDifficulty: Int,
    val tags: Set<ExerciseTag> = emptySet(),
) {
    init {
        require(musicalDifficulty >= 0) { "musicalDifficulty must not be negative" }
    }
}

/**
 * What an exercise is meant to drill.
 *
 * These are the axes the session summary reports on ("strong: stepwise reading") and the ones
 * the learner model will eventually key off, so they are part of the content contract rather
 * than free-form labels.
 */
enum class ExerciseTag {
    REPEATED_NOTES,
    STEPWISE,
    THIRDS,
    FOURTHS,
    FIFTHS,
    WIDE_LEAPS,
    ASCENDING,
    DESCENDING,
}
