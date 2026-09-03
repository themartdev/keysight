package dev.simonmartineau.keysight.run

/**
 * Where an open-ended run's next segments come from.
 *
 * The controller keeps [SEGMENTS_AHEAD] segments beyond the one being performed, topping up
 * [SEGMENT_BATCH] at a time, so the page always has its next systems laid out and the last,
 * partially filled system is never one the player is reading.
 */
fun interface SegmentSource {

    /** The [count] segments that follow [previous]. */
    fun next(count: Int, previous: Segment): List<Segment>

    companion object {
        /** Enough for the two-system window and the lookahead at up to six measures a system. */
        const val SEGMENTS_AHEAD = 12

        const val SEGMENT_BATCH = 8
    }
}
