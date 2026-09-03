package dev.simonmartineau.keysight.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.simonmartineau.keysight.data.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity)

    @Query("UPDATE sessions SET endedAtEpochMillis = :endedAtEpochMillis WHERE id = :id")
    suspend fun markEnded(id: String, endedAtEpochMillis: Long)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startedAtEpochMillis DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    /** Removes the session and, through the foreign keys, everything recorded in it. */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)
}
