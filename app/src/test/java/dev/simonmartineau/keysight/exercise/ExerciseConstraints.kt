package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import kotlin.math.abs

/**
 * What a measure written in C major must satisfy to be an instance of a config: the
 * generator's contract, stated once so that generated measures and the bundled fixtures are
 * held to the same rules. Returns every violation, empty when the measure is an instance.
 */
fun ExerciseConfig.violations(score: Score): List<String> {
    val problems = ArrayList<String>()
    if (score.keySignature != KeySignature.C_MAJOR) problems += "written in ${score.keySignature}, not C"
    if (score.timeSignature != timeSignature) problems += "in ${score.timeSignature}, not $timeSignature"
    if (score.measureCount != 1) problems += "${score.measureCount} measures"
    if (score.staves != staves) problems += "on ${score.staves}, not $staves"

    val held = score.notes.filter { it.id == ExerciseGenerator.HELD_NOTE_ID }
    val melody = score.notes.filter { it.id != ExerciseGenerator.HELD_NOTE_ID }.sortedBy { it.onset }
    if (melody.isEmpty()) problems += "no melody"

    val melodyStaves = melody.map { it.staff }.toSet()
    if (melodyStaves.size > 1) problems += "the melody is on staves $melodyStaves"
    val melodyStaff = melodyStaves.firstOrNull() ?: 0
    val range = rangeOf(score.staves[melodyStaff].clef)
    val allowed = noteValues.map { it.ticks }.toSet()

    var expectedOnset = Ticks.ZERO
    melody.forEach { note ->
        if (note.onset != expectedOnset) problems += "${note.id} starts at ${note.onset}, not $expectedOnset"
        if (note.duration !in allowed) problems += "${note.id} lasts ${note.duration}, not one of $noteValues"
        if (note.spelling !in range) problems += "${note.id} is ${note.spelling}, outside $range"
        if (note.spelling.alteration != 0) problems += "${note.id} is ${note.spelling}, not natural"
        if (note.voice != note.staff) problems += "${note.id} is in voice ${note.voice} on staff ${note.staff}"
        expectedOnset = note.end
    }
    if (melody.isNotEmpty() && expectedOnset != timeSignature.ticksPerMeasure) problems += "the melody ends at $expectedOnset, not at the barline"
    melody.zipWithNext().forEach { (a, b) ->
        val interval = abs(b.spelling.diatonicIndex - a.spelling.diatonicIndex)
        if (interval > maxInterval) problems += "${a.id} to ${b.id} is $interval letters, more than $maxInterval"
    }

    when (accompaniment) {
        Accompaniment.NONE -> if (held.isNotEmpty()) problems += "a held note without accompaniment"
        Accompaniment.HELD_NOTE -> {
            val note = held.singleOrNull()
            if (note == null) {
                problems += "${held.size} held notes"
            } else {
                if (note.staff == melodyStaff) problems += "the held note is on the melody's staff"
                if (note.onset != Ticks.ZERO || note.duration != timeSignature.ticksPerMeasure) problems += "the held note does not fill the measure"
                if (note.spelling !in rangeOf(score.staves[note.staff].clef)) problems += "the held note ${note.spelling} is out of its range"
                if (note.spelling.step !in setOf(Step.C, Step.E, Step.G)) problems += "the held note ${note.spelling} is not a tone of the triad"
                if (note.voice != note.staff) problems += "the held note is in voice ${note.voice} on staff ${note.staff}"
            }
        }
    }
    return problems
}
