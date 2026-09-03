package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Where the difficulty controller currently has the player.
 *
 * A single row: V1 is local-first with no accounts. Preview and musical difficulty are stored
 * separately because the controller moves one at a time.
 */
@Entity(tableName = "user_difficulty_state")
data class UserDifficultyStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val previewDurationBeats: Double,
    val musicalDifficulty: Int,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
