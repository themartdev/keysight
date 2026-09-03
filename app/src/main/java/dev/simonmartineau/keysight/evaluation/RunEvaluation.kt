package dev.simonmartineau.keysight.evaluation

/**
 * The evaluation of a run as it is committed, one segment at a time.
 *
 * [segments] holds the committed results in order, index k - 1 for segment k, and
 * [phaseBeats] the running beat phase after the last commit, which is the prior of the next.
 * A commit is final: the run's judgement is the concatenation of its segments, and the views
 * below only read them.
 */
data class RunEvaluation(
    val segments: List<EvaluationResult>,
    val phaseBeats: Double,
) {
    val committedCount: Int get() = segments.size

    /** Every committed outcome, in segment order. */
    val pitch: PitchResult get() = PitchResult(segments.flatMap { it.pitch.outcomes })

    /**
     * Every committed timing and pause, the final phase, the mean of the segment tempo ratios
     * and the worst segment continuity; null when no segment carries a rhythm judgement.
     */
    val rhythm: RhythmResult?
        get() {
            val rhythms = segments.mapNotNull { it.rhythm }
            if (rhythms.isEmpty()) return null
            val ratios = rhythms.mapNotNull { it.tempoRatio }
            return RhythmResult(
                timings = rhythms.flatMap { it.timings },
                phaseBeats = phaseBeats,
                tempoRatio = if (ratios.isEmpty()) null else ratios.average(),
                pauses = rhythms.flatMap { it.pauses },
                continuity = rhythms.maxOf { it.continuity },
            )
        }

    /**
     * The segments that went worst, at most [limit], weakest first: the ones with a wrong,
     * missing, extra, early or late note, ordered by pitch accuracy then rhythm accuracy.
     */
    fun weakestSegments(limit: Int = 3): List<Int> =
        segments.withIndex()
            .filter { (_, result) -> result.pitch.accuracy < 1.0 || result.pitch.extraCount > 0 || (result.rhythm?.accuracy ?: 1.0) < 1.0 }
            .sortedWith(compareBy({ it.value.pitch.accuracy }, { it.value.rhythm?.accuracy ?: 1.0 }, { it.index }))
            .take(limit)
            .map { it.index + 1 }

    companion object {
        val EMPTY = RunEvaluation(emptyList(), phaseBeats = 0.0)
    }
}
