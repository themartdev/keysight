package dev.simonmartineau.keysight.score

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreModelTest {

    @Test
    fun `middle C is MIDI 60 in octave 4`() {
        assertEquals(60, Pitch.C4.midiNoteNumber)
        assertEquals(4, Pitch.C4.octave)
        assertEquals(0, Pitch.C4.pitchClass)
        assertEquals("C4", Pitch.C4.toString())
    }

    @Test
    fun `intervals are signed semitone distances`() {
        val g4 = Pitch(67)

        assertEquals(7, Pitch.C4.semitonesTo(g4))
        assertEquals(-7, g4.semitonesTo(Pitch.C4))
    }

    @Test
    fun `a pitch range is inclusive at both ends`() {
        val range = PitchRange(Pitch(60), Pitch(72))

        assertTrue(Pitch(60) in range)
        assertTrue(Pitch(72) in range)
        assertFalse(Pitch(59) in range)
        assertEquals(12, range.semitoneSpan)
    }

    @Test
    fun `an inverted pitch range is rejected`() {
        assertFailsWith<IllegalArgumentException> { PitchRange(Pitch(72), Pitch(60)) }
    }

    @Test
    fun `notes are ordered by onset then pitch`() {
        val score = oneMeasure(
            note("b", Pitch(64), onsetBeat = 0.0),
            note("c", Pitch(60), onsetBeat = 2.0),
            note("a", Pitch(60), onsetBeat = 0.0),
        )

        assertEquals(listOf("a", "b", "c"), score.notesInPerformanceOrder.map { it.id })
    }

    @Test
    fun `a one measure score in four four is four beats long`() {
        assertEquals(4.0, oneMeasure(note("a", Pitch.C4, onsetBeat = 0.0)).totalBeats)
    }

    @Test
    fun `notes cannot run past the end of the score`() {
        assertFailsWith<IllegalArgumentException> {
            oneMeasure(note("a", Pitch.C4, onsetBeat = 3.0, durationBeats = 2.0))
        }
    }

    @Test
    fun `a note must have a positive duration`() {
        assertFailsWith<IllegalArgumentException> {
            note("a", Pitch.C4, onsetBeat = 0.0, durationBeats = 0.0)
        }
    }

    private fun note(
        id: String,
        pitch: Pitch,
        onsetBeat: Double,
        durationBeats: Double = 1.0,
    ) = ScoreNote(id = id, pitch = pitch, onsetBeat = onsetBeat, durationBeats = durationBeats)

    private fun oneMeasure(vararg notes: ScoreNote) = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        clef = Clef.TREBLE,
        keySignatureFifths = 0,
        measureCount = 1,
        notes = notes.toList(),
    )
}
