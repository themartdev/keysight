package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.timing.RunTimeline

/**
 * Scores a run from its score and its raw MIDI, one segment at a time.
 *
 * The evaluator is deterministic: it has no clock and no state of its own, so replaying the
 * commits of a stored run gives the results the run showed live, and [EVALUATOR_VERSION]
 * changes whenever the judgement would.
 *
 * A segment k is committed once its capture tail has passed, from a window of three segments:
 * the notes of k - 1 that were committed missing, which can still absorb a late note; k
 * itself; and k + 1, so that an anticipated downbeat is that downbeat and not an extra of k.
 * The played notes of the window are those from the start of k - 1 to the end of k's tail
 * that no earlier commit has consumed. Alignment runs over the window at the previous phase,
 * the residuals of k's matched notes step the phase, and the window is aligned again at the
 * new phase; only k's outcomes are committed. Every played note ends up with exactly one
 * committed outcome, every expected note with exactly one.
 */
object PerformanceEvaluator {

    /**
     * Version 1: pitch correctness by order-based alignment.
     * Version 2: alignment weighs onset time, and rhythm is scored after beat-phase estimation.
     * Version 3: the run's beat line. Beat 0 is the first count-in click and the score's tick 0,
     * so every beat in a result is a run beat rather than one counted from the first notated beat.
     * Version 4: segments are committed one at a time from a trailing window, the phase runs
     * from segment to segment with a bounded step, and a note played after its segment was
     * committed is [NoteOutcome.TooLate] rather than an extra.
     */
    const val EVALUATOR_VERSION = 4

    /** How far ahead of the first notated beat a note may land and still count as the first note. */
    const val EARLY_GRACE_BEATS = 0.5

    /**
     * The whole run, committed segment by segment up to the timeline's last segment: what
     * history re-evaluation runs, and what a live run's commits add up to.
     */
    fun evaluate(score: Score, timeline: RunTimeline, startedAtNanos: Long, events: List<MidiEvent>): RunEvaluation {
        val lastSegment = timeline.segmentCount - 1
        return timeline.performedSegments.fold(RunEvaluation.EMPTY) { evaluation, segment ->
            commit(evaluation, score, timeline, startedAtNanos, events, segment, lastSegment)
        }
    }

    /**
     * Commits [segment], the next one after [evaluation]'s, in a run that ends after
     * [lastSegment]. Only the [events] that had arrived by the end of the segment's capture
     * tail are seen, so a commit made live and one replayed from history see the same bytes.
     */
    fun commit(
        evaluation: RunEvaluation,
        score: Score,
        timeline: RunTimeline,
        startedAtNanos: Long,
        events: List<MidiEvent>,
        segment: Int,
        lastSegment: Int,
    ): RunEvaluation {
        require(lastSegment in timeline.performedSegments) { "no performed segment $lastSegment in ${timeline.segmentCount}" }
        require(segment == evaluation.committedCount + 1) { "segment $segment cannot be committed after ${evaluation.committedCount}" }
        require(segment <= lastSegment) { "segment $segment is past the run's last, $lastSegment" }
        require(lastSegment < score.measureCount) { "the score has ${score.measureCount} measures, not enough for segment $lastSegment" }

        val commitNanos = startedAtNanos + timeline.captureEndNanosAfter(segment)
        val seen = events.filter { it.timestampNanos <= commitNanos }
        val windowStart = if (segment == 1) Double.NEGATIVE_INFINITY else timeline.segmentStartBeat(segment - 1)
        val windowEnd = timeline.captureEndBeatAfter(segment)
        val consumed = evaluation.segments.takeLast(2).flatMap { it.pitch.outcomes }.mapNotNull { it.played?.key }.toSet()
        val played = PlayedNotes.extract(seen, timeline, startedAtNanos, EARLY_GRACE_BEATS)
            .filter { it.onsetBeat >= windowStart && it.onsetBeat <= windowEnd && it.key !in consumed }

        val ghosts = evaluation.segments.getOrNull(segment - 2)?.pitch?.outcomes.orEmpty()
            .filterIsInstance<NoteOutcome.Missing>().map { it.expected }
        val own = score.notesInMeasure(segment)
        val after = if (segment < lastSegment) score.notesInMeasure(segment + 1) else emptyList()
        val expected = (ghosts + own + after)
            .sortedWith(compareBy({ it.onset }, { it.pitch }, { it.voice }))
            .map { ExpectedNote(it, timeline.beatsOf(it.onset)) }
        val expectedBeats = expected.associate { it.note.id to it.beat }
        val ownIds = own.map { it.id }.toSet()
        val ghostIds = ghosts.map { it.id }.toSet()

        fun ownOutcomes(outcomes: List<NoteOutcome>) = outcomes.filter { it.expectedId in ownIds }

        val firstPass = NoteAlignment.align(expected, played, evaluation.phaseBeats)
        val residuals = BeatPhase.deviations(ownOutcomes(firstPass), expectedBeats).map { it - evaluation.phaseBeats }
        val phase = nextPhase(evaluation, residuals, timeline.metronomeThroughout)
        val aligned = NoteAlignment.align(expected, played, phase)

        val segmentEnd = timeline.segmentEndBeat(segment)
        val committed = aligned.mapNotNull { outcome ->
            when (outcome) {
                is NoteOutcome.Correct -> outcome.takeIf { it.expected.id in ownIds } ?: outcome.expected.tooLate(outcome.played, ghostIds)
                is NoteOutcome.WrongPitch -> outcome.takeIf { it.expected.id in ownIds } ?: outcome.expected.tooLate(outcome.played, ghostIds)
                is NoteOutcome.Missing -> outcome.takeIf { it.expected.id in ownIds }
                is NoteOutcome.Extra -> outcome.takeIf { segment == lastSegment || it.played.onsetBeat < segmentEnd }
                is NoteOutcome.TooLate -> error("the aligner does not judge lateness")
            }
        }
        val result = EvaluationResult(
            evaluatorVersion = EVALUATOR_VERSION,
            pitch = PitchResult(committed),
            rhythm = RhythmAnalysis.analyse(committed, expectedBeats, phase),
        )
        return RunEvaluation(evaluation.segments + result, phase)
    }

    /**
     * The first commit has no prior and may take the whole [BeatPhase.MAX_PHASE_BEATS]; later
     * ones step from the previous phase. Under a click the phase stays within the latency
     * bound; without one it follows the player.
     */
    private fun nextPhase(evaluation: RunEvaluation, residuals: List<Double>, metronomeThroughout: Boolean): Double {
        val maxStep = when {
            evaluation.committedCount == 0 -> BeatPhase.MAX_PHASE_BEATS
            metronomeThroughout -> BeatPhase.MAX_STEP_WITH_CLICK_BEATS
            else -> BeatPhase.MAX_STEP_WITHOUT_CLICK_BEATS
        }
        val stepped = BeatPhase.step(evaluation.phaseBeats, residuals, maxStep)
        return if (metronomeThroughout) stepped.coerceIn(-BeatPhase.MAX_PHASE_BEATS, BeatPhase.MAX_PHASE_BEATS) else stepped
    }

    private val NoteOutcome.expectedId: String?
        get() = when (this) {
            is NoteOutcome.Correct -> expected.id
            is NoteOutcome.WrongPitch -> expected.id
            is NoteOutcome.Missing -> expected.id
            is NoteOutcome.TooLate -> expected.id
            is NoteOutcome.Extra -> null
        }

    /** A note matched to a ghost of the previous segment was played too late to count there, and is not an extra here. */
    private fun ScoreNote.tooLate(played: PlayedNote, ghostIds: Set<String>): NoteOutcome? =
        if (id in ghostIds) NoteOutcome.TooLate(this, played) else null

    /** What identifies a played note across commits: when it was struck and which key. */
    private val PlayedNote.key: Pair<Long, Pitch> get() = onsetNanos to pitch
}
