package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.run.RunConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DifficultyTrackerTest {

    private val base = ExerciseConfig.DEFAULT
    private val runConfig = RunConfig.DEFAULT

    private fun bars(count: Int, correct: Int = 4, level: MusicalLevel = MusicalLevel.DEFAULT, runConfig: RunConfig = this.runConfig) =
        List(count) { SegmentEvidence(runConfig.exposure, level.applyTo(base), correct, 4, correct, correct) }

    @Test
    fun `restore reads the stored state and window, or starts at the default`() = runTest {
        val stored = DifficultyState(MusicalLevel.DEFAULT.copy(maxInterval = 3), lastMoved = Dimension.INTERVAL)
        val tracker = DifficultyTracker(InMemoryDifficultyStore(stored, bars(8, level = stored.level)), this)
        tracker.restore()
        assertEquals(stored, tracker.state.value)
        assertEquals(3, tracker.configFor(base).maxInterval)

        val fresh = DifficultyTracker(InMemoryDifficultyStore(), this)
        fresh.restore()
        assertEquals(DifficultyState.DEFAULT, fresh.state.value)
        assertEquals(base, fresh.configFor(base))
    }

    @Test
    fun `a run's segment is read at the moved level once the window fills, and the move is saved`() = runTest {
        val store = InMemoryDifficultyStore()
        val tracker = DifficultyTracker(store, this)
        tracker.restore()

        assertEquals(base, tracker.nextSegment(runConfig, base, bars(7)))
        val moved = tracker.nextSegment(runConfig, base, bars(8))
        assertEquals(3, moved.maxInterval)
        assertEquals(moved, tracker.nextSegment(runConfig, base, bars(8)), "the same evidence does not move twice")
        assertEquals(DifficultyState(MusicalLevel.of(moved), Dimension.INTERVAL), tracker.state.value)

        advanceUntilIdle()
        assertEquals(listOf(tracker.state.value), store.saves)
    }

    @Test
    fun `stored history and the run's commits make one window`() = runTest {
        val tracker = DifficultyTracker(InMemoryDifficultyStore(null, bars(5)), this)
        tracker.restore()

        assertEquals(base, tracker.nextSegment(runConfig, base, bars(2)))
        assertEquals(3, tracker.nextSegment(runConfig, base, bars(3)).maxInterval)
    }

    @Test
    fun `a run's end folds its commits in and may move the lookahead for the next run`() = runTest {
        val store = InMemoryDifficultyStore()
        val tracker = DifficultyTracker(store, this)
        tracker.restore()

        val first = tracker.runEnded(runConfig, base, bars(4))
        assertNull(first.move)
        assertEquals(runConfig, first.position.runConfig)

        val second = tracker.runEnded(runConfig, base, bars(4))
        assertEquals(Move(Dimension.LOOKAHEAD, Direction.UP), second.move)
        assertEquals(3.0, second.position.runConfig.lookaheadBeats)
        assertEquals(Dimension.LOOKAHEAD, tracker.state.value.lastMoved)
        assertEquals(base, tracker.configFor(base), "the music did not move")

        advanceUntilIdle()
        assertEquals(listOf(tracker.state.value), store.saves)
    }

    @Test
    fun `evidence from a run at another state does not count towards this one`() = runTest {
        val tracker = DifficultyTracker(InMemoryDifficultyStore(), this)
        tracker.restore()
        tracker.runEnded(runConfig, base, bars(8, level = MusicalLevel.DEFAULT.copy(maxInterval = 1)))

        val decision = tracker.runEnded(runConfig, base, bars(4))

        assertNull(decision.move)
        assertNull(tracker.runEnded(runConfig, base.copy(hands = Hands.LEFT), bars(4)).move)
    }

    @Test
    fun `a long run leaves the last window's worth of history, and a run at another state cuts it`() = runTest {
        val tracker = DifficultyTracker(InMemoryDifficultyStore(), this)
        tracker.restore()
        val slow = runConfig.copy(tempoBpm = 60.0)
        assertNull(tracker.runEnded(slow, base, bars(20, correct = 3, runConfig = slow)).move, "the middle band holds")

        assertEquals(Move(Dimension.LOOKAHEAD, Direction.UP), tracker.runEnded(slow, base, bars(8, runConfig = slow)).move, "a window of the last eight")
        val faster = slow.copy(lookaheadBeats = 3.0)
        assertNull(tracker.runEnded(faster, base, bars(7, runConfig = faster)).move, "the earlier bars are at another state")
        assertTrue(tracker.runEnded(faster, base, bars(1, runConfig = faster)).moved)
    }
}
