package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.Hand
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.transposed

/**
 * One measure from an [ExerciseConfig] and a seed, deterministic and tested against its own
 * constraints.
 *
 * The measure is written in C major and transposed into the requested key, so every key is
 * served by one algorithm and the spelling is the key's. The melody is a constrained random
 * walk: a rhythm from the config's vocabulary, a start anywhere in the hand's range, then
 * steps of at most [ExerciseConfig.maxInterval] letters that stay in the range, with a
 * contour: steps that carry on in the bar's direction weigh twice the others, and the
 * direction turns at the edge of the range. A rest in the rhythm is a gap the walk skips:
 * the notes stay one melody, and the score holds nothing for the silence. With both hands
 * the melody's staff is drawn per bar, the other staff resting or holding one note of the
 * tonic triad for the bar.
 *
 * [GENERATOR_VERSION] is stored with every segment beside its seed and configuration; it
 * changes whenever the same inputs would produce a different score.
 */
object ExerciseGenerator {

    const val GENERATOR_VERSION = 1

    /** The id of the held note of a hands-together bar; the melody's notes are `n1`, `n2` and so on. */
    const val HELD_NOTE_ID = "held"

    /** Weight of a step that continues the bar's direction against 1 for any other. */
    private const val CONTOUR_WEIGHT = 2

    /** The tonic triad in C, the tones a held note is drawn from. */
    private val TRIAD_STEPS = setOf(Step.C, Step.E, Step.G)

    fun generate(config: ExerciseConfig, seed: Long): Score = generateInC(config, seed).transposed(config.keySignature)

    /** The measure before transposition: what the constraints are stated on. */
    fun generateInC(config: ExerciseConfig, seed: Long): Score {
        val random = SeededRandom(seed)
        val staves = config.staves
        val melodyStaff = if (config.hands == Hands.BOTH) random.nextInt(staves.size) else 0
        val rhythm = config.rhythms[random.nextInt(config.rhythms.size)]
        val range = config.rangeOf(staves[melodyStaff].clef)
        val indices = walk(random, range.indices, config.maxInterval, rhythm.count { !it.rest })

        val notes = ArrayList<ScoreNote>()
        var onset = Ticks.ZERO
        for (event in rhythm) {
            if (!event.rest) {
                notes += ScoreNote(
                    id = "n${notes.size + 1}",
                    spelling = spellingOf(indices[notes.size]),
                    onset = onset,
                    duration = event.ticks,
                    voice = melodyStaff,
                    hand = handOn(staves[melodyStaff].clef),
                    staff = melodyStaff,
                )
            }
            onset += event.ticks
        }

        if (config.accompaniment == Accompaniment.HELD_NOTE) {
            val heldStaff = 1 - melodyStaff
            val candidates = config.rangeOf(staves[heldStaff].clef).indices.map(::spellingOf).filter { it.step in TRIAD_STEPS }
            check(candidates.isNotEmpty()) { "no tone of the triad in ${config.rangeOf(staves[heldStaff].clef)}" }
            notes += ScoreNote(
                id = HELD_NOTE_ID,
                spelling = candidates[random.nextInt(candidates.size)],
                onset = Ticks.ZERO,
                duration = config.timeSignature.ticksPerMeasure,
                voice = heldStaff,
                hand = handOn(staves[heldStaff].clef),
                staff = heldStaff,
            )
        }

        return Score(
            timeSignature = config.timeSignature,
            keySignature = KeySignature.C_MAJOR,
            staves = staves,
            measureCount = 1,
            notes = notes,
        )
    }

    /** [count] diatonic indices inside [range], each within [maxInterval] of the last, with a contour. */
    private fun walk(random: SeededRandom, range: IntRange, maxInterval: Int, count: Int): List<Int> {
        var current = range.first + random.nextInt(range.last - range.first + 1)
        var direction = if (random.nextBoolean()) 1 else -1
        val result = ArrayList<Int>(count)
        result += current
        repeat(count - 1) {
            val candidates = (-maxInterval..maxInterval).map { current + it }.filter { it in range }
            if (candidates.none { (it - current) * direction > 0 }) direction = -direction
            val weighted = candidates.flatMap { candidate ->
                List(if ((candidate - current) * direction > 0) CONTOUR_WEIGHT else 1) { candidate }
            }
            current = weighted[random.nextInt(weighted.size)]
            result += current
        }
        return result
    }

    private fun spellingOf(diatonicIndex: Int): SpelledPitch =
        SpelledPitch(Step.entries[Math.floorMod(diatonicIndex, Step.entries.size)], octave = Math.floorDiv(diatonicIndex, Step.entries.size))

    private fun handOn(clef: Clef): Hand = if (clef == Clef.BASS) Hand.LEFT else Hand.RIGHT
}
