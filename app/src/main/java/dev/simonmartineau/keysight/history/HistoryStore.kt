package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.evaluation.EvaluationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The read side of practice history, and the one write a reader makes: a re-evaluation
 * stored under its own version. [dev.simonmartineau.keysight.run.RunHistory] is the write
 * side the run controller uses; both are the same tables.
 */
interface HistoryStore {

    /** Every session, newest first, kept up to date. */
    fun sessions(): Flow<List<SessionRecord>>

    /** The runs of [sessionId] in the order they were played, each with the latest stored judgement of every segment, kept up to date. */
    fun runsOf(sessionId: String): Flow<List<StoredRun>>

    suspend fun run(id: String): StoredRun?

    /**
     * Stores [evaluations], one per judged segment of [runId] in order, under their own
     * evaluator version. Judgements at other versions are kept; nothing else is written.
     */
    suspend fun addEvaluations(runId: String, evaluations: List<EvaluationResult>)
}

/** History in memory, for tests and previews: runs keyed by session in the order they were added. */
class InMemoryHistoryStore(
    sessions: List<SessionRecord> = emptyList(),
    runs: List<StoredRun> = emptyList(),
) : HistoryStore {

    private val sessionsFlow = MutableStateFlow(sessions.sortedByDescending { it.startedAtEpochMillis })
    private val runsFlow = MutableStateFlow(runs)

    /** Every judgement ever stored per run, in the order stored: what [addEvaluations] wrote, for tests to check. */
    val stored = mutableMapOf<String, MutableList<List<EvaluationResult>>>()

    fun add(session: SessionRecord) {
        sessionsFlow.value = (sessionsFlow.value + session).sortedByDescending { it.startedAtEpochMillis }
    }

    fun add(run: StoredRun) {
        runsFlow.value = runsFlow.value + run
    }

    override fun sessions(): Flow<List<SessionRecord>> = sessionsFlow

    override fun runsOf(sessionId: String): Flow<List<StoredRun>> =
        runsFlow.map { runs -> runs.filter { it.record.sessionId == sessionId }.sortedBy { it.record.startedAtEpochMillis } }

    override suspend fun run(id: String): StoredRun? = runsFlow.value.firstOrNull { it.record.id == id }

    override suspend fun addEvaluations(runId: String, evaluations: List<EvaluationResult>) {
        stored.getOrPut(runId) { mutableListOf() } += evaluations
        runsFlow.value = runsFlow.value.map { run ->
            if (run.record.id != runId) run else run.copy(evaluations = latest(run.evaluations, evaluations))
        }
    }

    /** Per segment, the judgement at the higher version, as the latest-per-segment query reads. */
    private fun latest(before: List<EvaluationResult>, added: List<EvaluationResult>): List<EvaluationResult> =
        List(maxOf(before.size, added.size)) { index ->
            listOfNotNull(before.getOrNull(index), added.getOrNull(index)).maxBy { it.evaluatorVersion }
        }
}
