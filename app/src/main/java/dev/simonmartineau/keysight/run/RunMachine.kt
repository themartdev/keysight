package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.run.RunState.Aborted
import dev.simonmartineau.keysight.run.RunState.CountingIn
import dev.simonmartineau.keysight.run.RunState.Evaluating
import dev.simonmartineau.keysight.run.RunState.Performing
import dev.simonmartineau.keysight.run.RunState.Ready
import dev.simonmartineau.keysight.run.RunState.Running
import dev.simonmartineau.keysight.run.RunState.Summary
import dev.simonmartineau.keysight.timing.TimelinePhase

/**
 * The run state machine, as a pure reducer.
 *
 * It owns no clock and no coroutine. A controller feeds it the current time through
 * [RunEvent.ClockAdvanced] and asks [nextDeadlineNanos] when to do so next, which is how the
 * rule "one timeline, no second timer" is enforced structurally: there is exactly one moment
 * to wake up for, and it is derived from the timeline rather than from an accumulated delay.
 *
 * Whether a MIDI event is captured is decided by its own timestamp against the run clock, not
 * by the state the machine happens to be in when the event is delivered, so late delivery
 * cannot lose a note or admit a stray one.
 */
object RunMachine {

    fun reduce(state: RunState, event: RunEvent): RunState = when (event) {
        is RunEvent.Start -> start(state, event.nowNanos)
        is RunEvent.ClockAdvanced -> if (state is Running) advance(state, event.nowNanos) else state
        is RunEvent.MidiReceived -> if (state is Running) capture(state, event) else state
        is RunEvent.Stop -> stop(state, event.nowNanos)
        is RunEvent.Abort -> abort(state, event.reason)
        is RunEvent.Evaluated -> evaluated(state, event)
        is RunEvent.Next -> next(state, event)
    }

    /** The absolute instant the controller must deliver the next [RunEvent.ClockAdvanced]. */
    fun nextDeadlineNanos(state: RunState): Long? {
        val timeline = state.context.timeline
        return when (state) {
            is CountingIn -> state.startedAtNanos + timeline.performanceStartNanos
            is Performing -> state.startedAtNanos + timeline.captureEndNanosAfter(state.lastSegment)
            else -> null
        }
    }

    private fun start(state: RunState, nowNanos: Long): RunState {
        check(state is Ready) { "cannot start from $state" }
        val started = CountingIn(state.context, nowNanos, captured = emptyList(), lastSegment = state.context.lastSegment)
        return advance(started, nowNanos)
    }

    private fun advance(state: Running, nowNanos: Long): RunState {
        val timeline = state.context.timeline
        val elapsed = (nowNanos - state.startedAtNanos).coerceAtLeast(0)
        val ended = elapsed >= timeline.captureEndNanosAfter(state.lastSegment)
        return when {
            ended -> Evaluating(state.context, state.startedAtNanos, state.captured, state.lastSegment)
            timeline.phaseAt(elapsed) == TimelinePhase.COUNT_IN -> state
            state is Performing -> state
            else -> Performing(state.context, state.startedAtNanos, state.captured, state.lastSegment)
        }
    }

    private fun capture(state: Running, event: RunEvent.MidiReceived): RunState {
        val at = event.event.timestampNanos - state.startedAtNanos
        val inWindow = at >= 0 && at <= state.context.timeline.captureEndNanosAfter(state.lastSegment)
        if (!inWindow) return state
        val captured = state.captured + event.event
        return when (state) {
            is CountingIn -> state.copy(captured = captured)
            is Performing -> state.copy(captured = captured)
        }
    }

    /**
     * Stopping mid-performance keeps the segment the stop lands in and drops the ones after
     * it; the machine then ends where that segment's capture tail does. A stop that lands in
     * the capture tail of the last kept segment changes nothing.
     */
    private fun stop(state: RunState, nowNanos: Long): RunState = when (state) {
        is CountingIn -> abort(state, AbortReason.CANCELLED)
        is Performing -> {
            val timeline = state.context.timeline
            val current = timeline.segmentAt(timeline.beatAtNanos(nowNanos - state.startedAtNanos))
            val lastSegment = current.coerceIn(timeline.performedSegments.first, state.lastSegment)
            val stopped = if (lastSegment == state.lastSegment) state else state.copy(lastSegment = lastSegment)
            advance(stopped, nowNanos)
        }
        else -> state
    }

    private fun abort(state: RunState, reason: AbortReason): RunState = when (state) {
        is Ready -> Aborted(state.context, reason, startedAtNanos = null, captured = emptyList())
        is Running -> Aborted(state.context, reason, state.startedAtNanos, state.captured)
        is Evaluating -> Aborted(state.context, reason, state.startedAtNanos, state.captured)
        is Summary, is Aborted -> state
    }

    private fun evaluated(state: RunState, event: RunEvent.Evaluated): RunState {
        check(state is Evaluating) { "cannot accept an evaluation in $state" }
        return Summary(state.context, state.startedAtNanos, state.captured, state.lastSegment, event.result)
    }

    private fun next(state: RunState, event: RunEvent.Next): RunState {
        check(state.isTerminal) { "cannot move to the next run from $state" }
        return Ready(event.context)
    }
}
