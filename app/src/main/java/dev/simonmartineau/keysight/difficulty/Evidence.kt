package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.SegmentOrigin
import dev.simonmartineau.keysight.run.VisibilityPolicy
import kotlin.math.min

/** What in a run's presentation bears on difficulty: its tempo, its click and its visibility policy, not its length. */
data class Exposure(val tempoBpm: Double, val metronome: MetronomeMode, val policy: VisibilityPolicy)

val RunConfig.exposure: Exposure get() = Exposure(tempoBpm, metronome, policy)

/**
 * One committed segment as the controller's window sees it: the state it was played at and
 * the two counts the score line is made of. [config] is null for a segment of bundled
 * content, which was read at no level and never matches one.
 */
data class SegmentEvidence(
    val exposure: Exposure,
    val config: ExerciseConfig?,
    val correctCount: Int,
    val expectedCount: Int,
    val onTimeCount: Int,
    val matchedCount: Int,
)

fun evidenceOf(runConfig: RunConfig, segment: Segment, result: EvaluationResult): SegmentEvidence =
    evidenceOf(runConfig, (segment.origin as? SegmentOrigin.Generated)?.config, result)

/** The one place a judgement becomes evidence, so a live commit and a stored row agree. */
fun evidenceOf(runConfig: RunConfig, config: ExerciseConfig?, result: EvaluationResult): SegmentEvidence = SegmentEvidence(
    exposure = runConfig.exposure,
    config = config,
    correctCount = result.pitch.correctCount,
    expectedCount = result.pitch.expectedCount,
    onTimeCount = result.rhythm?.onTimeCount ?: 0,
    matchedCount = result.rhythm?.matchedCount ?: 0,
)

/**
 * The window a decision reads: the most recent segments of [evidence] (oldest first) that
 * were played at exactly the current state, stopping at the first that was not, at most
 * [limit], oldest first. Any change of state, the controller's or the player's, therefore
 * empties the window, so every move is backed by evidence gathered at the state it moves from.
 */
fun trailingWindow(evidence: List<SegmentEvidence>, exposure: Exposure, config: ExerciseConfig, limit: Int): List<SegmentEvidence> =
    evidence.asReversed().asSequence()
        .takeWhile { it.exposure == exposure && it.config == config }
        .take(limit)
        .toList()
        .asReversed()

/**
 * The window's success: the smaller of its pooled pitch accuracy and its pooled rhythm
 * accuracy, the same two numbers the score line shows. Pitch alone when nothing matched.
 */
fun successOf(window: List<SegmentEvidence>): Double {
    val expected = window.sumOf { it.expectedCount }
    if (expected == 0) return 0.0
    val pitch = window.sumOf { it.correctCount }.toDouble() / expected
    val matched = window.sumOf { it.matchedCount }
    if (matched == 0) return pitch
    return min(pitch, window.sumOf { it.onTimeCount }.toDouble() / matched)
}
