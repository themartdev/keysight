package dev.simonmartineau.keysight.exercise

import kotlin.random.Random

/**
 * Picks the next exercise. It knows nothing about the player: uniform over the pack, never
 * the same passage twice in a row. The generator replaces this as the source of segments.
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

    /** [count] exercises for the segments of one run, no two consecutive ones the same, the first differing from [previous]. */
    fun nextRun(count: Int, previous: Exercise? = null): List<Exercise> {
        require(count > 0) { "a run needs a segment" }
        val chosen = ArrayList<Exercise>(count)
        var last = previous
        repeat(count) {
            val exercise = next(last)
            chosen += exercise
            last = exercise
        }
        return chosen
    }
}
