package dev.simonmartineau.keysight.data

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.attempt.AbortReason
import dev.simonmartineau.keysight.attempt.AttemptRecord
import dev.simonmartineau.keysight.attempt.AttemptStatus
import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.PerformanceEvaluator
import dev.simonmartineau.keysight.midi.MidiEvent
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

    private val record = AttemptRecord(
        id = "attempt-1",
        sessionId = "session-1",
        exerciseId = Fixtures.exercise.id,
        startedAtEpochMillis = 1_700_000_000_000,
        startedAtNanos = 0,
        status = AttemptStatus.COMPLETED,
        abortReason = null,
        config = Fixtures.slowConfig,
        score = Fixtures.cdef,
        events = events,
    )

    @Test
    fun `an attempt round-trips through its rows`() {
        val entity = record.toEntity()
        val rows = record.toMidiEventEntities()

        assertEquals(record, entity.toRecord(rows))
    }

    @Test
    fun `queryable columns mirror the snapshot`() {
        val entity = record.toEntity()

        assertEquals(60.0, entity.tempoBpm)
        assertEquals(2.0, entity.previewDurationBeats)
        assertEquals(Fixtures.slowConfig, keySightJson.decodeFromString(FlashConfig.serializer(), entity.configJson))
        assertTrue(entity.scoreJson.contains("\"measureCount\":1"))
    }

    @Test
    fun `raw MIDI rows keep the exact bytes`() {
        events.forEach { event ->
            val row = event.toEntity("attempt-1")
            assertEquals("attempt-1", row.attemptId)
            assertEquals(event, row.toMidiEvent())
        }
        assertTrue(record.toMidiEventEntities().all { it.id == 0L }, "ids are assigned by the database")
    }

    @Test
    fun `an aborted attempt keeps its reason`() {
        val aborted = record.copy(status = AttemptStatus.ABORTED, abortReason = AbortReason.MIDI_DISCONNECTED)

        assertEquals(aborted, aborted.toEntity().toRecord(aborted.toMidiEventEntities()))
        assertFailsWith<IllegalArgumentException> { record.copy(abortReason = AbortReason.CANCELLED) }
        assertFailsWith<IllegalArgumentException> { record.copy(status = AttemptStatus.ABORTED) }
    }

    @Test
    fun `an evaluation round-trips with its summary columns`() {
        val evaluation = PerformanceEvaluator.evaluate(Fixtures.cdef, events, Fixtures.slowTimeline, startedAtNanos = 0)

        val entity = evaluation.toEntity("attempt-1", evaluatedAtEpochMillis = 5)

        assertEquals(PerformanceEvaluator.EVALUATOR_VERSION, entity.evaluatorVersion)
        assertEquals(1, entity.correctCount)
        assertEquals(4, entity.expectedCount)
        assertEquals(0, entity.extraCount)
        assertEquals(0.25, entity.pitchAccuracy)
        assertEquals(evaluation, entity.toResult())
    }

    @Test
    fun `snapshots ignore keys a newer app may add`() {
        val json = """{"tempoBpm":72.0,"countInMeasures":1,"previewDurationBeats":4.0,"metronomeDuringAttempt":false,"futureKnob":1}"""

        assertEquals(FlashConfig.DEFAULT, keySightJson.decodeFromString(FlashConfig.serializer(), json))
    }

    @Test
    fun `an empty evaluation still serialises`() {
        val empty = EvaluationResult(1, dev.simonmartineau.keysight.evaluation.PitchResult(emptyList()))

        assertEquals(empty, empty.toEntity("a", 0).toResult())
    }
}
