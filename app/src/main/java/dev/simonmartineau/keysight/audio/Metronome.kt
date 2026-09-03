package dev.simonmartineau.keysight.audio

import dev.simonmartineau.keysight.timing.AttemptTimeline

/**
 * How the attempt clock got anchored.
 *
 * [attemptStartNanos] is the instant, on the `System.nanoTime` base, at which the first click
 * reaches the listener. It becomes beat 0 for everything else in the attempt.
 */
data class MetronomeStart(
    val attemptStartNanos: Long,
    val anchoredByTimestamp: Boolean,
    val reportedLatencyNanos: Long?,
)

/**
 * Plays the click track of one attempt and reports where beat 0 landed.
 *
 * The metronome is the one thing in the app whose timing the player can hear, so it defines
 * the attempt clock rather than following it.
 */
interface Metronome {

    /** Starts playing [timeline]'s clicks and returns once beat 0 is known. */
    suspend fun start(timeline: AttemptTimeline): MetronomeStart

    /** Stops immediately. Safe to call when nothing is playing. */
    fun stop()
}
