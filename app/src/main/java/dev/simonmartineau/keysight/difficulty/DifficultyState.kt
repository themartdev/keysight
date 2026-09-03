package dev.simonmartineau.keysight.difficulty

import kotlinx.serialization.Serializable

/**
 * What the controller carries from one decision to the next: its musical level and the
 * dimension it moved last, which decides whose turn it is. The exposure position is not
 * here: the lookahead is the player's run setting, read when a decision is made and written
 * back when it moves, so a player's own change and the controller's are the same thing.
 */
@Serializable
data class DifficultyState(
    val level: MusicalLevel = MusicalLevel.DEFAULT,
    val lastMoved: Dimension? = null,
) {
    companion object {
        val DEFAULT = DifficultyState()
    }
}
