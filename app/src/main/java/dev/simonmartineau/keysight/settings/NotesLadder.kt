package dev.simonmartineau.keysight.settings

import dev.simonmartineau.keysight.difficulty.DifficultyController
import dev.simonmartineau.keysight.difficulty.DifficultyState
import dev.simonmartineau.keysight.difficulty.Dimension
import dev.simonmartineau.keysight.difficulty.Direction
import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.difficulty.Position
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode

/**
 * The levels a player picks the music from by hand, easiest first: the controller's own
 * walk up from the default, one dimension at a time in its order, until every dimension is
 * at its top. One list over five dimensions, so the Notes cell is one picker and a level
 * picked by hand is a level the controller could have reached.
 */
object NotesLadder {

    val LEVELS: List<MusicalLevel> = buildList {
        val base = ExerciseConfig.DEFAULT
        var position = Position(RunConfig.DEFAULT.copy(mode = VisibilityMode.OPEN_SCORE), DifficultyState.DEFAULT)
        add(position.state.level)
        while (true) {
            val next = DifficultyController.walk(position.state.lastMoved, Direction.UP)
                .filter { it.movesWithinRun }
                .firstNotNullOfOrNull { DifficultyController.step(it, position, base, Direction.UP) } ?: break
            position = next
            add(position.state.level)
        }
    }

    /** The rung of [level], or the nearest rung below it when the controller left the list. */
    fun indexOf(level: MusicalLevel): Int {
        val exact = LEVELS.indexOf(level)
        if (exact >= 0) return exact
        return LEVELS.indexOfLast { rung -> MusicalLevel.MUSICAL_DIMENSIONS.all { rung.rank(it) <= level.rank(it) } }.coerceAtLeast(0)
    }

    /** "Up to thirds · quarter notes", then "with rests" and "with accidentals" once they are on: what fits a cell. */
    fun shortLabel(level: MusicalLevel): String = buildList {
        add(level.label(Dimension.INTERVAL))
        add(level.label(Dimension.RHYTHM))
        if (level.rests) add(level.label(Dimension.RESTS))
        if (level.accidentals) add(level.label(Dimension.ACCIDENTALS))
    }.joinToString(" · ").replaceFirstChar { it.uppercase() }
}
