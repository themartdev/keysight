package dev.simonmartineau.keysight.run

import kotlinx.serialization.Serializable

/** Whether the metronome stops after the count-in or keeps the pulse through the performance. */
@Serializable
enum class MetronomeMode { COUNT_IN_ONLY, THROUGHOUT }

/** The visibility presets of the plan, each a [VisibilityPolicy] the player can name. */
@Serializable
enum class VisibilityMode(val label: String) {
    /** Read it, then play it from memory: notes show for the lookahead and vanish as the bar starts. */
    FLASH("Flash"),

    /** Everything is visible except the bar being played. */
    READ_AHEAD("Read ahead"),

    /** Plain sight reading: the notes stay. */
    OPEN_SCORE("Open score"),
}

/**
 * How a run is presented, independent of what the player is reading.
 *
 * Exposure difficulty and musical difficulty are separate variables on purpose: the mode and
 * the lookahead move here, the key and the staves move in the content settings, and the app
 * needs to move one without moving the other. [lookaheadBeats] is kept whatever the [mode] so
 * that switching back to Flash restores the ladder step the player was on.
 *
 * The count-in is always one segment, a measure of the score's meter, so the configuration
 * carries no count-in of its own. [segmentCount] is the number of performed segments, or null
 * for an open-ended run that keeps going until the player stops.
 */
@Serializable
data class RunConfig(
    val tempoBpm: Double,
    val metronome: MetronomeMode,
    val mode: VisibilityMode,
    val lookaheadBeats: Double,
    val segmentCount: Int?,
) {
    init {
        require(tempoBpm > 0.0) { "tempoBpm must be positive" }
        require(lookaheadBeats > 0.0) { "lookaheadBeats must be positive" }
        require(segmentCount == null || segmentCount > 0) { "segmentCount must be positive or open-ended" }
    }

    val isOpenEnded: Boolean get() = segmentCount == null

    val policy: VisibilityPolicy
        get() = when (mode) {
            VisibilityMode.FLASH -> VisibilityPolicy.flash(lookaheadBeats)
            VisibilityMode.READ_AHEAD -> VisibilityPolicy.READ_AHEAD
            VisibilityMode.OPEN_SCORE -> VisibilityPolicy.OPEN_SCORE
        }

    companion object {
        /**
         * The easiest exposure: Flash with a whole measure of lookahead, and a metronome that
         * establishes the pulse then gets out of the way.
         */
        val DEFAULT = RunConfig(
            tempoBpm = 72.0,
            metronome = MetronomeMode.COUNT_IN_ONLY,
            mode = VisibilityMode.FLASH,
            lookaheadBeats = 4.0,
            segmentCount = 8,
        )

        /** The lookahead ladder the difficulty controller walks between runs, easiest first. */
        val LOOKAHEAD_LADDER_BEATS = listOf(4.0, 3.0, 2.0, 1.5, 1.0, 0.75, 0.5, 0.25)
    }
}
