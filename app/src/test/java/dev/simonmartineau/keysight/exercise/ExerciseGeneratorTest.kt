package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.notation.Glyph
import dev.simonmartineau.keysight.notation.GlyphElement
import dev.simonmartineau.keysight.notation.Role
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
        ExerciseConfig.DEFAULT.copy(rests = true),
        ExerciseConfig.DEFAULT.copy(rests = true, noteValues = NoteValue.entries.toSet(), hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE),
        ExerciseConfig.DEFAULT.copy(rests = true, timeSignature = TimeSignature.THREE_FOUR),
        ExerciseConfig.DEFAULT.copy(rests = true, noteValues = setOf(NoteValue.QUARTER)),
        ExerciseConfig.DEFAULT.copy(accidentals = true),
        ExerciseConfig.DEFAULT.copy(accidentals = true, maxInterval = 1),
        ExerciseConfig.DEFAULT.copy(accidentals = true, maxInterval = 0),
        ExerciseConfig.DEFAULT.copy(accidentals = true, hands = Hands.LEFT),
        ExerciseConfig.DEFAULT.copy(accidentals = true, timeSignature = TimeSignature.THREE_FOUR),
        ExerciseConfig.DEFAULT.copy(accidentals = true, rests = true, noteValues = NoteValue.entries.toSet(), hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE),
        ExerciseConfig.DEFAULT.copy(accidentals = true, rests = true, noteValues = NoteValue.entries.toSet(), maxInterval = 7, rightHandRange = PitchRange(SpelledPitch(Step.A, octave = 3), SpelledPitch(Step.E, octave = 5))),
    )

    /** The rhythm a measure in C was written to: its notes' values, and the silences between them as rests. */
    private fun rhythmOf(score: Score): List<RhythmEvent> {
        val events = ArrayList<RhythmEvent>()
        var at = Ticks.ZERO
        fun silence(until: Ticks) {
            if (until > at) events += RhythmEvent(NoteValue.entries.single { it.ticks == until - at }, rest = true)
        }
        score.notes.filter { it.id != ExerciseGenerator.HELD_NOTE_ID }.sortedBy { it.onset }.forEach { note ->
            silence(note.onset)
            events += RhythmEvent(NoteValue.entries.single { it.ticks == note.duration })
            at = note.end
        }
        silence(score.timeSignature.ticksPerMeasure)
        return events
    }

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
            val roomy = config.maxInterval > 0 && config.rightHandRange.indices.count() > 1 && config.rhythms.size >= 6 && config.timeSignature.beatsPerMeasure >= 4
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

    /** Recorded when accidentals arrived, at version 1: the draw of the altered note is the last of the bar and must stay so. */
    @Test
    fun `the generator's output is pinned for configurations with accidentals`() {
        fun pinned(config: ExerciseConfig, seed: Long) =
            ExerciseGenerator.generate(config, seed).notes.map { "${it.spelling}:${it.duration.value}:${it.staff}" }
        val accidentals = ExerciseConfig.DEFAULT.copy(accidentals = true)

        assertEquals(
            listOf(
                listOf("F4:1920:0", "F4:960:0", "D4:960:0"),
                listOf("D4:960:0", "D4:960:0", "C#4:960:0", "D4:960:0"),
                listOf("Bb3:3840:1", "Eb4:3840:0"),
                listOf("E4:960:0", "D4:480:0", "E4:480:0", "C4:480:0", "B3:960:0"),
            ),
            listOf(
                pinned(accidentals, 1L),
                pinned(accidentals, 2L),
                pinned(accidentals.copy(keySignature = KeySignature(-3), hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE, maxInterval = 4), 2L),
                pinned(accidentals.copy(keySignature = KeySignature(3), rests = true, noteValues = NoteValue.entries.toSet(), maxInterval = 4), 5L),
            ),
            "a bar with no place for one is the bar the seed makes without accidentals; C sharp resolves up to D; in A the lowered third is C natural, resolving down",
        )
    }

    @Test
    fun `with eighths every rhythm of the vocabulary comes up, in pairs on the beat`() {
        val config = ExerciseConfig.DEFAULT.copy(noteValues = NoteValue.entries.toSet())
        val scores = (0L until 1000L).map { ExerciseGenerator.generateInC(config, it) }

        val rhythms = scores.map(::rhythmOf).toSet()
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

    /** With rests every rhythm of the vocabulary comes up, and every rest a measure holds is drawn as exactly one rest. */
    @Test
    fun `with rests the silences are the vocabulary's and each is one rest on the page`() {
        val config = ExerciseConfig.DEFAULT.copy(noteValues = NoteValue.entries.toSet(), rests = true)
        val scores = (0L until 2000L).map { ExerciseGenerator.generateInC(config, it) }

        val rhythms = scores.map(::rhythmOf).toSet()
        assertEquals(config.rhythms.toSet(), rhythms)
        assertTrue(rhythms.any { it.last().rest }, "a measure may end with a rest")
        assertTrue(rhythms.none { it.first().rest }, "and never starts with one")
        scores.forEach { score ->
            val restsWritten = rhythmOf(score).count { it.rest }
            val layout = ScoreLayoutEngine.layoutSystem(score, 0, null, showTimeSignature = true)
            val restsDrawn = layout.elements.filterIsInstance<GlyphElement>().count { it.role == Role.REST }
            assertEquals(restsWritten, restsDrawn, "every rest is one glyph: $score")
            assertTrue(layout.elements.none { it.role == Role.REST && it.ticks == null }, "every rest inside the bar carries its onset")
        }
        assertTrue(scores.count { rhythmOf(it).none { event -> event.rest } } < scores.size / 4, "with rests on, most bars hold one")
    }

    /**
     * With accidentals one note of the bar is a chromatic neighbour wherever the walk has a
     * whole-tone step for one: raised and resolving up, or lowered and resolving down, never
     * last, never before a rest, drawn once by the layout; a bar with no such step has none.
     */
    @Test
    fun `with accidentals most bars hold one chromatic neighbour, sharps and flats both, drawn once`() {
        val config = ExerciseConfig.DEFAULT.copy(accidentals = true, rests = true, noteValues = NoteValue.entries.toSet())
        val scores = (0L until 1000L).map { ExerciseGenerator.generateInC(config, it) }

        val altered = scores.map { score -> score.notes.filter { it.spelling.alteration != 0 } }
        assertTrue(altered.all { it.size <= 1 }, "at most one altered note per bar")
        assertTrue(altered.count { it.isNotEmpty() } > scores.size / 2, "most bars hold one: ${altered.count { it.isNotEmpty() }}")
        assertEquals(setOf(-1, 1), altered.flatten().map { it.spelling.alteration }.toSet(), "sharps and flats both come up")
        scores.zip(altered).forEach { (score, notes) ->
            val note = notes.singleOrNull() ?: return@forEach
            val melody = score.notes.filter { it.id != ExerciseGenerator.HELD_NOTE_ID }.sortedBy { it.onset }
            val next = melody[melody.indexOf(note) + 1]
            assertEquals(note.end, next.onset, "resolves into the next sound: $score")
            assertEquals(note.spelling.alteration, next.spelling.diatonicIndex - note.spelling.diatonicIndex, "a sharp resolves up, a flat down: $score")
            assertEquals(note.spelling.alteration, note.pitch.semitonesTo(next.pitch), "by a semitone: $score")
            val layout = ScoreLayoutEngine.layoutSystem(score, 0, null, showTimeSignature = true)
            val accidentals = layout.elements.filterIsInstance<GlyphElement>().filter { it.role == Role.ACCIDENTAL }
            assertEquals(note.id, accidentals.first().noteId, "the altered note is drawn with its accidental: $score")
            accidentals.drop(1).forEach { later ->
                val restored = melody.single { it.id == later.noteId }
                assertEquals(Glyph.ACCIDENTAL_NATURAL, later.glyph, "the only other accidental is the natural restoring the letter: $score")
                assertTrue(restored.onset > note.onset && restored.spelling == note.spelling.copy(alteration = 0), "on the same letter and octave, later in the bar: $score")
            }
        }
        assertTrue(altered.flatten().none { it.spelling.step == Step.E && it.spelling.alteration > 0 || it.spelling.step == Step.B && it.spelling.alteration > 0 }, "no white key spelled as a sharp")
        assertTrue(altered.flatten().none { it.spelling.step == Step.F && it.spelling.alteration < 0 || it.spelling.step == Step.C && it.spelling.alteration < 0 }, "or as a flat")

        val repeated = (0L until 100L).map { ExerciseGenerator.generateInC(config.copy(maxInterval = 0), it) }
        assertTrue(repeated.all { score -> score.notes.all { it.spelling.alteration == 0 } }, "repeated notes have no step to resolve by")
        assertEquals(
            (0L until 100L).map { ExerciseGenerator.generateInC(config.copy(accidentals = false), it) },
            scores.take(100).map { score -> score.copy(notes = score.notes.map { it.copy(spelling = it.spelling.copy(alteration = 0)) }) },
            "an accidental alters a note of the bar the seed makes without it, nothing else",
        )
    }

    /** In every key an altered note is a plain sharp, flat or natural, never a double, and stays a semitone from its resolution. */
    @Test
    fun `with accidentals every key writes plain accidentals and the pitches come back to C`() {
        val config = ExerciseConfig.DEFAULT.copy(accidentals = true, hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE)
        KeySignature.ALL.forEach { key ->
            seeds.take(50).forEach { seed ->
                val inC = ExerciseGenerator.generateInC(config, seed)
                val inKey = ExerciseGenerator.generate(config.copy(keySignature = key), seed)
                assertEquals(inC.transposed(key), inKey, "$key seed $seed")
                val back = inKey.transposed(KeySignature.C_MAJOR)
                assertEquals(inC.notes.map { it.pitch }, back.notes.map { it.pitch }, "$key seed $seed back to C")
                inKey.notes.forEach { note ->
                    assertTrue(note.spelling.alteration in -1..1, "$key seed $seed: ${note.spelling} is a double")
                }
                val altered = inC.notes.filter { it.spelling.alteration != 0 }.map { it.id }
                inKey.notes.filter { it.id !in altered }.forEach { note ->
                    assertEquals(key.alterationOf(note.spelling.step), note.spelling.alteration, "$key seed $seed: ${note.spelling} is not in the key")
                }
            }
        }
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

        val rhythms = scores.map(::rhythmOf).toSet()
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
