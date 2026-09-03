package dev.simonmartineau.keysight.timing

/**
 * The single source of time for a run.
 *
 * Audio scheduling, MIDI timestamps, notation visibility and performance start all read from
 * one monotonic base, never from independent timers. Android's MIDI framework stamps events
 * from `System.nanoTime`, so that is the base the whole app uses.
 *
 * It is an interface purely so that a four-second timeline can be tested in no time at all.
 */
fun interface MonotonicClock {
    fun nowNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}
