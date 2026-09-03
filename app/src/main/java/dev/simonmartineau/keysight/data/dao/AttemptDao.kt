package dev.simonmartineau.keysight.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.simonmartineau.keysight.data.entity.AttemptEntity
import dev.simonmartineau.keysight.data.entity.EvaluationResultEntity
import dev.simonmartineau.keysight.data.entity.MidiEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttemptDao {

    @Insert
    suspend fun insertAttempt(attempt: AttemptEntity)

    @Insert
    suspend fun insertMidiEvents(events: List<MidiEventEntity>)

    @Upsert
    suspend fun upsertEvaluation(result: EvaluationResultEntity)

    /**
     * Stores an attempt and its raw performance as one unit, so history can never contain an
     * attempt whose MIDI was lost.
     */
    @Transaction
    suspend fun insertAttemptWithEvents(attempt: AttemptEntity, events: List<MidiEventEntity>) {
        insertAttempt(attempt)
        insertMidiEvents(events)
    }

    @Query("SELECT * FROM attempts WHERE id = :id")
    suspend fun byId(id: String): AttemptEntity?

    @Query("SELECT * FROM midi_events WHERE attemptId = :attemptId ORDER BY timestampNanos ASC")
    suspend fun midiEventsFor(attemptId: String): List<MidiEventEntity>

    @Query("SELECT * FROM attempts WHERE sessionId = :sessionId ORDER BY startedAtEpochMillis ASC")
    fun observeBySession(sessionId: String): Flow<List<AttemptEntity>>

    @Query(
        """
        SELECT * FROM evaluation_results
        WHERE attemptId = :attemptId
        ORDER BY evaluatorVersion DESC
        LIMIT 1
        """
    )
    suspend fun latestEvaluationFor(attemptId: String): EvaluationResultEntity?

    /** The most recent attempts overall, newest first, for the difficulty controller. */
    @Query("SELECT * FROM attempts ORDER BY startedAtEpochMillis DESC LIMIT :limit")
    suspend fun mostRecent(limit: Int): List<AttemptEntity>
}
