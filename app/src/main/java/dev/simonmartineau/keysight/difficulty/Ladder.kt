package dev.simonmartineau.keysight.difficulty

/**
 * The rungs of one dimension, easiest first, ranked so that a value off the ladder still has
 * a neighbour: a value the ladder does not list steps to the nearest rung in the direction of
 * travel, so a ladder can be reshaped without stranding a stored position.
 */
class Ladder<T>(val rungs: List<T>, private val rank: (T) -> Double) {

    init {
        require(rungs.isNotEmpty()) { "a ladder needs a rung" }
        require(rungs.zipWithNext().all { (easier, harder) -> rank(easier) < rank(harder) }) { "rungs must be ordered easiest first: $rungs" }
    }

    /** The rung one step in [direction] from [current], or null at the end of the ladder. */
    fun step(current: T, direction: Direction): T? {
        val index = rungs.indexOf(current)
        if (index >= 0) return rungs.getOrNull(index + direction.sign)
        val here = rank(current)
        return when (direction) {
            Direction.UP -> rungs.firstOrNull { rank(it) > here }
            Direction.DOWN -> rungs.lastOrNull { rank(it) < here }
        }
    }

    val easiest: T get() = rungs.first()
    val hardest: T get() = rungs.last()
}
