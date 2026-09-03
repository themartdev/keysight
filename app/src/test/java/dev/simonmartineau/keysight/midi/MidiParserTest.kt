package dev.simonmartineau.keysight.midi

import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MidiParserTest {

    private val parser = MidiParser()

    @Test
    fun `a complete note-on in one delivery`() {
        val events = parser.feed(bytes(0x90, 60, 100), timestampNanos = 42)

        assertEquals(listOf(MidiEvent(42, 0x90, 60, 100)), events)
        assertEquals(MidiMessage.NoteOn(channel = 0, pitch = Pitch.C4, velocity = 100), events.single().message)
    }

    @Test
    fun `a message split across two deliveries is reassembled and stamped by the delivery that completes it`() {
        assertEquals(emptyList(), parser.feed(bytes(0x91, 62), timestampNanos = 1))
        val events = parser.feed(bytes(90), timestampNanos = 2)

        assertEquals(listOf(MidiEvent(2, 0x91, 62, 90)), events)
    }

    @Test
    fun `running status repeats the last channel status`() {
        val events = parser.feed(bytes(0x90, 60, 100, 62, 100, 64, 100), timestampNanos = 7)

        assertEquals(listOf(60, 62, 64), events.map { it.data1 })
        assertTrue(events.all { it.status == 0x90 && it.timestampNanos == 7L })
    }

    @Test
    fun `running status works across deliveries`() {
        parser.feed(bytes(0x90, 60, 100), timestampNanos = 1)
        val events = parser.feed(bytes(62, 100), timestampNanos = 2)

        assertEquals(listOf(MidiEvent(2, 0x90, 62, 100)), events)
    }

    @Test
    fun `two-byte channel messages take a single data byte`() {
        val events = parser.feed(bytes(0xC0, 5, 0xD0, 33, 34, 0x90, 60, 1), timestampNanos = 0)

        assertEquals(
            listOf(
                MidiEvent(0, 0xC0, 5, 0),
                MidiEvent(0, 0xD0, 33, 0),
                MidiEvent(0, 0xD0, 34, 0), // running status on channel pressure
                MidiEvent(0, 0x90, 60, 1),
            ),
            events,
        )
        assertIs<MidiMessage.Other>(events[0].message)
    }

    @Test
    fun `note-on with velocity zero is a note-off`() {
        val events = parser.feed(bytes(0x90, 60, 0), timestampNanos = 0)

        assertEquals(MidiMessage.NoteOff(channel = 0, pitch = Pitch.C4, velocity = 0), events.single().message)
        // The raw bytes still say what the keyboard actually sent.
        assertEquals(0x90, events.single().status)
    }

    @Test
    fun `real-time bytes in the middle of a message do not disturb it`() {
        val events = parser.feed(bytes(0x90, 0xF8, 60, 0xFE, 100), timestampNanos = 0)

        assertEquals(listOf(MidiEvent(0, 0x90, 60, 100)), events)
    }

    @Test
    fun `real-time bytes do not cancel running status`() {
        val events = parser.feed(bytes(0x90, 60, 100, 0xF8, 62, 100), timestampNanos = 0)

        assertEquals(2, events.size)
        assertEquals(62, events[1].data1)
    }

    @Test
    fun `system exclusive is skipped and cancels running status`() {
        val events = parser.feed(bytes(0x90, 60, 100, 0xF0, 0x7E, 0x7F, 0x09, 0x01, 0xF7, 62, 100), timestampNanos = 0)

        assertEquals(listOf(MidiEvent(0, 0x90, 60, 100)), events)
    }

    @Test
    fun `system exclusive may span deliveries`() {
        assertEquals(emptyList(), parser.feed(bytes(0xF0, 0x41, 0x10), timestampNanos = 0))
        assertEquals(emptyList(), parser.feed(bytes(0x42, 0xF7), timestampNanos = 1))
        assertEquals(1, parser.feed(bytes(0x80, 60, 0), timestampNanos = 2).size)
    }

    @Test
    fun `system common messages consume their data and cancel running status`() {
        // Song position pointer carries two data bytes; the 62 100 that follow have no status.
        val events = parser.feed(bytes(0x90, 60, 100, 0xF2, 0x10, 0x20, 62, 100), timestampNanos = 0)

        assertEquals(listOf(MidiEvent(0, 0x90, 60, 100)), events)
    }

    @Test
    fun `data bytes with no status are dropped`() {
        assertEquals(emptyList(), parser.feed(bytes(60, 100, 62, 100), timestampNanos = 0))
    }

    @Test
    fun `a new status byte discards a partial message`() {
        val events = parser.feed(bytes(0x90, 60, 0x80, 60, 0), timestampNanos = 0)

        assertEquals(listOf(MidiEvent(0, 0x80, 60, 0)), events)
    }

    @Test
    fun `control change decodes with its controller number`() {
        val events = parser.feed(bytes(0xB3, 64, 127), timestampNanos = 0)

        assertEquals(MidiMessage.ControlChange(channel = 3, controller = 64, value = 127), events.single().message)
    }

    @Test
    fun `the window into the buffer is honoured`() {
        val buffer = bytes(0, 0, 0x90, 60, 100, 0, 0)

        val events = parser.feed(buffer, offset = 2, count = 3, timestampNanos = 0)

        assertEquals(listOf(MidiEvent(0, 0x90, 60, 100)), events)
        assertFailsWith<IllegalArgumentException> { parser.feed(buffer, offset = 5, count = 3, timestampNanos = 0) }
    }

    @Test
    fun `decode round-trips every channel message kind`() {
        val raw = listOf(
            Triple(0x80, 60, 64),
            Triple(0x95, 60, 100),
            Triple(0xA0, 60, 10),
            Triple(0xB0, 64, 127),
            Triple(0xC1, 7, 0),
            Triple(0xD0, 50, 0),
            Triple(0xEF, 0, 64),
        )

        raw.forEach { (status, d1, d2) ->
            val event = MidiEvent(0, status, d1, d2)
            assertEquals(status and 0x0F, event.message.channel, "channel of $event")
        }
        assertFailsWith<IllegalArgumentException> { MidiMessage.decode(0xF8, 0, 0) }
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
