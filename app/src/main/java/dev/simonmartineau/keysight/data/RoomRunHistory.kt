package dev.simonmartineau.keysight.data

import androidx.room.withTransaction
import dev.simonmartineau.keysight.data.entity.SessionEntity
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.run.RunHistory
import dev.simonmartineau.keysight.run.RunRecord
import java.util.UUID

/** Run history on the attempt tables, one row per run, until schema version 3. */
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

    override suspend fun record(record: RunRecord, evaluation: EvaluationResult?) {
        database.withTransaction {
            database.attemptDao().insertAttemptWithEvents(record.toEntity(), record.toMidiEventEntities())
            if (evaluation != null) {
                database.attemptDao().upsertEvaluation(evaluation.toEntity(record.id, wallClock()))
            }
        }
    }
}
