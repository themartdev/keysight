package dev.simonmartineau.keysight.attempt

import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.exercise.Exercise
import dev.simonmartineau.keysight.midi.MidiEvent

/**
 * Everything that can happen to an attempt.
 *
 * Commands ([Start], [Evaluated], [Next]) are only legal in specific states and throw
 * otherwise, because sending one at the wrong time is a programming error. Inputs
 * ([ClockAdvanced], [MidiReceived], [Abort]) arrive asynchronously and may race with a
 * transition, so out-of-state ones are ignored.
 */
sealed interface AttemptEvent {

    /** The player started the attempt; [nowNanos] becomes beat 0. */
    data class Start(val nowNanos: Long) : AttemptEvent

    /** The clock reached [nowNanos]. Idempotent, and may jump past several boundaries at once. */
    data class ClockAdvanced(val nowNanos: Long) : AttemptEvent

    data class MidiReceived(val event: MidiEvent) : AttemptEvent

    data class Abort(val reason: AbortReason) : AttemptEvent

    data class Evaluated(val result: EvaluationResult) : AttemptEvent

    /** Load the next exercise, from a result or an aborted attempt. */
    data class Next(val exercise: Exercise, val config: FlashConfig) : AttemptEvent
}
