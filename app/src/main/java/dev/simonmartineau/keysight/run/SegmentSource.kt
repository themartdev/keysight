package dev.simonmartineau.keysight.run

/**
 * Where a run's segments come from, by index.
 *
 * The controller keeps [SEGMENTS_AHEAD] segments beyond the one being performed in an
 * open-ended run, topping up [SEGMENT_BATCH] at a time, so the page always has its next
 * systems laid out and the last, partially filled system is never one the player is reading.
 * A source is addressed by segment index so that the same source serves a run's first
 * segments and its extensions alike.
 */
fun interface SegmentSource {

    /** Segments [firstIndex] to `firstIndex + count - 1`, segment indices counting from 1 after the count-in. */
    fun next(count: Int, firstIndex: Int): List<Segment>

    companion object {
        /** Enough for the two-system window and the lookahead at up to six measures a system. */
        const val SEGMENTS_AHEAD = 12

        const val SEGMENT_BATCH = 8
    }
}
