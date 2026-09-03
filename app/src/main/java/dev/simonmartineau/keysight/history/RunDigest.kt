package dev.simonmartineau.keysight.history

import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A run as a table row or a chart reads it: what it was, how long, and its latest stored
 * judgement per bar, without the score or the raw MIDI. It is what the digests of many runs
 * are made of, cheap enough to load for every run there is. The judgements are at whatever
 * evaluator version stored them, as the difficulty window's are; a run's own page, read
 * through [HistoryReader], is where a judgement is brought up to date.
 */
data class RunDigest(
    val id: String,
    val sessionId: String,
    val startedAtEpochMillis: Long,
    val config: RunConfig,
    val keySignature: KeySignature,
    val hands: Hands,
    /** Bars performed, the bar an abort landed in included. */
    val barCount: Int,
    val evaluations: List<EvaluationResult>,
) {
    val pooled: PooledCounts get() = PooledCounts.of(evaluations)
}

/** Which hands a score is written for, read off its staves. */
fun handsOf(score: Score): Hands = when {
    score.staves.size > 1 -> Hands.BOTH
    score.staves.single().clef == Clef.BASS -> Hands.LEFT
    else -> Hands.RIGHT
}

/** [StoredRun] as a digest, for a store that holds whole runs. */
fun StoredRun.toDigest(): RunDigest = RunDigest(
    id = record.id,
    sessionId = record.sessionId,
    startedAtEpochMillis = record.startedAtEpochMillis,
    config = record.config,
    keySignature = record.score.keySignature,
    hands = handsOf(record.score),
    barCount = record.segments.size,
    evaluations = evaluations,
)

/** One session as a table row: its runs in the order played, and the counts pooled over them. */
data class SessionDigest(val session: SessionRecord, val runs: List<RunDigest>) {
    val runCount: Int get() = runs.size
    val barCount: Int get() = runs.sumOf { it.barCount }
    val pooled: PooledCounts get() = runs.fold(PooledCounts.NONE) { sum, run -> sum + run.pooled }

    /** The first run's configuration, what the session was; null for a session with no run. */
    val first: RunDigest? get() = runs.firstOrNull()
}

/** [sessions] in the order given, each with its runs from [runs] in the order played. */
fun sessionDigests(sessions: List<SessionRecord>, runs: List<RunDigest>): List<SessionDigest> {
    val bySession = runs.groupBy { it.sessionId }
    return sessions.map { session -> SessionDigest(session, bySession[session.id].orEmpty().sortedBy { it.startedAtEpochMillis }) }
}

/** Bars read on one calendar day. */
data class DayCount(val day: LocalDate, val bars: Int)

/**
 * Bars read per calendar day over the [days] ending on [today], oldest first, a day with
 * nothing counting zero. Days are the player's, in [zone], so a run just after midnight is
 * tomorrow's and daylight saving changes nothing.
 */
fun barsPerDay(runs: List<RunDigest>, days: Int, today: LocalDate, zone: ZoneId): List<DayCount> {
    require(days > 0) { "days must be positive" }
    val byDay = runs.groupBy({ Instant.ofEpochMilli(it.startedAtEpochMillis).atZone(zone).toLocalDate() }, { it.barCount })
    return (days - 1 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        DayCount(day, byDay[day]?.sum() ?: 0)
    }
}

/** The instant the window of [days] ending on [today] opens: local midnight, [days] - 1 days ago. */
fun windowStartMillis(days: Int, today: LocalDate, zone: ZoneId): Long =
    today.minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

/** The runs of [runs] that started on or after [sinceEpochMillis]. */
fun List<RunDigest>.since(sinceEpochMillis: Long): List<RunDigest> = filter { it.startedAtEpochMillis >= sinceEpochMillis }
