package dev.simonmartineau.keysight.ui.practice

import dev.simonmartineau.keysight.evaluation.Continuity
import dev.simonmartineau.keysight.evaluation.PitchResult
import dev.simonmartineau.keysight.evaluation.RhythmResult
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The words of the result screen, kept pure so they can be tested. Feedback stays compact: one
 * score line, then only the remarks that earned their place.
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

fun continuityLabel(continuity: Continuity): String = when (continuity) {
    Continuity.GOOD -> "Good"
    Continuity.HESITANT -> "Hesitant"
    Continuity.LOST -> "Lost"
}

fun scoreLine(pitch: PitchResult, rhythm: RhythmResult?): String {
    val parts = mutableListOf("Pitch ${percent(pitch.accuracy)}")
    if (rhythm != null) {
        parts += "Rhythm ${percent(rhythm.accuracy)}"
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

private fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"
