package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoreLayoutEngineTest {

    private val a3 = SpelledPitch(Step.A, octave = 3)
    private val a4 = SpelledPitch(Step.A, octave = 4)
    private val b4 = SpelledPitch(Step.B, octave = 4)
    private val a5 = SpelledPitch(Step.A, octave = 5)
    private val c6 = SpelledPitch(Step.C, octave = 6)
    private val c2 = SpelledPitch(Step.C, octave = 2)
    private val fSharp4 = SpelledPitch(Step.F, alteration = 1, octave = 4)

    private val blackHead = BravuraMetrics.of(Glyph.NOTEHEAD_BLACK)

    private fun single(spelling: SpelledPitch, duration: Ticks = Ticks.QUARTER): StaffLayout =
        ScoreLayoutEngine.layout(Fixtures.oneMeasure(ScoreNote("n", spelling, Ticks.ZERO, duration)))

    private fun StaffLayout.glyphs(role: Role) = elements.filterIsInstance<GlyphElement>().filter { it.role == role }
    private fun StaffLayout.lines(role: Role) = elements.filterIsInstance<LineElement>().filter { it.role == role }
    private fun StaffLayout.head(noteId: String) = glyphs(Role.NOTEHEAD).single { it.noteId == noteId }
    private fun StaffLayout.stem(noteId: String) = lines(Role.STEM).singleOrNull { it.noteId == noteId }
    private fun StaffLayout.barline() = lines(Role.BARLINE).single()

    @Test
    fun `five staff lines span the width`() {
        val layout = ScoreLayoutEngine.layout(Fixtures.cdef)
        val lines = layout.lines(Role.STAFF_LINE)

        assertEquals(listOf(0.0, 1.0, 2.0, 3.0, 4.0), lines.map { it.y1 })
        lines.forEach { line ->
            assertEquals(line.y1, line.y2)
            assertEquals(0.0, line.x1)
            assertEquals(layout.width, line.x2)
            assertEquals(BravuraMetrics.STAFF_LINE_THICKNESS, line.thickness)
        }
    }

    @Test
    fun `the treble clef sits on the G line after the margin`() {
        val clef = ScoreLayoutEngine.layout(Fixtures.cdef).glyphs(Role.CLEF).single()

        assertEquals(Glyph.G_CLEF, clef.glyph)
        assertEquals(1.0, clef.y)
        assertEquals(ScoreLayoutEngine.LEFT_MARGIN, clef.x + BravuraMetrics.of(Glyph.G_CLEF).left)
    }

    @Test
    fun `the bass clef sits on the F line`() {
        val score = Score(TimeSignature.FOUR_FOUR, Clef.BASS, KeySignature.C_MAJOR, 1, listOf(ScoreNote("n", a3, Ticks.ZERO, Ticks.QUARTER)))
        val layout = ScoreLayoutEngine.layout(score)
        val clef = layout.glyphs(Role.CLEF).single()

        assertEquals(Glyph.F_CLEF, clef.glyph)
        assertEquals(3.0, clef.y)
        assertEquals(ScoreLayoutEngine.LEFT_MARGIN, clef.x + BravuraMetrics.of(Glyph.F_CLEF).left, 1e-9)
        // A3 is the top line of a bass staff.
        assertEquals(StaffPosition.TOP_LINE.y, layout.head("n").y)
    }

    @Test
    fun `the time signature stacks the beats over the unit, after the clef`() {
        val layout = ScoreLayoutEngine.layout(Fixtures.cdef)
        val clef = layout.glyphs(Role.CLEF).single()
        val digits = layout.glyphs(Role.TIME_SIGNATURE)

        assertEquals(listOf(Glyph.TIME_SIG_4, Glyph.TIME_SIG_4), digits.map { it.glyph })
        assertEquals(listOf(3.0, 1.0), digits.map { it.y })
        assertEquals(digits[0].x, digits[1].x)
        val clefRight = clef.x + BravuraMetrics.of(Glyph.G_CLEF).right
        assertEquals(clefRight + ScoreLayoutEngine.CLEF_GAP, digits[0].x + BravuraMetrics.of(Glyph.TIME_SIG_4).left, 1e-9)
    }

    @Test
    fun `a two digit time signature centres the shorter number`() {
        val score = Fixtures.oneMeasure(ScoreNote("n", a4, Ticks.ZERO, Ticks.EIGHTH), timeSignature = TimeSignature(12, 8))
        val digits = ScoreLayoutEngine.layout(score).glyphs(Role.TIME_SIGNATURE)

        assertEquals(listOf(Glyph.TIME_SIG_1, Glyph.TIME_SIG_2, Glyph.TIME_SIG_8), digits.map { it.glyph })
        val one = BravuraMetrics.of(Glyph.TIME_SIG_1)
        val two = BravuraMetrics.of(Glyph.TIME_SIG_2)
        val eight = BravuraMetrics.of(Glyph.TIME_SIG_8)
        val numeratorLeft = digits[0].x + one.left
        val numeratorRight = digits[1].x + two.right
        assertEquals(digits[0].x + one.right, digits[1].x + two.left, 1e-9)
        val denominatorCentre = digits[2].x + (eight.left + eight.right) / 2
        assertEquals((numeratorLeft + numeratorRight) / 2, denominatorCentre, 1e-9)
    }

    @Test
    fun `heads step up the staff in performance order`() {
        val layout = ScoreLayoutEngine.layout(Fixtures.cdef)
        val heads = listOf("n1", "n2", "n3", "n4").map { layout.head(it) }

        assertEquals(listOf(-1.0, -0.5, 0.0, 0.5), heads.map { it.y })
        assertTrue(heads.zipWithNext().all { (a, b) -> a.x < b.x })
        assertTrue(heads.all { it.glyph == Glyph.NOTEHEAD_BLACK })
        val timeSignatureRight = layout.glyphs(Role.TIME_SIGNATURE).maxOf { it.x + BravuraMetrics.of(it.glyph).right }
        assertEquals(timeSignatureRight + ScoreLayoutEngine.TIME_SIGNATURE_GAP, heads[0].x, 1e-9)
        heads.forEach { head ->
            val anchor = layout.anchors.getValue(head.noteId!!)
            assertEquals(head.x, anchor.x)
            assertEquals(head.y, anchor.y)
            assertEquals(blackHead.width, anchor.headWidth)
        }
    }

    @Test
    fun `the head shape follows the duration and whole notes have no stem`() {
        assertEquals(Glyph.NOTEHEAD_BLACK, single(a4, Ticks.QUARTER).head("n").glyph)
        assertEquals(Glyph.NOTEHEAD_BLACK, single(a4, Ticks.QUARTER.dotted()).head("n").glyph)
        assertEquals(Glyph.NOTEHEAD_HALF, single(a4, Ticks.HALF).head("n").glyph)
        assertEquals(Glyph.NOTEHEAD_HALF, single(a4, Ticks.HALF.dotted()).head("n").glyph)
        assertEquals(Glyph.NOTEHEAD_WHOLE, single(a4, Ticks.WHOLE).head("n").glyph)

        assertTrue(single(a4, Ticks.HALF).stem("n") != null)
        assertNull(single(a4, Ticks.WHOLE).stem("n"))
    }

    @Test
    fun `stems go up on the right below the middle line`() {
        val layout = single(a4)
        val head = layout.head("n")
        val stem = layout.stem("n")!!
        val anchor = blackHead.stemUpSE!!

        assertEquals(head.x + anchor.x - BravuraMetrics.STEM_THICKNESS / 2, stem.x1, 1e-9)
        assertEquals(stem.x1, stem.x2)
        assertEquals(head.y + anchor.y, stem.y1, 1e-9)
        assertEquals(stem.y1 + ScoreLayoutEngine.STEM_LENGTH, stem.y2, 1e-9)
        assertEquals(BravuraMetrics.STEM_THICKNESS, stem.thickness)
    }

    @Test
    fun `stems go down on the left from the middle line up`() {
        val layout = single(b4)
        val head = layout.head("n")
        val stem = layout.stem("n")!!
        val anchor = blackHead.stemDownNW!!

        assertEquals(head.x + anchor.x + BravuraMetrics.STEM_THICKNESS / 2, stem.x1, 1e-9)
        assertEquals(head.y + anchor.y, stem.y1, 1e-9)
        assertEquals(stem.y1 - ScoreLayoutEngine.STEM_LENGTH, stem.y2, 1e-9)
    }

    @Test
    fun `stems of notes on ledger lines reach the middle line`() {
        assertEquals(StaffPosition.MIDDLE_LINE.y, single(a3).stem("n")!!.y2)
        assertEquals(StaffPosition.MIDDLE_LINE.y, single(c6).stem("n")!!.y2)
        assertTrue(single(Fixtures.C4).stem("n")!!.y2 > StaffPosition.MIDDLE_LINE.y)
    }

    @Test
    fun `ledger lines are drawn through and past the head`() {
        val c4 = single(Fixtures.C4)
        val ledger = c4.lines(Role.LEDGER).single()
        val head = c4.head("n")

        assertEquals(-1.0, ledger.y1)
        assertEquals(ledger.y1, ledger.y2)
        assertEquals(head.x - BravuraMetrics.LEDGER_LINE_EXTENSION, ledger.x1, 1e-9)
        assertEquals(head.x + blackHead.width + BravuraMetrics.LEDGER_LINE_EXTENSION, ledger.x2, 1e-9)
        assertEquals(BravuraMetrics.LEDGER_LINE_THICKNESS, ledger.thickness)
        assertEquals("n", ledger.noteId)

        assertEquals(listOf(-1.0, -2.0), single(a3).lines(Role.LEDGER).map { it.y1 })
        assertEquals(listOf(5.0), single(a5).lines(Role.LEDGER).map { it.y1 })
        assertEquals(listOf(5.0, 6.0), single(c6).lines(Role.LEDGER).map { it.y1 })
        assertEquals(emptyList(), single(Fixtures.E4).lines(Role.LEDGER))
    }

    @Test
    fun `an accidental is drawn only when the alteration is not zero`() {
        val plain = single(Fixtures.F4)
        val sharp = single(fSharp4)

        assertEquals(emptyList(), plain.glyphs(Role.ACCIDENTAL))
        val accidental = sharp.glyphs(Role.ACCIDENTAL).single()
        val metrics = BravuraMetrics.of(Glyph.ACCIDENTAL_SHARP)
        assertEquals(Glyph.ACCIDENTAL_SHARP, accidental.glyph)
        assertEquals("n", accidental.noteId)
        assertEquals(sharp.head("n").y, accidental.y)
        assertEquals(sharp.head("n").x - Spacing.ACCIDENTAL_GAP, accidental.x + metrics.right, 1e-9)
        assertEquals(plain.head("n").x + metrics.width + Spacing.ACCIDENTAL_GAP, sharp.head("n").x, 1e-9)
    }

    @Test
    fun `every note element belongs to a note and every note has an anchor`() {
        val score = Fixtures.oneMeasure(
            ScoreNote("a", a3, Ticks.ZERO, Ticks.HALF),
            ScoreNote("b", fSharp4, Ticks.HALF, Ticks.QUARTER),
            ScoreNote("c", c6, Ticks.quarters(3), Ticks.QUARTER),
        )
        val layout = ScoreLayoutEngine.layout(score)
        val ids = score.notes.map { it.id }.toSet()

        assertEquals(ids, layout.anchors.keys)
        layout.elements.forEach { element ->
            val noteId = element.noteId
            if (noteId != null) assertTrue(noteId in ids, "$element")
            val expectsNote = element.role in setOf(Role.NOTEHEAD, Role.STEM, Role.LEDGER, Role.ACCIDENTAL)
            assertEquals(expectsNote, noteId != null, "$element")
        }
    }

    @Test
    fun `longer notes get more room`() {
        val score = Fixtures.oneMeasure(
            ScoreNote("h", Fixtures.F4, Ticks.ZERO, Ticks.HALF),
            ScoreNote("q1", Fixtures.E4, Ticks.HALF, Ticks.QUARTER),
            ScoreNote("q2", Fixtures.D4, Ticks.quarters(3), Ticks.QUARTER),
        )
        val layout = ScoreLayoutEngine.layout(score)
        val half = layout.head("h").x
        val first = layout.head("q1").x
        val second = layout.head("q2").x

        assertEquals(Spacing.advanceFor(Ticks.HALF, blackHead.width), first - half, 1e-9)
        assertEquals(Spacing.advanceFor(Ticks.QUARTER, blackHead.width), second - first, 1e-9)
        assertTrue(first - half > second - first)
    }

    @Test
    fun `the time axis interpolates between onsets and clamps at the ends`() {
        val layout = ScoreLayoutEngine.layout(Fixtures.cdef)
        val heads = listOf("n1", "n2", "n3", "n4").map { layout.head(it).x }
        val endX = layout.barline().x1 - blackHead.width - Spacing.CUE_GAP

        assertEquals(heads[0], layout.xAtTicks(Ticks.ZERO))
        assertEquals(heads[1], layout.xAtTicks(Ticks.QUARTER))
        assertEquals((heads[0] + heads[1]) / 2, layout.xAtTicks(Ticks.EIGHTH), 1e-9)
        assertEquals(heads[3] + (endX - heads[3]) / 4, layout.xAtTicks(Ticks.quarters(3) + Ticks.SIXTEENTH), 1e-9)
        assertEquals(endX, layout.xAtTicks(Ticks.WHOLE))
        assertEquals(endX, layout.xAtTicks(Ticks(99_999)))
    }

    @Test
    fun `the barline closes the measure and sets the width`() {
        val layout = ScoreLayoutEngine.layout(Fixtures.cdef)
        val barline = layout.barline()
        val last = layout.head("n4").x

        assertEquals(last + Spacing.advanceFor(Ticks.QUARTER, blackHead.width), barline.x1, 1e-9)
        assertEquals(barline.x1, barline.x2)
        assertEquals(0.0, barline.y1)
        assertEquals(4.0, barline.y2)
        assertEquals(BravuraMetrics.THIN_BARLINE_THICKNESS, barline.thickness)
        assertEquals(barline.x1 + barline.thickness / 2 + ScoreLayoutEngine.RIGHT_MARGIN, layout.width, 1e-9)
        assertEquals(layout.elements.last(), barline)
    }

    @Test
    fun `the envelope is fixed for staff range content and widens beyond it`() {
        val usual = ScoreLayoutEngine.layout(Fixtures.cdef)
        assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, usual.top)
        assertEquals(ScoreLayoutEngine.ENVELOPE_BOTTOM, usual.bottom)

        val low = single(c2)
        assertEquals(low.head("n").y + blackHead.bottom, low.bottom)
        assertTrue(low.bottom < ScoreLayoutEngine.ENVELOPE_BOTTOM)
    }

    @Test
    fun `an empty measure still has a clef, a signature and a barline`() {
        val layout = ScoreLayoutEngine.layout(Fixtures.oneMeasure())

        assertEquals(1, layout.glyphs(Role.CLEF).size)
        assertEquals(2, layout.glyphs(Role.TIME_SIGNATURE).size)
        assertEquals(1, layout.lines(Role.BARLINE).size)
        assertTrue(layout.anchors.isEmpty())
        assertEquals(layout.timeAxis.single().x, layout.xAtTicks(Ticks.ZERO))
    }
}
