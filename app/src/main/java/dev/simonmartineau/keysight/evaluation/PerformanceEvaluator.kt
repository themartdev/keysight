package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.timing.RunTimeline

/**
 * Scores one run from its score and its raw MIDI.
 *
 * The evaluator is deterministic: it has no clock and no state, so the same stored run always
 * yields the same result, and [EVALUATOR_VERSION] changes whenever the judgement would.
 *
 * Alignment and beat phase depend on each other, so they run in two passes: align at phase 0,
 * estimate the phase from the matched notes, align again with it, and take the phase of that
 * alignment as final. Rhythm is then judged against the final phase.
 */
object PerformanceEvaluator {

    /**
     * Version 1: pitch correctness by order-based alignment.
     * Version 2: alignment weighs onset time, and rhythm is scored after beat-phase estimation.
     * Version 3: the run's beat line. Beat 0 is the first count-in click and the score's tick 0,
     * so every beat in a result is a run beat rather than one counted from the first notated beat.
     */
    const val EVALUATOR_VERSION = 3

    /** How far ahead of the first notated beat a note may land and still count as the first note. */
    const val EARLY_GRACE_BEATS = 0.5

    fun evaluate(
        score: Score,
        events: List<MidiEvent>,
        timeline: RunTimeline,
        startedAtNanos: Long,
    ): EvaluationResult {
        val played = PlayedNotes.extract(events, timeline, startedAtNanos, EARLY_GRACE_BEATS)
        val expected = score.notesInPerformanceOrder.map { ExpectedNote(it, timeline.beatsOf(it.onset)) }
        val expectedBeats = expected.associate { it.note.id to it.beat }

        val firstPass = NoteAlignment.align(expected, played, phaseBeats = 0.0)
        val firstPhase = BeatPhase.estimate(BeatPhase.deviations(firstPass, expectedBeats))
        val outcomes = NoteAlignment.align(expected, played, firstPhase)
        val phase = BeatPhase.estimate(BeatPhase.deviations(outcomes, expectedBeats))

        return EvaluationResult(
            evaluatorVersion = EVALUATOR_VERSION,
            pitch = PitchResult(outcomes),
            rhythm = RhythmAnalysis.analyse(outcomes, expectedBeats, phase),
        )
    }
}
