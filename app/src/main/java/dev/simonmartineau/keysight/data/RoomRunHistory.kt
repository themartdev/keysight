package dev.simonmartineau.keysight.data

import androidx.room.withTransaction
import dev.simonmartineau.keysight.data.entity.SessionEntity
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.run.RunHistory
import dev.simonmartineau.keysight.run.RunRecord
import java.util.UUID

/** Run history on the run, segment, MIDI and evaluation tables. */
class RoomRunHistory(
    private val database: KeySightDatabase,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : RunHistory {

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
}
