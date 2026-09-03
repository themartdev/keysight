package dev.simonmartineau.keysight.audio

import dev.simonmartineau.keysight.timing.RunTimeline

/**
 * How the run clock got anchored.
 *
 * [runStartNanos] is the instant, on the `System.nanoTime` base, at which the first click
 * reaches the listener. It becomes beat 0 for everything else in the run.
 */
data class MetronomeStart(
    val runStartNanos: Long,
    val anchoredByTimestamp: Boolean,
    val reportedLatencyNanos: Long?,
)

/**
 * Plays the click track of one run and reports where beat 0 landed.
 *
 * The metronome is the one thing in the app whose timing the player can hear, so it defines
 * the run clock rather than following it.
 */
interface Metronome {

    /** Starts playing [timeline]'s clicks and returns once beat 0 is known. */
    suspend fun start(timeline: RunTimeline): MetronomeStart

    /** Stops immediately. Safe to call when nothing is playing. */
    fun stop()
}
