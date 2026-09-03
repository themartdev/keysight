package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.notation.Mask
import dev.simonmartineau.keysight.notation.TickRange
import dev.simonmartineau.keysight.timing.RunTimeline

/**
 * The mask of a run at [beat]: the policy applied to every performed segment, as score time.
 *
 * Segment 0 is never hidden: it is a measure of rest and there is nothing in it to memorise.
 * Segments after [lastSegment], those a stopped run will not reach, are hidden so the page
 * shows what is still to be played and nothing beyond. This is the function the practice
 * screen evaluates on every frame; it is pure, so the mask at any beat can be checked directly.
 */
fun runMask(timeline: RunTimeline, policy: VisibilityPolicy, beat: Double, lastSegment: Int = timeline.segmentCount - 1): Mask {
    require(lastSegment in timeline.performedSegments) { "no performed segment $lastSegment in ${timeline.segmentCount}" }
    val hidden = timeline.performedSegments.filter { segment ->
        segment > lastSegment || !policy.isVisible(beat, timeline.segmentStartBeat(segment), timeline.segmentEndBeat(segment))
    }.map { segment ->
        TickRange(timeline.ticksAt(timeline.segmentStartBeat(segment)), timeline.ticksAt(timeline.segmentEndBeat(segment)))
    }
    return Mask(hidden)
}

/** The mask before a run starts: only what an unbounded lookahead shows. */
fun runMaskBeforeStart(timeline: RunTimeline, policy: VisibilityPolicy): Mask =
    runMask(timeline, policy, beat = Double.NEGATIVE_INFINITY)
