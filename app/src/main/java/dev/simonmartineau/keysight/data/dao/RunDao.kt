package dev.simonmartineau.keysight.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.simonmartineau.keysight.data.entity.EvaluationResultEntity
import dev.simonmartineau.keysight.data.entity.MidiEventEntity
import dev.simonmartineau.keysight.data.entity.RunEntity
import dev.simonmartineau.keysight.data.entity.SegmentEntity
import kotlinx.coroutines.flow.Flow

/** A committed segment with the configurations it was played under: what the difficulty window is made of. */
data class CommittedRow(
    val runConfigJson: String,
    val exerciseConfigJson: String?,
    val resultJson: String,
)

/** A run as a table row reads it: the configuration, the first bar's score for its key and staves, and the bar count. */
data class RunDigestRow(
    val id: String,
    val sessionId: String,
    val startedAtEpochMillis: Long,
    val configJson: String,
    val firstScoreJson: String?,
    val bars: Int,
)

/** The latest judgement of one segment, with the run and the index it belongs to. */
data class RunEvaluationRow(
    val runId: String,
    val segmentIndex: Int,
    @Embedded val evaluation: EvaluationResultEntity,
)

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

    /**
     * The most recent [limit] committed segments across all runs, newest first, each with its
     * latest evaluation and the configurations it was played under.
     */
    @Query(
        """
        SELECT runs.configJson AS runConfigJson, segments.exerciseConfigJson AS exerciseConfigJson, evaluation_results.resultJson AS resultJson
        FROM evaluation_results
        JOIN segments ON segments.id = evaluation_results.segmentId
        JOIN runs ON runs.id = segments.runId
        WHERE evaluation_results.evaluatorVersion = (
            SELECT MAX(evaluatorVersion) FROM evaluation_results AS latest WHERE latest.segmentId = segments.id
          )
        ORDER BY runs.startedAtEpochMillis DESC, segments.segmentIndex DESC
        LIMIT :limit
        """,
    )
    suspend fun recentCommitted(limit: Int): List<CommittedRow>

    /** Every run started at or after [since], oldest first, as a digest row; kept up to date. */
    @Query(
        """
        SELECT runs.id AS id, runs.sessionId AS sessionId, runs.startedAtEpochMillis AS startedAtEpochMillis, runs.configJson AS configJson,
          (SELECT scoreJson FROM segments WHERE segments.runId = runs.id ORDER BY segments.segmentIndex ASC LIMIT 1) AS firstScoreJson,
          (SELECT COUNT(*) FROM segments WHERE segments.runId = runs.id) AS bars
        FROM runs
        WHERE runs.startedAtEpochMillis >= :since
        ORDER BY runs.startedAtEpochMillis ASC
        """,
    )
    fun observeDigestsSince(since: Long): Flow<List<RunDigestRow>>

    /**
     * The latest judgement of every segment of every run started at or after [since], in run
     * then segment order; kept up to date. Segments without a judgement are absent.
     */
    @Query(
        """
        SELECT segments.runId AS runId, segments.segmentIndex AS segmentIndex, evaluation_results.*
        FROM evaluation_results
        JOIN segments ON segments.id = evaluation_results.segmentId
        JOIN runs ON runs.id = segments.runId
        WHERE runs.startedAtEpochMillis >= :since
          AND evaluation_results.evaluatorVersion = (
            SELECT MAX(evaluatorVersion) FROM evaluation_results AS latest WHERE latest.segmentId = segments.id
          )
        ORDER BY runs.startedAtEpochMillis ASC, segments.segmentIndex ASC
        """,
    )
    fun observeLatestEvaluationsSince(since: Long): Flow<List<RunEvaluationRow>>
}
