package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.math.max
import kotlin.math.min

/**
 * Lays out one measure of a [Score] as a [StaffLayout], in staff spaces.
 *
 * Left to right: margin, clef, the key signature slot (empty for C major, the only key in
 * V1; sharps and flats go between clef and time signature when they arrive), time
 * signature, one column per onset, the barline, margin. Every rule that Milestone 5 extends
 * is a small function here: which head a duration takes, where a stem starts and how long
 * it is, which ledger lines a position needs, how much room a duration gets.
 *
 * Chords are laid out with every head at the column's x, which is right until two heads a
 * second apart need offsetting; that, rests and flags are Milestone 5.
 */
object ScoreLayoutEngine {

    const val LEFT_MARGIN = 1.0
    const val RIGHT_MARGIN = 1.0
    const val CLEF_GAP = 1.0
    const val TIME_SIGNATURE_GAP = 1.5
    const val STEM_LENGTH = 3.5

    /**
     * The vertical extent every layout at least covers, so the staff is drawn at the same
     * size from one exercise to the next: two ledger lines and a mark row below, two ledger
     * lines and a down-stem above.
     */
    const val ENVELOPE_BOTTOM = -6.5
    const val ENVELOPE_TOP = 8.0

    private val STAFF_LINE_POSITIONS = listOf(0, 2, 4, 6, 8).map(::StaffPosition)

    fun layout(score: Score): StaffLayout {
        val elements = ArrayList<Element>()
        val anchors = LinkedHashMap<String, NoteAnchor>()
        val timeAxis = ArrayList<TimePoint>()

        var x = LEFT_MARGIN
        x = placeClef(score.clef, x, elements) + CLEF_GAP
        x = placeTimeSignature(score.timeSignature, x, elements) + TIME_SIGNATURE_GAP
        for (chord in score.chordsInPerformanceOrder) {
            x = placeChord(chord, score.clef, x, elements, anchors, timeAxis)
        }

        val barlineX = x
        elements += LineElement(barlineX, 0.0, barlineX, StaffPosition.TOP_LINE.y, BravuraMetrics.THIN_BARLINE_THICKNESS, Role.BARLINE)
        val blackHead = BravuraMetrics.of(Glyph.NOTEHEAD_BLACK)
        timeAxis += TimePoint(score.totalTicks, barlineX - blackHead.width - Spacing.CUE_GAP)

        val width = barlineX + BravuraMetrics.THIN_BARLINE_THICKNESS / 2 + RIGHT_MARGIN
        val staffLines = STAFF_LINE_POSITIONS.map { line ->
            LineElement(0.0, line.y, width, line.y, BravuraMetrics.STAFF_LINE_THICKNESS, Role.STAFF_LINE)
        }
        val all = staffLines + elements
        return StaffLayout(
            width = width,
            top = max(ENVELOPE_TOP, all.maxOf(::topOf)),
            bottom = min(ENVELOPE_BOTTOM, all.minOf(::bottomOf)),
            elements = all,
            anchors = anchors,
            timeAxis = timeAxis,
        )
    }

    private fun placeClef(clef: Clef, x: Double, elements: MutableList<Element>): Double {
        val glyph = when (clef) {
            Clef.TREBLE -> Glyph.G_CLEF
            Clef.BASS -> Glyph.F_CLEF
        }
        // A G clef curls around the G line, the second from the bottom; an F clef's dots
        // straddle the F line, the fourth. Both glyphs have their origin on that line.
        val line = when (clef) {
            Clef.TREBLE -> StaffPosition(2)
            Clef.BASS -> StaffPosition(6)
        }
        val metrics = BravuraMetrics.of(glyph)
        elements += GlyphElement(glyph, x - metrics.left, line.y, Role.CLEF)
        return x + metrics.width
    }

    /** Numerator centred over denominator, each two spaces tall, filling the staff. */
    private fun placeTimeSignature(timeSignature: TimeSignature, x: Double, elements: MutableList<Element>): Double {
        val numerator = Glyph.timeSigDigits(timeSignature.beatsPerMeasure)
        val denominator = Glyph.timeSigDigits(timeSignature.beatUnit)
        fun widthOf(digits: List<Glyph>) = digits.sumOf { BravuraMetrics.of(it).width }
        val column = max(widthOf(numerator), widthOf(denominator))

        fun place(digits: List<Glyph>, y: Double) {
            var digitX = x + (column - widthOf(digits)) / 2
            for (digit in digits) {
                val metrics = BravuraMetrics.of(digit)
                elements += GlyphElement(digit, digitX - metrics.left, y, Role.TIME_SIGNATURE)
                digitX += metrics.width
            }
        }
        place(numerator, StaffPosition(6).y)
        place(denominator, StaffPosition(2).y)
        return x + column
    }

    /** Lays out the notes that start together and returns the x of the next column. */
    private fun placeChord(
        chord: List<ScoreNote>,
        clef: Clef,
        x: Double,
        elements: MutableList<Element>,
        anchors: MutableMap<String, NoteAnchor>,
        timeAxis: MutableList<TimePoint>,
    ): Double {
        val accidentalRoom = chord.maxOf { note ->
            Glyph.accidentalFor(note.spelling.alteration)?.let { BravuraMetrics.of(it).width + Spacing.ACCIDENTAL_GAP } ?: 0.0
        }
        val headX = x + accidentalRoom
        var advance = Double.MAX_VALUE

        for (note in chord) {
            val position = StaffPosition.of(note.spelling, clef)
            val headGlyph = headFor(note.duration)
            val head = BravuraMetrics.of(headGlyph)
            val y = position.y

            elements += GlyphElement(headGlyph, headX - head.left, y, Role.NOTEHEAD, note.id)

            Glyph.accidentalFor(note.spelling.alteration)?.let { accidental ->
                val metrics = BravuraMetrics.of(accidental)
                elements += GlyphElement(accidental, headX - Spacing.ACCIDENTAL_GAP - metrics.right, y, Role.ACCIDENTAL, note.id)
            }

            for (ledger in position.ledgerLines) {
                elements += LineElement(
                    x1 = headX - BravuraMetrics.LEDGER_LINE_EXTENSION,
                    y1 = ledger.y,
                    x2 = headX + head.width + BravuraMetrics.LEDGER_LINE_EXTENSION,
                    y2 = ledger.y,
                    thickness = BravuraMetrics.LEDGER_LINE_THICKNESS,
                    role = Role.LEDGER,
                    noteId = note.id,
                )
            }

            stemFor(note, position, headX, head)?.let { elements += it }

            anchors[note.id] = NoteAnchor(note.id, headX, position, head.width)
            advance = min(advance, Spacing.advanceFor(note.duration, head.width))
        }

        timeAxis += TimePoint(chord.first().onset, headX)
        return headX + advance
    }

    private fun headFor(duration: Ticks): Glyph = when {
        duration >= Ticks.WHOLE -> Glyph.NOTEHEAD_WHOLE
        duration >= Ticks.HALF -> Glyph.NOTEHEAD_HALF
        else -> Glyph.NOTEHEAD_BLACK
    }

    /**
     * Stems go up on the right of heads below the middle line and down on the left of the
     * others, one octave long, and reach the middle line when the head is on a ledger
     * line, so a far note's stem is never a stub. Whole notes have none.
     */
    private fun stemFor(note: ScoreNote, position: StaffPosition, headX: Double, head: GlyphMetrics): LineElement? {
        if (note.duration >= Ticks.WHOLE) return null
        val halfThickness = BravuraMetrics.STEM_THICKNESS / 2
        val middle = StaffPosition.MIDDLE_LINE.y
        val stemX: Double
        val start: Double
        val tip: Double
        if (position.stemUp) {
            val anchor = checkNotNull(head.stemUpSE) { "${note.id}: ${head} has no stem anchor" }
            stemX = headX + anchor.x - halfThickness
            start = position.y + anchor.y
            tip = max(start + STEM_LENGTH, middle)
        } else {
            val anchor = checkNotNull(head.stemDownNW) { "${note.id}: ${head} has no stem anchor" }
            stemX = headX + anchor.x + halfThickness
            start = position.y + anchor.y
            tip = min(start - STEM_LENGTH, middle)
        }
        return LineElement(stemX, start, stemX, tip, BravuraMetrics.STEM_THICKNESS, Role.STEM, note.id)
    }

    private fun topOf(element: Element): Double = when (element) {
        is GlyphElement -> element.y + BravuraMetrics.of(element.glyph).top
        is LineElement -> max(element.y1, element.y2) + element.thickness / 2
    }

    private fun bottomOf(element: Element): Double = when (element) {
        is GlyphElement -> element.y + BravuraMetrics.of(element.glyph).bottom
        is LineElement -> min(element.y1, element.y2) - element.thickness / 2
    }
}
