package dev.simonmartineau.keysight.evaluation

import kotlinx.serialization.Serializable

enum class TimingJudgement { ON_TIME, EARLY, LATE }

/**
 * When one matched note was played against when it was due.
 *
 * [errorBeats] is measured after the player's beat phase, so it is the note's own error, not
 * the device's latency or the player's general lean.
 */
@Serializable
data class NoteTiming(
    val noteId: String,
    val expectedBeat: Double,
    val playedBeat: Double,
    val errorBeats: Double,
    val judgement: TimingJudgement,
)

/** A hesitation: the note [beforeNoteId] came [extraBeats] later than the pulse allowed. */
@Serializable
data class Pause(val beforeNoteId: String, val extraBeats: Double)

/** Whether the player kept going: the pulse held, wobbled, or was lost. */
enum class Continuity { GOOD, HESITANT, LOST }

/**
 * Phase 2 scoring: when the matched notes were played.
 *
 * Only notes the pitch alignment matched, right or wrong, have a timing; a missing note has no
 * onset and an extra note has no expected beat. [tempoRatio] is the player's tempo over the
 * configured one, 1.05 being 5% fast, or null when too few notes matched to tell.
 */
@Serializable
data class RhythmResult(
    val timings: List<NoteTiming>,
    val phaseBeats: Double,
    val tempoRatio: Double?,
    val pauses: List<Pause>,
    val continuity: Continuity,
) {
    val matchedCount: Int get() = timings.size
    val onTimeCount: Int get() = timings.count { it.judgement == TimingJudgement.ON_TIME }
    val earlyCount: Int get() = timings.count { it.judgement == TimingJudgement.EARLY }
    val lateCount: Int get() = timings.count { it.judgement == TimingJudgement.LATE }

    /** Fraction of matched notes on time, 0.0 to 1.0; 0.0 when nothing matched. */
    val accuracy: Double
        get() = if (matchedCount == 0) 0.0 else onTimeCount.toDouble() / matchedCount

    fun timingOf(noteId: String): NoteTiming? = timings.firstOrNull { it.noteId == noteId }
}
