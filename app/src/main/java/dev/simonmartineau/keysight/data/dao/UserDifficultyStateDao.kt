package dev.simonmartineau.keysight.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.simonmartineau.keysight.data.entity.UserDifficultyStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDifficultyStateDao {

    @Upsert
    suspend fun upsert(state: UserDifficultyStateEntity)

    @Query("SELECT * FROM user_difficulty_state WHERE id = :id")
    suspend fun get(id: Int = UserDifficultyStateEntity.SINGLETON_ID): UserDifficultyStateEntity?

    @Query("SELECT * FROM user_difficulty_state WHERE id = :id")
    fun observe(id: Int = UserDifficultyStateEntity.SINGLETON_ID):
        Flow<UserDifficultyStateEntity?>
}
