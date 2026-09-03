package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.evaluation.EvaluationResult

/** Where finished and abandoned runs go. */
interface RunHistory {

    /** Opens a session and returns its id. */
    suspend fun startSession(): String

    suspend fun endSession(sessionId: String)

    /** Stores a run with its raw MIDI, and its evaluation when it has one. */
    suspend fun record(record: RunRecord, evaluation: EvaluationResult?)
}
