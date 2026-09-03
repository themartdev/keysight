package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunStatus
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.generatedSegment
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.ScoreNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionSummaryTest {

    private val session = HistoryFixtures.session
    private val thirds = ExerciseConfig.DEFAULT
    private val fourths = thirds.copy(maxInterval = 3)
    private val flash = RunConfig.DEFAULT

    private fun bars(count: Int, level: (Int) -> ExerciseConfig) = (1..count).map { generatedSegment(7L, it, level(it)) }

    private fun run(id: String, minutes: Long, segments: List<Segment>, config: RunConfig = flash, played: (ScoreNote) -> Pitch? = { it.pitch }) =
        HistoryFixtures.stored(HistoryFixtures.record(id, segments, config, startedAtEpochMillis = minutes * 60_000L, played = played))

    /** Bar 2 has a wrong note, bar 3 a dropped one. */
    private val flawed = run("r1", 0, bars(4) { thirds }) { note ->
        when (note.id) {
            "2:n1" -> Pitch(note.pitch.midiNoteNumber + 1)
            "3:n1" -> null
            else -> note.pitch
        }
    }

    private val clean = run("r2", 5, bars(4) { thirds })

    @Test
    fun `nothing pooled from no runs`() {
        val summary = summarise(session, emptyList())

        assertEquals(0, summary.runCount)
        assertEquals(0, summary.barCount)
        assertNull(summary.pitchAccuracy)
        assertNull(summary.rhythmAccuracy)
        assertNull(summary.start)
        assertNull(summary.end)
        assertEquals(emptyList(), summary.moves)
        assertEquals(emptyList(), summary.weakestBars)
    }

    @Test
    fun `one run pools to the numbers its own score line shows`() {
        val summary = summarise(session, listOf(flawed))
        val evaluation = flawed.evaluation

        assertEquals(1, summary.runCount)
        assertEquals(4, summary.barCount)
        assertEquals(evaluation.pitch.accuracy, summary.pitchAccuracy)
        assertEquals(evaluation.rhythm!!.accuracy, summary.rhythmAccuracy)
        assertEquals(evaluation.pitch.correctCount, summary.correctCount)
        assertEquals(evaluation.pitch.expectedCount, summary.expectedCount)
    }

    @Test
    fun `runs pool by their counts, not by averaging their percentages`() {
        val summary = summarise(session, listOf(flawed, clean))
        val expected = flawed.evaluation.pitch.expectedCount + clean.evaluation.pitch.expectedCount
        val correct = flawed.evaluation.pitch.correctCount + clean.evaluation.pitch.correctCount

        assertEquals(8, summary.barCount)
        assertEquals(expected, summary.expectedCount)
        assertEquals(correct.toDouble() / expected, summary.pitchAccuracy)
        val onTime = flawed.evaluation.rhythm!!.onTimeCount + clean.evaluation.rhythm!!.onTimeCount
        val matched = flawed.evaluation.rhythm!!.matchedCount + clean.evaluation.rhythm!!.matchedCount
        assertEquals(onTime.toDouble() / matched, summary.rhythmAccuracy)
    }

    @Test
    fun `an aborted run counts the bar it stopped in as played and only its committed bars as judged`() {
        val aborted = HistoryFixtures.stored(
            HistoryFixtures.record("r3", bars(3) { thirds }, flash, startedAtEpochMillis = 10 * 60_000L, status = RunStatus.ABORTED, reason = AbortReason.BACKGROUNDED),
            judged = 2,
        )

        val summary = summarise(session, listOf(aborted))

        assertEquals(3, summary.barCount)
        assertEquals(aborted.evaluations.sumOf { it.pitch.expectedCount }, summary.expectedCount)
    }

    @Test
    fun `the weakest bars are the faulty ones across runs, worst first, naming their run`() {
        val summary = summarise(session, listOf(flawed, clean, flawed.copy(record = flawed.record.copy(id = "r4", startedAtEpochMillis = 9 * 60_000L))))

        val bars = summary.weakestBars
        assertEquals(setOf("Run 1, bar 2", "Run 3, bar 2", "Run 1, bar 3", "Run 3, bar 3"), bars.map { it.label }.toSet())
        assertEquals(bars.map { it.result.pitch.accuracy }.sorted(), bars.map { it.result.pitch.accuracy }, "worst first")
        assertEquals(listOf(1, 3, 1, 3), bars.map { it.runIndex }, "the same bar of the same run twice: the earlier run first")
        assertEquals(listOf("r1", "r4", "r1", "r4"), bars.map { it.runId })
        assertTrue(bars.all { it.result.hasFault })
        assertEquals(emptyList(), summarise(session, listOf(clean)).weakestBars)
    }

    @Test
    fun `at most five bars are singled out`() {
        val runs = (1..4).map { flawed.copy(record = flawed.record.copy(id = "r$it", startedAtEpochMillis = it * 60_000L)) }

        assertEquals(WEAKEST_BARS, summarise(session, runs).weakestBars.size)
    }

    @Test
    fun `the level starts at the first bar and ends at the last, with no move when nothing changed`() {
        val summary = summarise(session, listOf(clean, clean.copy(record = clean.record.copy(id = "r5", startedAtEpochMillis = 8 * 60_000L))))

        assertEquals(SessionLevel(4.0, MusicalLevel.DEFAULT), summary.start)
        assertEquals(summary.start, summary.end)
        assertEquals(emptyList(), summary.moves)
        assertEquals("4 beats ahead, up to thirds, five notes, quarter notes, no rests, no accidentals.", summary.start!!.description)
    }

    @Test
    fun `a move within a run is named at its bar, a move between runs at the run, each pointing at its run`() {
        val moved = run("r6", 0, bars(4) { if (it >= 3) fourths else thirds }, flash.copy(segmentCount = null))
        val next = run("r7", 5, bars(4) { fourths }, flash.copy(lookaheadBeats = 3.0))
        val eased = run("r8", 10, bars(4) { thirds }, flash.copy(lookaheadBeats = 3.0))

        val summary = summarise(session, listOf(moved, next, eased))

        assertEquals(
            listOf("Harder from bar 3 of run 1: up to fourths", "Harder from run 2: 3 beats ahead", "Easier from run 3: up to thirds"),
            summary.moves.map { it.line },
        )
        assertEquals(listOf("r6", "r7", "r8"), summary.moves.map { it.runId })
        assertEquals(SessionLevel(4.0, MusicalLevel.DEFAULT), summary.start)
        assertEquals(SessionLevel(3.0, MusicalLevel.DEFAULT), summary.end)
    }

    @Test
    fun `the lookahead is a level only in Flash, and bundled bars have no musical level`() {
        val open = run("r9", 0, bars(4) { thirds }, flash.copy(mode = VisibilityMode.OPEN_SCORE))
        val bundled = HistoryFixtures.stored(HistoryFixtures.record("r10", listOf(Fixtures.segment("m1", Fixtures.cdef)), Fixtures.slowConfig, startedAtEpochMillis = 5 * 60_000L))

        val summary = summarise(session, listOf(open, bundled))

        assertEquals(SessionLevel(null, MusicalLevel.DEFAULT), summary.start)
        assertEquals(SessionLevel(2.0, null), summary.end)
        assertEquals(emptyList(), summary.moves, "a change of mode or content is the player's, not a move")
        assertEquals("Up to thirds, five notes, quarter notes, no rests, no accidentals.", summary.start!!.description)
        assertEquals("2 beats ahead.", summary.end!!.description)
        assertNull(SessionLevel(null, null).description)
    }
}
