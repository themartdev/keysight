package dev.simonmartineau.keysight.data

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunRecord
import dev.simonmartineau.keysight.run.RunStatus
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MappersTest {

    private val events = listOf(
        MidiEvent.noteOn(4 * SECOND_NANOS, Pitch.C4, 90),
        MidiEvent.noteOff(4 * SECOND_NANOS + SECOND_NANOS / 2, Pitch.C4),
        MidiEvent.controlChange(5 * SECOND_NANOS, controller = 64, value = 127),
        MidiEvent(6 * SECOND_NANOS, 0xC0, 12, 0),
    )

    private val record = RunRecord(
        id = "run-1",
        sessionId = "session-1",
        exerciseIds = listOf("m01", "m07", "m01"),
        startedAtEpochMillis = 1_700_000_000_000,
        startedAtNanos = 0,
        status = RunStatus.COMPLETED,
        abortReason = null,
        config = Fixtures.slowConfig,
        score = Fixtures.slowScore,
        events = events,
    )

    @Test
    fun `a run round-trips through its attempt rows`() {
        val entity = record.toEntity()
        val rows = record.toMidiEventEntities()

        assertEquals(record, entity.toRecord(rows))
    }

    @Test
    fun `queryable columns mirror the snapshot`() {
        val entity = record.toEntity()

        assertEquals("m01,m07,m01", entity.exerciseId)
        assertEquals(60.0, entity.tempoBpm)
        assertEquals(2.0, entity.previewDurationBeats)
        assertEquals(Fixtures.slowConfig, keySightJson.decodeFromString(RunConfig.serializer(), entity.configJson))
        assertTrue(entity.scoreJson.contains("\"measureCount\":2"))
    }

    @Test
    fun `an unbounded lookahead is stored as infinity`() {
        val readAhead = record.copy(config = Fixtures.slowConfig.copy(mode = VisibilityMode.READ_AHEAD))

        assertEquals(Double.POSITIVE_INFINITY, readAhead.toEntity().previewDurationBeats)
        assertEquals(readAhead, readAhead.toEntity().toRecord(readAhead.toMidiEventEntities()))
    }

    @Test
    fun `raw MIDI rows keep the exact bytes`() {
        events.forEach { event ->
            val row = event.toEntity("run-1")
            assertEquals("run-1", row.attemptId)
            assertEquals(event, row.toMidiEvent())
        }
        assertTrue(record.toMidiEventEntities().all { it.id == 0L }, "ids are assigned by the database")
    }

    @Test
    fun `an aborted run keeps its reason`() {
        val aborted = record.copy(status = RunStatus.ABORTED, abortReason = AbortReason.MIDI_DISCONNECTED)

        assertEquals(aborted, aborted.toEntity().toRecord(aborted.toMidiEventEntities()))
        assertFailsWith<IllegalArgumentException> { record.copy(abortReason = AbortReason.CANCELLED) }
        assertFailsWith<IllegalArgumentException> { record.copy(status = RunStatus.ABORTED) }
    }

    @Test
    fun `an evaluation round-trips with its summary columns`() {
        val evaluation = PerformanceEvaluator.evaluate(Fixtures.slowScore, events, Fixtures.slowTimeline, startedAtNanos = 0)

        val entity = evaluation.toEntity("run-1", evaluatedAtEpochMillis = 5)

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
        val empty = EvaluationResult(1, dev.simonmartineau.keysight.evaluation.PitchResult(emptyList()))

        assertEquals(empty, empty.toEntity("a", 0).toResult())
    }
}
