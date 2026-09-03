package dev.simonmartineau.keysight.ui.practice

import dev.simonmartineau.keysight.difficulty.Decision
import dev.simonmartineau.keysight.difficulty.Dimension
import dev.simonmartineau.keysight.difficulty.Direction
import dev.simonmartineau.keysight.evaluation.Continuity
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.evaluation.RhythmResult
import dev.simonmartineau.keysight.evaluation.RunEvaluation
import dev.simonmartineau.keysight.history.levelChangesWithin
import dev.simonmartineau.keysight.history.musicalLevel
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunContext
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.beatsLabel
import dev.simonmartineau.keysight.run.lookaheadLabel
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.Score
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The words of the summary, kept pure so they can be tested. Feedback stays compact: what the
 * run was, one score line, then only the remarks that earned their place.
 */

/**
 * The lean, in beats, from which a remark about being ahead of or behind the beat is worth making.
 *
 * The lean is the frame every timing mark is measured in, so it is only mentioned when there is
 * at least one early or late mark for it to explain. On its own it cannot be told apart from the
 * device's audio latency, and a passage with every note on its own pulse earns no remark.
 */
const val NOTABLE_PHASE_BEATS = 0.1

/** The tempo drift from which a remark is worth making. */
const val NOTABLE_TEMPO_DRIFT = 0.05

/** Why a run stopped early, for the player. */
fun abortMessage(reason: AbortReason): String = when (reason) {
    AbortReason.CANCELLED -> "You stopped it during the count-in."
    AbortReason.MIDI_DISCONNECTED -> "The keyboard disconnected."
    AbortReason.BACKGROUNDED -> "The app went to the background."
}

fun continuityLabel(continuity: Continuity): String = when (continuity) {
    Continuity.GOOD -> "Good"
    Continuity.HESITANT -> "Hesitant"
    Continuity.LOST -> "Lost"
}

/** What the run was: `8 bars   Flash 2 beats   C major   right hand`. */
fun summaryHeader(config: RunConfig, score: Score, performedSegments: Int): String =
    listOf(barsLabel(performedSegments), modeLabel(config), score.keySignature.majorName, handsLabel(score)).joinToString("   ")

/** The mode with its lookahead when the lookahead applies: `Flash 2 beats`, `Read ahead`. */
fun modeLabel(config: RunConfig): String = when (config.mode) {
    VisibilityMode.FLASH -> "Flash ${config.lookaheadBeats.beatsLabel()} ${if (config.lookaheadBeats == 1.0) "beat" else "beats"}"
    VisibilityMode.READ_AHEAD, VisibilityMode.OPEN_SCORE -> config.mode.label
}

/** "72 bpm". */
fun tempoLabel(bpm: Double): String = "${bpm.toInt()} bpm"

fun barsLabel(bars: Int): String = if (bars == 1) "1 bar" else "$bars bars"

/** Which hands the score is for, read from its staves: a single staff is one hand, the grand staff both. */
fun handsLabel(score: Score): String = when {
    score.staves.size > 1 -> "both hands"
    score.staves.single().clef == Clef.BASS -> "left hand"
    else -> "right hand"
}

fun scoreLine(pitch: PitchResult, rhythm: RhythmResult?): String {
    val parts = mutableListOf("Pitch ${percentLabel(pitch.accuracy)}")
    if (rhythm != null) {
        parts += "Rhythm ${percentLabel(rhythm.accuracy)}"
        parts += "Continuity ${continuityLabel(rhythm.continuity)}"
    }
    return parts.joinToString("   ")
}

fun remarks(pitch: PitchResult, rhythm: RhythmResult?): List<String> {
    val lines = ArrayList<String>()
    if (pitch.extraCount > 0) lines += if (pitch.extraCount == 1) "1 extra note" else "${pitch.extraCount} extra notes"
    if (rhythm == null) return lines
    val hasTimingMarks = rhythm.earlyCount + rhythm.lateCount > 0
    if (hasTimingMarks && rhythm.phaseBeats <= -NOTABLE_PHASE_BEATS) lines += "Slightly ahead of the beat"
    if (hasTimingMarks && rhythm.phaseBeats >= NOTABLE_PHASE_BEATS) lines += "Slightly behind the beat"
    rhythm.tempoRatio?.let { ratio ->
        val drift = ratio - 1.0
        if (abs(drift) >= NOTABLE_TEMPO_DRIFT) {
            lines += "Drifted ${(abs(drift) * 100).roundToInt()}% ${if (drift > 0) "fast" else "slow"}"
        }
    }
    if (rhythm.pauses.size == 1) lines += "1 pause"
    if (rhythm.pauses.size > 1) lines += "${rhythm.pauses.size} pauses"
    return lines
}

/** `Weakest bars: 7, 12`, or null when no bar went wrong; a one-bar run has no bar to single out. */
fun weakestBarsLine(evaluation: RunEvaluation): String? {
    if (evaluation.committedCount < 2) return null
    val bars = evaluation.weakestSegments()
    if (bars.isEmpty()) return null
    return (if (bars.size == 1) "Weakest bar: " else "Weakest bars: ") + bars.joinToString(", ")
}

/**
 * The level the run is read at, "Up to thirds, five notes, quarter notes.", from its first
 * bar's configuration; null for content that was not generated.
 */
fun levelLine(context: RunContext): String? = levelLine(context.segments)

fun levelLine(segments: List<Segment>): String? = segments.first().musicalLevel?.description

/**
 * One line per bar the controller moved the level at, "Harder from bar 13: up to fourths",
 * read off the configurations the bars were generated from, so each points at a bar on the
 * page.
 */
fun levelChangeLines(segments: List<Segment>): List<String> =
    levelChangesWithin(segments).map { (bar, change) -> "${directionWord(change.harder)} from bar $bar: ${change.what}" }

/** "Harder next run: 3 beats ahead", or null when the controller held. */
fun nextRunLine(decision: Decision): String? {
    val move = decision.move ?: return null
    val what = when (move.dimension) {
        Dimension.LOOKAHEAD -> lookaheadLabel(decision.position.runConfig.lookaheadBeats)
        else -> decision.position.state.level.label(move.dimension)
    }
    return "${directionWord(move.direction == Direction.UP)} next run: $what"
}

private fun directionWord(harder: Boolean): String = if (harder) "Harder" else "Easier"

/** "91%", rounded. */
fun percentLabel(fraction: Double): String = "${(fraction * 100).roundToInt()}%"
