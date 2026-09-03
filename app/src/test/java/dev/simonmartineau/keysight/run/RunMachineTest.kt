package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.RunState.Aborted
import dev.simonmartineau.keysight.run.RunState.CountingIn
import dev.simonmartineau.keysight.run.RunState.Evaluating
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

/**
 * Against a three-segment run at 60 bpm in 4/4: the count-in is seconds 0 to 4, segment 1 is
 * 4 to 8, segment 2 is 8 to 12, segment 3 is 12 to 16 and capture ends at 17.
 */
class RunMachineTest {

    private val context = Fixtures.run(Fixtures.cdef, Fixtures.gfed, Fixtures.cdef)
    private val ready = Ready(context)
    private val evaluation = EvaluationResult(evaluatorVersion = 1, pitch = PitchResult(emptyList()))

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

    @Test
    fun `the happy path runs through every state at the exact deadlines`() {
        val clock = FakeClock(now = 10 * SECOND_NANOS)
        var state = RunMachine.reduce(ready, RunEvent.Start(clock.nowNanos()))

        assertEquals(CountingIn(context, 10 * SECOND_NANOS, captured = emptyList(), lastSegment = 3), state)
        assertEquals(14 * SECOND_NANOS, RunMachine.nextDeadlineNanos(state))

        state = runToNextDeadline(state, clock)
        assertEquals(Performing(context, 10 * SECOND_NANOS, captured = emptyList(), lastSegment = 3), state)
        assertEquals(27 * SECOND_NANOS, RunMachine.nextDeadlineNanos(state))

        state = runToNextDeadline(state, clock)
        assertEquals(Evaluating(context, 10 * SECOND_NANOS, captured = emptyList(), lastSegment = 3), state)
        assertNull(RunMachine.nextDeadlineNanos(state))

        state = RunMachine.reduce(state, RunEvent.Evaluated(evaluation))
        assertEquals(Summary(context, 10 * SECOND_NANOS, emptyList(), lastSegment = 3, evaluation = evaluation), state)
        assertEquals(context.score, (state as Summary).performed.score)

        state = RunMachine.reduce(state, RunEvent.Next(context))
        assertEquals(ready, state)
    }

    @Test
    fun `one nanosecond before a deadline nothing has changed`() {
        val state = RunMachine.reduce(ready, RunEvent.Start(0))

        assertSame(state, RunMachine.reduce(state, RunEvent.ClockAdvanced(4 * SECOND_NANOS - 1)))
        val performing = RunMachine.reduce(state, RunEvent.ClockAdvanced(17 * SECOND_NANOS - 1))
        assertIs<Performing>(performing)
        assertSame(performing, RunMachine.reduce(performing, RunEvent.ClockAdvanced(17 * SECOND_NANOS - 1)))
    }

    @Test
    fun `twenty consecutive runs end exactly where arithmetic says`() {
        val clock = FakeClock(now = 123_456_789L)
        var state: RunState = ready

        repeat(20) { run ->
            val startedAt = clock.now
            state = RunMachine.reduce(state, RunEvent.Start(clock.nowNanos()))
            while (state !is Evaluating) state = runToNextDeadline(state, clock)

            assertEquals(startedAt + 17 * SECOND_NANOS, clock.now, "run $run")
            state = RunMachine.reduce(state, RunEvent.Evaluated(evaluation))
            state = RunMachine.reduce(state, RunEvent.Next(context))
        }
        assertEquals(123_456_789L + 20 * 17 * SECOND_NANOS, clock.now)
    }

    @Test
    fun `a clock jump straight past the end lands in evaluating`() {
        val state = RunMachine.reduce(ready, RunEvent.Start(0))

        assertIs<Evaluating>(RunMachine.reduce(state, RunEvent.ClockAdvanced(60 * SECOND_NANOS)))
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
        val evaluating = RunMachine.reduce(RunMachine.reduce(ready, RunEvent.Start(0)), RunEvent.ClockAdvanced(17 * SECOND_NANOS))
        assertSame(evaluating, RunMachine.reduce(evaluating, event))
    }

    @Test
    fun `capture survives the transition into performing`() {
        var state = RunMachine.reduce(ready, RunEvent.Start(0))
        val early = MidiEvent.noteOn(SECOND_NANOS, Pitch.C4, 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(early))
        state = RunMachine.reduce(state, RunEvent.ClockAdvanced(4 * SECOND_NANOS))
        val played = MidiEvent.noteOn(4 * SECOND_NANOS, Pitch.C4, 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(played))
        state = RunMachine.reduce(state, RunEvent.ClockAdvanced(17 * SECOND_NANOS))

        assertEquals(Evaluating(context, 0, listOf(early, played), lastSegment = 3), state)
    }

    @Test
    fun `stopping mid-performance keeps the current segment and ends after its capture tail`() {
        var state = RunMachine.reduce(ready, RunEvent.Start(0))
        state = RunMachine.reduce(state, RunEvent.ClockAdvanced(9 * SECOND_NANOS))
        assertIs<Performing>(state)

        state = RunMachine.reduce(state, RunEvent.Stop(9 * SECOND_NANOS + 500_000_000L))
        assertEquals(Performing(context, 0, emptyList(), lastSegment = 2), state)
        assertEquals(13 * SECOND_NANOS, RunMachine.nextDeadlineNanos(state))

        val late = MidiEvent.noteOn(13 * SECOND_NANOS, Pitch.C4, 100)
        val tooLate = MidiEvent.noteOn(13 * SECOND_NANOS + 1, Pitch.C4, 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(late))
        state = RunMachine.reduce(state, RunEvent.MidiReceived(tooLate))
        assertEquals(listOf(late), (state as Performing).captured)

        state = RunMachine.reduce(state, RunEvent.ClockAdvanced(13 * SECOND_NANOS))
        assertEquals(Evaluating(context, 0, listOf(late), lastSegment = 2), state)

        val summary = RunMachine.reduce(state, RunEvent.Evaluated(evaluation)) as Summary
        assertEquals(3, summary.performed.score.measureCount)
        assertEquals(3, summary.performed.timeline.segmentCount)
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
        assertSame(tail, RunMachine.reduce(tail, RunEvent.Stop(16 * SECOND_NANOS + 1)))
    }

    @Test
    fun `stopping during the count-in cancels the run`() {
        var state = RunMachine.reduce(ready, RunEvent.Start(0))
        val early = MidiEvent.noteOn(SECOND_NANOS, Pitch.C4, 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(early))

        state = RunMachine.reduce(state, RunEvent.Stop(2 * SECOND_NANOS))

        assertEquals(Aborted(context, AbortReason.CANCELLED, 0, listOf(early)), state)
    }

    @Test
    fun `a stop outside a running run is ignored`() {
        val evaluating = RunMachine.reduce(RunMachine.reduce(ready, RunEvent.Start(0)), RunEvent.ClockAdvanced(17 * SECOND_NANOS))
        val summary = Summary(context, 0, emptyList(), 3, evaluation)

        assertSame(ready, RunMachine.reduce(ready, RunEvent.Stop(0)))
        assertSame(evaluating, RunMachine.reduce(evaluating, RunEvent.Stop(18 * SECOND_NANOS)))
        assertSame(summary, RunMachine.reduce(summary, RunEvent.Stop(18 * SECOND_NANOS)))
    }

    @Test
    fun `every non-terminal state can be abandoned for every reason`() {
        val started = RunMachine.reduce(ready, RunEvent.Start(0))
        val nonTerminal = listOf(
            ready,
            started,
            RunMachine.reduce(started, RunEvent.ClockAdvanced(4 * SECOND_NANOS)),
            RunMachine.reduce(started, RunEvent.ClockAdvanced(17 * SECOND_NANOS)),
        )

        nonTerminal.forEach { state ->
            AbortReason.entries.forEach { reason ->
                val aborted = RunMachine.reduce(state, RunEvent.Abort(reason))
                assertIs<Aborted>(aborted, "$state should abort")
                assertEquals(reason, aborted.reason)
                assertEquals(if (state is Ready) null else 0L, aborted.startedAtNanos)
            }
        }
    }

    @Test
    fun `an aborted run keeps what was captured and goes back to ready`() {
        var state = RunMachine.reduce(ready, RunEvent.Start(0))
        val event = MidiEvent.noteOn(SECOND_NANOS, Pitch.C4, 100)
        state = RunMachine.reduce(state, RunEvent.MidiReceived(event))
        state = RunMachine.reduce(state, RunEvent.Abort(AbortReason.MIDI_DISCONNECTED))

        assertEquals(Aborted(context, AbortReason.MIDI_DISCONNECTED, 0, listOf(event)), state)
        assertNull(RunMachine.nextDeadlineNanos(state))
        assertEquals(ready, RunMachine.reduce(state, RunEvent.Next(context)))
    }

    @Test
    fun `an interruption that races with completion is ignored`() {
        val summary = Summary(context, 0, emptyList(), 3, evaluation)
        val aborted = Aborted(context, AbortReason.CANCELLED, 0, emptyList())

        assertSame(summary, RunMachine.reduce(summary, RunEvent.Abort(AbortReason.BACKGROUNDED)))
        assertSame(aborted, RunMachine.reduce(aborted, RunEvent.Abort(AbortReason.BACKGROUNDED)))
    }

    @Test
    fun `commands in the wrong state are programming errors`() {
        val started = RunMachine.reduce(ready, RunEvent.Start(0))
        val next = RunEvent.Next(context)

        assertFailsWith<IllegalStateException> { RunMachine.reduce(started, RunEvent.Start(1)) }
        assertFailsWith<IllegalStateException> { RunMachine.reduce(ready, RunEvent.Evaluated(evaluation)) }
        assertFailsWith<IllegalStateException> { RunMachine.reduce(started, RunEvent.Evaluated(evaluation)) }
        assertFailsWith<IllegalStateException> { RunMachine.reduce(ready, next) }
        assertFailsWith<IllegalStateException> { RunMachine.reduce(started, next) }
    }
}
