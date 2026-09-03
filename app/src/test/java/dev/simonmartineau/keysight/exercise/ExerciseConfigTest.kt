package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.data.keySightJson
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Staff
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExerciseConfigTest {

    private val W = NoteValue.WHOLE
    private val H = NoteValue.HALF
    private val Q = NoteValue.QUARTER

    @Test
    fun `the rhythm vocabulary is every way to fill the measure, longest value first`() {
        assertEquals(
            listOf(listOf(W), listOf(H, H), listOf(H, Q, Q), listOf(Q, H, Q), listOf(Q, Q, H), listOf(Q, Q, Q, Q)),
            ExerciseConfig.DEFAULT.rhythms,
        )
        assertEquals(listOf(listOf(Q, Q, Q, Q)), ExerciseConfig.DEFAULT.copy(noteValues = setOf(Q)).rhythms)
        assertEquals(
            listOf(listOf(H, Q), listOf(Q, H), listOf(Q, Q, Q)),
            ExerciseConfig.DEFAULT.copy(timeSignature = TimeSignature.THREE_FOUR).rhythms,
        )
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
        assertTrue("\"timeSignature\"" in json, json)
        assertEquals(config, keySightJson.decodeFromString(ExerciseConfig.serializer(), json))
        assertEquals(ExerciseConfig.DEFAULT, keySightJson.decodeFromString(ExerciseConfig.serializer(), """{"keySignature":0,"hands":"RIGHT","later":1}"""))
    }
}
