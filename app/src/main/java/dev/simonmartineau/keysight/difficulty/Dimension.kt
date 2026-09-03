package dev.simonmartineau.keysight.difficulty

/**
 * One axis of difficulty the controller may move, in the order of section 8 of the plan,
 * easiest to move first: the exposure, then the music, rests after the note values since a
 * rest is read against the note values already learnt, accidentals after rests since a
 * chromatic neighbour is read against the key and the steps already learnt. The controller
 * walks this order; a new dimension is a new entry with its rung ladder in [Ladders] and its
 * step in [DifficultyController.step], nothing else.
 *
 * Mode, staves and key are the player's settings for now and are not walked; they belong
 * between [LOOKAHEAD] and [INTERVAL] when they are.
 */
enum class Dimension(
    /** Whether the dimension may move while a run is going on; the exposure of a run is stable. */
    val movesWithinRun: Boolean,
) {
    /** How long before a bar starts its notes appear, in Flash. Between runs only. */
    LOOKAHEAD(movesWithinRun = false),

    /** The largest step between consecutive melody notes. */
    INTERVAL(movesWithinRun = true),

    /** How many notes each hand reads, both hands moving together. */
    RANGE(movesWithinRun = true),

    /** The note values the generator writes. */
    RHYTHM(movesWithinRun = true),

    /** Whether the generator leaves rests between the notes of a bar. */
    RESTS(movesWithinRun = true),

    /** Whether one note of a bar may be a chromatic neighbour, written with an accidental. */
    ACCIDENTALS(movesWithinRun = true),
}

/** Up is harder, down is easier. */
enum class Direction(val sign: Int) {
    UP(1),
    DOWN(-1),
}

/** One step of one dimension: what a decision changed. */
data class Move(val dimension: Dimension, val direction: Direction)
