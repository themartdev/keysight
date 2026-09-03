package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.midi.MidiMessage
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.timing.RunTimeline
import kotlinx.serialization.Serializable

/**
 * One note the player sounded, positioned on the run's beat line.
 *
 * Beats are run beats: beat 0 is the first count-in click, the count-in is segment 0, and the
 * first notated beat is the start of segment 1. This is the same line the score's ticks are
 * on. [releaseBeat] is null when the key was still down when capture ended.
 */
@Serializable
data class PlayedNote(
    val pitch: Pitch,
    val onsetBeat: Double,
    val releaseBeat: Double?,
    val velocity: Int,
    val onsetNanos: Long,
) {
    init {
        require(releaseBeat == null || releaseBeat >= onsetBeat) { "a note cannot be released before it starts" }
    }
}

object PlayedNotes {

    /**
     * Pairs note-ons with their note-offs and places them on the beat line of [timeline], which
     * started at [startedAtNanos]. Only notes whose onset lies between [earlyGraceBeats] before
     * the performance start and the end of capture are kept: the count-in is not performance.
     *
     * A note-on for a key that is already down closes the earlier note first, and a note-off
     * for a key that is not down is ignored. Pedal and other messages do not affect pairing.
     */
    fun extract(
        events: List<MidiEvent>,
        timeline: RunTimeline,
        startedAtNanos: Long,
        earlyGraceBeats: Double,
    ): List<PlayedNote> {
        require(earlyGraceBeats >= 0.0) { "earlyGraceBeats must not be negative" }

        fun beatOf(timestampNanos: Long): Double = timeline.beatAtNanos(timestampNanos - startedAtNanos)

        val notes = ArrayList<Pending>()
        val open = HashMap<Pair<Int, Pitch>, Pending>()

        for (event in events.sortedBy { it.timestampNanos }) {
            when (val message = event.message) {
                is MidiMessage.NoteOn -> {
                    val key = message.channel to message.pitch
                    open.remove(key)?.releaseNanos = event.timestampNanos
                    val pending = Pending(message.pitch, message.velocity, event.timestampNanos)
                    notes += pending
                    open[key] = pending
                }
                is MidiMessage.NoteOff ->
                    open.remove(message.channel to message.pitch)?.releaseNanos = event.timestampNanos
                is MidiMessage.ControlChange, is MidiMessage.Other -> Unit
            }
        }

        val windowStart = timeline.performanceStartBeat - earlyGraceBeats
        val windowEnd = timeline.captureEndBeat
        return notes
            .map { pending ->
                PlayedNote(
                    pitch = pending.pitch,
                    onsetBeat = beatOf(pending.onsetNanos),
                    releaseBeat = pending.releaseNanos?.let(::beatOf),
                    velocity = pending.velocity,
                    onsetNanos = pending.onsetNanos,
                )
            }
            .filter { it.onsetBeat >= windowStart && it.onsetBeat <= windowEnd }
    }

    private class Pending(val pitch: Pitch, val velocity: Int, val onsetNanos: Long) {
        var releaseNanos: Long? = null
    }
}
