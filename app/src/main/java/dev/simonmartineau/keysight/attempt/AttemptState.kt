package dev.simonmartineau.keysight.attempt

/**
 * The lifecycle of one flash attempt.
 *
 * The states are phases of a single clock, not independent timers. Two of the boundaries are
 * exact coincidences rather than sequential steps: the count-in is already running when the
 * notation appears, and the notation disappears on the same instant the performance begins.
 */
enum class AttemptState {
    /** An exercise is loaded and the player has not started the count-in. */
    READY,

    /** The metronome is running and the notation is not visible yet. */
    COUNT_IN,

    /** Still counting in, with the notation on screen. */
    PREVIEW_VISIBLE,

    /** The notation has been hidden. Reached at the same instant as [PERFORMING]. */
    PREVIEW_HIDDEN,

    /** MIDI is being captured against the attempt clock. */
    PERFORMING,

    /** Capture is closed and the evaluator is running. */
    EVALUATING,

    /** The result is on screen, waiting for the player to continue. */
    RESULT,

    /** The attempt was abandoned - cancelled, backgrounded, or the keyboard went away. */
    ABORTED,
    ;

    fun canTransitionTo(next: AttemptState): Boolean = next in legalSuccessors

    private val legalSuccessors: Set<AttemptState>
        get() = when (this) {
            READY -> setOf(COUNT_IN, PREVIEW_VISIBLE, ABORTED)
            COUNT_IN -> setOf(PREVIEW_VISIBLE, ABORTED)
            PREVIEW_VISIBLE -> setOf(PREVIEW_HIDDEN, ABORTED)
            PREVIEW_HIDDEN -> setOf(PERFORMING, ABORTED)
            PERFORMING -> setOf(EVALUATING, ABORTED)
            EVALUATING -> setOf(RESULT, ABORTED)
            RESULT -> setOf(READY)
            ABORTED -> setOf(READY)
        }
}
