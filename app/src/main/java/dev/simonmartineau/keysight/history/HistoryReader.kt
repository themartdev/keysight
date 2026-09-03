package dev.simonmartineau.keysight.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * History at the current evaluator version.
 *
 * Every run read through here carries today's judgement: a run whose stored judgement is older
 * is re-evaluated from its stored segments and raw MIDI on the spot and the result stored under
 * the current version, beside the old one, so the next read finds it. The record, the raw MIDI
 * included, is never written.
 */
class HistoryReader(private val store: HistoryStore) {

    fun sessions(): Flow<List<SessionRecord>> = store.sessions()

    fun runsOf(sessionId: String): Flow<List<StoredRun>> = store.runsOf(sessionId).map { runs -> runs.map { current(it) } }

    fun summaryOf(session: SessionRecord): Flow<SessionSummary> = runsOf(session.id).map { summarise(session, it) }

    suspend fun run(id: String): StoredRun? = store.run(id)?.let { current(it) }

    /**
     * The digests of every run since [sinceEpochMillis], as stored: a digest carries the
     * judgement at the version that stored it and is not brought up to date, since a table of
     * every run cannot afford to replay every run's MIDI. Opening a run does that.
     */
    fun runDigests(sinceEpochMillis: Long): Flow<List<RunDigest>> = store.runDigests(sinceEpochMillis)

    private suspend fun current(run: StoredRun): StoredRun {
        if (run.isCurrent) return run
        val judged = run.reevaluated()
        store.addEvaluations(run.record.id, judged.evaluations)
        return judged
    }
}
