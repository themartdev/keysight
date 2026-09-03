package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/**
 * A sounding pitch, identified by its MIDI note number. Middle C, C4, is 60.
 *
 * This is what the keyboard produces and what the evaluator compares. How the pitch is spelled
 * on the page is a separate concern, see [SpelledPitch].
 */
@Serializable
@JvmInline
value class Pitch(val midiNoteNumber: Int) : Comparable<Pitch> {

    init {
        require(midiNoteNumber in MIDI_RANGE) { "MIDI note number out of range: $midiNoteNumber" }
    }

    /** Scientific pitch notation octave: 60 is C4, so octave 4. */
    val octave: Int get() = midiNoteNumber / 12 - 1

    /** 0 for C up to 11 for B. */
    val pitchClass: Int get() = midiNoteNumber % 12

    /** Signed semitone distance from this pitch up to [other]. */
    fun semitonesTo(other: Pitch): Int = other.midiNoteNumber - midiNoteNumber

    fun transposedBy(semitones: Int): Pitch = Pitch(midiNoteNumber + semitones)

    override fun compareTo(other: Pitch): Int = midiNoteNumber.compareTo(other.midiNoteNumber)

    /** Sharp-spelled name for logs and test output, never for notation. */
    override fun toString(): String = "${SHARP_NAMES[pitchClass]}$octave"

    companion object {
        val MIDI_RANGE = 0..127

        private val SHARP_NAMES =
            listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        val C4 = Pitch(60)
    }
}
