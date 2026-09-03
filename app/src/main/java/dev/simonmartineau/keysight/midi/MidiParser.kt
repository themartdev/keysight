package dev.simonmartineau.keysight.midi

/**
 * Turns the byte stream from one MIDI input port into [MidiEvent]s.
 *
 * One instance per port, because the parser carries state between calls: a message may be
 * split across two deliveries, and running status lets a keyboard omit the status byte on
 * consecutive messages of the same kind. Everything that is not a channel message is consumed
 * and dropped: system real-time bytes may appear anywhere, even inside a message, and never
 * disturb it; system exclusive is skipped as a block; system common messages consume their
 * data bytes and cancel running status, as the specification requires.
 *
 * Every event produced by one [feed] call carries that call's timestamp. A message completed
 * by a later delivery is stamped with the delivery that completed it.
 */
class MidiParser {

    /** Status of the message being assembled, or 0 when data bytes have nowhere to go. */
    private var status = 0

    /** Whether [status] is a channel message and therefore survives as running status. */
    private var statusIsChannel = false

    private var dataNeeded = 0
    private var data1 = 0
    private var dataCount = 0
    private var inSysex = false

    fun feed(bytes: ByteArray, offset: Int, count: Int, timestampNanos: Long): List<MidiEvent> {
        require(offset >= 0 && count >= 0 && offset + count <= bytes.size) {
            "invalid window $offset+$count for ${bytes.size} bytes"
        }
        val events = ArrayList<MidiEvent>(count / 3 + 1)
        for (i in offset until offset + count) {
            val byte = bytes[i].toInt() and 0xFF
            consume(byte, timestampNanos)?.let(events::add)
        }
        return events
    }

    fun feed(bytes: ByteArray, timestampNanos: Long): List<MidiEvent> =
        feed(bytes, 0, bytes.size, timestampNanos)

    private fun consume(byte: Int, timestampNanos: Long): MidiEvent? {
        when {
            byte >= SYSTEM_REAL_TIME_START -> return null
            byte == SYSEX_START -> {
                inSysex = true
                resetStatus()
                return null
            }
            byte == SYSEX_END -> {
                inSysex = false
                return null
            }
            inSysex -> return null
            byte >= STATUS_START -> {
                beginMessage(byte)
                return null
            }
        }

        if (status == 0) return null

        return when (dataCount) {
            0 -> {
                data1 = byte
                dataCount = 1
                if (dataNeeded == 1) complete(data1, 0, timestampNanos) else null
            }
            else -> complete(data1, byte, timestampNanos)
        }
    }

    private fun beginMessage(statusByte: Int) {
        dataCount = 0
        if (statusByte in MidiMessage.CHANNEL_STATUS_RANGE) {
            status = statusByte
            statusIsChannel = true
            dataNeeded = if (statusByte and 0xF0 in TWO_BYTE_CHANNEL_KINDS) 1 else 2
            return
        }
        // System common: cancels running status and consumes its own data bytes silently.
        statusIsChannel = false
        dataNeeded = SYSTEM_COMMON_DATA_LENGTH[statusByte] ?: 0
        status = if (dataNeeded == 0) 0 else statusByte
    }

    private fun complete(d1: Int, d2: Int, timestampNanos: Long): MidiEvent? {
        val completedStatus = status
        val channel = statusIsChannel
        dataCount = 0
        if (!channel) {
            status = 0
            return null
        }
        return MidiEvent(timestampNanos, completedStatus, d1, d2)
    }

    private fun resetStatus() {
        status = 0
        statusIsChannel = false
        dataCount = 0
    }

    private companion object {
        const val STATUS_START = 0x80
        const val SYSEX_START = 0xF0
        const val SYSEX_END = 0xF7
        const val SYSTEM_REAL_TIME_START = 0xF8

        /** Program change and channel pressure carry a single data byte. */
        val TWO_BYTE_CHANNEL_KINDS = setOf(0xC0, 0xD0)

        val SYSTEM_COMMON_DATA_LENGTH = mapOf(
            0xF1 to 1, // MIDI time code quarter frame
            0xF2 to 2, // song position pointer
            0xF3 to 1, // song select
            0xF4 to 0,
            0xF5 to 0,
            0xF6 to 0, // tune request
        )
    }
}
