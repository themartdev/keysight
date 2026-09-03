package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/**
 * One notated note.
 *
 * This is the canonical representation the evaluator works against, and it is independent of
 * how the note is drawn. Notes that share a [voice] and an [onset] form a chord; there is no
 * separate chord identifier that could disagree with the onsets.
 *
 * @param onset where the note starts, in ticks from the start of the score.
 * @param duration notated length, not the length the player actually held.
 */
@Serializable
data class ScoreNote(
    val id: String,
    val spelling: SpelledPitch,
    val onset: Ticks,
    val duration: Ticks,
    val voice: Int = 0,
    val hand: Hand = Hand.RIGHT,
) {
    init {
        require(id.isNotBlank()) { "a note needs an id" }
        require(duration > Ticks.ZERO) { "duration must be positive" }
        require(voice >= 0) { "voice must not be negative" }
    }

    val pitch: Pitch get() = spelling.pitch

    val end: Ticks get() = onset + duration
}
