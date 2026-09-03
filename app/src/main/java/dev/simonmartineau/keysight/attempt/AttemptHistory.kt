package dev.simonmartineau.keysight.attempt

import dev.simonmartineau.keysight.evaluation.EvaluationResult

/** Where finished and abandoned attempts go. */
interface AttemptHistory {

    /** Opens a session and returns its id. */
    suspend fun startSession(): String

    suspend fun endSession(sessionId: String)

    /** Stores an attempt with its raw MIDI, and its evaluation when it has one. */
    suspend fun record(record: AttemptRecord, evaluation: EvaluationResult?)
}
