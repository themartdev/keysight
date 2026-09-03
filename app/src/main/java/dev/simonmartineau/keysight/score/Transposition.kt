package dev.simonmartineau.keysight.score

/**
 * The same passage written in another major key.
 *
 * Every letter moves by the interval between the two tonics, and every accidental keeps its
 * meaning relative to the key: a raised fourth in C, F sharp, is a raised fourth in G, C
 * sharp. The passage moves by the smaller of the two possible intervals, at most a tritone
 * either way, so it stays in the register it was written in; a tritone goes up towards the
 * sharper key and down towards the flatter one. Going from any key back to the first therefore
 * lands where it started.
 */
fun Score.transposed(to: KeySignature): Score {
    if (to == keySignature) return this
    val from = keySignature
    val stepShift = Math.floorMod(to.tonicStep.ordinal - from.tonicStep.ordinal, Step.entries.size)
    var semitoneShift = Math.floorMod(to.tonicPitchClass - from.tonicPitchClass, 12)
    if (semitoneShift > 6 || (semitoneShift == 6 && to.fifths < from.fifths)) semitoneShift -= 12

    return copy(
        keySignature = to,
        notes = notes.map { note ->
            val old = note.spelling
            val step = Step.entries[(old.step.ordinal + stepShift) % Step.entries.size]
            val alteration = to.alterationOf(step) + (old.alteration - from.alterationOf(old.step))
            val spelling = checkNotNull(SpelledPitch.of(old.pitch.transposedBy(semitoneShift), step, alteration)) {
                "${note.id}: $old has no spelling on $step in $to"
            }
            note.copy(spelling = spelling)
        },
    )
}
