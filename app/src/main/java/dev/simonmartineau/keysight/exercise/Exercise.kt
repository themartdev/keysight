package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.score.Score
import kotlinx.serialization.Serializable

/**
 * One unseen passage the player is asked to flash-read.
 *
 * Exercises are content, not rows: V1 ships them as JSON assets. [musicalDifficulty] is the
 * notation-side difficulty, kept separate from the preview duration on purpose.
 */
@Serializable
data class Exercise(
    val id: String,
    val score: Score,
    val musicalDifficulty: Int,
) {
    init {
        require(id.isNotBlank()) { "an exercise needs an id" }
        require(musicalDifficulty >= 0) { "musicalDifficulty must not be negative" }
    }
}
