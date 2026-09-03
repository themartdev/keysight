package dev.simonmartineau.keysight.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.simonmartineau.keysight.data.entity.EvaluationResultEntity
import dev.simonmartineau.keysight.data.entity.MidiEventEntity
import dev.simonmartineau.keysight.data.entity.RunEntity
import dev.simonmartineau.keysight.data.entity.SegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {

    @Insert
    suspend fun insertRun(run: RunEntity)

    @Insert
    suspend fun insertSegments(segments: List<SegmentEntity>)

    @Insert
    suspend fun insertMidiEvents(events: List<MidiEventEntity>)

    /** Stores a run, its segments and its raw MIDI as one unit: history never holds a run without its performance. */
    @Transaction
    suspend fun insertRunWithSegmentsAndEvents(run: RunEntity, segments: List<SegmentEntity>, events: List<MidiEventEntity>) {
        insertRun(run)
        insertSegments(segments)
        insertMidiEvents(events)
    }

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun byId(id: String): RunEntity?

    @Query("SELECT * FROM segments WHERE runId = :runId ORDER BY segmentIndex ASC")
    suspend fun segmentsFor(runId: String): List<SegmentEntity>

    @Query("SELECT * FROM midi_events WHERE runId = :runId ORDER BY timestampNanos ASC, id ASC")
    suspend fun midiEventsFor(runId: String): List<MidiEventEntity>

    @Query("SELECT * FROM runs WHERE sessionId = :sessionId ORDER BY startedAtEpochMillis ASC")
    fun observeBySession(sessionId: String): Flow<List<RunEntity>>

    /** The most recent runs overall, newest first. */
    @Query("SELECT * FROM runs ORDER BY startedAtEpochMillis DESC LIMIT :limit")
    suspend fun mostRecent(limit: Int): List<RunEntity>

    @Upsert
    suspend fun upsertEvaluations(results: List<EvaluationResultEntity>)

    @Query(
        """
        SELECT * FROM evaluation_results
        WHERE segmentId = :segmentId
        ORDER BY evaluatorVersion DESC
        LIMIT 1
        """,
    )
    suspend fun latestEvaluationFor(segmentId: String): EvaluationResultEntity?

    @Query("SELECT * FROM evaluation_results WHERE segmentId = :segmentId ORDER BY evaluatorVersion ASC")
    suspend fun evaluationsFor(segmentId: String): List<EvaluationResultEntity>

    /** The latest evaluation of every segment of [runId], in segment order; segments without one are absent. */
    @Query(
        """
        SELECT evaluation_results.* FROM evaluation_results
        JOIN segments ON segments.id = evaluation_results.segmentId
        WHERE segments.runId = :runId
          AND evaluation_results.evaluatorVersion = (
            SELECT MAX(evaluatorVersion) FROM evaluation_results AS latest WHERE latest.segmentId = segments.id
          )
        ORDER BY segments.segmentIndex ASC
        """,
    )
    suspend fun latestEvaluationsForRun(runId: String): List<EvaluationResultEntity>
}
