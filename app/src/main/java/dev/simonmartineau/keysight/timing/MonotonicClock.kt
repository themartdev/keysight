package dev.simonmartineau.keysight.timing

/**
 * The single source of time for an attempt.
 *
 * Audio scheduling, MIDI timestamps, score visibility and performance start must all be read
 * from one monotonic base, never from independent UI timers. Android MIDI delivers timestamps
 * from `System.nanoTime`, so that is the base the whole app uses.
 *
 * It is an interface purely so tests can drive a timeline without waiting in real time.
 */
fun interface MonotonicClock {
    fun nowNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}
