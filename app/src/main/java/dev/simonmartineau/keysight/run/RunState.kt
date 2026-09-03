package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.midi.MidiEvent

/**
 * The lifecycle of one run.
 *
 * The states are phases of a single clock, not independent timers. What the page shows at any
 * instant, the mask, the cursor and the system on screen, is not state: it is derived from the
 * beat by whoever draws the frame. The reducer only knows the boundaries it must wake for.
 *
 * [Running.captured] holds every MIDI event received from the run start to the end of capture,
 * count-in included: what the player did before the first bar is raw data too. [Running.lastSegment]
 * is the last segment that will be performed; it is the run's last segment until the player
 * stops, after which it is the segment the stop landed in.
 */
sealed interface RunState {

    val context: RunContext

    /** A run is built and the player has not started. */
    data class Ready(override val context: RunContext) : RunState

    /** The clock is running: the count-in, the performance and the capture tail. */
    sealed interface Running : RunState {
        val startedAtNanos: Long
        val captured: List<MidiEvent>
        val lastSegment: Int
    }

    data class CountingIn(
        override val context: RunContext,
        override val startedAtNanos: Long,
        override val captured: List<MidiEvent>,
        override val lastSegment: Int,
    ) : Running

    data class Performing(
        override val context: RunContext,
        override val startedAtNanos: Long,
        override val captured: List<MidiEvent>,
        override val lastSegment: Int,
    ) : Running

    /** Capture is closed and the evaluator is working on [captured]. */
    data class Evaluating(
        override val context: RunContext,
        val startedAtNanos: Long,
        val captured: List<MidiEvent>,
        val lastSegment: Int,
    ) : RunState

    /** The run is over and its summary is on screen, waiting for the player to continue. */
    data class Summary(
        override val context: RunContext,
        val startedAtNanos: Long,
        val captured: List<MidiEvent>,
        val lastSegment: Int,
        val evaluation: EvaluationResult,
    ) : RunState {
        /** The run as it was performed: cut after [lastSegment]. */
        val performed: RunContext.Performed get() = context.performed(lastSegment)
    }

    /** The run ended early. [startedAtNanos] is null when it never started. */
    data class Aborted(
        override val context: RunContext,
        val reason: AbortReason,
        val startedAtNanos: Long?,
        val captured: List<MidiEvent>,
    ) : RunState

    val isTerminal: Boolean get() = this is Summary || this is Aborted
}
