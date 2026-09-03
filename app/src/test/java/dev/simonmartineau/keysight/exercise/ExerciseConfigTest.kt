package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.data.keySightJson
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Staff
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExerciseConfigTest {

    private val W = NoteValue.WHOLE
    private val H = NoteValue.HALF
    private val Q = NoteValue.QUARTER
    private val E = NoteValue.EIGHTH

    private fun notes(vararg values: NoteValue) = values.map { RhythmEvent(it) }
    private fun rest(value: NoteValue) = RhythmEvent(value, rest = true)

    @Test
    fun `the rhythm vocabulary is every way to fill the measure, longest value first`() {
        assertEquals(
            listOf(notes(W), notes(H, H), notes(H, Q, Q), notes(Q, H, Q), notes(Q, Q, H), notes(Q, Q, Q, Q)),
            ExerciseConfig.DEFAULT.rhythms,
        )
        assertEquals(listOf(notes(Q, Q, Q, Q)), ExerciseConfig.DEFAULT.copy(noteValues = setOf(Q)).rhythms)
        assertEquals(
            listOf(notes(H, Q), notes(Q, H), notes(Q, Q, Q)),
            ExerciseConfig.DEFAULT.copy(timeSignature = TimeSignature.THREE_FOUR).rhythms,
        )
    }

    @Test
    fun `eighths come in pairs on a beat and nothing longer starts off the beat`() {
        val config = ExerciseConfig.DEFAULT.copy(noteValues = setOf(W, H, Q, E))

        assertEquals(30, config.rhythms.size, "four beats of quarter or two eighths, halves over beat pairs, the whole")
        assertEquals(listOf(notes(W), notes(H, H), notes(H, Q, Q), notes(H, Q, E, E), notes(H, E, E, Q), notes(H, E, E, E, E)), config.rhythms.take(6))
        assertEquals(ExerciseConfig.DEFAULT.rhythms, config.rhythms.filter { rhythm -> rhythm.none { it.value == E } }, "the quarter vocabulary is unchanged, in its order")
        config.rhythms.forEach { rhythm ->
            var onset = Ticks.ZERO
            rhythm.forEach { value ->
                assertTrue(config.mayStartAt(value.ticks, onset), "$rhythm puts $value at $onset")
                onset += value.ticks
            }
        }
        assertTrue(notes(Q, E, Q, E, Q) !in config.rhythms, "syncopation is not in the vocabulary")
        assertTrue(notes(E, Q, E, H) !in config.rhythms)
        assertTrue(notes(E, E, E, E, E, E, E, E) in config.rhythms)

        assertTrue(config.mayStartAt(Ticks.EIGHTH, Ticks.EIGHTH))
        assertTrue(config.mayStartAt(Ticks.QUARTER, Ticks.QUARTER))
        assertTrue(config.mayStartAt(Ticks.HALF, Ticks.quarters(3)))
        assertTrue(!config.mayStartAt(Ticks.QUARTER, Ticks.EIGHTH))
        assertTrue(!config.mayStartAt(Ticks.HALF, Ticks.EIGHTH))

        val waltz = config.copy(timeSignature = TimeSignature.THREE_FOUR)
        assertEquals(12, waltz.rhythms.size, "three beats of quarter or two eighths, halves over the first or last two")
        assertEquals(listOf(notes(H, Q), notes(H, E, E), notes(Q, H), notes(Q, Q, Q)), waltz.rhythms.take(4))
    }

    @Test
    fun `with rests a silence of one value may follow a note, never opens the measure and never follows another`() {
        val config = ExerciseConfig.DEFAULT.copy(rests = true)

        assertEquals(16, config.rhythms.size, "six without rests, then a rest in every place one may go")
        assertEquals(ExerciseConfig.DEFAULT.rhythms, config.rhythms.filter { rhythm -> rhythm.none { it.rest } }, "the vocabulary without rests is unchanged, in its order")
        assertEquals(listOf(notes(W), notes(H, H), listOf(RhythmEvent(H), rest(H)), notes(H, Q, Q)), config.rhythms.take(4), "a rest of a value is tried after the note of it")
        assertTrue(listOf(RhythmEvent(Q), rest(Q), RhythmEvent(H)) in config.rhythms)
        assertTrue(listOf(RhythmEvent(Q), RhythmEvent(Q), rest(H)) in config.rhythms, "a half rest on beat 3")
        assertTrue(listOf(RhythmEvent(Q), rest(H), RhythmEvent(Q)) !in config.rhythms, "a half rest never crosses the middle of the measure")
        assertTrue(listOf(RhythmEvent(Q), rest(Q), rest(Q), RhythmEvent(Q)) !in config.rhythms, "no two rests in a row")
        assertTrue(config.rhythms.none { it.first().rest }, "the measure never starts with a rest")
        assertTrue(config.rhythms.none { rhythm -> rhythm.zipWithNext().any { (a, b) -> a.rest && b.rest } })
        config.rhythms.forEach { rhythm ->
            var onset = Ticks.ZERO
            rhythm.forEach { event ->
                if (event.rest) assertTrue(config.mayRestAt(event.ticks, onset, afterRest = false), "$rhythm rests $event at $onset")
                assertTrue(config.mayStartAt(event.ticks, onset), "$rhythm puts $event at $onset")
                onset += event.ticks
            }
            assertEquals(config.timeSignature.ticksPerMeasure, onset, "$rhythm fills the measure")
        }

        assertTrue(config.mayRestAt(Ticks.QUARTER, Ticks.QUARTER, afterRest = false))
        assertTrue(config.mayRestAt(Ticks.EIGHTH, Ticks.EIGHTH, afterRest = false))
        assertTrue(config.mayRestAt(Ticks.HALF, Ticks.HALF, afterRest = false))
        assertTrue(!config.mayRestAt(Ticks.HALF, Ticks.QUARTER, afterRest = false), "a half rest starts on beat 1 or 3")
        assertTrue(!config.mayRestAt(Ticks.QUARTER, Ticks.EIGHTH, afterRest = false), "a quarter rest starts on a beat")
        assertTrue(!config.mayRestAt(Ticks.QUARTER, Ticks.ZERO, afterRest = false), "never first")
        assertTrue(!config.mayRestAt(Ticks.QUARTER, Ticks.QUARTER, afterRest = true), "never after another")
        assertTrue(!config.mayRestAt(Ticks.WHOLE, Ticks.ZERO, afterRest = false), "never the whole measure")

        val eighths = config.copy(noteValues = NoteValue.entries.toSet())
        assertTrue(eighths.rhythms.size > 30)
        assertTrue(listOf(RhythmEvent(Q), RhythmEvent(E), rest(E), RhythmEvent(H)) in eighths.rhythms, "an eighth rest on the off-beat")
        assertTrue(listOf(RhythmEvent(Q), rest(E), RhythmEvent(E), RhythmEvent(H)) in eighths.rhythms, "an eighth rest on the beat, the note after it off the beat")
        assertTrue(eighths.rhythms.none { rhythm -> rhythm.zipWithNext().any { (a, b) -> a.rest && b.rest } }, "never two in a beat")

        val waltz = config.copy(timeSignature = TimeSignature.THREE_FOUR)
        assertEquals(6, waltz.rhythms.size, "three without rests, a quarter rest after the half, one or two among the quarters")
        assertTrue(waltz.rhythms.none { rhythm -> rhythm.any { it.rest && it.value == H } }, "a half rest only opens a measure of 3/4, and nothing opens with a rest")
        assertEquals(ExerciseConfig.DEFAULT.rhythms, ExerciseConfig.DEFAULT.copy(rests = false).rhythms)
    }

    @Test
    fun `a config that cannot fill its measure is refused`() {
        assertFailsWith<IllegalArgumentException> { ExerciseConfig.DEFAULT.copy(noteValues = emptySet()) }
        assertFailsWith<IllegalArgumentException> { ExerciseConfig.DEFAULT.copy(noteValues = setOf(W), timeSignature = TimeSignature.THREE_FOUR) }
    }

    @Test
    fun `an accompaniment needs both hands and a held note needs a measure of one plain value`() {
        assertFailsWith<IllegalArgumentException> { ExerciseConfig.DEFAULT.copy(accompaniment = Accompaniment.HELD_NOTE) }
        assertFailsWith<IllegalArgumentException> {
            ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE, timeSignature = TimeSignature.THREE_FOUR)
        }
        ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE)
        ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH, accompaniment = Accompaniment.HELD_NOTE, timeSignature = TimeSignature(2, 2))
    }

    @Test
    fun `the interval is bounded and the ranges are natural and ordered`() {
        assertFailsWith<IllegalArgumentException> { ExerciseConfig.DEFAULT.copy(maxInterval = -1) }
        assertFailsWith<IllegalArgumentException> { ExerciseConfig.DEFAULT.copy(maxInterval = ExerciseConfig.MAX_INTERVAL + 1) }
        assertFailsWith<IllegalArgumentException> { PitchRange(SpelledPitch(Step.G, octave = 4), SpelledPitch(Step.C, octave = 4)) }
        assertFailsWith<IllegalArgumentException> { PitchRange(SpelledPitch(Step.F, alteration = 1, octave = 4), SpelledPitch(Step.G, octave = 4)) }
        val fifth = PitchRange(SpelledPitch(Step.C, octave = 4), SpelledPitch(Step.G, octave = 4))
        assertEquals(5, fifth.indices.count())
        assertTrue(SpelledPitch(Step.E, octave = 4) in fifth)
        assertTrue(SpelledPitch(Step.A, octave = 4) !in fifth)
    }

    @Test
    fun `the staves and ranges follow the hands`() {
        assertEquals(listOf(Staff(Clef.TREBLE)), ExerciseConfig.DEFAULT.staves)
        assertEquals(listOf(Staff(Clef.BASS)), ExerciseConfig.DEFAULT.copy(hands = Hands.LEFT).staves)
        assertEquals(listOf(Staff(Clef.TREBLE), Staff(Clef.BASS)), ExerciseConfig.DEFAULT.copy(hands = Hands.BOTH).staves)
        assertEquals(ExerciseConfig.DEFAULT_RIGHT_HAND_RANGE, ExerciseConfig.DEFAULT.rangeOf(Clef.TREBLE))
        assertEquals(ExerciseConfig.DEFAULT_LEFT_HAND_RANGE, ExerciseConfig.DEFAULT.rangeOf(Clef.BASS))
    }

    @Test
    fun `a config round-trips through JSON with its defaults written out`() {
        val config = ExerciseConfig(KeySignature(-3), Hands.BOTH, Accompaniment.HELD_NOTE, maxInterval = 4)

        val json = keySightJson.encodeToString(ExerciseConfig.serializer(), config)

        assertTrue("\"noteValues\":[\"WHOLE\",\"HALF\",\"QUARTER\"]" in json, json)
        assertTrue("\"rests\":false" in json, json)
        assertTrue(!ExerciseConfig.DEFAULT.rests, "rests are a rung, not the default")
        assertEquals(ExerciseConfig.DEFAULT_NOTE_VALUES, ExerciseConfig.DEFAULT.noteValues, "eighths are a rung, not the default")
        assertTrue("\"timeSignature\"" in json, json)
        assertEquals(config, keySightJson.decodeFromString(ExerciseConfig.serializer(), json))
        assertEquals(ExerciseConfig.DEFAULT, keySightJson.decodeFromString(ExerciseConfig.serializer(), """{"keySignature":0,"hands":"RIGHT","later":1}"""))
    }
}
