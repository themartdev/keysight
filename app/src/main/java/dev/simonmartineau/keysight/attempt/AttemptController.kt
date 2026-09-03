package dev.simonmartineau.keysight.attempt

import dev.simonmartineau.keysight.attempt.AttemptState.Aborted
import dev.simonmartineau.keysight.attempt.AttemptState.Evaluating
import dev.simonmartineau.keysight.attempt.AttemptState.Ready
import dev.simonmartineau.keysight.attempt.AttemptState.Result
import dev.simonmartineau.keysight.attempt.AttemptState.Running
import dev.simonmartineau.keysight.audio.Metronome
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.exercise.Exercise
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.timing.MonotonicClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Drives [AttemptMachine] with real time, the metronome and the keyboard.
 *
 * There is exactly one timer here, and it is not a timer: the attempt job sleeps until the
 * machine's next deadline, then reads the clock and reduces. The metronome's anchor is beat 0.
 * Every state change happens on [scope]'s dispatcher, which must be single-threaded (the main
 * thread in the app, the test scheduler in tests), so the reducer is never raced.
 *
 * [state] is null until the first exercise is loaded.
 */
class AttemptController(
    private val scope: CoroutineScope,
    private val persistScope: CoroutineScope,
    private val clock: MonotonicClock,
    private val metronome: Metronome,
    private val history: AttemptHistory,
    private val evaluationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<AttemptState?>(null)
    val state: StateFlow<AttemptState?> = _state.asStateFlow()

    private var attemptJob: Job? = null
    private var startedAtEpochMillis = 0L
    private var sessionId: String? = null

    /**
     * Makes [exercise] the current one with [config]. Legal before anything is loaded, while
     * ready (a settings change re-times the same passage) and after a result or an abort.
     */
    fun load(exercise: Exercise, config: FlashConfig) {
        _state.value = when (val current = _state.value) {
            null, is Ready -> Ready(AttemptContext(exercise, config))
            else -> AttemptMachine.reduce(current, AttemptEvent.Next(exercise, config))
        }
    }

    fun start() {
        check(_state.value is Ready) { "cannot start from ${_state.value}" }
        check(attemptJob == null) { "an attempt is already running" }
        attemptJob = scope.launch { runAttempt() }
    }

    fun onMidi(event: MidiEvent) {
        val current = _state.value ?: return
        _state.value = AttemptMachine.reduce(current, AttemptEvent.MidiReceived(event))
    }

    fun abort(reason: AbortReason) {
        val before = _state.value ?: return
        if (before.isTerminal) return
        attemptJob?.cancel()
        attemptJob = null
        metronome.stop()
        val after = AttemptMachine.reduce(before, AttemptEvent.Abort(reason))
        _state.value = after
        if (after is Aborted && after.startedAtNanos != null) {
            persist(recordOf(after.context, after.startedAtNanos, after.captured, AttemptStatus.ABORTED, reason), evaluation = null)
        }
    }

    /** Closes the session, if one was opened. Safe after [scope] is gone: persistence has its own scope. */
    fun endSession() {
        val id = sessionId ?: return
        sessionId = null
        persistScope.launch { history.endSession(id) }
    }

    private suspend fun runAttempt() {
        val timeline = (_state.value as Ready).context.timeline
        val anchor = metronome.start(timeline)
        startedAtEpochMillis = wallClock()
        reduce(AttemptEvent.Start(anchor.attemptStartNanos))

        while (true) {
            val deadline = AttemptMachine.nextDeadlineNanos(_state.value!!) ?: break
            delayUntil(deadline)
            reduce(AttemptEvent.ClockAdvanced(clock.nowNanos()))
        }

        val evaluating = _state.value as? Evaluating ?: return
        metronome.stop()
        val result = withContext(evaluationDispatcher) {
            PerformanceEvaluator.evaluate(
                score = evaluating.context.exercise.score,
                events = evaluating.captured,
                timeline = evaluating.context.timeline,
                startedAtNanos = evaluating.startedAtNanos,
            )
        }
        reduce(AttemptEvent.Evaluated(result))
        val done = _state.value as Result
        attemptJob = null
        persist(recordOf(done.context, done.startedAtNanos, done.captured, AttemptStatus.COMPLETED, null), result)
    }

    private suspend fun delayUntil(deadlineNanos: Long) {
        while (true) {
            val remainingNanos = deadlineNanos - clock.nowNanos()
            if (remainingNanos <= 0) return
            delay((remainingNanos / 1_000_000L).coerceAtLeast(1L))
        }
    }

    private fun reduce(event: AttemptEvent) {
        _state.value = AttemptMachine.reduce(_state.value!!, event)
    }

    private fun recordOf(
        context: AttemptContext,
        startedAtNanos: Long,
        captured: List<MidiEvent>,
        status: AttemptStatus,
        reason: AbortReason?,
    ) = PendingRecord(
        exerciseId = context.exercise.id,
        startedAtNanos = startedAtNanos,
        status = status,
        abortReason = reason,
        config = context.config,
        score = context.exercise.score,
        events = captured,
    )

    private fun persist(pending: PendingRecord, evaluation: EvaluationResult?) {
        val startedAt = startedAtEpochMillis
        persistScope.launch {
            val session = sessionId ?: history.startSession().also { sessionId = it }
            val record = AttemptRecord(
                id = ids(),
                sessionId = session,
                exerciseId = pending.exerciseId,
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
        val exerciseId: String,
        val startedAtNanos: Long,
        val status: AttemptStatus,
        val abortReason: AbortReason?,
        val config: FlashConfig,
        val score: dev.simonmartineau.keysight.score.Score,
        val events: List<MidiEvent>,
    )

    val isRunning: Boolean get() = _state.value is Running
}
