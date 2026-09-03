package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.ExerciseGenerator
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.exercise.NoteValue
import dev.simonmartineau.keysight.exercise.violations
import dev.simonmartineau.keysight.notation.ScoreLayoutEngine
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.runScore
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rungs of every dimension: ordered, reachable from the default, and every level they make is one the generator serves. */
class LaddersTest {

    /** Every level the ladders can reach: every consistent combination of their rungs. */
    private val levels: List<MusicalLevel> = Ladders.INTERVAL.rungs.flatMap { interval ->
        Ladders.RANGE.rungs.flatMap { ranges ->
            Ladders.RHYTHM.rungs.map { values -> MusicalLevel(interval, ranges.right, ranges.left, values) }
        }
    }.filter { it.isConsistent }

    private val bases = listOf(
        ExerciseConfig.DEFAULT,
        ExerciseConfig.DEFAULT.copy(hands = Hands.LEFT),
        ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH),
        ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE),
    )

    @Test
    fun `the default level stands on every ladder and the lookahead ladder is the run's`() {
        assertEquals(MusicalLevel.DEFAULT, MusicalLevel.of(ExerciseConfig.DEFAULT))
        assertTrue(MusicalLevel.DEFAULT.maxInterval in Ladders.INTERVAL.rungs)
        assertTrue(MusicalLevel.DEFAULT.ranges in Ladders.RANGE.rungs)
        assertTrue(MusicalLevel.DEFAULT.noteValues in Ladders.RHYTHM.rungs)
        assertEquals(RunConfig.LOOKAHEAD_LADDER_BEATS, Ladders.LOOKAHEAD.rungs)
        assertTrue(RunConfig.DEFAULT.lookaheadBeats in Ladders.LOOKAHEAD.rungs)
        assertEquals(Ladders.RANGE.easiest, MusicalLevel.DEFAULT.ranges, "the player starts at the five-finger position")
        assertEquals(Ladders.RHYTHM.hardest, MusicalLevel.DEFAULT.noteValues, "quarters are the top of the rhythm ladder until eighths come")
    }

    @Test
    fun `a ladder steps to its neighbours, stops at its ends and snaps a stray value`() {
        val ladder = Ladder(listOf(1, 2, 3, 4, 5, 7)) { it.toDouble() }

        assertEquals(3, ladder.step(2, Direction.UP))
        assertEquals(1, ladder.step(2, Direction.DOWN))
        assertNull(ladder.step(7, Direction.UP))
        assertNull(ladder.step(1, Direction.DOWN))
        assertEquals(7, ladder.step(6, Direction.UP))
        assertEquals(5, ladder.step(6, Direction.DOWN))
        assertNull(ladder.step(8, Direction.UP))
        assertEquals(1, ladder.step(0, Direction.UP))
        assertFailsWith<IllegalArgumentException> { Ladder(listOf(2, 1)) { it.toDouble() } }
        assertFailsWith<IllegalArgumentException> { Ladder(emptyList<Int>()) { it.toDouble() } }
    }

    @Test
    fun `the lookahead ladder gets harder as the beats get fewer`() {
        assertEquals(3.0, Ladders.LOOKAHEAD.step(4.0, Direction.UP))
        assertEquals(4.0, Ladders.LOOKAHEAD.step(3.0, Direction.DOWN))
        assertNull(Ladders.LOOKAHEAD.step(0.25, Direction.UP))
        assertNull(Ladders.LOOKAHEAD.step(4.0, Direction.DOWN))
        assertEquals(1.0, Ladders.LOOKAHEAD.step(1.25, Direction.UP))
    }

    @Test
    fun `every range rung is the same width for both hands and holds a tone of the triad`() {
        val triad = setOf(Step.C, Step.E, Step.G)
        Ladders.RANGE.rungs.forEach { rung ->
            listOf(rung.right, rung.left).forEach { range ->
                assertTrue(range.indices.any { Step.entries[Math.floorMod(it, Step.entries.size)] in triad }, "$range has no tone of the triad")
            }
        }
        assertEquals(listOf(5, 6, 8, 10, 12), Ladders.RANGE.rungs.map { it.width })
        assertFailsWith<IllegalArgumentException> { HandRanges(Ladders.RANGE.rungs[0].right, Ladders.RANGE.rungs[1].left) }
    }

    @Test
    fun `every level on the ladders is a configuration the generator satisfies for every hands`() {
        assertEquals(6 * 5 * 2 - 3 * 2, levels.size, "sixths and octaves need more than five notes, octaves more than six")
        levels.forEach { level ->
            bases.forEach { base ->
                val config = level.applyTo(base)
                (0L until 20L).forEach { seed ->
                    val problems = config.violations(ExerciseGenerator.generateInC(config, seed))
                    assertTrue(problems.isEmpty(), "$level on $base, seed $seed: $problems")
                }
            }
        }
    }

    @Test
    fun `the hardest rungs lay out inside the envelope in C and in every key on both staves`() {
        val hardest = MusicalLevel(Ladders.INTERVAL.hardest, Ladders.RANGE.hardest.right, Ladders.RANGE.hardest.left, Ladders.RHYTHM.hardest)
        KeySignature.ALL.forEach { key ->
            val config = hardest.applyTo(ExerciseConfig(key, Hands.BOTH, Accompaniment.HELD_NOTE))
            val run = runScore((0L until 12L).map { ExerciseGenerator.generate(config, it) })
            val page = ScoreLayoutEngine.layoutPage(run, targetWidth = 60.0)
            assertEquals(run.notes.map { it.id }.toSet(), page.systems.flatMap { it.layout.anchors.keys }.toSet(), "$key")
            if (key == KeySignature.C_MAJOR) {
                page.systems.forEach { placed ->
                    assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, placed.layout.top)
                    assertEquals(ScoreLayoutEngine.ENVELOPE_BOTTOM - ScoreLayoutEngine.STAFF_DISTANCE, placed.layout.bottom)
                }
            }
        }
    }

    @Test
    fun `a level is described in words, one per dimension`() {
        assertEquals("Up to thirds, five notes, quarter notes.", MusicalLevel.DEFAULT.description)
        val hardest = MusicalLevel(7, Ladders.RANGE.hardest.right, Ladders.RANGE.hardest.left, Ladders.RHYTHM.hardest)
        assertEquals("Up to octaves, a twelfth, quarter notes.", hardest.description)
        val easiest = MusicalLevel(1, Ladders.RANGE.easiest.right, Ladders.RANGE.easiest.left, Ladders.RHYTHM.easiest)
        assertEquals("Steps only, five notes, half notes.", easiest.description)
        assertEquals("an octave", MusicalLevel.rangeLabel(8))
        assertEquals("up to fifths", MusicalLevel.intervalLabel(4))
        assertEquals("whole notes", MusicalLevel.rhythmLabel(NoteValue.WHOLE))
    }

    @Test
    fun `a level applied to a base keeps the player's choices and reads back as itself`() {
        val level = MusicalLevel(3, Ladders.RANGE.rungs[2].right, Ladders.RANGE.rungs[2].left, Ladders.RHYTHM.easiest)
        val base = ExerciseConfig(KeySignature(-2), Hands.BOTH, Accompaniment.HELD_NOTE)

        val config = level.applyTo(base)

        assertEquals(KeySignature(-2), config.keySignature)
        assertEquals(Hands.BOTH, config.hands)
        assertEquals(Accompaniment.HELD_NOTE, config.accompaniment)
        assertEquals(level, MusicalLevel.of(config))
        assertEquals(8, level.width)
        assertEquals(NoteValue.HALF, level.shortestValue)
    }
}
