package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.evaluation.RunEvaluation
import dev.simonmartineau.keysight.run.RunState.Aborted
import dev.simonmartineau.keysight.run.RunState.CountingIn
import dev.simonmartineau.keysight.run.RunState.Performing
import dev.simonmartineau.keysight.run.RunState.Ready
import dev.simonmartineau.keysight.run.RunState.Running
import dev.simonmartineau.keysight.run.RunState.Summary

/**
 * The run state machine, as a pure reducer.
 *
 * It owns no clock and no coroutine. A controller feeds it the current time through
 * [RunEvent.ClockAdvanced] and asks [nextDeadlineNanos] when to do so next, which is how the
 * rule "one timeline, no second timer" is enforced structurally: there is exactly one moment
 * to wake up for, and it is derived from the timeline rather than from an accumulated delay.
 *
 * The deadlines while performing are the capture tails of the segments: at each one the
 * machine commits that segment through [PerformanceEvaluator], a pure call over the events
 * captured so far, and the last commit is the summary. Whether a MIDI event is captured is
 * decided by its own timestamp against the run clock, not by the state the machine happens to
 * be in when the event is delivered, so late delivery cannot lose a note or admit a stray one.
 */
object RunMachine {

    fun reduce(state: RunState, event: RunEvent): RunState = when (event) {
        is RunEvent.Start -> start(state, event.nowNanos)
        is RunEvent.ClockAdvanced -> if (state is Running) advance(state, event.nowNanos) else state
        is RunEvent.MidiReceived -> if (state is Running) capture(state, event) else state
        is RunEvent.Stop -> stop(state, event.nowNanos)
        is RunEvent.Abort -> abort(state, event.reason, event.nowNanos)
        is RunEvent.Extended -> if (state is Running) extend(state, event.segments) else state
        is RunEvent.Next -> next(state, event)
    }

    /** The absolute instant the controller must deliver the next [RunEvent.ClockAdvanced]. */
    fun nextDeadlineNanos(state: RunState): Long? {
        val timeline = state.context.timeline
        return when (state) {
            is CountingIn -> state.startedAtNanos + timeline.performanceStartNanos
            is Performing -> state.startedAtNanos + timeline.captureEndNanosAfter(state.evaluation.committedCount + 1)
            else -> null
        }
    }

    private fun start(state: RunState, nowNanos: Long): RunState {
        check(state is Ready) { "cannot start from $state" }
        return advance(CountingIn(state.context, nowNanos, captured = emptyList()), nowNanos)
    }

    /**
     * Moves through every boundary [nowNanos] has passed: into the performance, then one commit
     * per segment whose capture tail is over, and to the summary once the last segment that
     * will be performed is committed.
     */
    private fun advance(state: Running, nowNanos: Long): RunState {
        val timeline = state.context.timeline
        val elapsed = (nowNanos - state.startedAtNanos).coerceAtLeast(0)
        var performing = when (state) {
            is CountingIn -> if (elapsed < timeline.performanceStartNanos) return state else Performing(state.context, state.startedAtNanos, state.captured)
            is Performing -> state
        }
        while (true) {
            val next = performing.evaluation.committedCount + 1
            if (next > performing.lastSegment) return performing.summary()
            if (elapsed < timeline.captureEndNanosAfter(next)) return performing
            val evaluation = PerformanceEvaluator.commit(
                evaluation = performing.evaluation,
                score = performing.context.score,
                timeline = timeline,
                startedAtNanos = performing.startedAtNanos,
                events = performing.captured,
                segment = next,
                lastSegment = performing.lastSegment,
            )
            performing = performing.copy(evaluation = evaluation)
            if (next == performing.lastSegment) return performing.summary()
        }
    }

    private fun Performing.summary() = Summary(context, startedAtNanos, captured, lastSegment, evaluation)

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
     * Stopping mid-performance pins the end at the segment the stop lands in and drops the
     * ones after it; the machine then ends where that segment's capture tail does, and no
     * extension can move the end again. A stop that lands in the capture tail of the last
     * kept segment keeps that segment.
     */
    private fun stop(state: RunState, nowNanos: Long): RunState = when (state) {
        is CountingIn -> abort(state, AbortReason.CANCELLED, nowNanos)
        is Performing -> {
            val lastSegment = state.segmentAt(nowNanos).coerceIn(state.context.timeline.performedSegments.first, state.lastSegment)
            val stopped = if (lastSegment == state.stopAfter) state else state.copy(stopAfter = lastSegment)
            advance(stopped, nowNanos)
        }
        else -> state
    }

    private fun abort(state: RunState, reason: AbortReason, nowNanos: Long): RunState = when (state) {
        is Ready -> Aborted(state.context, reason, startedAtNanos = null, captured = emptyList(), lastSegment = null, evaluation = RunEvaluation.EMPTY)
        is Running -> Aborted(state.context, reason, state.startedAtNanos, state.captured, state.segmentAt(nowNanos).coerceIn(1, state.lastSegment), state.evaluation)
        is Summary, is Aborted -> state
    }

    private fun Running.segmentAt(nowNanos: Long): Int {
        val timeline = context.timeline
        return timeline.segmentAt(timeline.beatAtNanos(nowNanos - startedAtNanos))
    }

    private fun extend(state: Running, segments: List<Segment>): RunState = when (state) {
        is CountingIn -> state.copy(context = state.context.extended(segments))
        is Performing -> state.copy(context = state.context.extended(segments))
    }

    private fun next(state: RunState, event: RunEvent.Next): RunState {
        check(state.isTerminal) { "cannot move to the next run from $state" }
        return Ready(event.context)
    }
}
