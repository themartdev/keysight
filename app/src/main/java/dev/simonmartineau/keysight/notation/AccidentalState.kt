package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step

/**
 * What the reader currently takes each letter to mean on one staff, within one measure.
 *
 * The key signature sets the meaning at the barline, and every written accidental changes it
 * for that letter in that octave until the next barline, as the convention goes. An accidental
 * is therefore written only when the note differs from what the reader would assume, and a
 * natural is an accidental like any other. No courtesy accidentals.
 */
class AccidentalState(private val key: KeySignature) {

    private val written = HashMap<Pair<Step, Int>, Int>()

    /** The accidental to write before [spelling], or null when none is needed; remembers what it wrote. */
    fun accidentalFor(spelling: SpelledPitch): Glyph? {
        val letter = spelling.step to spelling.octave
        val assumed = written[letter] ?: key.alterationOf(spelling.step)
        if (assumed == spelling.alteration) return null
        written[letter] = spelling.alteration
        return Glyph.accidentalGlyph(spelling.alteration)
    }
}
