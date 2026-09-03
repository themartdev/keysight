package dev.simonmartineau.keysight.data

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunStatus
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.runScore
import dev.simonmartineau.keysight.score.Score
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The two shapes of attempt row that schema 2 could hold, each turned into a run and its segments. */
class LegacyAttemptsTest {

    private fun row(
        configJson: String,
        scoreJson: String,
        exerciseId: String = "m01",
        status: String = "COMPLETED",
        abortReason: String? = null,
        tempoBpm: Double = 60.0,
        previewDurationBeats: Double = 2.0,
    ) = LegacyAttemptRow(
        id = "a1",
        sessionId = "s1",
        exerciseId = exerciseId,
        startedAtEpochMillis = 1_700_000_000_000,
        startedAtNanos = 10 * SECOND_NANOS,
        status = status,
        abortReason = abortReason,
        tempoBpm = tempoBpm,
        previewDurationBeats = previewDurationBeats,
        configJson = configJson,
        scoreJson = scoreJson,
    )

    private fun flashConfig(countInMeasures: Int = 1, metronomeDuringAttempt: Boolean = false) =
        """{"tempoBpm":60.0,"countInMeasures":$countInMeasures,"previewDurationBeats":2.0,"metronomeDuringAttempt":$metronomeDuringAttempt}"""

    private fun score(score: Score) = keySightJson.encodeToString(Score.serializer(), score)

    private fun config(json: String) = keySightJson.decodeFromString(RunConfig.serializer(), json)

    @Test
    fun `a pre-Round 6 attempt becomes a one-segment Flash run with its preview as the lookahead`() {
        val (run, segments) = row(flashConfig(), score(Fixtures.cdef)).toRun()

        assertEquals("a1", run.id)
        assertEquals("s1", run.sessionId)
        assertEquals(1_700_000_000_000, run.startedAtEpochMillis)
        assertEquals(10 * SECOND_NANOS, run.startedAtNanos)
        assertEquals(RunStatus.COMPLETED, run.status)
        assertNull(run.abortReason)
        assertEquals(60.0, run.tempoBpm)
        assertEquals(RunConfig(60.0, MetronomeMode.COUNT_IN_ONLY, VisibilityMode.FLASH, 2.0, segmentCount = 1), config(run.configJson))

        val segment = segments.single()
        assertEquals("a1:1", segment.id)
        assertEquals("a1", segment.runId)
        assertEquals(1, segment.segmentIndex)
        assertEquals("m01", segment.exerciseId)
        assertEquals(Fixtures.cdef, segment.toSegment().score)
    }

    @Test
    fun `a longer count-in moves the anchor so the performance starts on the run's beat`() {
        val (run, _) = row(flashConfig(countInMeasures = 2), score(Fixtures.cdef)).toRun()

        assertEquals(14 * SECOND_NANOS, run.startedAtNanos)
    }

    @Test
    fun `a metronome that played through stays on`() {
        val (run, _) = row(flashConfig(metronomeDuringAttempt = true), score(Fixtures.cdef)).toRun()

        assertEquals(MetronomeMode.THROUGHOUT, config(run.configJson).metronome)
    }

    @Test
    fun `a Round 6 run row becomes one segment per measure, ids unprefixed and onsets from zero`() {
        val runConfig = Fixtures.slowConfig.copy(segmentCount = 2)
        val configJson = keySightJson.encodeToString(RunConfig.serializer(), runConfig)
        val (run, segments) = row(configJson, score(runScore(listOf(Fixtures.cdef, Fixtures.gfed))), exerciseId = "m01,m07").toRun()

        assertEquals(configJson, run.configJson)
        assertEquals(10 * SECOND_NANOS, run.startedAtNanos)
        assertEquals(listOf("a1:1", "a1:2"), segments.map { it.id })
        assertEquals(listOf(1, 2), segments.map { it.segmentIndex })
        assertEquals(listOf("m01", "m07"), segments.map { it.exerciseId })
        assertEquals(listOf(Fixtures.cdef, Fixtures.gfed), segments.map { it.toSegment().score })
    }

    @Test
    fun `an aborted attempt keeps its status and reason`() {
        val (run, _) = row(flashConfig(), score(Fixtures.cdef), status = "ABORTED", abortReason = "MIDI_DISCONNECTED").toRun()

        assertEquals(RunStatus.ABORTED, run.status)
        assertEquals(AbortReason.MIDI_DISCONNECTED, run.abortReason)
    }

    @Test
    fun `a row whose snapshots cannot be read is kept as one segment with the text as it is`() {
        val (run, segments) = row("not json", "{\"broken\":", previewDurationBeats = 3.0, tempoBpm = 84.0).toRun()

        assertEquals(RunConfig(84.0, MetronomeMode.COUNT_IN_ONLY, VisibilityMode.FLASH, 3.0, segmentCount = 1), config(run.configJson))
        assertEquals(10 * SECOND_NANOS, run.startedAtNanos)
        val segment = segments.single()
        assertEquals("a1:1", segment.id)
        assertEquals("{\"broken\":", segment.scoreJson)
        assertEquals("m01", segment.exerciseId)
    }
}
