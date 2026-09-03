package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.evaluation.Continuity
import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.evaluation.NoteTiming
import dev.simonmartineau.keysight.evaluation.PlayedNote
import dev.simonmartineau.keysight.evaluation.RhythmResult
import dev.simonmartineau.keysight.evaluation.TimingJudgement
import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NoteMarksTest {

    private val score = Fixtures.cdef
    private val layout = ScoreLayoutEngine.layout(score)
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

        assertEquals(NoteMark.WrongPitch("n2", played = StaffPosition(1), playedAlteration = 1), mark)
    }

    @Test
    fun `an extra between two beats sits between their heads`() {
        val mark = assertIs<NoteMark.Extra>(marks(NoteOutcome.Extra(played(69, 0.5))).single())

        assertEquals((heads[0].x + heads[1].x) / 2, mark.x, 1e-9)
        assertEquals(StaffPosition(3), mark.position)
        assertEquals(0, mark.alteration)
    }

    @Test
    fun `an extra on a notated beat moves beside that head`() {
        val mark = assertIs<NoteMark.Extra>(marks(NoteOutcome.Extra(played(61, 1.0))).single())

        assertEquals(heads[1].x + heads[1].headWidth + Spacing.CUE_GAP, mark.x, 1e-9)
        assertEquals(StaffPosition(-2), mark.position)
        assertEquals(1, mark.alteration)
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
    fun `marks come back in outcome order`() {
        val result = marks(
            NoteOutcome.Extra(played(64, 2.5)),
            NoteOutcome.Correct(score.notes[3], played(65, 3.0)),
        )

        assertIs<NoteMark.Extra>(result[0])
        assertEquals(NoteMark.Correct("n4"), result[1])
    }
}
