package dev.simonmartineau.keysight.midi

import dev.simonmartineau.keysight.score.Pitch

/**
 * A decoded MIDI channel message.
 *
 * The typed cases are the ones the app reasons about. Every other channel message (program
 * change, aftertouch, pitch bend) decodes to [Other] so that it still travels through capture
 * and storage; nothing a keyboard sends during a performance is thrown away.
 */
sealed interface MidiMessage {

    val channel: Int

    data class NoteOn(override val channel: Int, val pitch: Pitch, val velocity: Int) : MidiMessage {
        init {
            require(channel in CHANNELS) { "channel out of range: $channel" }
            require(velocity in 1..127) { "note-on velocity must be 1..127, was $velocity" }
        }
    }

    data class NoteOff(override val channel: Int, val pitch: Pitch, val velocity: Int) : MidiMessage {
        init {
            require(channel in CHANNELS) { "channel out of range: $channel" }
            require(velocity in 0..127) { "velocity out of range: $velocity" }
        }
    }

    data class ControlChange(override val channel: Int, val controller: Int, val value: Int) : MidiMessage {
        init {
            require(channel in CHANNELS) { "channel out of range: $channel" }
            require(controller in 0..127) { "controller out of range: $controller" }
            require(value in 0..127) { "value out of range: $value" }
        }
    }

    /** Any other channel message, kept as its raw bytes. */
    data class Other(val status: Int, val data1: Int, val data2: Int) : MidiMessage {
        init {
            require(status in CHANNEL_STATUS_RANGE) { "not a channel status byte: $status" }
        }

        override val channel: Int get() = status and 0x0F
    }

    companion object {
        val CHANNELS = 0..15
        val CHANNEL_STATUS_RANGE = 0x80..0xEF

        const val STATUS_NOTE_OFF = 0x80
        const val STATUS_NOTE_ON = 0x90
        const val STATUS_CONTROL_CHANGE = 0xB0

        /** The sustain pedal controller number. */
        const val CC_SUSTAIN = 64

        /**
         * Decodes one channel message from its raw bytes. A note-on with velocity 0 is a
         * note-off, which is what many keyboards actually send.
         */
        fun decode(status: Int, data1: Int, data2: Int): MidiMessage {
            require(status in CHANNEL_STATUS_RANGE) { "not a channel status byte: $status" }
            val channel = status and 0x0F
            return when (status and 0xF0) {
                STATUS_NOTE_OFF -> NoteOff(channel, Pitch(data1), data2)
                STATUS_NOTE_ON ->
                    if (data2 == 0) NoteOff(channel, Pitch(data1), 0) else NoteOn(channel, Pitch(data1), data2)
                STATUS_CONTROL_CHANGE -> ControlChange(channel, data1, data2)
                else -> Other(status, data1, data2)
            }
        }
    }
}
