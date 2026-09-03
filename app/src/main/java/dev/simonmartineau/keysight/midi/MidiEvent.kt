package dev.simonmartineau.keysight.midi

import dev.simonmartineau.keysight.score.Pitch

/**
 * One MIDI channel message exactly as it was received, with the moment it arrived.
 *
 * This is the raw record: the three bytes are stored verbatim and [message] is decoded from
 * them on demand, so a stored run can always be re-read by a smarter decoder.
 *
 * [timestampNanos] is on the `System.nanoTime` base, the same base as the run clock, which
 * is what makes it comparable to the scheduled timeline. Android's MIDI framework stamps
 * messages on that base; the transport substitutes the clock when the framework hands it 0.
 */
data class MidiEvent(
    val timestampNanos: Long,
    val status: Int,
    val data1: Int,
    val data2: Int,
) {
    init {
        require(status in MidiMessage.CHANNEL_STATUS_RANGE) { "not a channel status byte: $status" }
        require(data1 in DATA_RANGE) { "data1 out of range: $data1" }
        require(data2 in DATA_RANGE) { "data2 out of range: $data2" }
    }

    val message: MidiMessage get() = MidiMessage.decode(status, data1, data2)

    companion object {
        val DATA_RANGE = 0..127

        fun noteOn(timestampNanos: Long, pitch: Pitch, velocity: Int, channel: Int = 0): MidiEvent =
            MidiEvent(timestampNanos, MidiMessage.STATUS_NOTE_ON or channel, pitch.midiNoteNumber, velocity)

        fun noteOff(timestampNanos: Long, pitch: Pitch, velocity: Int = 0, channel: Int = 0): MidiEvent =
            MidiEvent(timestampNanos, MidiMessage.STATUS_NOTE_OFF or channel, pitch.midiNoteNumber, velocity)

        fun controlChange(timestampNanos: Long, controller: Int, value: Int, channel: Int = 0): MidiEvent =
            MidiEvent(timestampNanos, MidiMessage.STATUS_CONTROL_CHANGE or channel, controller, value)
    }
}
