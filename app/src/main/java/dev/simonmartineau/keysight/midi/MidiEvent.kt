package dev.simonmartineau.keysight.midi

import dev.simonmartineau.keysight.score.Pitch

enum class MidiEventType { NOTE_ON, NOTE_OFF }

/**
 * One note-on or note-off received from the keyboard.
 *
 * [timestampNanos] is on the same monotonic base as the attempt clock (`System.nanoTime`), which
 * is what makes it comparable to the scheduled timeline. Raw events are stored verbatim so that
 * an attempt can be re-evaluated later by a better evaluator.
 */
data class MidiEvent(
    val timestampNanos: Long,
    val type: MidiEventType,
    val channel: Int,
    val pitch: Pitch,
    val velocity: Int,
) {
    init {
        require(channel in 0..15) { "channel must be within 0..15" }
        require(velocity in 0..127) { "velocity must be within 0..127" }
    }
}
