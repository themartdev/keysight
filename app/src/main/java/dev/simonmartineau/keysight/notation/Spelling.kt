package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step

/**
 * A spelling for a pitch that only exists as a MIDI number: what the player sounded.
 *
 * Black keys are spelled as sharps. That is right in C major and the sharp keys and merely
 * unusual in the flat keys; a key-aware spelling can replace this when the content grows
 * key signatures.
 */
fun sharpSpelling(pitch: Pitch): SpelledPitch {
    val (step, alteration) = SHARP_SPELLINGS[pitch.pitchClass]
    return SpelledPitch(step, alteration, pitch.octave)
}

private val SHARP_SPELLINGS: List<Pair<Step, Int>> = listOf(
    Step.C to 0, Step.C to 1, Step.D to 0, Step.D to 1, Step.E to 0, Step.F to 0,
    Step.F to 1, Step.G to 0, Step.G to 1, Step.A to 0, Step.A to 1, Step.B to 0,
)
