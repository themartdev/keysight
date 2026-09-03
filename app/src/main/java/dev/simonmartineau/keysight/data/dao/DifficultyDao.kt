package dev.simonmartineau.keysight.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.simonmartineau.keysight.data.entity.DifficultyStateEntity

@Dao
interface DifficultyDao {

    @Query("SELECT * FROM difficulty_state WHERE id = ${DifficultyStateEntity.SINGLETON_ID}")
    suspend fun get(): DifficultyStateEntity?

    @Upsert
    suspend fun put(state: DifficultyStateEntity)
}
