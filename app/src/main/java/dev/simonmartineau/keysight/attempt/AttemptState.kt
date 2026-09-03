package dev.simonmartineau.keysight.attempt

import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.exercise.Exercise
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.timing.AttemptTimeline

/** What one attempt is made of: the passage, how it is flashed, and the resulting schedule. */
data class AttemptContext(
    val exercise: Exercise,
    val config: FlashConfig,
    val timeline: AttemptTimeline,
) {
    constructor(exercise: Exercise, config: FlashConfig) :
        this(exercise, config, AttemptTimeline.of(config, exercise.score))
}

/**
 * The lifecycle of one flash attempt.
 *
 * The states are phases of a single clock, not independent timers. Two of the plan's
 * boundaries are coincidences rather than steps: the count-in is already running when the
 * notation appears, which is [CountingIn.notationVisible] flipping, and the notation disappears
 * on the very instant the performance begins, so hiding and performing are one state.
 *
 * [Running.captured] holds every MIDI event received from the attempt start to the end of
 * capture, count-in included: what the player did before the passage started is raw data too.
 */
sealed interface AttemptState {

    val context: AttemptContext

    /** An exercise is loaded and the player has not started. */
    data class Ready(override val context: AttemptContext) : AttemptState

    /** The clock is running: the count-in, the performance and the capture tail. */
    sealed interface Running : AttemptState {
        val startedAtNanos: Long
        val captured: List<MidiEvent>
    }

    data class CountingIn(
        override val context: AttemptContext,
        override val startedAtNanos: Long,
        val notationVisible: Boolean,
        override val captured: List<MidiEvent>,
    ) : Running

    data class Performing(
        override val context: AttemptContext,
        override val startedAtNanos: Long,
        override val captured: List<MidiEvent>,
    ) : Running

    /** Capture is closed and the evaluator is working on [captured]. */
    data class Evaluating(
        override val context: AttemptContext,
        val startedAtNanos: Long,
        val captured: List<MidiEvent>,
    ) : AttemptState

    /** The result is on screen, waiting for the player to continue. */
    data class Result(
        override val context: AttemptContext,
        val startedAtNanos: Long,
        val captured: List<MidiEvent>,
        val evaluation: EvaluationResult,
    ) : AttemptState

    /** The attempt ended early. [startedAtNanos] is null when it never started. */
    data class Aborted(
        override val context: AttemptContext,
        val reason: AbortReason,
        val startedAtNanos: Long?,
        val captured: List<MidiEvent>,
    ) : AttemptState

    val isTerminal: Boolean get() = this is Result || this is Aborted
}
