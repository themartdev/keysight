package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.run.RunConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The controller across a session: its state, restored from the [store] and saved after every
 * move, and the evidence it decides from, the last window's worth from history followed by
 * the runs of this session as they end. A running run's commits are passed in by the caller
 * at every decision, so the tracker holds nothing about a run that is still going.
 *
 * [nextSegment] is the within-run consultation, made by the segment source for every
 * segment it produces; [runEnded] is the between-run one, made once a run is over, and the
 * only one that may move the lookahead. The first segments of a run are read at [configFor]
 * without a decision, because a move there would reach the player unannounced.
 */
class DifficultyTracker(
    private val store: DifficultyStore,
    private val persistScope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DifficultyState.DEFAULT)
    val state: StateFlow<DifficultyState> = _state.asStateFlow()

    private var history: List<SegmentEvidence> = emptyList()

    suspend fun restore() {
        _state.value = store.load() ?: DifficultyState.DEFAULT
        history = store.recentEvidence(DifficultyController.WINDOW_SEGMENTS)
    }

    /** The player's [base] configuration read at the current level. */
    fun configFor(base: ExerciseConfig): ExerciseConfig = _state.value.level.applyTo(base)

    /**
     * The configuration of the next segment of a run presented as [runConfig] and read from
     * [base], given the run's [committed] segments so far: the current level, stepped if the
     * window says so.
     */
    fun nextSegment(runConfig: RunConfig, base: ExerciseConfig, committed: List<SegmentEvidence>): ExerciseConfig {
        apply(DifficultyController.decide(Position(runConfig, _state.value), base, history + committed, betweenRuns = false))
        return configFor(base)
    }

    /**
     * A run presented as [runConfig] and read from [base] ended with [committed] segments:
     * they join the evidence, and the decision for the next run is returned. Its position's
     * run configuration is [runConfig] with the lookahead moved when it did.
     */
    fun runEnded(runConfig: RunConfig, base: ExerciseConfig, committed: List<SegmentEvidence>): Decision {
        history = (history + committed).takeLast(DifficultyController.WINDOW_SEGMENTS)
        return apply(DifficultyController.decide(Position(runConfig, _state.value), base, history, betweenRuns = true))
    }

    private fun apply(decision: Decision): Decision {
        val state = decision.position.state
        if (state != _state.value) {
            _state.value = state
            persistScope.launch { store.save(state) }
        }
        return decision
    }
}
