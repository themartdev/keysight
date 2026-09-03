package dev.simonmartineau.keysight.score

/**
 * One notated note in an exercise.
 *
 * This is the canonical representation the evaluator works against. It is deliberately
 * independent of how the note is drawn: nothing downstream of here should need to inspect
 * MusicXML or SVG.
 *
 * @param onsetBeat when the note starts, in beats from the start of the exercise (0-based).
 * @param durationBeats notated length in beats, not the length the player actually held.
 * @param chordId shared by every note sounded together as one chord, null for single notes.
 */
data class ScoreNote(
    val id: String,
    val pitch: Pitch,
    val onsetBeat: Double,
    val durationBeats: Double,
    val voice: Int = 0,
    val hand: Hand = Hand.RIGHT,
    val chordId: String? = null,
) {
    init {
        require(onsetBeat >= 0.0) { "onsetBeat must not be negative" }
        require(durationBeats > 0.0) { "durationBeats must be positive" }
    }

    val endBeat: Double get() = onsetBeat + durationBeats
}
