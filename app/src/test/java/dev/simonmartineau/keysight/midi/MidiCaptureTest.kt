package dev.simonmartineau.keysight.midi

import dev.simonmartineau.keysight.timing.MonotonicClock
import kotlin.test.Test
import kotlin.test.assertEquals

class MidiCaptureTest {

    private val received = mutableListOf<MidiEvent>()
    private var now = 5_000L
    private val capture = MidiCapture(MonotonicClock { now }, received::add)

    @Test
    fun `deliveries become events with the framework timestamp`() {
        capture.receive(byteArrayOf(0x90.toByte(), 60, 100), 0, 3, timestampNanos = 42)

        assertEquals(listOf(MidiEvent(42, 0x90, 60, 100)), received)
    }

    @Test
    fun `a zero timestamp is replaced by the clock`() {
        capture.receive(byteArrayOf(0x90.toByte(), 60, 100), 0, 3, timestampNanos = 0)

        assertEquals(5_000L, received.single().timestampNanos)
    }

    @Test
    fun `running status survives between deliveries`() {
        capture.receive(byteArrayOf(0x90.toByte(), 60, 100), 0, 3, timestampNanos = 1)
        capture.receive(byteArrayOf(62, 100), 0, 2, timestampNanos = 2)

        assertEquals(listOf(60, 62), received.map { it.data1 })
        assertEquals(listOf(0x90, 0x90), received.map { it.status })
    }

    @Test
    fun `the offset and count window is honoured`() {
        capture.receive(byteArrayOf(0, 0x80.toByte(), 60, 0, 0), 1, 3, timestampNanos = 7)

        assertEquals(listOf(MidiEvent(7, 0x80, 60, 0)), received)
    }
}
