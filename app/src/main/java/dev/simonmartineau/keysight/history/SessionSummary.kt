package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.difficulty.LevelChange
import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.evaluation.RunEvaluation
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.SegmentOrigin
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.lookaheadLabel

/** How many of a session's bars the summary singles out. */
const val WEAKEST_BARS = 5

/**
 * Where the player stood at one bar: the lookahead, when the run was Flash, and the musical
 * level, when the bar was generated. Both null for a bundled bar in an open score.
 */
data class SessionLevel(val lookaheadBeats: Double?, val musical: MusicalLevel?) {

    /** "4 beats ahead, up to thirds, five notes, quarter notes, no rests, no accidentals.", or null when nothing is known. */
    val description: String?
        get() {
            val parts = listOfNotNull(lookaheadBeats?.let(::lookaheadLabel), musical?.description?.removeSuffix(".")?.replaceFirstChar { it.lowercase() })
            return parts.takeIf { it.isNotEmpty() }?.joinToString(", ", postfix = ".")?.replaceFirstChar { it.uppercase() }
        }

    companion object {
        fun of(config: RunConfig, segment: Segment): SessionLevel = SessionLevel(
            lookaheadBeats = config.lookaheadBeats.takeIf { config.mode == VisibilityMode.FLASH },
            musical = segment.musicalLevel,
        )
    }
}

/** The level a generated segment was read at; null for bundled content, which was read at no level. */
val Segment.musicalLevel: MusicalLevel? get() = (origin as? SegmentOrigin.Generated)?.let { MusicalLevel.of(it.config) }

/**
 * The bars at which the level changed within a run, as pairs of the bar the new level
 * reached and the change, read off the configurations the bars were generated from.
 */
fun levelChangesWithin(segments: List<Segment>): List<Pair<Int, LevelChange>> =
    segments.map { it.musicalLevel }.zipWithNext().mapIndexedNotNull { index, (before, after) ->
        if (before == null || after == null) null else before.changeTo(after)?.let { (index + 2) to it }
    }

/**
 * One change of level in a session, at [bar] of the run numbered [runIndex] within the
 * session, or between runs when [bar] is null. [what] names the rung reached the way the
 * run summary announced it, "up to fourths" or "2 beats ahead".
 */
data class SessionMove(val runIndex: Int, val runId: String, val bar: Int?, val harder: Boolean, val what: String) {

    /** "Harder from bar 13 of run 2: up to fourths", "Easier from run 3: 4 beats ahead". */
    val line: String
        get() = (if (harder) "Harder" else "Easier") + " from " + (bar?.let { "bar $it of " } ?: "") + "run $runIndex: $what"
}

/** One bar of one run of the session, with its judgement. */
data class SessionBar(val runIndex: Int, val runId: String, val bar: Int, val result: EvaluationResult) {
    val label: String get() = "Run $runIndex, bar $bar"
}

/**
 * One session pooled: the runs, the bars, the two accuracies the score line shows pooled over
 * every judged bar, the level at the first and the last bar with every change between, and
 * the weakest bars. Every number is the same one the run rows show: a run's pitch accuracy is
 * its correct notes over its expected notes, and the session's is the sum over the sum.
 */
data class SessionSummary(
    val session: SessionRecord,
    val runs: List<StoredRun>,
    val correctCount: Int,
    val expectedCount: Int,
    val onTimeCount: Int,
    val matchedCount: Int,
    val start: SessionLevel?,
    val end: SessionLevel?,
    val moves: List<SessionMove>,
    val weakestBars: List<SessionBar>,
) {
    val runCount: Int get() = runs.size

    /** Bars played, the bar an abort landed in included: the count each run's header shows. */
    val barCount: Int get() = runs.sumOf { it.record.segments.size }

    /** Null when no bar was judged. */
    val pitchAccuracy: Double? get() = if (expectedCount == 0) null else correctCount.toDouble() / expectedCount

    /** Null when no note matched. */
    val rhythmAccuracy: Double? get() = if (matchedCount == 0) null else onTimeCount.toDouble() / matchedCount
}

fun summarise(session: SessionRecord, runs: List<StoredRun>): SessionSummary {
    val results = runs.flatMap { it.evaluations }
    val moves = buildList {
        runs.forEachIndexed { index, run ->
            val runIndex = index + 1
            val record = run.record
            if (index > 0) {
                val previous = runs[index - 1].record
                val before = SessionLevel.of(previous.config, previous.segments.last())
                val after = SessionLevel.of(record.config, record.segments.first())
                if (before.lookaheadBeats != null && after.lookaheadBeats != null && before.lookaheadBeats != after.lookaheadBeats) {
                    add(SessionMove(runIndex, record.id, bar = null, harder = after.lookaheadBeats < before.lookaheadBeats, what = lookaheadLabel(after.lookaheadBeats)))
                }
                if (before.musical != null && after.musical != null) {
                    before.musical.changeTo(after.musical)?.let { add(SessionMove(runIndex, record.id, bar = null, harder = it.harder, what = it.what)) }
                }
            }
            levelChangesWithin(record.segments).forEach { (bar, change) ->
                add(SessionMove(runIndex, record.id, bar, change.harder, change.what))
            }
        }
    }
    val weakest = runs.flatMapIndexed { index, run ->
        run.evaluations.mapIndexedNotNull { bar, result -> if (result.hasFault) SessionBar(index + 1, run.record.id, bar + 1, result) else null }
    }.sortedWith(compareBy<SessionBar, EvaluationResult>(RunEvaluation.WEAKNESS) { it.result }.thenBy { it.runIndex }.thenBy { it.bar }).take(WEAKEST_BARS)
    return SessionSummary(
        session = session,
        runs = runs,
        correctCount = results.sumOf { it.pitch.correctCount },
        expectedCount = results.sumOf { it.pitch.expectedCount },
        onTimeCount = results.sumOf { it.rhythm?.onTimeCount ?: 0 },
        matchedCount = results.sumOf { it.rhythm?.matchedCount ?: 0 },
        start = runs.firstOrNull()?.record?.let { SessionLevel.of(it.config, it.segments.first()) },
        end = runs.lastOrNull()?.record?.let { SessionLevel.of(it.config, it.segments.last()) },
        moves = moves,
        weakestBars = weakest,
    )
}
