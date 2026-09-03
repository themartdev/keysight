package dev.simonmartineau.keysight.data

import androidx.room.withTransaction
import dev.simonmartineau.keysight.data.entity.RunEntity
import dev.simonmartineau.keysight.data.entity.SessionEntity
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.history.HistoryStore
import dev.simonmartineau.keysight.history.RunDigest
import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.history.StoredRun
import dev.simonmartineau.keysight.run.RunHistory
import dev.simonmartineau.keysight.run.RunRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Run history on the session, run, segment, MIDI and evaluation tables: the write side the
 * run controller records through, and the read side history reads through.
 */
class RoomRunHistory(
    private val database: KeySightDatabase,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : RunHistory, HistoryStore {

    override suspend fun startSession(): String {
        val id = ids()
        database.sessionDao().insert(SessionEntity(id, startedAtEpochMillis = wallClock(), endedAtEpochMillis = null))
        return id
    }

    override suspend fun endSession(sessionId: String) {
        database.sessionDao().markEnded(sessionId, wallClock())
    }

    override suspend fun record(record: RunRecord, evaluations: List<EvaluationResult>) {
        require(evaluations.size <= record.segments.size) { "${evaluations.size} evaluations for ${record.segments.size} segments" }
        val evaluatedAt = wallClock()
        database.withTransaction {
            database.runDao().insertRunWithSegmentsAndEvents(record.toEntity(), record.toSegmentEntities(), record.toMidiEventEntities())
            database.runDao().upsertEvaluations(
                evaluations.mapIndexed { index, evaluation -> evaluation.toEntity(segmentId(record.id, index + 1), evaluatedAt) },
            )
        }
    }

    override fun sessions(): Flow<List<SessionRecord>> = database.sessionDao().observeAll().map { rows -> rows.map { it.toRecord() } }

    override fun runsOf(sessionId: String): Flow<List<StoredRun>> =
        database.runDao().observeBySession(sessionId).map { rows -> rows.mapNotNull { load(it) } }

    override suspend fun run(id: String): StoredRun? = database.runDao().byId(id)?.let { load(it) }

    override fun runDigests(sinceEpochMillis: Long): Flow<List<RunDigest>> {
        val dao = database.runDao()
        return combine(dao.observeDigestsSince(sinceEpochMillis), dao.observeLatestEvaluationsSince(sinceEpochMillis)) { rows, judged ->
            val byRun = judged.groupBy({ it.runId }, { it.evaluation })
            rows.mapNotNull { row -> row.toDigest(byRun[row.id].orEmpty()) }
        }
    }

    /** A new row per segment at the judgement's version: the primary key keeps every older judgement. */
    override suspend fun addEvaluations(runId: String, evaluations: List<EvaluationResult>) {
        val evaluatedAt = wallClock()
        database.runDao().upsertEvaluations(
            evaluations.mapIndexed { index, evaluation -> evaluation.toEntity(segmentId(runId, index + 1), evaluatedAt) },
        )
    }

    private suspend fun load(run: RunEntity): StoredRun? {
        val dao = database.runDao()
        return run.toStoredRun(dao.segmentsFor(run.id), dao.midiEventsFor(run.id), dao.latestEvaluationsForRun(run.id))
    }
}
