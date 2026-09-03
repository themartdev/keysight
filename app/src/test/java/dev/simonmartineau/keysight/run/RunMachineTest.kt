package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.evaluation.RunEvaluation
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.RunState.Aborted
import dev.simonmartineau.keysight.run.RunState.CountingIn
import dev.simonmartineau.keysight.run.RunState.Performing
import dev.simonmartineau.keysight.run.RunState.Ready
import dev.simonmartineau.keysight.run.RunState.Summary
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.timing.MonotonicClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Against a three-segment run at 60 bpm in 4/4: the count-in is seconds 0 to 4, segment 1 is
 * 4 to 8, segment 2 is 8 to 12, segment 3 is 12 to 16 and capture ends at 17. Segment k is
 * committed at the end of its capture tail: 9, 13 and 17 seconds.
 */
class RunMachineTest {

    private val context = Fixtures.run(Fixtures.cdef, Fixtures.gfed, Fixtures.cdef)
    private val ready = Ready(context)

    /** A clock the test moves by hand. */
    private class FakeClock(var now: Long) : MonotonicClock {
        override fun nowNanos(): Long = now
    }

    /** Drives the machine the way a controller will: wake at each deadline and advance the clock. */
    private fun runToNextDeadline(state: RunState, clock: FakeClock): RunState {
        val deadline = RunMachine.nextDeadlineNanos(state) ?: error("no deadline in $state")
        clock.now = deadline
        return RunMachine.reduce(state, RunEvent.ClockAdvanced(clock.nowNanos()))
    }

    private fun perfect(state: RunState, startedAtNanos: Long, segment: Int, pitches: List<Int>): RunState {
        var current = state
        pitches.forEachIndexed { index, pitch ->
            val at = startedAtNanos + (4 * segment + index) * SECOND_NANOS
            current = RunMachine.reduce(current, RunEvent.MidiReceived(MidiEvent.noteOn(at, Pitch(pitch), 100)))
            current = RunMachine.reduce(current, RunEvent.MidiReceived(MidiEvent.noteOff(at + SECOND_NANOS / 2, Pitch(pitch))))
        }
        return current
    }

    @Test
    fun `the happy path runs through every state at the exact deadlines`() {
        val clock = FakeClock(now = 10 * SECOND_NANOS)
        var state = RunMachine.reduce(ready, RunEvent.Start(clock.nowNanos()))

        assertEquals(CountingIn(context, 10 * SECOND_NANOS, captured = emptyList()), state)
        assertEquals(3, (state as CountingIn).lastSegment)
        assertEquals(14 * SECOND_NANOS, RunMachine.nextDeadlineNanos(state))

        state = runToNextDeadline(state, clock)
        assertEquals(Performing(context, 10 * SECOND_NANOS, captured = emptyList()), state)
        assertEquals(19 * SECOND_NANOS, RunMachine.nextDeadlineNanos(state))

        state = runToNextDeadline(state, clock)
        assertEquals(1, assertIs<Performing>(state).evaluation.committedCount)
        assertEquals(23 * SECOND_NANOS, RunMachine.nextDeadlineNanos(state))

        state = runToNextDeadline(state, clock)
        assertEquals(2, assertIs<Performing>(state).evaluation.committedCount)
        assertEquals(27 * SECOND_NANOS, RunMachine.nextDeadlineNanos(state))

        state = runToNextDeadline(state, clock)
        val summary = assertIs<Summary>(state)
        assertEquals(3, summary.lastSegment)
        assertEquals(3, summary.evaluation.committedCount)
        assertEquals(12, summary.evaluation.pitch.missingCount)
        assertEquals(context.score, summary.performed.score)
        assertNull(RunMachine.nextDeadlineNanos(state))

        state = RunMachine.reduce(state, RunEvent.Next(context))
        assertEquals(ready, state)
    }

    @Test
    fun `each commit judges the bar just played, and the summary is their sum`() {
        var state = RunMachine.reduce(ready, RunEvent.Start(0))
        state = perfect(state, 0, segment = 1, pitches = listOf(60, 62, 64, 65))
        state = perfect(state, 0, segment = 2, pitches = listOf(67, 65, 64, 61))
        state = RunMachine.reduce(state, RunEvent.ClockAdvanced(13 * SECOND_NANOS))

        val performing = assertIs<Performing>(state)
        assertEquals(2, performing.evaluation.committedCount)
        assertEquals(1.0, performing.evaluation.segments[0].pitch.accuracy)
        assertEquals(0.75, performing.evaluation.segments[1].pitch.accuracy)
        assertEquals(17 * SECOND_NANOS, RunMachine.nextDeadlineNanos(performing))

        val summary = assertIs<Summary>(RunMachine.reduce(state, RunEvent.ClockAdvanced(17 * SECOND_NANOS)))
        assertEquals(3, summary.evaluation.committedCount)
        assertEquals(7, summary.evaluation.pitch.correctCount)
        assertEquals(12, summary.evaluation.pitch.expectedCount)
        assertEquals(PerformanceEvaluator.evaluate(context.score, context.timeline, 0, summary.captured), summary.evaluation)
    }

    @Test
    fun `one nanosecond before a deadline nothing has changed`() {
        val state = RunMachine.reduce(ready, RunEvent.Start(0))

        assertSame(state, RunMachine.reduce(state, RunEvent.ClockAdvanced(4 * SECOND_NANOS - 1)))
        val performing = RunMachine.reduce(state, RunEvent.ClockAdvanced(9 * SECOND_NANOS - 1))
        assertEquals(0, assertIs<Performing>(performing).evaluation.committedCount)
        assertSame(performing, RunMachine.reduce(performing, RunEvent.ClockAdvanced(9 * SECOND_NANOS - 1)))
        val committed = RunMachine.reduce(performing, RunEvent.ClockAdvanced(17 * SECOND_NANOS - 1))
        assertEquals(2, assertIs<Performing>(committed).evaluation.committedCount)
        assertSame(committed, RunMachine.reduce(committed, RunEvent.ClockAdvanced(17 * SECOND_NANOS - 1)))
    }

    @Test
    fun `twenty consecutive runs end exactly where arithmetic says`() {
        val clock = FakeClock(now = 123_456_789L)
        var state: RunState = ready

        repeat(20) { run ->
            val startedAt = clock.now
            state = RunMachine.reduce(state, RunEvent.Start(clock.nowNanos()))
            while (state !is Summary) state = runToNextDeadline(state, clock)

            assertEquals(startedAt + 17 * SECOND_NANOS, clock.now, "run $run")
            state = RunMachine.reduce(state, RunEvent.Next(context))
        }
        assertEquals(123_456_789L + 20 * 17 * SECOND_NANOS, clock.now)
    }

    @Test
    fun `a clock jump straight past the end commits every bar and lands in the summary`() {
        val state = RunMachine.reduce(ready, RunEvent.Start(0))

        val summary = assertIs<Summary>(RunMachine.reduce(state, RunEvent.ClockAdvanced(60 * SECOND_NANOS)))
        assertEquals(3, summary.evaluation.committedCount)
    }

    @Test
    fun `clock ticks are idempotent and ignored outside a running run`() {
        val performing = RunMachine.reduce(RunMachine.reduce(ready, RunEvent.Start(0)), RunEvent.ClockAdvanced(5 * SECOND_NANOS))

        assertSame(performing, RunMachine.reduce(performing, RunEvent.ClockAdvanced(6 * SECOND_NANOS)))
        assertSame(ready, RunMachine.reduce(ready, RunEvent.ClockAdvanced(6 * SECOND_NANOS)))
    }

    @Test
    fun `MIDI is captured by its own timestamp, count-in included`() {
        val started = RunMachine.reduce(ready, RunEvent.Start(10 * SECOND_NANOS))

        val beforeStart = MidiEvent.noteOn(10 * SECOND_NANOS - 1, Pitch.C4, 100)
        val duringCountIn = MidiEvent.noteOn(11 * SECOND_NANOS, Pitch.C4, 100)
        val duringPerformance = MidiEvent.noteOn(20 * SECOND_NANOS, Pitch.C4, 100)
        val atCaptureEnd = MidiEvent.noteOn(27 * SECOND_NANOS, Pitch.C4, 100)
        val afterCaptureEnd = MidiEvent.noteOn(27 * SECOND_NANOS + 1, Pitch.C4, 100)

        var state = started
        listOf(beforeStart, duringCountIn, duringPerformance, atCaptureEnd, afterCaptureEnd).forEach {
            state = RunMachine.reduce(state, RunEvent.MidiReceived(it))
        }

        assertIs<CountingIn>(state)
        assertEquals(listOf(duringCountIn, duringPerformance, atCaptureEnd), state.captured)
    }

    @Test
    fun `MIDI is ignored when nothing is running`() {
        val event = RunEvent.MidiReceived(MidiEvent.noteOn(0, Pitch.C4, 100))

        assertSame(ready, RunMachine.reduce(ready, event))
        val summary = RunMachine.reduce(RunMachine.reduce(ready, RunEvent.Start(0)), RunEvent.ClockAdvanced(17 * SECOND_NANOS))
        assertSame(summary, RunMachine.reduce(summary, event))
    }

    @Test
    fun `capture survives the transition into performing and a commit only sees what had arrived`() {
        var state = RunMachine.reduce(ready, RunEvent.Start(0))
        val early = MidiEvent.noteOn(SECOND_NANOS, Pitch.C4, 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(early))
        state = RunMachine.reduce(state, RunEvent.ClockAdvanced(4 * SECOND_NANOS))
        val played = MidiEvent.noteOn(4 * SECOND_NANOS, Pitch.C4, 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(played))
        // Delivered late: it arrives after the first commit's deadline but was struck in the second bar.
        val secondBar = MidiEvent.noteOn(8 * SECOND_NANOS, Pitch(67), 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(secondBar))
        state = RunMachine.reduce(state, RunEvent.ClockAdvanced(17 * SECOND_NANOS))

        val summary = assertIs<Summary>(state)
        assertEquals(listOf(early, played, secondBar), summary.captured)
        assertEquals(1, summary.evaluation.segments[0].pitch.correctCount)
        assertEquals(1, summary.evaluation.segments[1].pitch.correctCount)
    }

    @Test
    fun `stopping mid-performance keeps the current segment and ends after its capture tail`() {
        var state = RunMachine.reduce(ready, RunEvent.Start(0))
        state = RunMachine.reduce(state, RunEvent.ClockAdvanced(9 * SECOND_NANOS))
        assertEquals(1, assertIs<Performing>(state).evaluation.committedCount)

        state = RunMachine.reduce(state, RunEvent.Stop(9 * SECOND_NANOS + 500_000_000L))
        val stopped = assertIs<Performing>(state)
        assertEquals(2, stopped.stopAfter)
        assertEquals(2, stopped.lastSegment)
        assertEquals(13 * SECOND_NANOS, RunMachine.nextDeadlineNanos(state))

        val late = MidiEvent.noteOn(13 * SECOND_NANOS, Pitch.C4, 100)
        val tooLate = MidiEvent.noteOn(13 * SECOND_NANOS + 1, Pitch.C4, 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(late))
        state = RunMachine.reduce(state, RunEvent.MidiReceived(tooLate))
        assertEquals(listOf(late), (state as Performing).captured)

        val summary = assertIs<Summary>(RunMachine.reduce(state, RunEvent.ClockAdvanced(13 * SECOND_NANOS)))
        assertEquals(2, summary.lastSegment)
        assertEquals(2, summary.evaluation.committedCount)
        assertEquals(3, summary.performed.score.measureCount)
        assertEquals(3, summary.performed.timeline.segmentCount)
        assertEquals(context.segments.take(2), summary.performed.segments)
    }

    @Test
    fun `stopping again, or in the capture tail, changes nothing`() {
        var state = RunMachine.reduce(ready, RunEvent.Start(0))
        state = RunMachine.reduce(state, RunEvent.ClockAdvanced(5 * SECOND_NANOS))
        state = RunMachine.reduce(state, RunEvent.Stop(5 * SECOND_NANOS))
        assertEquals(1, (state as Performing).lastSegment)

        assertSame(state, RunMachine.reduce(state, RunEvent.Stop(6 * SECOND_NANOS)))
        assertSame(state, RunMachine.reduce(state, RunEvent.Stop(8 * SECOND_NANOS + 1)))

        val tail = RunMachine.reduce(RunMachine.reduce(ready, RunEvent.Start(0)), RunEvent.ClockAdvanced(16 * SECOND_NANOS + 1))
        val stoppedInTail = assertIs<Performing>(RunMachine.reduce(tail, RunEvent.Stop(16 * SECOND_NANOS + 1)))
        assertEquals(3, stoppedInTail.lastSegment)
        assertEquals(3, stoppedInTail.stopAfter)
        assertEquals(RunMachine.nextDeadlineNanos(tail), RunMachine.nextDeadlineNanos(stoppedInTail))
    }

    @Test
    fun `stopping during the count-in cancels the run`() {
        var state = RunMachine.reduce(ready, RunEvent.Start(0))
        val early = MidiEvent.noteOn(SECOND_NANOS, Pitch.C4, 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(early))

        state = RunMachine.reduce(state, RunEvent.Stop(2 * SECOND_NANOS))

        assertEquals(Aborted(context, AbortReason.CANCELLED, 0, listOf(early), lastSegment = 1, evaluation = RunEvaluation.EMPTY), state)
    }

    @Test
    fun `a stop outside a running run is ignored`() {
        val summary = RunMachine.reduce(RunMachine.reduce(ready, RunEvent.Start(0)), RunEvent.ClockAdvanced(17 * SECOND_NANOS))

        assertSame(ready, RunMachine.reduce(ready, RunEvent.Stop(0)))
        assertSame(summary, RunMachine.reduce(summary, RunEvent.Stop(18 * SECOND_NANOS)))
    }

    @Test
    fun `every non-terminal state can be abandoned for every reason`() {
        val started = RunMachine.reduce(ready, RunEvent.Start(0))
        val nonTerminal = listOf(
            ready,
            started,
            RunMachine.reduce(started, RunEvent.ClockAdvanced(4 * SECOND_NANOS)),
            RunMachine.reduce(started, RunEvent.ClockAdvanced(13 * SECOND_NANOS)),
        )

        nonTerminal.forEach { state ->
            AbortReason.entries.forEach { reason ->
                val aborted = RunMachine.reduce(state, RunEvent.Abort(reason, 13 * SECOND_NANOS + 1))
                assertIs<Aborted>(aborted, "$state should abort")
                assertEquals(reason, aborted.reason)
                assertEquals(if (state is Ready) null else 0L, aborted.startedAtNanos)
                assertEquals(if (state is Ready) null else 3, aborted.lastSegment)
                assertEquals(if (state is Performing) state.evaluation else RunEvaluation.EMPTY, aborted.evaluation)
            }
        }
    }

    @Test
    fun `an aborted run keeps what was captured, where it got to, and goes back to ready`() {
        var state = RunMachine.reduce(ready, RunEvent.Start(0))
        val event = MidiEvent.noteOn(SECOND_NANOS, Pitch.C4, 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(event))
        state = RunMachine.reduce(state, RunEvent.Abort(AbortReason.MIDI_DISCONNECTED, SECOND_NANOS))

        assertEquals(Aborted(context, AbortReason.MIDI_DISCONNECTED, 0, listOf(event), lastSegment = 1, evaluation = RunEvaluation.EMPTY), state)
        assertNull(RunMachine.nextDeadlineNanos(state))
        assertEquals(ready, RunMachine.reduce(state, RunEvent.Next(context)))

        val midRun = RunMachine.reduce(RunMachine.reduce(ready, RunEvent.Start(0)), RunEvent.ClockAdvanced(9 * SECOND_NANOS))
        val abortedMidRun = assertIs<Aborted>(RunMachine.reduce(midRun, RunEvent.Abort(AbortReason.BACKGROUNDED, 10 * SECOND_NANOS)))
        assertEquals(2, abortedMidRun.lastSegment)
        assertEquals(1, abortedMidRun.evaluation.committedCount)
    }

    @Test
    fun `an interruption that races with completion is ignored`() {
        val summary = RunMachine.reduce(RunMachine.reduce(ready, RunEvent.Start(0)), RunEvent.ClockAdvanced(17 * SECOND_NANOS))
        val aborted = Aborted(context, AbortReason.CANCELLED, 0, emptyList(), lastSegment = 1, evaluation = RunEvaluation.EMPTY)

        assertSame(summary, RunMachine.reduce(summary, RunEvent.Abort(AbortReason.BACKGROUNDED, 18 * SECOND_NANOS)))
        assertSame(aborted, RunMachine.reduce(aborted, RunEvent.Abort(AbortReason.BACKGROUNDED, 18 * SECOND_NANOS)))
    }

    @Test
    fun `an open-ended run grows as segments arrive and ends where the stop lands`() {
        val open = RunContext(context.segments, Fixtures.slowConfig.copy(segmentCount = null))
        var state = RunMachine.reduce(Ready(open), RunEvent.Start(0))
        assertTrue(open.timeline.openEnded)

        state = RunMachine.reduce(state, RunEvent.Extended(listOf(Segment("segment-4", Fixtures.gfed))))
        assertEquals(4, assertIs<CountingIn>(state).lastSegment)
        state = RunMachine.reduce(state, RunEvent.ClockAdvanced(17 * SECOND_NANOS))
        val performing = assertIs<Performing>(state)
        assertEquals(3, performing.evaluation.committedCount)
        assertEquals(5, performing.context.timeline.segmentCount)
        assertEquals(21 * SECOND_NANOS, RunMachine.nextDeadlineNanos(performing))

        state = RunMachine.reduce(state, RunEvent.Stop(17 * SECOND_NANOS))
        assertEquals(4, assertIs<Performing>(state).stopAfter)
        state = RunMachine.reduce(state, RunEvent.Extended(listOf(Segment("segment-5", Fixtures.cdef))))
        assertEquals(4, assertIs<Performing>(state).lastSegment)
        assertEquals(6, state.context.timeline.segmentCount)

        val summary = assertIs<Summary>(RunMachine.reduce(state, RunEvent.ClockAdvanced(21 * SECOND_NANOS)))
        assertEquals(4, summary.lastSegment)
        assertEquals(4, summary.evaluation.committedCount)
        assertEquals(5, summary.performed.score.measureCount)
        assertEquals(false, summary.performed.timeline.openEnded)
    }

    @Test
    fun `an extension is ignored outside a running run`() {
        val more = RunEvent.Extended(listOf(Segment("segment-4", Fixtures.gfed)))

        assertSame(ready, RunMachine.reduce(ready, more))
        val summary = RunMachine.reduce(RunMachine.reduce(ready, RunEvent.Start(0)), RunEvent.ClockAdvanced(17 * SECOND_NANOS))
        assertSame(summary, RunMachine.reduce(summary, more))
    }

    @Test
    fun `commands in the wrong state are programming errors`() {
        val started = RunMachine.reduce(ready, RunEvent.Start(0))
        val next = RunEvent.Next(context)

        assertFailsWith<IllegalStateException> { RunMachine.reduce(started, RunEvent.Start(1)) }
        assertFailsWith<IllegalStateException> { RunMachine.reduce(ready, next) }
        assertFailsWith<IllegalStateException> { RunMachine.reduce(started, next) }
    }
}
