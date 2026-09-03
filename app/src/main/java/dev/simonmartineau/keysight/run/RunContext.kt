package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.timing.RunTimeline

/**
 * What one run is made of: its segments, how it is presented, and the resulting score and
 * schedule. Segment k of the timeline is `segments[k - 1]`; segment 0 is the count-in.
 *
 * An open-ended run's segments are the ones known so far; [extended] adds the next ones as
 * the run goes on, and the score and timeline grow with them. [seed] is the run seed every
 * generated segment's seed derives from, null for a run of bundled content.
 */
data class RunContext(
    val segments: List<Segment>,
    val config: RunConfig,
    val seed: Long? = null,
) {
    init {
        require(segments.isNotEmpty()) { "a run needs a segment" }
        config.segmentCount?.let { require(segments.size == it) { "${segments.size} segments for a run of $it" } }
    }

    /** The whole run as one score, measure 0 resting. */
    val score: Score = runScore(segments.map { it.score })

    val timeline: RunTimeline = RunTimeline(
        tempoBpm = config.tempoBpm,
        timeSignature = score.timeSignature,
        segmentCount = segments.size + 1,
        metronomeThroughout = config.metronome == MetronomeMode.THROUGHOUT,
        openEnded = config.isOpenEnded,
    )

    val policy: VisibilityPolicy get() = config.policy

    /** The index of the last known segment: where the run ends unless it is open-ended and gets extended. */
    val lastSegment: Int get() = segments.size

    /** The same run with [more] segments after the known ones. */
    fun extended(more: List<Segment>): RunContext = if (more.isEmpty()) this else copy(segments = segments + more)

    /** The run as performed when it ended after [lastSegment]: its score and timeline cut there. */
    fun performed(lastSegment: Int): Performed {
        require(lastSegment in timeline.performedSegments) { "no performed segment $lastSegment in ${timeline.segmentCount}" }
        return Performed(score.firstMeasures(lastSegment + 1), timeline.truncatedTo(lastSegment + 1), segments.take(lastSegment))
    }

    data class Performed(val score: Score, val timeline: RunTimeline, val segments: List<Segment>)
}
