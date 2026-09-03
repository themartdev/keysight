package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.timing.RunTimeline

/**
 * What one run is made of: its segments, how it is presented, and the resulting score and
 * schedule. Segment k of the timeline is `segments[k - 1]`; segment 0 is the count-in.
 */
data class RunContext(
    val segments: List<Segment>,
    val config: RunConfig,
) {
    init {
        require(segments.isNotEmpty()) { "a run needs a segment" }
    }

    /** The whole run as one score, measure 0 resting. */
    val score: Score = runScore(segments.map { it.score })

    val timeline: RunTimeline = RunTimeline(
        tempoBpm = config.tempoBpm,
        timeSignature = score.timeSignature,
        segmentCount = segments.size + 1,
        metronomeThroughout = config.metronome == MetronomeMode.THROUGHOUT,
    )

    val policy: VisibilityPolicy get() = config.policy

    /** The index of the last segment when the run goes to the end. */
    val lastSegment: Int get() = segments.size

    /** The run as performed when it ended after [lastSegment]: its score and timeline cut there. */
    fun performed(lastSegment: Int): Performed {
        require(lastSegment in timeline.performedSegments) { "no performed segment $lastSegment in ${timeline.segmentCount}" }
        return Performed(score.firstMeasures(lastSegment + 1), timeline.truncatedTo(lastSegment + 1))
    }

    data class Performed(val score: Score, val timeline: RunTimeline)
}
