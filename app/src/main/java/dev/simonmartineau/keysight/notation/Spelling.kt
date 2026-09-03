package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step

/**
 * A spelling for a pitch that only exists as a MIDI number: what the player sounded.
 *
 * A note of the key is spelled as the key spells it, so a played E flat in E flat major is
 * not written D sharp. A note outside the key follows the key's leaning: sharps in the sharp
 * keys and C, flats in the flat keys.
 */
fun spelledIn(pitch: Pitch, key: KeySignature): SpelledPitch {
    val diatonic = Step.entries.firstNotNullOfOrNull { step ->
        SpelledPitch.of(pitch, step, key.alterationOf(step))
    }
    if (diatonic != null) return diatonic
    val (step, alteration) = (if (key.fifths < 0) FLAT_SPELLINGS else SHARP_SPELLINGS)[pitch.pitchClass]
    return SpelledPitch(step, alteration, pitch.octave)
}

/** The accidental a cue note outside any measure context needs: none when the key already says so. */
fun cueAccidental(spelling: SpelledPitch, key: KeySignature): Glyph? =
    if (spelling.alteration == key.alterationOf(spelling.step)) null else Glyph.accidentalGlyph(spelling.alteration)

private val SHARP_SPELLINGS: List<Pair<Step, Int>> = listOf(
    Step.C to 0, Step.C to 1, Step.D to 0, Step.D to 1, Step.E to 0, Step.F to 0,
    Step.F to 1, Step.G to 0, Step.G to 1, Step.A to 0, Step.A to 1, Step.B to 0,
)

private val FLAT_SPELLINGS: List<Pair<Step, Int>> = listOf(
    Step.C to 0, Step.D to -1, Step.D to 0, Step.E to -1, Step.E to 0, Step.F to 0,
    Step.G to -1, Step.G to 0, Step.A to -1, Step.A to 0, Step.B to -1, Step.B to 0,
)
