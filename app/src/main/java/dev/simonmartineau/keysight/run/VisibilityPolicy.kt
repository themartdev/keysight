package dev.simonmartineau.keysight.run

/**
 * Whether a segment's notes are drawn, decided per segment and per beat.
 *
 * The staff, clefs, signatures and barlines are always drawn; a policy only says when a
 * segment's notes are on the page. All three parameters are on the beat line relative to the
 * segment being decided, so the same policy serves every tempo and meter.
 *
 * @param lookaheadBeats how long before a segment starts its notes appear; null means always.
 * @param hideWhilePlaying whether the notes are hidden from the segment's first beat to its last.
 * @param showAfter whether the notes come back once the segment is over.
 */
data class VisibilityPolicy(
    val lookaheadBeats: Double?,
    val hideWhilePlaying: Boolean,
    val showAfter: Boolean,
) {
    init {
        require(lookaheadBeats == null || lookaheadBeats > 0.0) { "lookaheadBeats must be positive or unbounded" }
    }

    /**
     * Whether a segment spanning [segmentStartBeat] until [segmentEndBeat] has its notes drawn
     * at [beat]. Boundaries belong to the span they open: on the very beat the segment starts
     * its notes are already governed by [hideWhilePlaying], which is how they disappear exactly
     * when the performance of that bar begins.
     */
    fun isVisible(beat: Double, segmentStartBeat: Double, segmentEndBeat: Double): Boolean = when {
        beat < segmentStartBeat -> lookaheadBeats == null || beat >= segmentStartBeat - lookaheadBeats
        beat < segmentEndBeat -> !hideWhilePlaying
        else -> showAfter
    }

    companion object {
        /** Retention: the notes show for [lookaheadBeats], vanish as the bar starts and return after it. */
        fun flash(lookaheadBeats: Double) = VisibilityPolicy(lookaheadBeats, hideWhilePlaying = true, showAfter = true)

        /** Reading ahead: everything is visible except the bar being played. */
        val READ_AHEAD = VisibilityPolicy(lookaheadBeats = null, hideWhilePlaying = true, showAfter = true)

        /** Plain sight reading, the baseline and the warm-up. */
        val OPEN_SCORE = VisibilityPolicy(lookaheadBeats = null, hideWhilePlaying = false, showAfter = true)
    }
}
