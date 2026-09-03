package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.rhythmsFilling
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode

/** Everything a decision reads and may change: the run's presentation and the controller's state. */
data class Position(val runConfig: RunConfig, val state: DifficultyState)

/** What a decision came to: the position afterwards, and the one move it made, if any. */
data class Decision(val position: Position, val move: Move?) {
    val moved: Boolean get() = move != null
}

/**
 * The difficulty controller of section 8 of the plan, as a pure function.
 *
 * It reads a window of the last [WINDOW_SEGMENTS] committed segments played at the current
 * state and steps one dimension up when the window's success is above [STEP_UP_ABOVE], one
 * down when it is below [STEP_DOWN_BELOW], and holds otherwise or while the window is short.
 * Only one dimension ever moves in a decision, and a move empties the window, so no two moves
 * rest on the same evidence.
 *
 * Which dimension moves is a walk of [Dimension] in its order, taking turns: a step up goes
 * to the first dimension after the one moved last that can still go up, so the music is never
 * stranded behind the exposure; a step down starts at the dimension moved last and walks
 * back, so the most recent step is the first undone and a dimension keeps easing while the
 * player keeps struggling, before the ones before it ease. Within a run only the musical
 * dimensions are eligible, so the run's exposure is stable; the lookahead moves between runs.
 */
object DifficultyController {

    const val WINDOW_SEGMENTS = 8
    const val STEP_UP_ABOVE = 0.90
    const val STEP_DOWN_BELOW = 0.65

    /**
     * Decides from [evidence] (oldest first) at [position], the level laid over the player's
     * [base] configuration; [betweenRuns] says whether the exposure may move.
     */
    fun decide(position: Position, base: ExerciseConfig, evidence: List<SegmentEvidence>, betweenRuns: Boolean): Decision {
        val hold = Decision(position, null)
        val window = trailingWindow(evidence, position.runConfig.exposure, position.state.level.applyTo(base), WINDOW_SEGMENTS)
        if (window.size < WINDOW_SEGMENTS) return hold
        val direction = directionFor(successOf(window)) ?: return hold
        for (dimension in walk(position.state.lastMoved, direction)) {
            if (!betweenRuns && !dimension.movesWithinRun) continue
            val stepped = step(dimension, position, base, direction) ?: continue
            return Decision(stepped.copy(state = stepped.state.copy(lastMoved = dimension)), Move(dimension, direction))
        }
        return hold
    }

    fun directionFor(success: Double): Direction? = when {
        success > STEP_UP_ABOVE -> Direction.UP
        success < STEP_DOWN_BELOW -> Direction.DOWN
        else -> null
    }

    /**
     * The dimensions in the order a decision tries them: up, the ones after [lastMoved] then
     * round to it; down, [lastMoved] itself then the ones before it, round to the one after.
     */
    fun walk(lastMoved: Dimension?, direction: Direction): List<Dimension> {
        val all = Dimension.entries
        return when (direction) {
            Direction.UP -> {
                val from = lastMoved?.let { it.ordinal + 1 } ?: 0
                all.indices.map { all[(from + it) % all.size] }
            }
            Direction.DOWN -> {
                val from = lastMoved?.ordinal ?: (all.size - 1)
                all.indices.map { all[Math.floorMod(from - it, all.size)] }
            }
        }
    }

    /** [position] with [dimension] one rung in [direction], or null when it cannot go that way. */
    fun step(dimension: Dimension, position: Position, base: ExerciseConfig, direction: Direction): Position? {
        val level = position.state.level
        return when (dimension) {
            Dimension.LOOKAHEAD -> {
                if (position.runConfig.mode != VisibilityMode.FLASH) return null
                val beats = Ladders.LOOKAHEAD.step(position.runConfig.lookaheadBeats, direction) ?: return null
                position.copy(runConfig = position.runConfig.copy(lookaheadBeats = beats))
            }
            Dimension.INTERVAL -> {
                val interval = Ladders.INTERVAL.step(level.maxInterval, direction) ?: return null
                position.withLevel(level.copy(maxInterval = interval))
            }
            Dimension.RANGE -> {
                val ranges = Ladders.RANGE.step(level.ranges, direction) ?: return null
                position.withLevel(level.copy(rightHandRange = ranges.right, leftHandRange = ranges.left))
            }
            Dimension.RHYTHM -> {
                val values = Ladders.RHYTHM.step(level.noteValues, direction) ?: return null
                if (rhythmsFilling(values, base.timeSignature.ticksPerMeasure).isEmpty()) return null
                position.withLevel(level.copy(noteValues = values))
            }
        }
    }

    /** The position at [level], provided the level makes sense on its own. */
    private fun Position.withLevel(level: MusicalLevel): Position? =
        if (level.isConsistent) copy(state = state.copy(level = level)) else null
}
