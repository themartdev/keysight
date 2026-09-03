package dev.simonmartineau.keysight.attempt

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.attempt.AttemptState.Aborted
import dev.simonmartineau.keysight.attempt.AttemptState.CountingIn
import dev.simonmartineau.keysight.attempt.AttemptState.Evaluating
import dev.simonmartineau.keysight.attempt.AttemptState.Performing
import dev.simonmartineau.keysight.attempt.AttemptState.Ready
import dev.simonmartineau.keysight.attempt.AttemptState.Result
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.timing.MonotonicClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class AttemptMachineTest {

    private val ready = Ready(Fixtures.slowContext)
    private val evaluation = EvaluationResult(evaluatorVersion = 1, pitch = PitchResult(emptyList()))

    /** A clock the test moves by hand. */
    private class FakeClock(var now: Long) : MonotonicClock {
        override fun nowNanos(): Long = now
    }

    /** Drives the machine the way a controller will: wake at each deadline and advance the clock. */
    private fun runToNextDeadline(state: AttemptState, clock: FakeClock): AttemptState {
        val deadline = AttemptMachine.nextDeadlineNanos(state) ?: error("no deadline in $state")
        clock.now = deadline
        return AttemptMachine.reduce(state, AttemptEvent.ClockAdvanced(clock.nowNanos()))
    }

    @Test
    fun `the happy path runs through every state at the exact deadlines`() {
        val clock = FakeClock(now = 10 * SECOND_NANOS)
        var state = AttemptMachine.reduce(ready, AttemptEvent.Start(clock.nowNanos()))

        assertEquals(CountingIn(Fixtures.slowContext, 10 * SECOND_NANOS, notationVisible = false, captured = emptyList()), state)
        assertEquals(12 * SECOND_NANOS, AttemptMachine.nextDeadlineNanos(state))

        state = runToNextDeadline(state, clock)
        assertEquals(CountingIn(Fixtures.slowContext, 10 * SECOND_NANOS, notationVisible = true, captured = emptyList()), state)
        assertEquals(14 * SECOND_NANOS, AttemptMachine.nextDeadlineNanos(state))

        state = runToNextDeadline(state, clock)
        assertEquals(Performing(Fixtures.slowContext, 10 * SECOND_NANOS, captured = emptyList()), state)
        assertEquals(19 * SECOND_NANOS, AttemptMachine.nextDeadlineNanos(state))

        state = runToNextDeadline(state, clock)
        assertEquals(Evaluating(Fixtures.slowContext, 10 * SECOND_NANOS, captured = emptyList()), state)
        assertNull(AttemptMachine.nextDeadlineNanos(state))

        state = AttemptMachine.reduce(state, AttemptEvent.Evaluated(evaluation))
        assertEquals(Result(Fixtures.slowContext, 10 * SECOND_NANOS, emptyList(), evaluation), state)

        state = AttemptMachine.reduce(state, AttemptEvent.Next(Fixtures.exercise, Fixtures.slowConfig))
        assertEquals(ready, state)
    }

    @Test
    fun `a full count-in preview shows the notation from the start`() {
        val context = AttemptContext(Fixtures.exercise, Fixtures.slowConfig.copy(previewDurationBeats = 4.0))

        val state = AttemptMachine.reduce(Ready(context), AttemptEvent.Start(0))

        assertEquals(CountingIn(context, 0, notationVisible = true, captured = emptyList()), state)
        assertEquals(4 * SECOND_NANOS, AttemptMachine.nextDeadlineNanos(state))
    }

    @Test
    fun `one nanosecond before a deadline nothing has changed`() {
        val state = AttemptMachine.reduce(ready, AttemptEvent.Start(0))

        val stillCountingIn = AttemptMachine.reduce(state, AttemptEvent.ClockAdvanced(2 * SECOND_NANOS - 1))
        assertSame(state, stillCountingIn)

        val performing = AttemptMachine.reduce(state, AttemptEvent.ClockAdvanced(9 * SECOND_NANOS - 1))
        assertIs<Performing>(performing)
    }

    @Test
    fun `twenty consecutive attempts end exactly where arithmetic says`() {
        val clock = FakeClock(now = 123_456_789L)
        var state: AttemptState = ready

        repeat(20) { attempt ->
            val startedAt = clock.now
            state = AttemptMachine.reduce(state, AttemptEvent.Start(clock.nowNanos()))
            while (state !is Evaluating) state = runToNextDeadline(state, clock)

            assertEquals(startedAt + 9 * SECOND_NANOS, clock.now, "attempt $attempt")
            state = AttemptMachine.reduce(state, AttemptEvent.Evaluated(evaluation))
            state = AttemptMachine.reduce(state, AttemptEvent.Next(Fixtures.exercise, Fixtures.slowConfig))
        }
        assertEquals(123_456_789L + 20 * 9 * SECOND_NANOS, clock.now)
    }

    @Test
    fun `a clock jump straight past the end lands in evaluating`() {
        val state = AttemptMachine.reduce(ready, AttemptEvent.Start(0))

        assertIs<Evaluating>(AttemptMachine.reduce(state, AttemptEvent.ClockAdvanced(60 * SECOND_NANOS)))
    }

    @Test
    fun `clock ticks are idempotent and ignored outside a running attempt`() {
        val performing = AttemptMachine.reduce(AttemptMachine.reduce(ready, AttemptEvent.Start(0)), AttemptEvent.ClockAdvanced(5 * SECOND_NANOS))

        assertSame(performing, AttemptMachine.reduce(performing, AttemptEvent.ClockAdvanced(6 * SECOND_NANOS)))
        assertSame(ready, AttemptMachine.reduce(ready, AttemptEvent.ClockAdvanced(6 * SECOND_NANOS)))
    }

    @Test
    fun `MIDI is captured by its own timestamp, count-in included`() {
        val started = AttemptMachine.reduce(ready, AttemptEvent.Start(10 * SECOND_NANOS))

        val beforeStart = MidiEvent.noteOn(10 * SECOND_NANOS - 1, Pitch.C4, 100)
        val duringCountIn = MidiEvent.noteOn(11 * SECOND_NANOS, Pitch.C4, 100)
        val duringPerformance = MidiEvent.noteOn(15 * SECOND_NANOS, Pitch.C4, 100)
        val atCaptureEnd = MidiEvent.noteOn(19 * SECOND_NANOS, Pitch.C4, 100)
        val afterCaptureEnd = MidiEvent.noteOn(19 * SECOND_NANOS + 1, Pitch.C4, 100)

        var state = started
        listOf(beforeStart, duringCountIn, duringPerformance, atCaptureEnd, afterCaptureEnd).forEach {
            state = AttemptMachine.reduce(state, AttemptEvent.MidiReceived(it))
        }

        assertIs<CountingIn>(state)
        assertEquals(listOf(duringCountIn, duringPerformance, atCaptureEnd), state.captured)
    }

    @Test
    fun `MIDI is ignored when nothing is running`() {
        val event = AttemptEvent.MidiReceived(MidiEvent.noteOn(0, Pitch.C4, 100))

        assertSame(ready, AttemptMachine.reduce(ready, event))
        val evaluating = AttemptMachine.reduce(AttemptMachine.reduce(ready, AttemptEvent.Start(0)), AttemptEvent.ClockAdvanced(9 * SECOND_NANOS))
        assertSame(evaluating, AttemptMachine.reduce(evaluating, event))
    }

    @Test
    fun `capture survives the transition into performing`() {
        var state = AttemptMachine.reduce(ready, AttemptEvent.Start(0))
        val early = MidiEvent.noteOn(SECOND_NANOS, Pitch.C4, 100)
        state = AttemptMachine.reduce(state, AttemptEvent.MidiReceived(early))
        state = AttemptMachine.reduce(state, AttemptEvent.ClockAdvanced(4 * SECOND_NANOS))
        val played = MidiEvent.noteOn(4 * SECOND_NANOS, Pitch.C4, 100)
        state = AttemptMachine.reduce(state, AttemptEvent.MidiReceived(played))
        state = AttemptMachine.reduce(state, AttemptEvent.ClockAdvanced(9 * SECOND_NANOS))

        assertEquals(Evaluating(Fixtures.slowContext, 0, listOf(early, played)), state)
    }

    @Test
    fun `every non-terminal state can be abandoned for every reason`() {
        val started = AttemptMachine.reduce(ready, AttemptEvent.Start(0))
        val nonTerminal = listOf(
            ready,
            started,
            AttemptMachine.reduce(started, AttemptEvent.ClockAdvanced(2 * SECOND_NANOS)),
            AttemptMachine.reduce(started, AttemptEvent.ClockAdvanced(4 * SECOND_NANOS)),
            AttemptMachine.reduce(started, AttemptEvent.ClockAdvanced(9 * SECOND_NANOS)),
        )

        nonTerminal.forEach { state ->
            AbortReason.entries.forEach { reason ->
                val aborted = AttemptMachine.reduce(state, AttemptEvent.Abort(reason))
                assertIs<Aborted>(aborted, "$state should abort")
                assertEquals(reason, aborted.reason)
                assertEquals(if (state is Ready) null else 0L, aborted.startedAtNanos)
            }
        }
    }

    @Test
    fun `an aborted attempt keeps what was captured and goes back to ready`() {
        var state = AttemptMachine.reduce(ready, AttemptEvent.Start(0))
        val event = MidiEvent.noteOn(SECOND_NANOS, Pitch.C4, 100)
        state = AttemptMachine.reduce(state, AttemptEvent.MidiReceived(event))
        state = AttemptMachine.reduce(state, AttemptEvent.Abort(AbortReason.MIDI_DISCONNECTED))

        assertEquals(Aborted(Fixtures.slowContext, AbortReason.MIDI_DISCONNECTED, 0, listOf(event)), state)
        assertNull(AttemptMachine.nextDeadlineNanos(state))
        assertEquals(ready, AttemptMachine.reduce(state, AttemptEvent.Next(Fixtures.exercise, Fixtures.slowConfig)))
    }

    @Test
    fun `an interruption that races with completion is ignored`() {
        val result = Result(Fixtures.slowContext, 0, emptyList(), evaluation)
        val aborted = Aborted(Fixtures.slowContext, AbortReason.CANCELLED, 0, emptyList())

        assertSame(result, AttemptMachine.reduce(result, AttemptEvent.Abort(AbortReason.BACKGROUNDED)))
        assertSame(aborted, AttemptMachine.reduce(aborted, AttemptEvent.Abort(AbortReason.BACKGROUNDED)))
    }

    @Test
    fun `commands in the wrong state are programming errors`() {
        val started = AttemptMachine.reduce(ready, AttemptEvent.Start(0))
        val next = AttemptEvent.Next(Fixtures.exercise, Fixtures.slowConfig)

        assertFailsWith<IllegalStateException> { AttemptMachine.reduce(started, AttemptEvent.Start(1)) }
        assertFailsWith<IllegalStateException> { AttemptMachine.reduce(ready, AttemptEvent.Evaluated(evaluation)) }
        assertFailsWith<IllegalStateException> { AttemptMachine.reduce(started, AttemptEvent.Evaluated(evaluation)) }
        assertFailsWith<IllegalStateException> { AttemptMachine.reduce(ready, next) }
        assertFailsWith<IllegalStateException> { AttemptMachine.reduce(started, next) }
    }
}
