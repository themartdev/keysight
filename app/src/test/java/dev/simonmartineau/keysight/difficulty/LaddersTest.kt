package dev.simonmartineau.keysight.difficulty

import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.ExerciseGenerator
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.exercise.NoteValue
import dev.simonmartineau.keysight.exercise.violations
import dev.simonmartineau.keysight.notation.Glyph
import dev.simonmartineau.keysight.notation.GlyphElement
import dev.simonmartineau.keysight.notation.Role
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
            Ladders.RHYTHM.rungs.flatMap { values ->
                Ladders.RESTS.rungs.flatMap { rests ->
                    Ladders.ACCIDENTALS.rungs.map { accidentals -> MusicalLevel(interval, ranges.right, ranges.left, values, rests, accidentals) }
                }
            }
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
        assertEquals(Ladders.RHYTHM.rungs[1], MusicalLevel.DEFAULT.noteValues, "the player starts on quarters, one rung under eighths")
        assertEquals(
            listOf(NoteValue.HALF, NoteValue.QUARTER, NoteValue.EIGHTH),
            Ladders.RHYTHM.rungs.map { rung -> rung.minBy { it.ticks } },
            "each rhythm rung adds the next shorter value",
        )
        assertEquals(setOf(NoteValue.WHOLE, NoteValue.HALF, NoteValue.QUARTER, NoteValue.EIGHTH), Ladders.RHYTHM.step(MusicalLevel.DEFAULT.noteValues, Direction.UP))
        assertNull(Ladders.RHYTHM.step(Ladders.RHYTHM.hardest, Direction.UP))
        assertEquals(listOf(false, true), Ladders.RESTS.rungs, "rests are off, then on")
        assertTrue(!MusicalLevel.DEFAULT.rests, "the player starts without rests")
        assertEquals(true, Ladders.RESTS.step(false, Direction.UP))
        assertNull(Ladders.RESTS.step(true, Direction.UP))
        assertEquals(listOf(false, true), Ladders.ACCIDENTALS.rungs, "accidentals are off, then on")
        assertTrue(!MusicalLevel.DEFAULT.accidentals, "the player starts without accidentals")
        assertEquals(true, Ladders.ACCIDENTALS.step(false, Direction.UP))
        assertNull(Ladders.ACCIDENTALS.step(true, Direction.UP))
        assertEquals(listOf(Dimension.INTERVAL, Dimension.RANGE, Dimension.RHYTHM, Dimension.RESTS, Dimension.ACCIDENTALS), MusicalLevel.MUSICAL_DIMENSIONS)
    }

    @Test
    fun `a level with accidentals needs a step for them to resolve by`() {
        assertTrue(MusicalLevel.DEFAULT.copy(accidentals = true).isConsistent)
        assertTrue(MusicalLevel.DEFAULT.copy(maxInterval = 1, accidentals = true).isConsistent)
        assertTrue(!MusicalLevel.DEFAULT.copy(maxInterval = 0, accidentals = true).isConsistent)
        assertTrue(MusicalLevel.DEFAULT.copy(maxInterval = 0).isConsistent)
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
        assertEquals((6 * 5 * 3 - 3 * 3) * 2 * 2, levels.size, "sixths and octaves need more than five notes, octaves more than six; each with and without rests, with and without accidentals")
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
        val hardest = MusicalLevel(Ladders.INTERVAL.hardest, Ladders.RANGE.hardest.right, Ladders.RANGE.hardest.left, Ladders.RHYTHM.hardest, Ladders.RESTS.hardest, Ladders.ACCIDENTALS.hardest)
        KeySignature.ALL.forEach { key ->
            val config = hardest.applyTo(ExerciseConfig(key, Hands.BOTH, Accompaniment.HELD_NOTE))
            val run = runScore((0L until 12L).map { ExerciseGenerator.generate(config, it) })
            val page = ScoreLayoutEngine.layoutPage(run, targetWidth = 60.0)
            assertEquals(run.notes.map { it.id }.toSet(), page.systems.flatMap { it.layout.anchors.keys }.toSet(), "$key")
            val glyphs = page.systems.flatMap { it.layout.elements }.filterIsInstance<GlyphElement>()
            val rests = glyphs.filter { it.role == Role.REST }
            assertTrue(rests.any { it.glyph != Glyph.REST_WHOLE }, "$key: twelve bars with rests on draw a rest inside a bar")
            val accidentals = glyphs.filter { it.role == Role.ACCIDENTAL }
            assertTrue(accidentals.isNotEmpty(), "$key: twelve bars with accidentals on draw one")
            assertTrue(accidentals.none { it.glyph == Glyph.ACCIDENTAL_DOUBLE_SHARP || it.glyph == Glyph.ACCIDENTAL_DOUBLE_FLAT }, "$key: never a double")
            if (key == KeySignature.C_MAJOR) {
                page.systems.forEach { placed ->
                    assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, placed.layout.top)
                    assertEquals(ScoreLayoutEngine.ENVELOPE_BOTTOM - ScoreLayoutEngine.STAFF_DISTANCE, placed.layout.bottom)
                }
            }
        }
    }

    /**
     * In every key but C some chromatic neighbour lands on a letter the key alters and is
     * written as a natural, once the range holds every letter: G major's is F natural, the
     * lowered seventh, which the five-finger position on C cannot reach.
     */
    @Test
    fun `at the accidentals rung a natural cancelling the key comes up in every key but C`() {
        val level = MusicalLevel.DEFAULT.copy(accidentals = true, rightHandRange = Ladders.RANGE.hardest.right, leftHandRange = Ladders.RANGE.hardest.left)
        KeySignature.ALL.filter { it != KeySignature.C_MAJOR }.forEach { key ->
            val config = level.applyTo(ExerciseConfig(key, Hands.RIGHT))
            val natural = (0L until 300L).any { seed ->
                ExerciseGenerator.generate(config, seed).notes.any { it.spelling.alteration == 0 && key.alterationOf(it.spelling.step) != 0 }
            }
            assertTrue(natural, "$key: no natural in three hundred bars")
        }
    }

    @Test
    fun `a level is described in words, one per dimension`() {
        assertEquals("Up to thirds, five notes, quarter notes, no rests, no accidentals.", MusicalLevel.DEFAULT.description)
        val hardest = MusicalLevel(7, Ladders.RANGE.hardest.right, Ladders.RANGE.hardest.left, Ladders.RHYTHM.hardest, rests = true, accidentals = true)
        assertEquals("Up to octaves, a twelfth, eighth notes, with rests, with accidentals.", hardest.description)
        val easiest = MusicalLevel(1, Ladders.RANGE.easiest.right, Ladders.RANGE.easiest.left, Ladders.RHYTHM.easiest)
        assertEquals("Steps only, five notes, half notes, no rests, no accidentals.", easiest.description)
        assertEquals("with rests", MusicalLevel.restsLabel(true))
        assertEquals("with accidentals", MusicalLevel.accidentalsLabel(true))
        assertEquals("no accidentals", MusicalLevel.accidentalsLabel(false))
        assertEquals("an octave", MusicalLevel.rangeLabel(8))
        assertEquals("up to fifths", MusicalLevel.intervalLabel(4))
        assertEquals("whole notes", MusicalLevel.rhythmLabel(NoteValue.WHOLE))
        assertEquals("eighth notes", MusicalLevel.rhythmLabel(NoteValue.EIGHTH))
    }

    @Test
    fun `a level applied to a base keeps the player's choices and reads back as itself`() {
        val level = MusicalLevel(3, Ladders.RANGE.rungs[2].right, Ladders.RANGE.rungs[2].left, Ladders.RHYTHM.easiest, rests = true, accidentals = true)
        val base = ExerciseConfig(KeySignature(-2), Hands.BOTH, Accompaniment.HELD_NOTE)

        val config = level.applyTo(base)

        assertEquals(KeySignature(-2), config.keySignature)
        assertEquals(Hands.BOTH, config.hands)
        assertEquals(Accompaniment.HELD_NOTE, config.accompaniment)
        assertEquals(level, MusicalLevel.of(config))
        assertEquals(8, level.width)
        assertEquals(NoteValue.HALF, level.shortestValue)
        assertTrue(config.rests)
        assertTrue(config.accidentals)
    }
}
