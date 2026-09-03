package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.evaluation.EvaluationResult

/** Where finished and abandoned runs go. */
interface RunHistory {

    /** Opens a session and returns its id. */
    suspend fun startSession(): String

    suspend fun endSession(sessionId: String)

    /**
     * Stores a run with its segments and raw MIDI, and the [evaluations] committed for its
     * segments in order: one per segment for a completed run, fewer for an aborted one.
     */
    suspend fun record(record: RunRecord, evaluations: List<EvaluationResult>)
}
