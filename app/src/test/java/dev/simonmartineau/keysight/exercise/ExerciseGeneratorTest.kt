package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.notation.ScoreLayoutEngine
import dev.simonmartineau.keysight.run.runScore
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.Hand
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import dev.simonmartineau.keysight.score.transposed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The generator against its own constraints, over many seeds, every key, every hands. */
class ExerciseGeneratorTest {

    private val seeds = (0L until 200L).toList()

    /** Every hands and accompaniment the settings can ask for, plus the dimensions the controller will move. */
    private val configs = listOf(
        ExerciseConfig.DEFAULT,
        ExerciseConfig.DEFAULT.copy(hands = Hands.LEFT),
        ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH),
        ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE),
        ExerciseConfig.DEFAULT.copy(maxInterval = 0),
        ExerciseConfig.DEFAULT.copy(maxInterval = 1),
        ExerciseConfig.DEFAULT.copy(maxInterval = 7, rightHandRange = PitchRange(SpelledPitch(Step.A, octave = 3), SpelledPitch(Step.C, octave = 6))),
        ExerciseConfig.DEFAULT.copy(noteValues = setOf(NoteValue.QUARTER)),
        ExerciseConfig.DEFAULT.copy(noteValues = setOf(NoteValue.WHOLE)),
        ExerciseConfig.DEFAULT.copy(noteValues = NoteValue.entries.toSet()),
        ExerciseConfig.DEFAULT.copy(noteValues = setOf(NoteValue.QUARTER, NoteValue.EIGHTH), hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE),
        ExerciseConfig.DEFAULT.copy(noteValues = NoteValue.entries.toSet(), timeSignature = TimeSignature.THREE_FOUR),
        ExerciseConfig.DEFAULT.copy(timeSignature = TimeSignature.THREE_FOUR),
        ExerciseConfig.DEFAULT.copy(rightHandRange = PitchRange(SpelledPitch(Step.E, octave = 4), SpelledPitch(Step.E, octave = 4))),
    )

    private fun assertInstance(config: ExerciseConfig, seed: Long, score: Score) {
        val problems = config.violations(score)
        assertTrue(problems.isEmpty(), "seed $seed of $config: $problems\n$score")
    }

    @Test
    fun `every generated measure in C satisfies its config`() {
        configs.forEach { config ->
            seeds.forEach { seed -> assertInstance(config, seed, ExerciseGenerator.generateInC(config, seed)) }
        }
    }

    @Test
    fun `the same seed gives the same measure and different seeds give different ones`() {
        configs.filter { it.rhythms.size > 1 }.forEach { config ->
            seeds.forEach { seed ->
                assertEquals(ExerciseGenerator.generate(config, seed), ExerciseGenerator.generate(config, seed))
            }
            val distinct = seeds.map { ExerciseGenerator.generate(config, it) }.toSet()
            val roomy = config.maxInterval > 0 && config.rightHandRange.indices.count() > 1 && config.rhythms.size >= 6
            assertTrue(distinct.size > if (roomy) seeds.size / 2 else 1, "$config: ${distinct.size} distinct measures from ${seeds.size} seeds")
        }
        assertNotEquals(ExerciseGenerator.generate(ExerciseConfig.DEFAULT, 1L), ExerciseGenerator.generate(ExerciseConfig.DEFAULT, 2L))
    }

    /**
     * Recorded before eighths existed, so a stored seed still means what it meant: adding a
     * value to the enum changed no configuration without it.
     */
    @Test
    fun `the generator's output is pinned for configurations without eighths`() {
        fun pinned(config: ExerciseConfig, seed: Long) =
            ExerciseGenerator.generate(config, seed).notes.map { "${it.spelling}:${it.duration.value}:${it.staff}" }

        assertEquals(listOf("F4:1920:0", "F4:960:0", "D4:960:0"), pinned(ExerciseConfig.DEFAULT, 1L))
        assertEquals(listOf("D4:960:0", "D4:960:0", "C4:960:0", "D4:960:0"), pinned(ExerciseConfig.DEFAULT, 2L))
        assertEquals(listOf("G4:3840:0"), pinned(ExerciseConfig.DEFAULT, 3L))
        assertEquals(listOf("G3:1920:0", "E3:1920:0"), pinned(ExerciseConfig.DEFAULT.copy(hands = Hands.LEFT), 4L))
        assertEquals(listOf("E4:960:0", "G4:1920:0", "G4:960:0"), pinned(ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH), 3L))
        assertEquals(listOf("C3:960:1", "E3:1920:1", "F3:960:1"), pinned(ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH), 4L))
        val together = ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE)
        assertEquals(listOf("G3:3840:1", "C4:3840:0"), pinned(together, 2L))
        assertEquals(listOf("E4:960:0", "G4:1920:0", "G4:960:0", "G3:3840:1"), pinned(together, 3L))
        assertEquals(listOf("G4:1920:0", "E4:960:0"), pinned(ExerciseConfig.DEFAULT.copy(timeSignature = TimeSignature.THREE_FOUR), 3L))
        assertEquals(listOf("D4:1920:0", "D4:1920:0"), pinned(ExerciseConfig.DEFAULT.copy(noteValues = setOf(NoteValue.WHOLE, NoteValue.HALF)), 2L))
        assertEquals(listOf("A3:960:0", "C4:960:0", "G3:960:0", "A3:960:0"), pinned(ExerciseConfig.DEFAULT.copy(keySignature = KeySignature(1), maxInterval = 4), 2L))
        assertEquals(1, ExerciseGenerator.GENERATOR_VERSION)
    }

    @Test
    fun `with eighths every rhythm of the vocabulary comes up, in pairs on the beat`() {
        val config = ExerciseConfig.DEFAULT.copy(noteValues = NoteValue.entries.toSet())
        val scores = (0L until 1000L).map { ExerciseGenerator.generateInC(config, it) }

        val rhythms = scores.map { score -> score.notes.map { note -> NoteValue.entries.single { it.ticks == note.duration } } }.toSet()
        assertEquals(config.rhythms.toSet(), rhythms)
        assertEquals(30, rhythms.size)
        scores.forEach { score ->
            score.notes.forEach { note -> assertTrue(config.mayStartAt(note.duration, note.onset), "seed: $score") }
            val eighths = score.notes.filter { it.duration == Ticks.EIGHTH }
            assertTrue(eighths.size % 2 == 0, "eighths come in pairs: $score")
            eighths.chunked(2).forEach { (first, second) ->
                assertEquals(Ticks.ZERO, Ticks(first.onset.value % Ticks.PER_QUARTER), "a pair starts on the beat: $score")
                assertEquals(first.end, second.onset, "a pair fills its beat: $score")
            }
        }
        assertTrue(scores.any { score -> score.notes.all { it.duration == Ticks.EIGHTH } }, "eight eighths come up")
    }

    @Test
    fun `every key is the C measure transposed, and comes back to C`() {
        val config = ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE)
        KeySignature.ALL.forEach { key ->
            seeds.take(50).forEach { seed ->
                val inC = ExerciseGenerator.generateInC(config, seed)
                val inKey = ExerciseGenerator.generate(config.copy(keySignature = key), seed)
                assertEquals(inC.transposed(key), inKey, "$key seed $seed")
                assertEquals(key, inKey.keySignature)
                assertEquals(inC, inKey.transposed(KeySignature.C_MAJOR), "$key seed $seed back to C")
                inKey.notes.forEach { note ->
                    assertEquals(key.alterationOf(note.spelling.step), note.spelling.alteration, "$key seed $seed: ${note.spelling} is not in the key")
                }
            }
        }
    }

    @Test
    fun `every hand's notes sit on its staff with its hand`() {
        seeds.forEach { seed ->
            val right = ExerciseGenerator.generateInC(ExerciseConfig.DEFAULT, seed)
            assertTrue(right.notes.all { it.staff == 0 && it.hand == Hand.RIGHT }, "seed $seed")
            val left = ExerciseGenerator.generateInC(ExerciseConfig.DEFAULT.copy(hands = Hands.LEFT), seed)
            assertTrue(left.notes.all { it.staff == 0 && it.hand == Hand.LEFT }, "seed $seed")
            assertEquals(Clef.BASS, left.staves.single().clef)
        }
    }

    @Test
    fun `both hands puts the melody on either staff over the seeds, the other resting or holding`() {
        val resting = seeds.map { ExerciseGenerator.generateInC(ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH), it) }
        assertEquals(setOf(0, 1), resting.map { it.notes.map { note -> note.staff }.toSet().single() }.toSet())
        resting.forEach { score -> assertEquals(1, score.notes.map { it.staff }.toSet().size, "one staff sounds") }

        val together = seeds.map { ExerciseGenerator.generateInC(ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE), it) }
        assertEquals(setOf(0, 1), together.map { it.notes.single { note -> note.id == ExerciseGenerator.HELD_NOTE_ID }.staff }.toSet())
        together.forEach { score ->
            val held = score.notes.single { it.id == ExerciseGenerator.HELD_NOTE_ID }
            assertEquals(Ticks.WHOLE, held.duration)
            assertEquals(if (held.staff == 1) Hand.LEFT else Hand.RIGHT, held.hand)
            assertEquals(2, score.notes.map { it.staff }.toSet().size, "both staves sound")
        }
        assertEquals(setOf("C3", "E3", "G3"), together.filter { it.notes.single { n -> n.id == "held" }.staff == 1 }.map { it.notes.single { n -> n.id == "held" }.spelling.toString() }.toSet())
    }

    @Test
    fun `every rhythm of the vocabulary and every note of the range come up`() {
        val config = ExerciseConfig.DEFAULT
        val scores = seeds.map { ExerciseGenerator.generateInC(config, it) }

        val rhythms = scores.map { score -> score.notes.map { note -> NoteValue.entries.single { it.ticks == note.duration } } }.toSet()
        assertEquals(config.rhythms.toSet(), rhythms)
        val pitches = scores.flatMap { score -> score.notes.map { it.spelling } }.toSet()
        assertEquals(config.rightHandRange.indices.count(), pitches.size)
    }

    @Test
    fun `a run of generated measures chains and lays out inside the envelope in every key`() {
        KeySignature.ALL.forEach { key ->
            listOf(Hands.RIGHT, Hands.LEFT, Hands.BOTH).forEach { hands ->
                val config = ExerciseConfig(key, hands, if (hands == Hands.BOTH) Accompaniment.HELD_NOTE else Accompaniment.NONE)
                val run = runScore(seeds.take(8).map { ExerciseGenerator.generate(config, it) })
                val page = ScoreLayoutEngine.layoutPage(run, targetWidth = 60.0)
                page.systems.forEach { placed ->
                    val staffCount = run.staves.size
                    assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, placed.layout.top, "$key $hands")
                    assertEquals(ScoreLayoutEngine.ENVELOPE_BOTTOM - ScoreLayoutEngine.STAFF_DISTANCE * (staffCount - 1), placed.layout.bottom, "$key $hands")
                }
                assertEquals(run.notes.map { it.id }.toSet(), page.systems.flatMap { it.layout.anchors.keys }.toSet(), "$key $hands")
            }
        }
    }
}
