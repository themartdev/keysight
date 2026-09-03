package dev.simonmartineau.keysight.data

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunRecord
import dev.simonmartineau.keysight.run.RunStatus
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MappersTest {

    private val events = listOf(
        MidiEvent.noteOn(4 * SECOND_NANOS, Pitch.C4, 90),
        MidiEvent.noteOff(4 * SECOND_NANOS + SECOND_NANOS / 2, Pitch.C4),
        MidiEvent.controlChange(5 * SECOND_NANOS, controller = 64, value = 127),
        MidiEvent(6 * SECOND_NANOS, 0xC0, 12, 0),
    )

    private val run = Fixtures.run(Fixtures.cdef, Fixtures.gfed)

    private val record = RunRecord(
        id = "run-1",
        sessionId = "session-1",
        startedAtEpochMillis = 1_700_000_000_000,
        startedAtNanos = 0,
        status = RunStatus.COMPLETED,
        abortReason = null,
        config = run.config,
        segments = listOf(Segment("m01", Fixtures.cdef), Segment("m07", Fixtures.gfed)),
        events = events,
    )

    private fun RunRecord.roundTrip(): RunRecord = toEntity().toRecord(toSegmentEntities(), toMidiEventEntities())

    @Test
    fun `a run round-trips through its run, segment and MIDI rows`() {
        assertEquals(record, record.roundTrip())
        assertEquals(run.score, record.score)
    }

    @Test
    fun `segments are keyed by their run and position`() {
        val rows = record.toSegmentEntities()

        assertEquals(listOf("run-1:1", "run-1:2"), rows.map { it.id })
        assertEquals(listOf(1, 2), rows.map { it.segmentIndex })
        assertEquals(listOf("m01", "m07"), rows.map { it.exerciseId })
        assertTrue(rows.all { it.runId == "run-1" })
        assertTrue(rows.all { it.scoreJson.contains("\"measureCount\":1") })
        assertEquals(Fixtures.gfed, rows[1].toSegment().score)
    }

    @Test
    fun `segments come back in position order whatever order the rows arrive in`() {
        val rows = record.toSegmentEntities().reversed()

        assertEquals(record, record.toEntity().toRecord(rows, record.toMidiEventEntities()))
    }

    @Test
    fun `queryable columns mirror the snapshot`() {
        val entity = record.toEntity()

        assertEquals(60.0, entity.tempoBpm)
        assertEquals(run.config, keySightJson.decodeFromString(RunConfig.serializer(), entity.configJson))
    }

    @Test
    fun `an open-ended run stores no segment count and reads back as open-ended`() {
        val open = record.copy(config = record.config.copy(segmentCount = null))

        assertTrue(open.toEntity().configJson.contains("\"segmentCount\":null"))
        assertNull(open.roundTrip().config.segmentCount)
    }

    @Test
    fun `raw MIDI rows keep the exact bytes`() {
        events.forEach { event ->
            val row = event.toEntity("run-1")
            assertEquals("run-1", row.runId)
            assertEquals(event, row.toMidiEvent())
        }
        assertTrue(record.toMidiEventEntities().all { it.id == 0L }, "ids are assigned by the database")
    }

    @Test
    fun `an aborted run keeps its reason`() {
        val aborted = record.copy(status = RunStatus.ABORTED, abortReason = AbortReason.MIDI_DISCONNECTED)

        assertEquals(aborted, aborted.roundTrip())
        assertFailsWith<IllegalArgumentException> { record.copy(abortReason = AbortReason.CANCELLED) }
        assertFailsWith<IllegalArgumentException> { record.copy(status = RunStatus.ABORTED) }
        assertFailsWith<IllegalArgumentException> { record.copy(segments = emptyList()) }
    }

    @Test
    fun `an evaluation round-trips with its summary columns`() {
        val evaluation = PerformanceEvaluator.evaluate(Fixtures.slowScore, Fixtures.slowTimeline, startedAtNanos = 0, events = events).segments.single()

        val entity = evaluation.toEntity("run-1:1", evaluatedAtEpochMillis = 5)

        assertEquals("run-1:1", entity.segmentId)
        assertEquals(PerformanceEvaluator.EVALUATOR_VERSION, entity.evaluatorVersion)
        assertEquals(1, entity.correctCount)
        assertEquals(4, entity.expectedCount)
        assertEquals(0, entity.extraCount)
        assertEquals(0.25, entity.pitchAccuracy)
        assertEquals(evaluation.rhythm!!.accuracy, entity.rhythmAccuracy)
        assertEquals(evaluation, entity.toResult())
    }

    @Test
    fun `a version 1 result decodes without rhythm`() {
        val json = """{"evaluatorVersion":1,"pitch":{"outcomes":[]}}"""

        val result = keySightJson.decodeFromString(EvaluationResult.serializer(), json)

        assertEquals(1, result.evaluatorVersion)
        assertEquals(null, result.rhythm)
        assertEquals(null, result.toEntity("a", 0).rhythmAccuracy)
    }

    @Test
    fun `snapshots ignore keys a newer app may add`() {
        val json = """{"tempoBpm":72.0,"metronome":"COUNT_IN_ONLY","mode":"FLASH","lookaheadBeats":4.0,"segmentCount":8,"futureKnob":1}"""

        assertEquals(RunConfig.DEFAULT, keySightJson.decodeFromString(RunConfig.serializer(), json))
    }

    @Test
    fun `an empty evaluation still serialises`() {
        val empty = EvaluationResult(1, PitchResult(emptyList()))

        assertEquals(empty, empty.toEntity("a", 0).toResult())
    }
}
