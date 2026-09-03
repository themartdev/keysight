package dev.simonmartineau.keysight.score

/**
 * The same passage written in another major key.
 *
 * Every letter moves by the interval between the two tonics, and every accidental keeps its
 * meaning relative to the key: a raised fourth in C, F sharp, is a raised fourth in G, C
 * sharp. The passage moves by the smaller of the two possible intervals, at most a tritone
 * either way, so it stays in the register it was written in; a tritone goes up towards the
 * sharper key and down towards the flatter one. Going from any key back to the first therefore
 * lands where it started, with one exception: an accidental is never written double. Where
 * the key already sharpens or flattens the letter an altered note lands on, the note is
 * respelled on the neighbouring letter, up from a double sharp and down from a double flat,
 * with the plain accidental that sounds the same. A raised sixth in C, A sharp, is not F
 * double sharp in A but G natural, which comes back to C as B flat; the pitch always comes
 * back, the spelling only when no double was avoided.
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
            val pitch = old.pitch.transposedBy(semitoneShift)
            val spelling = if (alteration in PLAIN_ALTERATIONS) {
                SpelledPitch.of(pitch, step, alteration)
            } else {
                val neighbour = Step.entries[Math.floorMod(step.ordinal + Integer.signum(alteration), Step.entries.size)]
                PLAIN_ALTERATIONS.firstNotNullOfOrNull { SpelledPitch.of(pitch, neighbour, it) }
            }
            note.copy(spelling = checkNotNull(spelling) { "${note.id}: $old has no plain spelling on $step in $to" })
        },
    )
}

/** A sharp, a natural or a flat: what an accidental on the page may be. */
private val PLAIN_ALTERATIONS = -1..1
