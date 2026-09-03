package dev.simonmartineau.keysight.score

/**
 * A sounding pitch, identified by its MIDI note number (middle C, C4, is 60).
 *
 * Notation cares about spelling - F sharp and G flat are drawn differently - but evaluation
 * only ever compares what the keyboard actually sounded, so the canonical model stores the
 * note number and leaves spelling to the rendered notation.
 */
@JvmInline
value class Pitch(val midiNoteNumber: Int) : Comparable<Pitch> {

    /** Scientific pitch notation octave: C4 is 60, so octave 4. */
    val octave: Int get() = midiNoteNumber / 12 - 1

    /** 0 for C through 11 for B. */
    val pitchClass: Int get() = midiNoteNumber.mod(12)

    /** Signed distance in semitones from this pitch to [other]. */
    fun semitonesTo(other: Pitch): Int = other.midiNoteNumber - midiNoteNumber

    fun transposedBy(semitones: Int): Pitch = Pitch(midiNoteNumber + semitones)

    override fun compareTo(other: Pitch): Int = midiNoteNumber.compareTo(other.midiNoteNumber)

    /** A sharp-spelled name, for logs and developer output only. */
    override fun toString(): String = "${SHARP_NAMES[pitchClass]}$octave"

    companion object {
        private val SHARP_NAMES =
            listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        val C4 = Pitch(60)
    }
}

/** An inclusive range of pitches, used to constrain what an exercise may contain. */
data class PitchRange(val lowest: Pitch, val highest: Pitch) {
    init {
        require(lowest <= highest) { "lowest ($lowest) must not be above highest ($highest)" }
    }

    operator fun contains(pitch: Pitch): Boolean = pitch >= lowest && pitch <= highest

    val semitoneSpan: Int get() = lowest.semitonesTo(highest)
}
