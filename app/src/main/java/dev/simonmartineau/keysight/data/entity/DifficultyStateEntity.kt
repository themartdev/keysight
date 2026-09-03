package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The difficulty controller's state, one row. [stateJson] is the
 * [dev.simonmartineau.keysight.difficulty.DifficultyState] snapshot, so a new dimension is a
 * new field with a default rather than a new column. Added in schema version 5.
 */
@Entity(tableName = "difficulty_state")
data class DifficultyStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val stateJson: String,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
