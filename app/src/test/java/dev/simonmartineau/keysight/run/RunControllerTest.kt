package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.audio.Metronome
import dev.simonmartineau.keysight.audio.MetronomeStart
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.RunState.Aborted
import dev.simonmartineau.keysight.run.RunState.CountingIn
import dev.simonmartineau.keysight.run.RunState.Performing
import dev.simonmartineau.keysight.run.RunState.Ready
import dev.simonmartineau.keysight.run.RunState.Summary
import dev.simonmartineau.keysight.run.SegmentOrigin
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.timing.MonotonicClock
import dev.simonmartineau.keysight.timing.RunTimeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

/**
 * The two-segment run of C4 D4 E4 F4 then G4 F4 E4 D4 at 60 bpm: the metronome anchors 300 ms
 * after start, the count-in is the next four seconds, segment 1 the four after, segment 2 the
 * four after that, and capture ends one second later. Segment 1 commits at 9.3 s, segment 2
 * at 13.3 s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunControllerTest {

    /** The one clock: virtual time, in nanoseconds. */
    private class TestClock(private val scheduler: TestCoroutineScheduler) : MonotonicClock {
        override fun nowNanos(): Long = scheduler.currentTime * 1_000_000L
    }

    /** Anchors beat 0 to 300 virtual milliseconds after being asked, like the pre-roll. */
    private class FakeMetronome(private val clock: MonotonicClock) : Metronome {
        var starts = 0
        var stops = 0
        var lastTimeline: RunTimeline? = null

        override suspend fun start(timeline: RunTimeline): MetronomeStart {
            starts++
            lastTimeline = timeline
            delay(300)
            return MetronomeStart(clock.nowNanos(), anchoredByTimestamp = true, reportedLatencyNanos = null)
        }

        override fun stop() {
            stops++
        }
    }

    private class FakeHistory : RunHistory {
        val sessions = mutableListOf<String>()
        val ended = mutableListOf<String>()
        val records = mutableListOf<Pair<RunRecord, List<EvaluationResult>>>()

        override suspend fun startSession(): String = "session-${sessions.size + 1}".also(sessions::add)
        override suspend fun endSession(sessionId: String) { ended += sessionId }
        override suspend fun record(record: RunRecord, evaluations: List<EvaluationResult>) { records += record to evaluations }
    }

    /** Hands out numbered copies of the same measure and counts how often it was asked. */
    private class FakeSource : SegmentSource {
        var calls = 0
        private var served = 0

        var lastFirstIndex = 0
        var lastCount = 0
        var lastCommitted: List<CommittedSegment> = emptyList()

        override fun next(count: Int, firstIndex: Int, committed: List<CommittedSegment>): List<Segment> {
            calls++
            lastFirstIndex = firstIndex
            lastCount = count
            lastCommitted = committed
            return (1..count).map { Fixtures.segment("more-${++served}", Fixtures.cdef) }
        }
    }

    private class Rig(scope: TestScope) {
        val clock = TestClock(scope.testScheduler)
        val metronome = FakeMetronome(clock)
        val history = FakeHistory()
        val ended = mutableListOf<RunState>()
        private var nextId = 0
        val controller = RunController(
            scope = scope,
            persistScope = scope,
            clock = clock,
            metronome = metronome,
            history = history,
            ids = { "run-${++nextId}" },
            wallClock = { WALL_EPOCH + scope.testScheduler.currentTime },
            onRunEnded = { ended += it },
        )
        val state get() = controller.state.value

        fun loadDefault() = controller.load(twoBars)

        /** Strikes [pitch] now and releases it half a second later. */
        fun play(pitch: Int) {
            controller.onMidi(MidiEvent.noteOn(clock.nowNanos(), Pitch(pitch), 90))
            controller.onMidi(MidiEvent.noteOff(clock.nowNanos() + 500 * ms, Pitch(pitch)))
        }
    }

    @Test
    fun `the happy path follows the timeline from the metronome's anchor`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        assertIs<Ready>(rig.state)

        rig.controller.start()
        runCurrent()
        assertIs<Ready>(rig.state)
        assertEquals(1, rig.metronome.starts)
        assertEquals(twoBars.timeline, rig.metronome.lastTimeline)

        advanceTimeBy(300)
        runCurrent()
        val countingIn = assertIs<CountingIn>(rig.state)
        assertEquals(300 * ms, countingIn.startedAtNanos)
        assertEquals(2, countingIn.lastSegment)

        advanceTimeBy(3999)
        runCurrent()
        assertIs<CountingIn>(rig.state)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(0, assertIs<Performing>(rig.state).evaluation.committedCount)

        advanceTimeBy(5000)
        runCurrent()
        assertEquals(1, assertIs<Performing>(rig.state).evaluation.committedCount)

        advanceTimeBy(3999)
        runCurrent()
        assertIs<Performing>(rig.state)
        advanceTimeBy(1)
        runCurrent()
        val summary = assertIs<Summary>(rig.state)
        assertEquals(2, summary.evaluation.committedCount)
        assertEquals(0, summary.evaluation.pitch.correctCount)
        assertEquals(8, summary.evaluation.pitch.expectedCount)
        assertEquals(1, rig.metronome.stops)

        advanceUntilIdle()
        val (record, evaluations) = rig.history.records.single()
        assertEquals("run-1", record.id)
        assertEquals("session-1", record.sessionId)
        assertEquals(listOf("segment-1", "segment-2"), record.segments.map { (it.origin as SegmentOrigin.Bundled).exerciseId })
        assertEquals(RunStatus.COMPLETED, record.status)
        assertEquals(300 * ms, record.startedAtNanos)
        assertEquals(WALL_EPOCH + 300, record.startedAtEpochMillis)
        assertEquals(twoBars.score, record.score)
        assertEquals(twoBars.config, record.config)
        assertEquals(summary.evaluation.segments, evaluations)
        assertEquals(listOf<RunState>(summary), rig.ended)
        assertEquals(twoBars.segments.zip(evaluations, ::CommittedSegment), summary.committed)
    }

    @Test
    fun `a perfect performance through the keyboard scores full marks and is recorded`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(300 + 4000)
        runCurrent()
        assertIs<Performing>(rig.state)

        listOf(60, 62, 64, 65, 67, 65, 64, 62).forEach { pitch ->
            rig.play(pitch)
            advanceTimeBy(1000)
        }
        advanceUntilIdle()

        val summary = assertIs<Summary>(rig.state)
        assertEquals(1.0, summary.evaluation.pitch.accuracy)
        assertEquals(16, rig.history.records.single().first.events.size)
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
        val summary = assertIs<Summary>(rig.state)
        assertEquals(8, summary.evaluation.pitch.missingCount)
        assertEquals(listOf(early), rig.history.records.single().first.events)
    }

    @Test
    fun `stopping mid-run finishes the bar and summarises what was performed`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(300 + 4000)
        runCurrent()
        listOf(60, 62, 64, 65).forEach { pitch ->
            rig.play(pitch)
            advanceTimeBy(1000)
        }
        advanceTimeBy(500)
        runCurrent()
        assertEquals(2, assertIs<Performing>(rig.state).lastSegment)

        rig.controller.stop()
        runCurrent()
        assertEquals(2, assertIs<Performing>(rig.state).lastSegment)

        // Segment 2 ends 4 s after 12.3 s, capture one second later: the summary is up at 13.3 s.
        advanceTimeBy(4000 + 1000 - 500 - 1)
        runCurrent()
        assertIs<Performing>(rig.state)
        advanceTimeBy(1)
        runCurrent()
        val summary = assertIs<Summary>(rig.state)
        assertEquals(2, summary.lastSegment)
        assertEquals(8, summary.evaluation.pitch.expectedCount)
        assertEquals(4, summary.evaluation.pitch.correctCount)

        advanceUntilIdle()
        assertEquals(twoBars.score, rig.history.records.single().first.score)
    }

    @Test
    fun `stopping in the first bar wakes the sleeping job and cuts the run after it`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(300 + 4000 + 1500)
        runCurrent()

        rig.controller.stop()
        runCurrent()
        assertEquals(1, assertIs<Performing>(rig.state).lastSegment)

        advanceTimeBy(2500 + 1000)
        runCurrent()
        val summary = assertIs<Summary>(rig.state)
        assertEquals(1, summary.lastSegment)
        assertEquals(4, summary.evaluation.pitch.expectedCount)
        assertEquals(2, summary.performed.score.measureCount)

        advanceUntilIdle()
        val (record, evaluations) = rig.history.records.single()
        assertEquals(RunStatus.COMPLETED, record.status)
        assertEquals(2, record.score.measureCount)
        assertEquals(listOf("segment-1"), record.segments.map { (it.origin as SegmentOrigin.Bundled).exerciseId })
        assertEquals(1, evaluations.size)
    }

    @Test
    fun `stopping during the count-in cancels the run`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(300 + 1000)
        runCurrent()

        rig.controller.stop()
        val aborted = assertIs<Aborted>(rig.state)
        assertEquals(AbortReason.CANCELLED, aborted.reason)
        assertEquals(1, rig.metronome.stops)

        advanceUntilIdle()
        assertIs<Aborted>(rig.state)
        val (record, evaluations) = rig.history.records.single()
        assertEquals(RunStatus.ABORTED, record.status)
        assertEquals(AbortReason.CANCELLED, record.abortReason)
        assertEquals(listOf("segment-1"), record.segments.map { (it.origin as SegmentOrigin.Bundled).exerciseId })
        assertTrue(evaluations.isEmpty())
    }

    @Test
    fun `a disconnect mid-run aborts, stops the metronome and keeps the raw MIDI and the commits`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(300 + 9500)
        runCurrent()
        val note = MidiEvent.noteOn(rig.clock.nowNanos(), Pitch(60), 90)
        rig.controller.onMidi(note)

        rig.controller.abort(AbortReason.MIDI_DISCONNECTED)
        val aborted = assertIs<Aborted>(rig.state)
        assertEquals(AbortReason.MIDI_DISCONNECTED, aborted.reason)
        assertEquals(2, aborted.lastSegment)
        assertEquals(1, rig.metronome.stops)

        advanceUntilIdle()
        assertIs<Aborted>(rig.state)
        val (record, evaluations) = rig.history.records.single()
        assertEquals(RunStatus.ABORTED, record.status)
        assertEquals(AbortReason.MIDI_DISCONNECTED, record.abortReason)
        assertEquals(listOf(note), record.events)
        assertEquals(twoBars.score, record.score)
        assertEquals(1, evaluations.size)
        assertEquals(listOf<RunState>(aborted), rig.ended)
        assertEquals(1, aborted.committed.size)
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
        assertTrue(rig.ended.isEmpty(), "a run that never started did not end")
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

        rig.controller.load(Fixtures.slowRun)
        assertEquals(Fixtures.slowRun, assertIs<Ready>(rig.state).context)
    }

    @Test
    fun `reloading while ready re-times the same run`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()

        rig.controller.load(twoBars.copy(config = twoBars.config.copy(tempoBpm = 120.0)))

        assertEquals(120.0, assertIs<Ready>(rig.state).context.timeline.tempoBpm)
    }

    @Test
    fun `starting twice or loading mid-run is a programming error`() = runTest {
        val rig = Rig(this)
        rig.loadDefault()
        rig.controller.start()
        advanceTimeBy(300)
        runCurrent()

        assertFailsWith<IllegalStateException> { rig.controller.start() }
        assertFailsWith<IllegalStateException> { rig.controller.load(twoBars) }
        rig.controller.abort(AbortReason.CANCELLED)
        advanceUntilIdle()
    }

    @Test
    fun `one session spans consecutive runs and is closed on request`() = runTest {
        val rig = Rig(this)
        repeat(3) {
            rig.controller.load(Fixtures.slowRun)
            rig.controller.start()
            advanceUntilIdle()
            assertIs<Summary>(rig.state)
        }
        rig.controller.endSession()
        advanceUntilIdle()

        assertEquals(listOf("session-1"), rig.history.sessions)
        assertEquals(listOf("session-1", "session-1", "session-1"), rig.history.records.map { it.first.sessionId })
        assertEquals(listOf("run-1", "run-2", "run-3"), rig.history.records.map { it.first.id })
        assertEquals(listOf("session-1"), rig.history.ended)
    }

    @Test
    fun `MIDI is ignored before anything is loaded and after a summary`() = runTest {
        val rig = Rig(this)
        rig.controller.onMidi(MidiEvent.noteOn(0, Pitch(60), 90))
        assertNull(rig.state)

        rig.loadDefault()
        rig.controller.start()
        advanceUntilIdle()
        val summary = assertIs<Summary>(rig.state)
        rig.controller.onMidi(MidiEvent.noteOn(rig.clock.nowNanos(), Pitch(60), 90))
        assertEquals(summary, rig.state)
    }

    @Test
    fun `an open-ended run is topped up one segment at a time ahead of the cursor and ends where the player stops`() = runTest {
        val rig = Rig(this)
        val source = FakeSource()
        val initial = SegmentSource.SEGMENTS_AHEAD
        val open = RunContext((1..initial).map { Fixtures.segment("first-$it", Fixtures.cdef) }, Fixtures.slowConfig.copy(segmentCount = null), seed = 42L)
        assertFailsWith<IllegalArgumentException> { rig.controller.load(open) }
        rig.controller.load(open, source)
        rig.controller.start()

        // Twelve segments are ahead of the count-in; as the cursor enters segment 1 at 4.3 s only eleven are.
        advanceTimeBy(300 + 4000 - 1)
        runCurrent()
        assertEquals(initial, rig.state!!.context.segments.size)
        assertEquals(0, source.calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(initial + 1, rig.state!!.context.segments.size)
        assertEquals(1, source.calls)
        assertEquals(1, source.lastCount)
        assertEquals(initial + 1, source.lastFirstIndex)
        assertTrue(source.lastCommitted.isEmpty())
        assertTrue(rig.metronome.lastTimeline!!.openEnded)

        // Segment 1 commits at 9.3 s, when the cursor is in segment 2: one more segment, and the commit is passed along.
        advanceTimeBy(5000)
        runCurrent()
        val extended = assertIs<Performing>(rig.state)
        assertEquals(initial + 2, extended.context.segments.size)
        assertEquals(2, source.calls)
        assertEquals(1, extended.evaluation.committedCount)
        assertEquals(listOf("first-1"), source.lastCommitted.map { (it.segment.origin as SegmentOrigin.Bundled).exerciseId })
        assertEquals(extended.evaluation.segments, source.lastCommitted.map { it.result })

        advanceTimeBy(4000 * 7 + 1000)
        runCurrent()
        assertEquals(9, source.calls)
        rig.controller.stop()
        runCurrent()
        assertEquals(9, assertIs<Performing>(rig.state).stopAfter)

        advanceTimeBy(3000)
        runCurrent()
        val summary = assertIs<Summary>(rig.state)
        assertEquals(9, summary.lastSegment)
        assertEquals(9, summary.evaluation.committedCount)
        assertEquals(9, source.calls)

        advanceUntilIdle()
        val (record, evaluations) = rig.history.records.single()
        assertEquals(9, record.segments.size)
        assertEquals(9, evaluations.size)
        assertNull(record.config.segmentCount)
        assertEquals(listOf<RunState>(summary), rig.ended)
    }

    private companion object {
        const val WALL_EPOCH = 1_700_000_000_000L
        const val ms = 1_000_000L
        val twoBars = Fixtures.run(Fixtures.cdef, Fixtures.gfed)
    }
}
