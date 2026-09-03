package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.evaluation.Continuity
import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.evaluation.NoteTiming
import dev.simonmartineau.keysight.evaluation.PlayedNote
import dev.simonmartineau.keysight.evaluation.RhythmResult
import dev.simonmartineau.keysight.evaluation.TimingJudgement
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Staff
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class NoteMarksTest {

    private val score = Fixtures.cdef
    private val layout = ScoreLayoutEngine.layoutSystem(score, 0, null, showTimeSignature = true)
    private val head = layout.anchors.getValue("n1")
    private val heads = score.notes.map { layout.anchors.getValue(it.id) }

    private fun played(midi: Int, onsetBeat: Double) =
        PlayedNote(Pitch(midi), onsetBeat, onsetBeat + 0.5, velocity = 80, onsetNanos = 0L)

    private fun marks(vararg outcomes: NoteOutcome) = noteMarks(layout, score, outcomes.toList())

    @Test
    fun `outcomes about notated notes keep the note id`() {
        val result = marks(
            NoteOutcome.Correct(score.notes[0], played(60, 0.0)),
            NoteOutcome.Missing(score.notes[2]),
        )

        assertEquals(listOf(NoteMark.Correct("n1"), NoteMark.Missing("n3")), result)
    }

    @Test
    fun `a wrong pitch carries where the played note sits and its accidental`() {
        val mark = marks(NoteOutcome.WrongPitch(score.notes[1], played(66, 1.0))).single()

        assertEquals(NoteMark.WrongPitch("n2", played = StaffPosition(1), accidental = Glyph.ACCIDENTAL_SHARP), mark)
    }

    @Test
    fun `an extra between two beats sits between their heads`() {
        val mark = assertIs<NoteMark.Extra>(marks(NoteOutcome.Extra(played(69, 0.5))).single())

        assertEquals((heads[0].x + heads[1].x) / 2, mark.x, 1e-9)
        assertEquals(StaffPosition(3), mark.position)
        assertNull(mark.accidental)
        assertEquals(0, mark.system)
        assertEquals(0, mark.staff)
        assertEquals(Ticks.EIGHTH, mark.ticks)
    }

    @Test
    fun `an extra on a notated beat moves beside that head`() {
        val mark = assertIs<NoteMark.Extra>(marks(NoteOutcome.Extra(played(61, 1.0))).single())

        assertEquals(heads[1].x + heads[1].headWidth + Spacing.CUE_GAP, mark.x, 1e-9)
        assertEquals(StaffPosition(-2), mark.position)
        assertEquals(Glyph.ACCIDENTAL_SHARP, mark.accidental)
    }

    @Test
    fun `an extra played during a rest is drawn beside the rest`() {
        val withRest = Fixtures.oneMeasure(
            ScoreNote("n1", Fixtures.C4, Ticks.ZERO, Ticks.QUARTER),
            ScoreNote("n2", Fixtures.E4, Ticks.HALF, Ticks.QUARTER),
            ScoreNote("n3", Fixtures.D4, Ticks.quarters(3), Ticks.QUARTER),
        )
        val restLayout = ScoreLayoutEngine.layoutSystem(withRest, 0, null, showTimeSignature = true)
        val rest = restLayout.elements.filterIsInstance<GlyphElement>().single { it.role == Role.REST }

        val mark = assertIs<NoteMark.Extra>(noteMarks(restLayout, withRest, listOf(NoteOutcome.Extra(played(62, 1.0)))).single())

        assertEquals(rest.x + BravuraMetrics.of(Glyph.REST_QUARTER).right + Spacing.CUE_GAP, mark.x, 1e-9)
        assertEquals(Ticks.QUARTER, mark.ticks)
        val between = assertIs<NoteMark.Extra>(noteMarks(restLayout, withRest, listOf(NoteOutcome.Extra(played(62, 1.5)))).single())
        assertEquals((rest.x + BravuraMetrics.of(Glyph.REST_QUARTER).left + restLayout.anchors.getValue("n2").x) / 2, between.x, 1e-9)
    }

    @Test
    fun `an early extra lands beside the first head`() {
        val mark = assertIs<NoteMark.Extra>(marks(NoteOutcome.Extra(played(64, -0.3))).single())

        assertEquals(head.x + head.headWidth + Spacing.CUE_GAP, mark.x, 1e-9)
    }

    @Test
    fun `a late extra lands at the end of the measure`() {
        val mark = assertIs<NoteMark.Extra>(marks(NoteOutcome.Extra(played(64, 9.0))).single())

        assertEquals(layout.timeAxis.last().x, mark.x, 1e-9)
    }

    @Test
    fun `matched notes carry their timing unless they were on time`() {
        val rhythm = RhythmResult(
            timings = listOf(
                NoteTiming("n1", 0.0, -0.3, -0.3, TimingJudgement.EARLY),
                NoteTiming("n2", 1.0, 1.0, 0.0, TimingJudgement.ON_TIME),
                NoteTiming("n4", 3.0, 3.4, 0.4, TimingJudgement.LATE),
            ),
            phaseBeats = 0.0,
            tempoRatio = null,
            pauses = emptyList(),
            continuity = Continuity.GOOD,
        )
        val result = noteMarks(
            layout,
            score,
            listOf(
                NoteOutcome.Correct(score.notes[0], played(60, -0.3)),
                NoteOutcome.Correct(score.notes[1], played(62, 1.0)),
                NoteOutcome.WrongPitch(score.notes[3], played(67, 3.4)),
            ),
            rhythm,
        )

        assertEquals(NoteMark.Correct("n1", TimingJudgement.EARLY), result[0])
        assertEquals(NoteMark.Correct("n2", null), result[1])
        assertEquals(TimingJudgement.LATE, assertIs<NoteMark.WrongPitch>(result[2]).timing)
    }

    @Test
    fun `without a rhythm result no mark carries timing`() {
        val mark = marks(NoteOutcome.Correct(score.notes[0], played(60, -0.3))).single()

        assertEquals(NoteMark.Correct("n1", null), mark)
    }

    @Test
    fun `played pitches are spelled as the key spells them`() {
        val flatScore = score.copy(keySignature = KeySignature(-2))
        val flatLayout = ScoreLayoutEngine.layoutSystem(flatScore, 0, null, showTimeSignature = true)

        // B flat is in the key: no accidental. E natural is not: a natural sign.
        val bFlat = assertIs<NoteMark.WrongPitch>(noteMarks(flatLayout, flatScore, listOf(NoteOutcome.WrongPitch(score.notes[1], played(70, 1.0)))).single())
        assertEquals(StaffPosition(4), bFlat.played)
        assertNull(bFlat.accidental)
        val eNatural = assertIs<NoteMark.Extra>(noteMarks(flatLayout, flatScore, listOf(NoteOutcome.Extra(played(64, 0.5)))).single())
        assertEquals(StaffPosition(0), eNatural.position)
        assertEquals(Glyph.ACCIDENTAL_NATURAL, eNatural.accidental)
    }

    /**
     * A cue beside an altered note shares its letter, so its accidental is always written,
     * the key's own included; anywhere else the cue is spelled against the key alone.
     */
    @Test
    fun `a cue beside an altered note always carries its accidental`() {
        val bNatural = ScoreNote("n2", SpelledPitch(Step.B, octave = 4), Ticks.QUARTER, Ticks.QUARTER)
        val inF = Fixtures.oneMeasure(
            ScoreNote("n1", SpelledPitch(Step.C, octave = 5), Ticks.ZERO, Ticks.QUARTER),
            bNatural,
            ScoreNote("n3", SpelledPitch(Step.C, octave = 5), Ticks.HALF, Ticks.HALF),
        ).copy(keySignature = KeySignature(-1))
        val layoutInF = ScoreLayoutEngine.layoutSystem(inF, 0, null, showTimeSignature = true)
        fun cue(midi: Int) = assertIs<NoteMark.WrongPitch>(noteMarks(layoutInF, inF, listOf(NoteOutcome.WrongPitch(bNatural, played(midi, 1.0)))).single())

        val bFlat = cue(70)
        assertEquals(StaffPosition.MIDDLE_LINE, bFlat.played)
        assertEquals(Glyph.ACCIDENTAL_FLAT, bFlat.accidental, "the key's flat is written, since the bar just wrote a natural on B")
        val a = cue(69)
        assertEquals(StaffPosition(3), a.played)
        assertNull(a.accidental, "another letter is spelled against the key")
        val dFlat = cue(73)
        assertEquals(StaffPosition(6), dFlat.played)
        assertEquals(Glyph.ACCIDENTAL_FLAT, dFlat.accidental, "a black key leans the way of the key: D flat, not C sharp")
        assertEquals(Glyph.ACCIDENTAL_NATURAL, assertIs<NoteMark.WrongPitch>(noteMarks(layoutInF, inF, listOf(NoteOutcome.WrongPitch(inF.notes[0], played(71, 0.0)))).single()).accidental, "a natural played for an in-key note is a natural")
    }

    @Test
    fun `an extra goes on the staff and system nearest to what was played`() {
        val grand = Score(
            timeSignature = TimeSignature.FOUR_FOUR,
            keySignature = KeySignature.C_MAJOR,
            staves = listOf(Staff(Clef.TREBLE), Staff(Clef.BASS)),
            measureCount = 2,
            notes = listOf(
                ScoreNote("a", Fixtures.G4, Ticks.ZERO, Ticks.WHOLE),
                ScoreNote("b", Fixtures.G4, Ticks.WHOLE, Ticks.WHOLE),
            ),
        )
        val page = PageLayout(
            listOf(
                PlacedSystem(ScoreLayoutEngine.layoutSystem(grand, 0, null, showTimeSignature = true), 0.0),
                PlacedSystem(ScoreLayoutEngine.layoutSystem(grand, 1, null, showTimeSignature = false), -40.0),
            ),
        )
        val marks = noteMarks(page, grand, listOf(NoteOutcome.Extra(played(48, 5.0)), NoteOutcome.Extra(played(72, 1.0))))

        val low = assertIs<NoteMark.Extra>(marks[0])
        assertEquals(1, low.system)
        assertEquals(1, low.staff)
        assertEquals(StaffPosition(3), low.position)
        val high = assertIs<NoteMark.Extra>(marks[1])
        assertEquals(0, high.system)
        assertEquals(0, high.staff)
        assertEquals(StaffPosition(5), high.position)
    }

    @Test
    fun `marks come back in outcome order`() {
        val result = marks(
            NoteOutcome.Extra(played(64, 2.5)),
            NoteOutcome.Correct(score.notes[3], played(65, 3.0)),
        )

        assertIs<NoteMark.Extra>(result[0])
        assertEquals(NoteMark.Correct("n4"), result[1])
    }
}
