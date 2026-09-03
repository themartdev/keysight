package dev.simonmartineau.keysight.midi

import dev.simonmartineau.keysight.timing.MonotonicClock

/**
 * Turns the byte deliveries of one input port into [MidiEvent]s on the attempt clock.
 *
 * The framework stamps deliveries on the `System.nanoTime` base, which is what the app uses
 * everywhere; a delivery stamped 0 (some drivers do that) is stamped with the clock on
 * arrival instead. One instance per port, because [MidiParser] keeps running status between
 * deliveries.
 */
class MidiCapture(
    private val clock: MonotonicClock,
    private val onEvent: (MidiEvent) -> Unit,
) {
    private val parser = MidiParser()

    fun receive(bytes: ByteArray, offset: Int, count: Int, timestampNanos: Long) {
        val stamp = if (timestampNanos == 0L) clock.nowNanos() else timestampNanos
        parser.feed(bytes, offset, count, stamp).forEach(onEvent)
    }
}
