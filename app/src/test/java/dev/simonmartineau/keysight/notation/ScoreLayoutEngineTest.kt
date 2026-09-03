package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.Staff
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

    /** One system at natural width, the time signature shown. */
    private fun layout(score: Score): SystemLayout = ScoreLayoutEngine.layoutSystem(score, 0, null, showTimeSignature = true)

    private fun grandStaff(vararg notes: ScoreNote, key: KeySignature = KeySignature.C_MAJOR, measures: Int = 1) = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        keySignature = key,
        staves = listOf(Staff(Clef.TREBLE), Staff(Clef.BASS)),
        measureCount = measures,
        notes = notes.toList(),
    )

    private fun inKey(key: KeySignature, vararg notes: ScoreNote, measures: Int = 1) =
        Fixtures.measures(measures, *notes).copy(keySignature = key)

    private fun note(id: String, spelling: SpelledPitch, onset: Ticks, duration: Ticks = Ticks.QUARTER, staff: Int = 0) =
        ScoreNote(id, spelling, onset, duration, staff = staff)

    private fun single(spelling: SpelledPitch, duration: Ticks = Ticks.QUARTER): SystemLayout =
        layout(Fixtures.oneMeasure(ScoreNote("n", spelling, Ticks.ZERO, duration)))

    private fun SystemLayout.glyphs(role: Role) = elements.filterIsInstance<GlyphElement>().filter { it.role == role }
    private fun SystemLayout.lines(role: Role) = elements.filterIsInstance<LineElement>().filter { it.role == role }
    private fun SystemLayout.head(noteId: String) = glyphs(Role.NOTEHEAD).single { it.noteId == noteId }
    private fun SystemLayout.stem(noteId: String) = lines(Role.STEM).singleOrNull { it.noteId == noteId }
    private fun SystemLayout.flag(noteId: String) = glyphs(Role.FLAG).singleOrNull { it.noteId == noteId }
    private fun SystemLayout.beams() = elements.filterIsInstance<BeamElement>()

    /** One measure of eighths named e1, e2 and so on, each at its onset in ticks. */
    private fun eighths(vararg notes: Pair<SpelledPitch, Int>): SystemLayout = layout(
        Fixtures.oneMeasure(*notes.mapIndexed { index, (spelling, onset) -> ScoreNote("e${index + 1}", spelling, Ticks(onset), Ticks.EIGHTH) }.toTypedArray()),
    )

    /** The y of the beam's centre line at [x]. */
    private fun BeamElement.yAt(x: Double) = y1 + (y2 - y1) * (x - x1) / (x2 - x1)
    private fun SystemLayout.barlines() = lines(Role.BARLINE)
    private fun SystemLayout.barline() = barlines().first()

    @Test
    fun `five staff lines span the width`() {
        val layout = layout(Fixtures.cdef)
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
        val clef = layout(Fixtures.cdef).glyphs(Role.CLEF).single()

        assertEquals(Glyph.G_CLEF, clef.glyph)
        assertEquals(1.0, clef.y)
        assertEquals(ScoreLayoutEngine.LEFT_MARGIN, clef.x + BravuraMetrics.of(Glyph.G_CLEF).left)
    }

    @Test
    fun `the bass clef sits on the F line`() {
        val score = Fixtures.oneMeasure(ScoreNote("n", a3, Ticks.ZERO, Ticks.QUARTER)).copy(staves = listOf(Staff(Clef.BASS)))
        val layout = layout(score)
        val clef = layout.glyphs(Role.CLEF).single()

        assertEquals(Glyph.F_CLEF, clef.glyph)
        assertEquals(3.0, clef.y)
        assertEquals(ScoreLayoutEngine.LEFT_MARGIN, clef.x + BravuraMetrics.of(Glyph.F_CLEF).left, 1e-9)
        // A3 is the top line of a bass staff.
        assertEquals(StaffPosition.TOP_LINE.y, layout.head("n").y)
    }

    @Test
    fun `the time signature stacks the beats over the unit, after the clef`() {
        val layout = layout(Fixtures.cdef)
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
        val digits = layout(score).glyphs(Role.TIME_SIGNATURE)

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
        val layout = layout(Fixtures.cdef)
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

    /** Every accidental sits on the head's line by its origin, its right edge a gap left of the head, and the column makes room for it. */
    @Test
    fun `a flat and a natural are placed like the sharp, and the column grows by their width`() {
        val bFlat4 = SpelledPitch(Step.B, alteration = -1, octave = 4)
        val flat = single(bFlat4)
        val flatGlyph = flat.glyphs(Role.ACCIDENTAL).single()
        val flatMetrics = BravuraMetrics.of(Glyph.ACCIDENTAL_FLAT)
        assertEquals(Glyph.ACCIDENTAL_FLAT, flatGlyph.glyph)
        assertEquals(flat.head("n").y, flatGlyph.y)
        assertEquals(flat.head("n").x - Spacing.ACCIDENTAL_GAP, flatGlyph.x + flatMetrics.right, 1e-9)
        assertEquals(single(b4).head("n").x + flatMetrics.width + Spacing.ACCIDENTAL_GAP, flat.head("n").x, 1e-9)

        val natural = layout(inKey(KeySignature(-2), note("n", b4, Ticks.ZERO)))
        val naturalGlyph = natural.glyphs(Role.ACCIDENTAL).single()
        val naturalMetrics = BravuraMetrics.of(Glyph.ACCIDENTAL_NATURAL)
        assertEquals(Glyph.ACCIDENTAL_NATURAL, naturalGlyph.glyph)
        assertEquals(natural.head("n").y, naturalGlyph.y)
        assertEquals(natural.head("n").x - Spacing.ACCIDENTAL_GAP, naturalGlyph.x + naturalMetrics.right, 1e-9)
        val inKeyFlat = layout(inKey(KeySignature(-2), note("n", bFlat4, Ticks.ZERO)))
        assertEquals(emptyList(), inKeyFlat.glyphs(Role.ACCIDENTAL), "the key's own flat is not written")
        assertEquals(inKeyFlat.head("n").x + naturalMetrics.width + Spacing.ACCIDENTAL_GAP, natural.head("n").x, 1e-9)

        // An accidental in the middle of the bar never reaches back into the column before it.
        val stepped = layout(Fixtures.oneMeasure(note("a", Fixtures.C4, Ticks.ZERO), note("b", SpelledPitch(Step.C, 1, 4), Ticks.QUARTER)))
        val sharp = stepped.glyphs(Role.ACCIDENTAL).single()
        assertTrue(sharp.x + BravuraMetrics.of(Glyph.ACCIDENTAL_SHARP).left > stepped.head("a").x + blackHead.width, "the sharp clears the head before it")
        assertEquals(Ticks.QUARTER, sharp.ticks, "the accidental carries its note's onset, so the mask hides it with the head")
        assertEquals(
            Spacing.advanceFor(Ticks.QUARTER, blackHead.width) + BravuraMetrics.of(Glyph.ACCIDENTAL_SHARP).width + Spacing.ACCIDENTAL_GAP,
            stepped.head("b").x - stepped.head("a").x,
            1e-9,
            "the column is the quarter's room plus the sharp's, and the sharp's room never stretches",
        )
    }

    /** The memory is per letter and octave, per staff, per measure; a natural cancels an earlier sharp as it cancels the key. */
    @Test
    fun `an accidental is remembered per octave and per staff and cancelled by a natural`() {
        val fSharp5 = SpelledPitch(Step.F, alteration = 1, octave = 5)
        val twice = layout(Fixtures.oneMeasure(note("a", fSharp4, Ticks.ZERO), note("b", fSharp4, Ticks.QUARTER), note("c", fSharp5, Ticks.HALF), note("d", Fixtures.F4, Ticks.quarters(3))))
        assertEquals(
            listOf("a" to Glyph.ACCIDENTAL_SHARP, "c" to Glyph.ACCIDENTAL_SHARP, "d" to Glyph.ACCIDENTAL_NATURAL),
            twice.glyphs(Role.ACCIDENTAL).map { it.noteId to it.glyph },
            "the second F sharp is remembered, the octave above is not, and the natural restores the letter",
        )

        val fSharp3 = SpelledPitch(Step.F, alteration = 1, octave = 3)
        val grand = layout(grandStaff(note("t", fSharp3, Ticks.ZERO), note("b", fSharp3, Ticks.QUARTER, staff = 1)))
        assertEquals(listOf("t", "b"), grand.glyphs(Role.ACCIDENTAL).map { it.noteId }, "each staff remembers its own")

        val restored = layout(inKey(KeySignature(2), note("a", fSharp4, Ticks.ZERO), note("b", Fixtures.F4, Ticks.QUARTER), note("c", fSharp4, Ticks.HALF), note("d", fSharp4, Ticks.quarters(3))))
        assertEquals(
            listOf("b" to Glyph.ACCIDENTAL_NATURAL, "c" to Glyph.ACCIDENTAL_SHARP),
            restored.glyphs(Role.ACCIDENTAL).map { it.noteId to it.glyph },
            "in D the key's F sharp is unwritten, the natural cancels it, and the sharp after the natural is written again, once",
        )
    }

    @Test
    fun `every note element belongs to a note and every note has an anchor`() {
        val score = Fixtures.oneMeasure(
            ScoreNote("a", a3, Ticks.ZERO, Ticks.HALF),
            ScoreNote("b", fSharp4, Ticks.HALF, Ticks.QUARTER),
            ScoreNote("c", c6, Ticks.quarters(3), Ticks.QUARTER),
        )
        val layout = layout(score)
        val ids = score.notes.map { it.id }.toSet()

        assertEquals(ids, layout.anchors.keys)
        layout.elements.forEach { element ->
            val noteId = element.noteId
            if (noteId != null) assertTrue(noteId in ids, "$element")
            val expectsNote = element.role in setOf(Role.NOTEHEAD, Role.STEM, Role.LEDGER, Role.ACCIDENTAL, Role.FLAG)
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
        val layout = layout(score)
        val half = layout.head("h").x
        val first = layout.head("q1").x
        val second = layout.head("q2").x

        assertEquals(Spacing.advanceFor(Ticks.HALF, blackHead.width), first - half, 1e-9)
        assertEquals(Spacing.advanceFor(Ticks.QUARTER, blackHead.width), second - first, 1e-9)
        assertTrue(first - half > second - first)
    }

    @Test
    fun `the time axis interpolates between onsets and clamps at the ends`() {
        val layout = layout(Fixtures.cdef)
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
    fun `the final barline closes the score, thin then thick, and sets the width`() {
        val layout = layout(Fixtures.cdef)
        val (thin, thick) = layout.barlines()
        val last = layout.head("n4").x

        assertEquals(last + Spacing.advanceFor(Ticks.QUARTER, blackHead.width), thin.x1, 1e-9)
        assertEquals(thin.x1, thin.x2)
        assertEquals(0.0, thin.y1)
        assertEquals(4.0, thin.y2)
        assertEquals(BravuraMetrics.THIN_BARLINE_THICKNESS, thin.thickness)
        assertEquals(BravuraMetrics.THICK_BARLINE_THICKNESS, thick.thickness)
        assertEquals(thin.x1 + thin.thickness / 2 + ScoreLayoutEngine.FINAL_BARLINE_GAP + thick.thickness / 2, thick.x1, 1e-9)
        assertEquals(thick.x1 + thick.thickness / 2 + ScoreLayoutEngine.RIGHT_MARGIN, layout.width, 1e-9)
        assertEquals(2, layout.barlines().size)
    }

    @Test
    fun `measures within a system are separated by thin barlines and a gap`() {
        val score = inKey(
            KeySignature.C_MAJOR,
            note("a", Fixtures.C4, Ticks.ZERO, Ticks.WHOLE),
            note("b", Fixtures.D4, Ticks.WHOLE, Ticks.WHOLE),
            note("c", Fixtures.E4, Ticks.WHOLE * 2, Ticks.WHOLE),
            measures = 3,
        )
        val layout = layout(score)
        val barlines = layout.barlines()

        assertEquals(0..2, layout.measures)
        assertEquals(TickRange(Ticks.ZERO, Ticks.WHOLE * 3), layout.ticks)
        assertEquals(4, barlines.size)
        assertTrue(barlines.dropLast(1).all { it.thickness == BravuraMetrics.THIN_BARLINE_THICKNESS })
        val wholeHead = BravuraMetrics.of(Glyph.NOTEHEAD_WHOLE)
        assertEquals(layout.head("a").x + Spacing.advanceFor(Ticks.WHOLE, wholeHead.width), barlines[0].x1, 1e-9)
        assertEquals(barlines[0].x1 + barlines[0].thickness / 2 + ScoreLayoutEngine.MEASURE_START_GAP, layout.head("b").x, 1e-9)
        assertEquals(listOf(Ticks.ZERO, Ticks.WHOLE, Ticks.WHOLE * 2, Ticks.WHOLE * 3), layout.timeAxis.map { it.ticks })
    }

    @Test
    fun `a system packs the measures that fit its width and stretches columns to fill it`() {
        val score = inKey(
            KeySignature.C_MAJOR,
            note("a", Fixtures.C4, Ticks.ZERO, Ticks.WHOLE),
            note("b", Fixtures.D4, Ticks.WHOLE, Ticks.WHOLE),
            note("c", Fixtures.E4, Ticks.WHOLE * 2, Ticks.WHOLE),
            measures = 3,
        )
        val natural = layout(score)
        val twoMeasures = ScoreLayoutEngine.layoutSystem(score, 0, natural.width - 1.0, showTimeSignature = true)
        assertEquals(0..1, twoMeasures.measures)
        assertEquals(natural.width - 1.0, twoMeasures.width, 1e-9)

        val naturalTwo = ScoreLayoutEngine.layoutSystem(score.copy(measureCount = 2, notes = score.notes.take(2)), 0, null, showTimeSignature = true)
        // Only the room after each note grew; the header did not move.
        assertEquals(naturalTwo.head("a").x, twoMeasures.head("a").x, 1e-9)
        assertTrue(twoMeasures.head("b").x - twoMeasures.head("a").x > naturalTwo.head("b").x - naturalTwo.head("a").x)

        val wide = ScoreLayoutEngine.layoutSystem(score, 0, natural.width * 10, showTimeSignature = true)
        val wholeHead = BravuraMetrics.of(Glyph.NOTEHEAD_WHOLE)
        val advance = Spacing.advanceFor(Ticks.WHOLE, wholeHead.width)
        assertEquals(wide.head("a").x + advance * ScoreLayoutEngine.MAX_STRETCH, wide.barlines()[0].x1, 1e-9)
        assertTrue(wide.width < natural.width * 10)

        val rest = ScoreLayoutEngine.layoutSystem(score, 2, null, showTimeSignature = false)
        assertEquals(2..2, rest.measures)
        assertEquals(emptyList(), rest.glyphs(Role.TIME_SIGNATURE))
        assertEquals(1, rest.glyphs(Role.CLEF).size)
    }

    @Test
    fun `every element carries the tick of its note and structure carries none`() {
        val layout = layout(Fixtures.cdef)

        layout.elements.forEach { element ->
            val expected = element.noteId?.let { id -> Fixtures.cdef.notes.single { it.id == id }.onset }
            assertEquals(expected, element.ticks, "$element")
        }
        assertEquals(Ticks.QUARTER, layout.anchors.getValue("n2").ticks)
    }

    @Test
    fun `the envelope is fixed for staff range content and widens beyond it`() {
        val usual = layout(Fixtures.cdef)
        assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, usual.top)
        assertEquals(ScoreLayoutEngine.ENVELOPE_BOTTOM, usual.bottom)

        val low = single(c2)
        assertEquals(low.head("n").y + blackHead.bottom, low.bottom)
        assertTrue(low.bottom < ScoreLayoutEngine.ENVELOPE_BOTTOM)
    }

    @Test
    fun `an empty measure shows a whole rest and still has a clef, a signature and a barline`() {
        val layout = layout(Fixtures.oneMeasure())

        assertEquals(1, layout.glyphs(Role.CLEF).size)
        assertEquals(2, layout.glyphs(Role.TIME_SIGNATURE).size)
        assertEquals(2, layout.lines(Role.BARLINE).size)
        assertTrue(layout.anchors.isEmpty())
        val rest = layout.glyphs(Role.REST).single()
        assertEquals(Glyph.REST_WHOLE, rest.glyph)
        assertEquals(3.0, rest.y)
        assertNull(rest.ticks, "a whole-measure rest is not content and is never masked")
        val measureStart = layout.timeAxis.first().x
        val metrics = BravuraMetrics.of(Glyph.REST_WHOLE)
        assertEquals((measureStart + layout.barline().x1) / 2, rest.x + metrics.left + metrics.width / 2, 1e-9)
        assertEquals(measureStart, layout.xAtTicks(Ticks.ZERO))
        assertTrue(layout.xAtTicks(Ticks.WHOLE) > measureStart)
    }

    @Test
    fun `the key signature writes the sharps and flats in order on every staff`() {
        val treble = inKey(KeySignature(3), note("n", Fixtures.C4, Ticks.ZERO))
        val sharps = layout(treble).glyphs(Role.KEY_SIGNATURE)
        assertEquals(List(3) { Glyph.ACCIDENTAL_SHARP }, sharps.map { it.glyph })
        // F5 C5 G5 in treble clef: positions 8, 5, 9.
        assertEquals(listOf(4.0, 2.5, 4.5), sharps.map { it.y })
        val sharp = BravuraMetrics.of(Glyph.ACCIDENTAL_SHARP)
        assertEquals(sharps[0].x + sharp.width + ScoreLayoutEngine.KEY_ACCIDENTAL_GAP, sharps[1].x, 1e-9)
        val clefRight = layout(treble).glyphs(Role.CLEF).single().let { it.x + BravuraMetrics.of(Glyph.G_CLEF).right }
        assertEquals(clefRight + ScoreLayoutEngine.CLEF_GAP, sharps[0].x + sharp.left, 1e-9)
        val signatureRight = sharps[2].x + sharp.right
        assertEquals(signatureRight + ScoreLayoutEngine.KEY_SIGNATURE_GAP, layout(treble).glyphs(Role.TIME_SIGNATURE)[0].x + BravuraMetrics.of(Glyph.TIME_SIG_4).left, 1e-9)

        val grand = grandStaff(note("n", Fixtures.C4, Ticks.ZERO), key = KeySignature(-2))
        val flats = layout(grand).glyphs(Role.KEY_SIGNATURE)
        assertEquals(4, flats.size)
        // B4 E5 in treble: 4, 7; B2 E3 in bass: 2, 5, one staff distance lower.
        assertEquals(listOf(2.0, 3.5), flats.take(2).map { it.y })
        assertEquals(listOf(1.0, 2.5).map { it - ScoreLayoutEngine.STAFF_DISTANCE }, flats.drop(2).map { it.y })
        assertEquals(flats[0].x, flats[2].x)
    }

    @Test
    fun `accidentals are written against the key and remembered within the measure`() {
        val eFlat4 = SpelledPitch(Step.E, alteration = -1, octave = 4)
        val eFlat5 = SpelledPitch(Step.E, alteration = -1, octave = 5)
        val score = inKey(
            KeySignature(-3),
            note("a", eFlat4, Ticks.ZERO),
            note("b", Fixtures.E4, Ticks.QUARTER),
            note("c", Fixtures.E4, Ticks.quarters(2)),
            note("d", eFlat5, Ticks.quarters(3)),
        )
        val accidentals = layout(score).glyphs(Role.ACCIDENTAL)

        assertEquals(listOf("b" to Glyph.ACCIDENTAL_NATURAL), accidentals.map { it.noteId to it.glyph })

        val nextMeasure = inKey(
            KeySignature(-3),
            note("a", Fixtures.E4, Ticks.ZERO, Ticks.WHOLE),
            note("b", Fixtures.E4, Ticks.WHOLE, Ticks.WHOLE),
            measures = 2,
        )
        assertEquals(listOf("a", "b"), layout(nextMeasure).glyphs(Role.ACCIDENTAL).map { it.noteId })
    }

    @Test
    fun `a grand staff has a brace, clefs on both staves and barlines spanning them`() {
        val score = grandStaff(
            note("t", Fixtures.G4, Ticks.ZERO, Ticks.HALF),
            note("b", SpelledPitch(Step.C, octave = 3), Ticks.ZERO, Ticks.HALF, staff = 1),
        )
        val layout = layout(score)

        assertEquals(listOf(0.0, -ScoreLayoutEngine.STAFF_DISTANCE), layout.staves.map { it.baselineY })
        assertEquals(10, layout.lines(Role.STAFF_LINE).size)
        val brace = layout.glyphs(Role.BRACE).single()
        val metrics = BravuraMetrics.of(Glyph.BRACE)
        assertEquals(ScoreLayoutEngine.LEFT_MARGIN, brace.x + metrics.left * brace.scale, 1e-9)
        assertEquals(-ScoreLayoutEngine.STAFF_DISTANCE, brace.y + metrics.bottom * brace.scale, 1e-9)
        assertEquals(StaffPosition.TOP_LINE.y, brace.y + metrics.top * brace.scale, 1e-9)
        val clefs = layout.glyphs(Role.CLEF)
        assertEquals(listOf(Glyph.G_CLEF, Glyph.F_CLEF), clefs.map { it.glyph })
        assertEquals(listOf(1.0, 3.0 - ScoreLayoutEngine.STAFF_DISTANCE), clefs.map { it.y })
        assertEquals(clefs[0].x + BravuraMetrics.of(Glyph.G_CLEF).left, clefs[1].x + BravuraMetrics.of(Glyph.F_CLEF).left, 1e-9)
        layout.barlines().forEach { barline ->
            assertEquals(-ScoreLayoutEngine.STAFF_DISTANCE, barline.y1)
            assertEquals(StaffPosition.TOP_LINE.y, barline.y2)
        }
        // The two notes share one column, each on its own staff.
        val treble = layout.anchors.getValue("t")
        val bass = layout.anchors.getValue("b")
        assertEquals(treble.x, bass.x)
        assertEquals(0, treble.staff)
        assertEquals(1, bass.staff)
        assertEquals(1.0, treble.y)
        assertEquals(1.5 - ScoreLayoutEngine.STAFF_DISTANCE, bass.y, 1e-9)
        // C3 is below the bass staff's middle line: its stem goes up from the head, an octave long.
        val bassStem = layout.stem("b")!!
        assertEquals(bass.y + blackHead.stemUpSE!!.y, bassStem.y1, 1e-9)
        assertEquals(bassStem.y1 + ScoreLayoutEngine.STEM_LENGTH, bassStem.y2, 1e-9)
        assertEquals(-ScoreLayoutEngine.STAFF_DISTANCE + ScoreLayoutEngine.ENVELOPE_BOTTOM, layout.bottom)
        assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, layout.top)
        // Each staff is silent for the second half of the bar: a half rest on each, in one column.
        val rests = layout.glyphs(Role.REST)
        assertEquals(listOf(Glyph.REST_HALF, Glyph.REST_HALF), rests.map { it.glyph })
        assertEquals(listOf(StaffPosition.MIDDLE_LINE.y, StaffPosition.MIDDLE_LINE.y - ScoreLayoutEngine.STAFF_DISTANCE), rests.map { it.y })
        assertEquals(rests[0].x, rests[1].x)
    }

    @Test
    fun `a staff with nothing in a measure rests while the other plays`() {
        val layout = layout(grandStaff(note("t", Fixtures.G4, Ticks.ZERO, Ticks.WHOLE)))
        val rest = layout.glyphs(Role.REST).single()

        assertEquals(3.0 - ScoreLayoutEngine.STAFF_DISTANCE, rest.y)
        assertNull(rest.ticks)
    }

    private fun SystemLayout.rests() = glyphs(Role.REST)

    @Test
    fun `a beat's silence is a quarter rest on the middle line, in a column of its own`() {
        val layout = layout(
            Fixtures.oneMeasure(
                note("a", Fixtures.C4, Ticks.ZERO),
                note("b", Fixtures.E4, Ticks.HALF),
                note("c", Fixtures.D4, Ticks.quarters(3)),
            ),
        )
        val rest = layout.rests().single()
        val metrics = BravuraMetrics.of(Glyph.REST_QUARTER)

        assertEquals(Glyph.REST_QUARTER, rest.glyph)
        assertEquals(StaffPosition.MIDDLE_LINE.y, rest.y)
        assertNull(rest.noteId)
        assertEquals(Ticks.QUARTER, rest.ticks, "a rest inside a bar is content and carries its onset")
        assertTrue(Mask.ALL.hides(rest))
        assertTrue(!Mask(listOf(TickRange(Ticks.HALF, Ticks.WHOLE))).hides(rest))
        val restLeft = rest.x + metrics.left
        assertEquals(layout.head("a").x + Spacing.advanceFor(Ticks.QUARTER, blackHead.width), restLeft, 1e-9, "the rest sits where a head would")
        assertEquals(restLeft + Spacing.advanceFor(Ticks.QUARTER, metrics.width), layout.head("b").x, 1e-9, "and takes the room a quarter takes")
        assertEquals(listOf(Ticks.ZERO, Ticks.QUARTER, Ticks.HALF, Ticks.quarters(3), Ticks.WHOLE), layout.timeAxis.map { it.ticks })
        assertEquals(restLeft, layout.xAtTicks(Ticks.QUARTER))
        assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, layout.top)
        assertEquals(ScoreLayoutEngine.ENVELOPE_BOTTOM, layout.bottom)
    }

    @Test
    fun `a half rest sits on the middle line on beat 1 or 3, and beat 2's silence is two quarter rests`() {
        val onThree = layout(Fixtures.oneMeasure(note("a", Fixtures.C4, Ticks.ZERO), note("b", Fixtures.D4, Ticks.QUARTER)))
        val half = onThree.rests().single()
        assertEquals(Glyph.REST_HALF, half.glyph)
        assertEquals(StaffPosition.MIDDLE_LINE.y, half.y)
        assertTrue(half.y + BravuraMetrics.of(Glyph.REST_HALF).bottom >= StaffPosition.MIDDLE_LINE.y - 0.01, "it sits on the line")
        assertEquals(onThree.head("b").x + Spacing.advanceFor(Ticks.QUARTER, blackHead.width), half.x + BravuraMetrics.of(Glyph.REST_HALF).left, 1e-9)

        val onOne = layout(Fixtures.oneMeasure(note("a", Fixtures.C4, Ticks.HALF, Ticks.HALF)))
        val opening = onOne.rests().single()
        assertEquals(Glyph.REST_HALF, opening.glyph)
        assertEquals(onOne.timeAxis.first().x, opening.x + BravuraMetrics.of(Glyph.REST_HALF).left, 1e-9)
        assertEquals(Ticks.ZERO, onOne.timeAxis.first().ticks)

        val onTwo = layout(Fixtures.oneMeasure(note("a", Fixtures.C4, Ticks.ZERO), note("b", Fixtures.D4, Ticks.quarters(3))))
        val quarters = onTwo.rests()
        assertEquals(listOf(Glyph.REST_QUARTER, Glyph.REST_QUARTER), quarters.map { it.glyph }, "a half rest never crosses the middle of the measure")
        assertTrue(quarters[0].x < quarters[1].x)
        assertEquals(listOf(Ticks.QUARTER, Ticks.HALF), quarters.map { it.ticks })
        assertEquals(listOf(Ticks.ZERO, Ticks.QUARTER, Ticks.HALF, Ticks.quarters(3), Ticks.WHOLE), onTwo.timeAxis.map { it.ticks })
    }

    @Test
    fun `an eighth rest stays inside its beat and the eighths beside it keep their flags`() {
        val layout = layout(
            Fixtures.oneMeasure(
                note("a", Fixtures.C4, Ticks.ZERO, Ticks.EIGHTH),
                note("b", Fixtures.D4, Ticks.QUARTER + Ticks.EIGHTH, Ticks.EIGHTH),
                note("c", Fixtures.E4, Ticks.HALF, Ticks.HALF),
            ),
        )
        val rests = layout.rests()

        assertEquals(listOf(Glyph.REST_8TH, Glyph.REST_8TH), rests.map { it.glyph }, "the silence from the off-beat to the next off-beat is two eighth rests")
        rests.forEach { assertEquals(StaffPosition.MIDDLE_LINE.y, it.y) }
        assertEquals(listOf("a", "b"), layout.glyphs(Role.FLAG).map { it.noteId })
        assertEquals(emptyList(), layout.beams())
        assertEquals(listOf(0, 480, 960, 1440, 1920, 3840), layout.timeAxis.map { it.ticks.value })
        assertEquals(layout.head("a").x + Spacing.advanceFor(Ticks.EIGHTH, blackHead.width), rests[0].x + BravuraMetrics.of(Glyph.REST_8TH).left, 1e-9)
    }

    @Test
    fun `the splitting rule fills a silence with the largest aligned rests`() {
        val fourFour = TimeSignature.FOUR_FOUR
        fun split(from: Int, to: Int, timeSignature: TimeSignature = fourFour) =
            ScoreLayoutEngine.restsFilling(Ticks(from), Ticks(to), timeSignature).map { (onset, duration) -> onset.value to duration.value }

        assertEquals(listOf(0 to 1920), split(0, 1920))
        assertEquals(listOf(1920 to 1920), split(1920, 3840))
        assertEquals(listOf(960 to 960, 1920 to 960), split(960, 2880), "a half's worth on beat 2 is two quarter rests")
        assertEquals(listOf(960 to 960, 1920 to 1920), split(960, 3840))
        assertEquals(listOf(480 to 480, 960 to 480), split(480, 1440), "an eighth rest never crosses a beat")
        assertEquals(listOf(480 to 480, 960 to 960, 1920 to 480), split(480, 2400))
        assertEquals(listOf(0 to 3840), split(0, 3840))
        assertEquals(listOf(0 to 1920), split(0, 1920, TimeSignature.THREE_FOUR))
        assertEquals(listOf(960 to 960, 1920 to 960), split(960, 2880, TimeSignature.THREE_FOUR), "in 3/4 a half rest only opens the measure")
        assertEquals(listOf(240 to 480, 720 to 480), split(240, 960), "nothing aligns with a sixteenth's offset: the largest that fits, then an eighth")
    }

    @Test
    fun `a rest on one staff of a grand staff shares its column with the other staff's note`() {
        val layout = layout(
            grandStaff(
                note("t1", Fixtures.G4, Ticks.ZERO, Ticks.HALF),
                note("t2", Fixtures.G4, Ticks.HALF, Ticks.HALF),
                note("b1", SpelledPitch(Step.C, octave = 3), Ticks.ZERO, Ticks.HALF, staff = 1),
            ),
        )
        val rest = layout.rests().single()

        assertEquals(Glyph.REST_HALF, rest.glyph)
        assertEquals(StaffPosition.MIDDLE_LINE.y - ScoreLayoutEngine.STAFF_DISTANCE, rest.y, 1e-9)
        assertEquals(layout.head("t2").x, rest.x + BravuraMetrics.of(Glyph.REST_HALF).left, 1e-9)
        assertEquals(3, layout.timeAxis.size)
    }

    @Test
    fun `a lone eighth hangs a flag from its stem tip, up on the right and down on the left`() {
        val up = single(Fixtures.C4, Ticks.EIGHTH)
        val upStem = up.stem("n")!!
        val upFlag = up.flag("n")!!
        val upAnchor = BravuraMetrics.of(Glyph.FLAG_8TH_UP).stemUpNW!!
        assertEquals(Glyph.FLAG_8TH_UP, upFlag.glyph)
        assertEquals(upStem.x1 - BravuraMetrics.STEM_THICKNESS / 2, upFlag.x + upAnchor.x, 1e-9, "the anchor is the stem's top left corner")
        assertEquals(upStem.y2, upFlag.y + upAnchor.y, 1e-9)
        assertEquals(upStem.y1 + ScoreLayoutEngine.STEM_LENGTH, upStem.y2, 1e-9, "a flag does not lengthen the stem")
        assertEquals(Ticks.ZERO, upFlag.ticks)
        assertEquals(emptyList(), up.beams())

        val down = single(c6, Ticks.EIGHTH)
        val downStem = down.stem("n")!!
        val downFlag = down.flag("n")!!
        val downAnchor = BravuraMetrics.of(Glyph.FLAG_8TH_DOWN).stemDownSW!!
        assertEquals(Glyph.FLAG_8TH_DOWN, downFlag.glyph)
        assertEquals(downStem.x1 - BravuraMetrics.STEM_THICKNESS / 2, downFlag.x + downAnchor.x, 1e-9, "the anchor is the stem's bottom left corner")
        assertEquals(downStem.y2, downFlag.y + downAnchor.y, 1e-9)

        assertNull(single(Fixtures.C4, Ticks.QUARTER).flag("n"))
        assertNull(single(Fixtures.C4, Ticks.HALF).flag("n"))
    }

    @Test
    fun `two eighths on a beat share a beam and lose their flags`() {
        val layout = eighths(Fixtures.C4 to 0, Fixtures.C4 to 480)
        val beam = layout.beams().single()
        val first = layout.stem("e1")!!
        val second = layout.stem("e2")!!

        assertEquals(emptyList(), layout.glyphs(Role.FLAG))
        assertEquals(BravuraMetrics.BEAM_THICKNESS, beam.thickness)
        assertEquals(beam.y1, beam.y2, "level heads, level beam")
        assertEquals(first.y2, second.y2)
        assertEquals(first.y1 + ScoreLayoutEngine.STEM_LENGTH, first.y2, 1e-9, "the stems keep their length")
        assertEquals(first.y2, beam.y1 + beam.thickness / 2, 1e-9, "the beam's outer edge is at the stem tips")
        assertEquals(first.x1 - BravuraMetrics.STEM_THICKNESS / 2, beam.x1, 1e-9)
        assertEquals(second.x1 + BravuraMetrics.STEM_THICKNESS / 2, beam.x2, 1e-9)
        assertEquals(Role.BEAM, beam.role)
        assertNull(beam.noteId, "a beam belongs to both notes")
        assertEquals(Ticks.ZERO, beam.ticks)
        assertTrue(Mask.ALL.hides(beam))
        assertTrue(Spacing.advanceFor(Ticks.EIGHTH, blackHead.width) < Spacing.advanceFor(Ticks.QUARTER, blackHead.width))
        assertEquals(layout.head("e1").x + Spacing.advanceFor(Ticks.EIGHTH, blackHead.width), layout.head("e2").x, 1e-9)
    }

    @Test
    fun `a beam never crosses a beat`() {
        val acrossTheBeat = eighths(Fixtures.C4 to 480, Fixtures.C4 to 960)
        assertEquals(emptyList(), acrossTheBeat.beams())
        assertEquals(listOf("e1", "e2"), acrossTheBeat.glyphs(Role.FLAG).map { it.noteId })

        val three = eighths(Fixtures.C4 to 0, Fixtures.C4 to 480, Fixtures.C4 to 960)
        assertEquals(1, three.beams().size)
        assertEquals(listOf("e3"), three.glyphs(Role.FLAG).map { it.noteId })
        assertEquals(3, three.lines(Role.STEM).size)

        val twoPairs = eighths(Fixtures.C4 to 0, Fixtures.D4 to 480, Fixtures.E4 to 1920, Fixtures.F4 to 2400)
        assertEquals(listOf(Ticks.ZERO, Ticks.HALF), twoPairs.beams().map { it.ticks })
    }

    @Test
    fun `a beamed group's stems follow the head farthest from the middle line and the beam slants with the heads`() {
        // F4 is three steps under the middle line, C5 one over: the group's stems go up.
        val up = eighths(Fixtures.F4 to 0, SpelledPitch(Step.C, octave = 5) to 480)
        val f = up.stem("e1")!!
        val c = up.stem("e2")!!
        val upBeam = up.beams().single()
        assertEquals(up.head("e2").x + blackHead.stemUpSE!!.x - BravuraMetrics.STEM_THICKNESS / 2, c.x1, 1e-9, "C5's stem is on its right")
        assertEquals(c.y1 + ScoreLayoutEngine.STEM_LENGTH, c.y2, 1e-9, "the stem nearest the beam has its own length")
        assertEquals(ScoreLayoutEngine.MAX_BEAM_RISE, c.y2 - f.y2, 1e-9, "a fifth rises the most a beam may")
        assertTrue(f.y2 > f.y1 + ScoreLayoutEngine.STEM_LENGTH, "the other stem is longer")
        assertEquals(f.y2, upBeam.yAt(f.x1) + upBeam.thickness / 2, 1e-9)
        assertEquals(c.y2, upBeam.yAt(c.x1) + upBeam.thickness / 2, 1e-9)

        // C5 and B4, a second down: stems down, the beam falling a quarter of a space.
        val down = eighths(SpelledPitch(Step.C, octave = 5) to 0, b4 to 480)
        val c5 = down.stem("e1")!!
        val b = down.stem("e2")!!
        val downBeam = down.beams().single()
        assertEquals(down.head("e1").x + blackHead.stemDownNW!!.x + BravuraMetrics.STEM_THICKNESS / 2, c5.x1, 1e-9, "C5's stem is on its left")
        assertEquals(b.y1 - ScoreLayoutEngine.STEM_LENGTH, b.y2, 1e-9)
        assertEquals(0.5 * ScoreLayoutEngine.BEAM_SLANT, c5.y2 - b.y2, 1e-9)
        assertEquals(c5.y2, downBeam.yAt(c5.x1) - downBeam.thickness / 2, 1e-9, "the outer edge is below the centre line for down stems")

        // G4 and D5 are two steps either side of the middle line: level, the stems go down.
        val level = eighths(Fixtures.G4 to 0, SpelledPitch(Step.D, octave = 5) to 480)
        assertTrue(level.stem("e1")!!.y2 < level.stem("e1")!!.y1)
        assertTrue(level.stem("e2")!!.y2 < level.stem("e2")!!.y1)

        // A leap the other way: the beam falls, capped at the same rise.
        val falling = eighths(SpelledPitch(Step.C, octave = 5) to 0, Fixtures.F4 to 480)
        val fallingBeam = falling.beams().single()
        assertEquals(-ScoreLayoutEngine.MAX_BEAM_RISE, falling.stem("e2")!!.y2 - falling.stem("e1")!!.y2, 1e-9)
        assertTrue(fallingBeam.y2 < fallingBeam.y1)
    }

    @Test
    fun `eighths on a ledger line still reach the middle line and stay inside the envelope`() {
        val low = eighths(a3 to 0, a3 to 480)
        assertEquals(StaffPosition.MIDDLE_LINE.y, low.stem("e1")!!.y2)
        assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, low.top)
        assertEquals(ScoreLayoutEngine.ENVELOPE_BOTTOM, low.bottom)
        val high = eighths(c6 to 0, a5 to 480)
        assertEquals(high.stem("e2")!!.y1 - ScoreLayoutEngine.STEM_LENGTH, high.stem("e2")!!.y2, 1e-9, "A5, nearest the beam, keeps its own length")
        assertTrue(high.stem("e1")!!.y2 < StaffPosition.MIDDLE_LINE.y, "C6's stem runs past the middle line to the beam")
        assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, high.top)
    }
}
