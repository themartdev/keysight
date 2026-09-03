package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.midi.MidiEvent

/**
 * Everything that can happen to a run.
 *
 * Commands ([Start], [Evaluated], [Next]) are only legal in specific states and throw
 * otherwise, because sending one at the wrong time is a programming error. Inputs
 * ([ClockAdvanced], [MidiReceived], [Stop], [Abort]) arrive asynchronously and may race with a
 * transition, so out-of-state ones are ignored.
 */
sealed interface RunEvent {

    /** The player started the run; [nowNanos] becomes beat 0. */
    data class Start(val nowNanos: Long) : RunEvent

    /** The clock reached [nowNanos]. Idempotent, and may jump past several boundaries at once. */
    data class ClockAdvanced(val nowNanos: Long) : RunEvent

    data class MidiReceived(val event: MidiEvent) : RunEvent

    /**
     * The player asked to stop at [nowNanos]. While performing, the run ends after the current
     * segment's capture tail and is summarised; during the count-in nothing was performed, so
     * the run is cancelled instead.
     */
    data class Stop(val nowNanos: Long) : RunEvent

    data class Abort(val reason: AbortReason) : RunEvent

    data class Evaluated(val result: EvaluationResult) : RunEvent

    /** Build the next run, from a summary or an aborted run. */
    data class Next(val context: RunContext) : RunEvent
}
