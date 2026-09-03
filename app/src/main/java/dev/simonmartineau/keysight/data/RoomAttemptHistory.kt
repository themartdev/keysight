package dev.simonmartineau.keysight.data

import androidx.room.withTransaction
import dev.simonmartineau.keysight.attempt.AttemptHistory
import dev.simonmartineau.keysight.attempt.AttemptRecord
import dev.simonmartineau.keysight.data.entity.SessionEntity
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import java.util.UUID

class RoomAttemptHistory(
    private val database: KeySightDatabase,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val ids: () -> String = { UUID.randomUUID().toString() },
) : AttemptHistory {

    override suspend fun startSession(): String {
        val id = ids()
        database.sessionDao().insert(SessionEntity(id, startedAtEpochMillis = wallClock(), endedAtEpochMillis = null))
        return id
    }

    override suspend fun endSession(sessionId: String) {
        database.sessionDao().markEnded(sessionId, wallClock())
    }

    override suspend fun record(record: AttemptRecord, evaluation: EvaluationResult?) {
        database.withTransaction {
            database.attemptDao().insertAttemptWithEvents(record.toEntity(), record.toMidiEventEntities())
            if (evaluation != null) {
                database.attemptDao().upsertEvaluation(evaluation.toEntity(record.id, wallClock()))
            }
        }
    }
}
