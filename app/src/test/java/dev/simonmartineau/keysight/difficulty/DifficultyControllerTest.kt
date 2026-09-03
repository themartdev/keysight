package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.exercise.NoteValue
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DifficultyControllerTest {

    private val base = ExerciseConfig.DEFAULT
    private val start = Position(RunConfig.DEFAULT, DifficultyState.DEFAULT)

    /** [count] bars at [position] with [correct] of four notes right and [onTime] of the matched on time. */
    private fun bars(count: Int, correct: Int = 4, onTime: Int = correct, position: Position = start, base: ExerciseConfig = this.base) =
        List(count) { SegmentEvidence(position.runConfig.exposure, position.state.level.applyTo(base), correct, 4, onTime, correct) }

    private fun decide(evidence: List<SegmentEvidence>, position: Position = start, betweenRuns: Boolean = true, base: ExerciseConfig = this.base) =
        DifficultyController.decide(position, base, evidence, betweenRuns)

    @Test
    fun `the thresholds are section 8's`() {
        assertEquals(Direction.UP, DifficultyController.directionFor(0.91))
        assertNull(DifficultyController.directionFor(0.90))
        assertNull(DifficultyController.directionFor(0.65))
        assertEquals(Direction.DOWN, DifficultyController.directionFor(0.64))
        assertEquals(8, DifficultyController.WINDOW_SEGMENTS)
    }

    @Test
    fun `a short window holds, whatever it says`() {
        val decision = decide(bars(7))

        assertEquals(start, decision.position)
        assertNull(decision.move)
    }

    @Test
    fun `a full window in the middle band holds`() {
        assertNull(decide(bars(8, correct = 3)).move)
        assertNull(decide(bars(4) + bars(4, correct = 3, onTime = 2)).move)
    }

    @Test
    fun `success above ninety percent steps one dimension up, the lookahead first between runs`() {
        val decision = decide(bars(8))

        assertEquals(Move(Dimension.LOOKAHEAD, Direction.UP), decision.move)
        assertEquals(3.0, decision.position.runConfig.lookaheadBeats)
        assertEquals(DifficultyState.DEFAULT.level, decision.position.state.level)
        assertEquals(Dimension.LOOKAHEAD, decision.position.state.lastMoved)
    }

    @Test
    fun `within a run only the music moves`() {
        val decision = decide(bars(8), betweenRuns = false)

        assertEquals(Move(Dimension.INTERVAL, Direction.UP), decision.move)
        assertEquals(RunConfig.DEFAULT, decision.position.runConfig)
        assertEquals(3, decision.position.state.level.maxInterval)
    }

    @Test
    fun `success below sixty-five percent steps one dimension down, the last one up first`() {
        val afterInterval = decide(bars(8), betweenRuns = false).position
        val struggling = bars(8, correct = 2, position = afterInterval)

        val decision = decide(struggling, afterInterval)

        assertEquals(Move(Dimension.INTERVAL, Direction.DOWN), decision.move)
        assertEquals(DifficultyState.DEFAULT.level, decision.position.state.level)
        assertEquals(Dimension.INTERVAL, decision.position.state.lastMoved)
    }

    @Test
    fun `with nothing moved yet a step down starts from the end of the order`() {
        val decision = decide(bars(8, correct = 1))

        assertEquals(Move(Dimension.RHYTHM, Direction.DOWN), decision.move, "rests are already off, so the rhythm eases")
        assertEquals(setOf(NoteValue.WHOLE, NoteValue.HALF), decision.position.state.level.noteValues)
    }

    @Test
    fun `steps up take turns round the order, skipping what cannot move`() {
        var position = start
        val moves = ArrayList<Dimension>()
        repeat(8) {
            val decision = decide(bars(8, position = position), position)
            moves += decision.move!!.dimension
            position = decision.position
        }

        // The fourth move takes the rhythm to eighths and the fifth turns rests on, both their tops, so the second time round the turn passes them by.
        assertEquals(
            listOf(
                Dimension.LOOKAHEAD, Dimension.INTERVAL, Dimension.RANGE, Dimension.RHYTHM, Dimension.RESTS,
                Dimension.LOOKAHEAD, Dimension.INTERVAL, Dimension.RANGE,
            ),
            moves,
        )
        assertEquals(2.0, position.runConfig.lookaheadBeats)
        assertEquals(4, position.state.level.maxInterval)
        assertEquals(8, position.state.level.width)
        assertEquals(Ladders.RHYTHM.hardest, position.state.level.noteValues)
        assertTrue(position.state.level.rests)
    }

    @Test
    fun `steps down walk back the order from the last move`() {
        var position = Position(RunConfig.DEFAULT.copy(lookaheadBeats = 2.0), DifficultyState(MusicalLevel.DEFAULT.copy(maxInterval = 3), lastMoved = Dimension.RANGE))
        val moves = ArrayList<Dimension>()
        repeat(5) {
            val decision = decide(bars(8, correct = 1, position = position), position)
            moves += decision.move!!.dimension
            position = decision.position
        }

        // The range is at its floor, so the interval eases first and keeps easing to its floor, then the lookahead to its, then the rhythm.
        assertEquals(listOf(Dimension.INTERVAL, Dimension.INTERVAL, Dimension.LOOKAHEAD, Dimension.LOOKAHEAD, Dimension.RHYTHM), moves)
        assertEquals(4.0, position.runConfig.lookaheadBeats)
        assertEquals(1, position.state.level.maxInterval)
        assertEquals(Ladders.RHYTHM.easiest, position.state.level.noteValues)
    }

    @Test
    fun `the walk order is the section 8 order, cyclic from the last move`() {
        assertEquals(listOf(Dimension.LOOKAHEAD, Dimension.INTERVAL, Dimension.RANGE, Dimension.RHYTHM, Dimension.RESTS), Dimension.entries)
        assertEquals(Dimension.entries, DifficultyController.walk(null, Direction.UP))
        assertEquals(Dimension.entries.reversed(), DifficultyController.walk(null, Direction.DOWN))
        assertEquals(listOf(Dimension.RANGE, Dimension.RHYTHM, Dimension.RESTS, Dimension.LOOKAHEAD, Dimension.INTERVAL), DifficultyController.walk(Dimension.INTERVAL, Direction.UP))
        assertEquals(listOf(Dimension.INTERVAL, Dimension.LOOKAHEAD, Dimension.RESTS, Dimension.RHYTHM, Dimension.RANGE), DifficultyController.walk(Dimension.INTERVAL, Direction.DOWN))
        assertEquals(listOf(Dimension.RESTS, Dimension.LOOKAHEAD, Dimension.INTERVAL, Dimension.RANGE, Dimension.RHYTHM), DifficultyController.walk(Dimension.RHYTHM, Direction.UP))
    }

    @Test
    fun `the lookahead moves only in Flash and only along its ladder`() {
        val readAhead = Position(RunConfig.DEFAULT.copy(mode = VisibilityMode.READ_AHEAD), DifficultyState.DEFAULT)
        assertEquals(Dimension.INTERVAL, decide(bars(8, position = readAhead), readAhead).move!!.dimension)

        val shortest = Position(RunConfig.DEFAULT.copy(lookaheadBeats = 0.25), DifficultyState.DEFAULT)
        assertEquals(Dimension.INTERVAL, decide(bars(8, position = shortest), shortest).move!!.dimension)

        val longest = Position(RunConfig.DEFAULT, DifficultyState(MusicalLevel.DEFAULT.copy(maxInterval = 1), lastMoved = Dimension.INTERVAL))
        assertEquals(Dimension.RHYTHM, decide(bars(8, correct = 1, position = longest), longest).move!!.dimension)
    }

    @Test
    fun `an interval never outgrows the range, and a range never shrinks under its interval`() {
        val fifths = Position(
            RunConfig.DEFAULT.copy(mode = VisibilityMode.OPEN_SCORE),
            DifficultyState(MusicalLevel.DEFAULT.copy(maxInterval = 4, noteValues = Ladders.RHYTHM.hardest, rests = true), lastMoved = Dimension.RANGE),
        )
        val up = decide(bars(8, position = fifths), fifths)
        assertEquals(Move(Dimension.RANGE, Direction.UP), up.move)

        val wide = Position(RunConfig.DEFAULT.copy(mode = VisibilityMode.OPEN_SCORE), DifficultyState(MusicalLevel(5, Ladders.RANGE.rungs[1].right, Ladders.RANGE.rungs[1].left, Ladders.RHYTHM.hardest), lastMoved = Dimension.RANGE))
        val down = decide(bars(8, correct = 1, position = wide), wide)
        assertEquals(Move(Dimension.INTERVAL, Direction.DOWN), down.move)
    }

    @Test
    fun `a rhythm rung that cannot fill the meter is skipped`() {
        val waltz = base.copy(timeSignature = TimeSignature.THREE_FOUR)
        val position = Position(RunConfig.DEFAULT.copy(mode = VisibilityMode.OPEN_SCORE), DifficultyState.DEFAULT)

        val decision = decide(bars(8, correct = 1, position = position, base = waltz), position, base = waltz)

        assertEquals(Move(Dimension.INTERVAL, Direction.DOWN), decision.move)
    }

    @Test
    fun `nothing eligible holds without touching the state`() {
        val top = Position(
            RunConfig.DEFAULT.copy(lookaheadBeats = 0.25),
            DifficultyState(MusicalLevel(7, Ladders.RANGE.hardest.right, Ladders.RANGE.hardest.left, Ladders.RHYTHM.hardest, rests = true), lastMoved = Dimension.RANGE),
        )

        val decision = decide(bars(8, position = top), top)

        assertNull(decision.move)
        assertEquals(top, decision.position)
    }

    @Test
    fun `rests come on after eighths and go off before them`() {
        val eighths = Position(RunConfig.DEFAULT.copy(mode = VisibilityMode.OPEN_SCORE), DifficultyState(MusicalLevel.DEFAULT.copy(noteValues = Ladders.RHYTHM.hardest), lastMoved = Dimension.RHYTHM))
        val up = decide(bars(8, position = eighths), eighths)
        assertEquals(Move(Dimension.RESTS, Direction.UP), up.move)
        assertTrue(up.position.state.level.rests)
        assertEquals(Ladders.RHYTHM.hardest, up.position.state.level.noteValues)
        assertTrue(up.position.state.level.applyTo(base).rests)

        val struggling = decide(bars(8, correct = 1, position = up.position), up.position)
        assertEquals(Move(Dimension.RESTS, Direction.DOWN), struggling.move)
        assertEquals(eighths.state.level, struggling.position.state.level)

        val quarters = Position(RunConfig.DEFAULT.copy(mode = VisibilityMode.OPEN_SCORE), DifficultyState(MusicalLevel.DEFAULT, lastMoved = Dimension.RHYTHM))
        assertEquals(Move(Dimension.RESTS, Direction.UP), decide(bars(8, position = quarters), quarters).move, "rests may come on before eighths when it is their turn")
    }

    @Test
    fun `a move empties the window, so the same evidence cannot move twice`() {
        val evidence = bars(8)
        val first = decide(evidence)

        val second = decide(evidence, first.position)

        assertNull(second.move)
        assertEquals(first.position, second.position)
    }

    @Test
    fun `a player's own change empties the window too`() {
        val evidence = bars(8)
        val slower = Position(RunConfig.DEFAULT.copy(tempoBpm = 60.0), DifficultyState.DEFAULT)
        assertNull(decide(evidence, slower).move)
        assertNull(decide(evidence, base = base.copy(hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE)).move)
        assertNull(decide(evidence, start.copy(runConfig = RunConfig.DEFAULT.copy(mode = VisibilityMode.READ_AHEAD))).move)
        assertEquals(Dimension.LOOKAHEAD, decide(evidence, start.copy(runConfig = RunConfig.DEFAULT.copy(segmentCount = null))).move!!.dimension)
    }

    @Test
    fun `success is the weaker of pitch and rhythm`() {
        assertEquals(Direction.UP, decide(bars(8)).move!!.direction)
        assertNull(decide(bars(8, correct = 4, onTime = 3)).move)
        assertEquals(Direction.DOWN, decide(bars(8, correct = 4, onTime = 2)).move!!.direction)
    }
}
