package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.evaluation.EvaluationResult

/**
 * The counts behind every accuracy the app shows, pooled: correct notes over expected notes
 * is the pitch accuracy, on-time notes over matched notes the rhythm accuracy, and a pool of
 * judgements is the sum of each count over the sum of the other, never a mean of percentages.
 * This is the one place that arithmetic lives; a session, a week and a run row all read it.
 */
data class PooledCounts(
    val correctCount: Int,
    val expectedCount: Int,
    val onTimeCount: Int,
    val matchedCount: Int,
) {
    /** Null when no note was expected, so nothing was judged. */
    val pitchAccuracy: Double? get() = if (expectedCount == 0) null else correctCount.toDouble() / expectedCount

    /** Null when no note matched, so no timing was judged. */
    val rhythmAccuracy: Double? get() = if (matchedCount == 0) null else onTimeCount.toDouble() / matchedCount

    operator fun plus(other: PooledCounts) = PooledCounts(
        correctCount + other.correctCount,
        expectedCount + other.expectedCount,
        onTimeCount + other.onTimeCount,
        matchedCount + other.matchedCount,
    )

    companion object {
        val NONE = PooledCounts(0, 0, 0, 0)

        fun of(results: List<EvaluationResult>): PooledCounts = PooledCounts(
            correctCount = results.sumOf { it.pitch.correctCount },
            expectedCount = results.sumOf { it.pitch.expectedCount },
            onTimeCount = results.sumOf { it.rhythm?.onTimeCount ?: 0 },
            matchedCount = results.sumOf { it.rhythm?.matchedCount ?: 0 },
        )
    }
}
