package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.audio.Metronome
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.RunState.Aborted
import dev.simonmartineau.keysight.run.RunState.Evaluating
import dev.simonmartineau.keysight.run.RunState.Ready
import dev.simonmartineau.keysight.run.RunState.Running
import dev.simonmartineau.keysight.run.RunState.Summary
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.timing.MonotonicClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Drives [RunMachine] with real time, the metronome and the keyboard.
 *
 * There is exactly one timer here, and it is not a timer: the run job sleeps until the
 * machine's next deadline, then reads the clock and reduces. The sleep also ends when the
 * deadline moves, which a stop does, so the machine is never left waiting for a boundary it no
 * longer has. The metronome's anchor is beat 0. Every state change happens on [scope]'s
 * dispatcher, which must be single-threaded (the main thread in the app, the test scheduler in
 * tests), so the reducer is never raced.
 *
 * [state] is null until the first run is loaded.
 */
class RunController(
    private val scope: CoroutineScope,
    private val persistScope: CoroutineScope,
    private val clock: MonotonicClock,
    private val metronome: Metronome,
    private val history: RunHistory,
    private val evaluationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<RunState?>(null)
    val state: StateFlow<RunState?> = _state.asStateFlow()

    private var runJob: Job? = null
    private var startedAtEpochMillis = 0L
    private var sessionId: String? = null

    /**
     * Makes [context] the current run. Legal before anything is loaded, while ready (a settings
     * change rebuilds the run that is waiting) and after a summary or an abort.
     */
    fun load(context: RunContext) {
        _state.value = when (val current = _state.value) {
            null, is Ready -> Ready(context)
            else -> RunMachine.reduce(current, RunEvent.Next(context))
        }
    }

    fun start() {
        check(_state.value is Ready) { "cannot start from ${_state.value}" }
        check(runJob == null) { "a run is already running" }
        runJob = scope.launch { runRun() }
    }

    fun onMidi(event: MidiEvent) {
        val current = _state.value ?: return
        _state.value = RunMachine.reduce(current, RunEvent.MidiReceived(event))
    }

    /** The player's Stop: ends the run after the current segment, or cancels a count-in. */
    fun stop() {
        val before = _state.value ?: return
        if (before !is Running) return
        val after = RunMachine.reduce(before, RunEvent.Stop(clock.nowNanos()))
        _state.value = after
        if (after is Aborted) finishAbort(after, AbortReason.CANCELLED)
    }

    fun abort(reason: AbortReason) {
        val before = _state.value ?: return
        if (before.isTerminal) return
        val after = RunMachine.reduce(before, RunEvent.Abort(reason))
        _state.value = after
        finishAbort(after, reason)
    }

    private fun finishAbort(after: RunState, reason: AbortReason) {
        runJob?.cancel()
        runJob = null
        metronome.stop()
        if (after is Aborted && after.startedAtNanos != null) {
            persist(recordOf(after.context, after.context.score, after.startedAtNanos, after.captured, RunStatus.ABORTED, reason), evaluation = null)
        }
    }

    /** Closes the session, if one was opened. Safe after [scope] is gone: persistence has its own scope. */
    fun endSession() {
        val id = sessionId ?: return
        sessionId = null
        persistScope.launch { history.endSession(id) }
    }

    private suspend fun runRun() {
        val timeline = (_state.value as Ready).context.timeline
        val anchor = metronome.start(timeline)
        startedAtEpochMillis = wallClock()
        reduce(RunEvent.Start(anchor.runStartNanos))

        while (true) {
            val deadline = RunMachine.nextDeadlineNanos(_state.value!!) ?: break
            val remainingNanos = deadline - clock.nowNanos()
            if (remainingNanos > 0) {
                // Sleep until the deadline, or until the machine's deadline changes, whichever comes first.
                withTimeoutOrNull((remainingNanos / 1_000_000L).coerceAtLeast(1L)) {
                    _state.first { RunMachine.nextDeadlineNanos(it!!) != deadline }
                }
                continue
            }
            reduce(RunEvent.ClockAdvanced(clock.nowNanos()))
        }

        val evaluating = _state.value as? Evaluating ?: return
        metronome.stop()
        val performed = evaluating.context.performed(evaluating.lastSegment)
        val result = withContext(evaluationDispatcher) {
            PerformanceEvaluator.evaluate(
                score = performed.score,
                events = evaluating.captured,
                timeline = performed.timeline,
                startedAtNanos = evaluating.startedAtNanos,
            )
        }
        reduce(RunEvent.Evaluated(result))
        val done = _state.value as Summary
        runJob = null
        persist(recordOf(done.context, performed.score, done.startedAtNanos, done.captured, RunStatus.COMPLETED, null), result)
    }

    private fun reduce(event: RunEvent) {
        _state.value = RunMachine.reduce(_state.value!!, event)
    }

    private fun recordOf(
        context: RunContext,
        score: Score,
        startedAtNanos: Long,
        captured: List<MidiEvent>,
        status: RunStatus,
        reason: AbortReason?,
    ) = PendingRecord(
        exerciseIds = context.segments.map { it.exerciseId },
        startedAtNanos = startedAtNanos,
        status = status,
        abortReason = reason,
        config = context.config,
        score = score,
        events = captured,
    )

    private fun persist(pending: PendingRecord, evaluation: EvaluationResult?) {
        val startedAt = startedAtEpochMillis
        persistScope.launch {
            val session = sessionId ?: history.startSession().also { sessionId = it }
            val record = RunRecord(
                id = ids(),
                sessionId = session,
                exerciseIds = pending.exerciseIds,
                startedAtEpochMillis = startedAt,
                startedAtNanos = pending.startedAtNanos,
                status = pending.status,
                abortReason = pending.abortReason,
                config = pending.config,
                score = pending.score,
                events = pending.events,
            )
            history.record(record, evaluation)
        }
    }

    private class PendingRecord(
        val exerciseIds: List<String>,
        val startedAtNanos: Long,
        val status: RunStatus,
        val abortReason: AbortReason?,
        val config: RunConfig,
        val score: Score,
        val events: List<MidiEvent>,
    )

    val isRunning: Boolean get() = _state.value is Running
}
