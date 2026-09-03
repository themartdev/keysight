package dev.simonmartineau.keysight.exercise

import kotlin.random.Random

/**
 * Picks the next exercise. Round 2 knows nothing about the player: uniform over the pack,
 * never the same passage twice in a row. Adaptation replaces this in Milestone 4.
 */
class ExerciseSelector(
    private val exercises: List<Exercise>,
    private val random: Random = Random.Default,
) {
    init {
        require(exercises.isNotEmpty()) { "no exercises to select from" }
    }

    fun next(previous: Exercise?): Exercise {
        val candidates = exercises.filter { it.id != previous?.id }.ifEmpty { exercises }
        return candidates[random.nextInt(candidates.size)]
    }
}
