package dev.simonmartineau.keysight.attempt

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.attempt.AttemptState.Aborted
import dev.simonmartineau.keysight.attempt.AttemptState.CountingIn
import dev.simonmartineau.keysight.attempt.AttemptState.Performing
import dev.simonmartineau.keysight.attempt.AttemptState.Ready
import dev.simonmartineau.keysight.attempt.AttemptState.Result
import dev.simonmartineau.keysight.audio.Metronome
import dev.simonmartineau.keysight.audio.MetronomeStart
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.exercise.Exercise
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.timing.AttemptTimeline
import dev.simonmartineau.keysight.timing.MonotonicClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttemptControllerTest {

    /** The one clock: virtual time, in nanoseconds. */
    private class TestClock(private val scheduler: TestCoroutineScheduler) : MonotonicClock {
        override fun nowNanos(): Long = scheduler.currentTime * 1_000_000L
    }

    /** Anchors beat 0 to 300 virtual milliseconds after being asked, like the pre-roll. */
    private class FakeMetronome(private val clock: MonotonicClock) : Metronome {
        var starts = 0
        var stops = 0
        var lastTimeline: AttemptTimeline? = null

        override suspend fun start(timeline: AttemptTimeline): MetronomeStart {
            starts++
            lastTimeline = timeline
            delay(300)
            return MetronomeStart(clock.nowNanos(), anchoredByTimestamp = true, reportedLatencyNanos = null)
        }

        override fun stop() {
            stops++
        }
    }

    private class FakeHistory : AttemptHistory {
        val sessions = mutableListOf<String>()
        val ended = mutableListOf<String>()
        val records = mutableListOf<Pair<AttemptRecord, EvaluationResult?>>()

        override suspend fun startSession(): String = "session-${sessions.size + 1}".also(sessions::add)
        override suspend fun endSession(sessionId: String) { ended += sessionId }
        override suspend fun record(record: AttemptRecord, evaluation: EvaluationResult?) { records += record to evaluation }
    }

    private class Rig(scope: TestScope) {
        val clock = TestClock(scope.testScheduler)
        val metronome = FakeMetronome(clock)
        val history = FakeHistory()
        private var nextId = 0
        val controller = AttemptController(
            scope = scope,
            persistScope = scope,
            clock = clock,
            metronome = metronome,
            history = history,
            evaluationDispatcher = StandardTestDispatcher(scope.testScheduler),
            ids = { "attempt-${++nextId}" },
            wallClock = { WALL_EPOCH + scope.testScheduler.currentTime },
        )
        val state get() = controller.state.value

        fun loadDefault() = controller.load(Fixtures.exercise, Fixtures.slowConfig)
    }

    private val ms = 1_000_000L

    @Test
    fun `the happy path follows the timeline from the metronome's anchor`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        assertIs<Ready>(rig.state)

        rig.controller.start()
        runCurrent()
        assertIs<Ready>(rig.state)
        assertEquals(1, rig.metronome.starts)

        advanceTimeBy(300)
        runCurrent()
        val countingIn = assertIs<CountingIn>(rig.state)
        assertEquals(300 * ms, countingIn.startedAtNanos)
        assertEquals(false, countingIn.notationVisible)

        advanceTimeBy(1999)
        runCurrent()
        assertEquals(false, assertIs<CountingIn>(rig.state).notationVisible)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(true, assertIs<CountingIn>(rig.state).notationVisible)

        advanceTimeBy(2000)
        runCurrent()
        assertIs<Performing>(rig.state)

        advanceTimeBy(5000)
        runCurrent()
        val result = assertIs<Result>(rig.state)
        assertEquals(0, result.evaluation.pitch.correctCount)
        assertEquals(1, rig.metronome.stops)

        advanceUntilIdle()
        val (record, evaluation) = rig.history.records.single()
        assertEquals("attempt-1", record.id)
        assertEquals("session-1", record.sessionId)
        assertEquals(AttemptStatus.COMPLETED, record.status)
        assertEquals(300 * ms, record.startedAtNanos)
        assertEquals(WALL_EPOCH + 300, record.startedAtEpochMillis)
        assertEquals(Fixtures.cdef, record.score)
        assertEquals(result.evaluation, evaluation)
    }

    @Test
    fun `a perfect performance through the keyboard scores full marks and is recorded`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(300 + 4000)
        runCurrent()
        assertIs<Performing>(rig.state)

        val pitches = listOf(60, 62, 64, 65).map(::Pitch)
        pitches.forEach { pitch ->
            rig.controller.onMidi(MidiEvent.noteOn(rig.clock.nowNanos(), pitch, 90))
            advanceTimeBy(500)
            rig.controller.onMidi(MidiEvent.noteOff(rig.clock.nowNanos(), pitch))
            advanceTimeBy(500)
        }
        advanceUntilIdle()

        val result = assertIs<Result>(rig.state)
        assertEquals(1.0, result.evaluation.pitch.accuracy)
        assertEquals(8, rig.history.records.single().first.events.size)
    }

    @Test
    fun `notes played during the count-in are kept but not scored`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(300 + 1000)
        runCurrent()
        val early = MidiEvent.noteOn(rig.clock.nowNanos(), Pitch(60), 90)
        rig.controller.onMidi(early)
        assertEquals(listOf(early), assertIs<CountingIn>(rig.state).captured)

        advanceUntilIdle()
        val result = assertIs<Result>(rig.state)
        assertEquals(4, result.evaluation.pitch.missingCount)
        assertEquals(listOf(early), rig.history.records.single().first.events)
    }

    @Test
    fun `a disconnect mid-attempt aborts, stops the metronome and keeps the raw MIDI`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(300 + 2500)
        runCurrent()
        val note = MidiEvent.noteOn(rig.clock.nowNanos(), Pitch(60), 90)
        rig.controller.onMidi(note)

        rig.controller.abort(AbortReason.MIDI_DISCONNECTED)
        val aborted = assertIs<Aborted>(rig.state)
        assertEquals(AbortReason.MIDI_DISCONNECTED, aborted.reason)
        assertEquals(1, rig.metronome.stops)

        advanceUntilIdle()
        assertIs<Aborted>(rig.state)
        val (record, evaluation) = rig.history.records.single()
        assertEquals(AttemptStatus.ABORTED, record.status)
        assertEquals(AbortReason.MIDI_DISCONNECTED, record.abortReason)
        assertEquals(listOf(note), record.events)
        assertNull(evaluation)
    }

    @Test
    fun `aborting before the metronome anchored records nothing but still stops it`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(100)
        runCurrent()

        rig.controller.abort(AbortReason.CANCELLED)

        advanceUntilIdle()
        val aborted = assertIs<Aborted>(rig.state)
        assertNull(aborted.startedAtNanos)
        assertTrue(rig.history.records.isEmpty())
        assertEquals(1, rig.metronome.stops)
    }

    @Test
    fun `aborting in ready records nothing and next reloads`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()

        rig.controller.abort(AbortReason.BACKGROUNDED)
        assertIs<Aborted>(rig.state)
        advanceUntilIdle()
        assertTrue(rig.history.records.isEmpty())

        val other = Exercise("other", Fixtures.cdef, 1)
        rig.controller.load(other, Fixtures.slowConfig)
        assertEquals(other, assertIs<Ready>(rig.state).context.exercise)
    }

    @Test
    fun `reloading while ready re-times the same exercise`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()

        rig.controller.load(Fixtures.exercise, Fixtures.slowConfig.copy(previewDurationBeats = 1.0))

        assertEquals(1.0, assertIs<Ready>(rig.state).context.timeline.previewDurationBeats)
    }

    @Test
    fun `starting twice or loading mid-attempt is a programming error`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(300)
        runCurrent()

        assertFailsWith<IllegalStateException> { rig.controller.start() }
        assertFailsWith<IllegalStateException> { rig.controller.load(Fixtures.exercise, Fixtures.slowConfig) }
        rig.controller.abort(AbortReason.CANCELLED)
        advanceUntilIdle()
    }

    @Test
    fun `one session spans consecutive attempts and is closed on request`() = runTest {
        val rig = Rig(this)
        repeat(3) {
            rig.controller.load(Fixtures.exercise, Fixtures.slowConfig)
            rig.controller.start()
            advanceUntilIdle()
            assertIs<Result>(rig.state)
        }
        rig.controller.endSession()
        advanceUntilIdle()

        assertEquals(listOf("session-1"), rig.history.sessions)
        assertEquals(listOf("session-1", "session-1", "session-1"), rig.history.records.map { it.first.sessionId })
        assertEquals(listOf("attempt-1", "attempt-2", "attempt-3"), rig.history.records.map { it.first.id })
        assertEquals(listOf("session-1"), rig.history.ended)
    }

    @Test
    fun `MIDI is ignored before anything is loaded and after a result`() = runTest {
        val rig = Rig(this)
        rig.controller.onMidi(MidiEvent.noteOn(0, Pitch(60), 90))
        assertNull(rig.state)

        rig.loadDefault()
        rig.controller.start()
        advanceUntilIdle()
        val result = assertIs<Result>(rig.state)
        rig.controller.onMidi(MidiEvent.noteOn(rig.clock.nowNanos(), Pitch(60), 90))
        assertEquals(result, rig.state)
    }

    private companion object {
        const val WALL_EPOCH = 1_700_000_000_000L
    }
}
