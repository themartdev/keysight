package dev.simonmartineau.keysight.attempt

import dev.simonmartineau.keysight.attempt.AttemptState.Aborted
import dev.simonmartineau.keysight.attempt.AttemptState.CountingIn
import dev.simonmartineau.keysight.attempt.AttemptState.Evaluating
import dev.simonmartineau.keysight.attempt.AttemptState.Performing
import dev.simonmartineau.keysight.attempt.AttemptState.Ready
import dev.simonmartineau.keysight.attempt.AttemptState.Result
import dev.simonmartineau.keysight.attempt.AttemptState.Running
import dev.simonmartineau.keysight.timing.TimelinePhase

/**
 * The attempt state machine, as a pure reducer.
 *
 * It owns no clock and no coroutine. A controller feeds it the current time through
 * [AttemptEvent.ClockAdvanced] and asks [nextDeadlineNanos] when to do so next, which is how the
 * rule "one timeline, no second timer" is enforced structurally: there is exactly one moment
 * to wake up for, and it is derived from the timeline rather than from an accumulated delay.
 *
 * Whether a MIDI event is captured is decided by its own timestamp against the attempt clock,
 * not by the state the machine happens to be in when the event is delivered, so late delivery
 * cannot lose a note or admit a stray one.
 */
object AttemptMachine {

    fun reduce(state: AttemptState, event: AttemptEvent): AttemptState = when (event) {
        is AttemptEvent.Start -> start(state, event.nowNanos)
        is AttemptEvent.ClockAdvanced -> if (state is Running) advance(state, event.nowNanos) else state
        is AttemptEvent.MidiReceived -> if (state is Running) capture(state, event) else state
        is AttemptEvent.Abort -> abort(state, event.reason)
        is AttemptEvent.Evaluated -> evaluated(state, event)
        is AttemptEvent.Next -> next(state, event)
    }

    /** The absolute instant the controller must deliver the next [AttemptEvent.ClockAdvanced]. */
    fun nextDeadlineNanos(state: AttemptState): Long? {
        val timeline = state.context.timeline
        return when (state) {
            is CountingIn ->
                state.startedAtNanos + if (state.notationVisible) timeline.performanceStartNanos else timeline.previewStartNanos
            is Performing -> state.startedAtNanos + timeline.captureEndNanos
            else -> null
        }
    }

    private fun start(state: AttemptState, nowNanos: Long): AttemptState {
        check(state is Ready) { "cannot start from $state" }
        val started = CountingIn(state.context, nowNanos, notationVisible = false, captured = emptyList())
        return advance(started, nowNanos)
    }

    private fun advance(state: Running, nowNanos: Long): AttemptState {
        val elapsed = (nowNanos - state.startedAtNanos).coerceAtLeast(0)
        return when (state.context.timeline.phaseAt(elapsed)) {
            TimelinePhase.COUNT_IN -> countingIn(state, notationVisible = false)
            TimelinePhase.PREVIEW -> countingIn(state, notationVisible = true)
            TimelinePhase.PERFORMING, TimelinePhase.CAPTURE_TAIL ->
                if (state is Performing) state else Performing(state.context, state.startedAtNanos, state.captured)
            TimelinePhase.ENDED -> Evaluating(state.context, state.startedAtNanos, state.captured)
        }
    }

    private fun countingIn(state: Running, notationVisible: Boolean): AttemptState = when {
        state is CountingIn && state.notationVisible == notationVisible -> state
        else -> CountingIn(state.context, state.startedAtNanos, notationVisible, state.captured)
    }

    private fun capture(state: Running, event: AttemptEvent.MidiReceived): AttemptState {
        val at = event.event.timestampNanos - state.startedAtNanos
        val inWindow = at >= 0 && at <= state.context.timeline.captureEndNanos
        if (!inWindow) return state
        val captured = state.captured + event.event
        return when (state) {
            is CountingIn -> state.copy(captured = captured)
            is Performing -> state.copy(captured = captured)
        }
    }

    private fun abort(state: AttemptState, reason: AbortReason): AttemptState = when (state) {
        is Ready -> Aborted(state.context, reason, startedAtNanos = null, captured = emptyList())
        is Running -> Aborted(state.context, reason, state.startedAtNanos, state.captured)
        is Evaluating -> Aborted(state.context, reason, state.startedAtNanos, state.captured)
        is Result, is Aborted -> state
    }

    private fun evaluated(state: AttemptState, event: AttemptEvent.Evaluated): AttemptState {
        check(state is Evaluating) { "cannot accept an evaluation in $state" }
        return Result(state.context, state.startedAtNanos, state.captured, event.result)
    }

    private fun next(state: AttemptState, event: AttemptEvent.Next): AttemptState {
        check(state.isTerminal) { "cannot move to the next exercise from $state" }
        return Ready(AttemptContext(event.exercise, event.config))
    }
}
