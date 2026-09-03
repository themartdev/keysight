package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.math.max
import kotlin.math.min

/**
 * Lays out a [Score] as systems of measures across all of its staves, in staff spaces.
 *
 * A system reads, left to right: margin, the brace of a grand staff, one clef per staff, the
 * key signature, the time signature on the first system only, then the measures, each a
 * column per onset closed by a barline, and the score's last measure by a final barline.
 * Given a target width a system takes as many measures as fit at natural spacing and then
 * stretches the room after each column so the last barline reaches the edge, up to
 * [MAX_STRETCH]; the fixed parts, signatures and accidentals, never stretch.
 *
 * Every rule the generator round extends is a small function here: which head a duration
 * takes, where a stem starts and how long it is, which ledger lines a position needs, how much
 * room a duration gets, when an accidental is written. Chords are laid out with every head at
 * the column's x, which is right until two heads a second apart need offsetting; that, rests
 * shorter than a measure and flags are for later.
 */
object ScoreLayoutEngine {

    const val LEFT_MARGIN = 1.0
    const val RIGHT_MARGIN = 1.0
    const val BRACE_GAP = 0.5
    const val CLEF_GAP = 1.0
    const val KEY_ACCIDENTAL_GAP = 0.15
    const val KEY_SIGNATURE_GAP = 1.0
    const val TIME_SIGNATURE_GAP = 1.5
    const val MEASURE_START_GAP = 1.5
    const val FINAL_BARLINE_GAP = 0.4
    const val STEM_LENGTH = 3.5

    /** How far a column's room may grow to fill a system. */
    const val MAX_STRETCH = 1.6

    /**
     * The vertical extent every staff at least covers, so the staff is drawn at the same
     * size from one exercise to the next: two ledger lines and a mark row below, two ledger
     * lines and a down-stem above.
     */
    const val ENVELOPE_BOTTOM = -6.5
    const val ENVELOPE_TOP = 8.0

    /** Bottom line to bottom line on a grand staff: the upper staff's envelope clears the lower's ledger lines. */
    const val STAFF_DISTANCE = 13.0

    /** Between one system's envelope and the next's. */
    const val SYSTEM_GAP = 4.0

    private val STAFF_LINE_POSITIONS = listOf(0, 2, 4, 6, 8).map(::StaffPosition)

    /** Height of a system of [staffCount] staves holding nothing beyond the envelope. */
    fun systemHeight(staffCount: Int): Double = ENVELOPE_TOP - (ENVELOPE_BOTTOM - STAFF_DISTANCE * (staffCount - 1))

    /** Every measure of [score] on as many systems as [targetWidth] takes; natural width when null. */
    fun layoutPage(score: Score, targetWidth: Double?): PageLayout {
        val systems = ArrayList<PlacedSystem>()
        var measure = 0
        var y = 0.0
        while (measure < score.measureCount) {
            val system = layoutSystem(score, measure, targetWidth, showTimeSignature = measure == 0)
            systems.lastOrNull()?.let { previous -> y = previous.y + previous.layout.bottom - SYSTEM_GAP - system.top }
            systems += PlacedSystem(system, y)
            measure = system.measures.last + 1
        }
        return PageLayout(systems)
    }

    /**
     * One system starting at [firstMeasure]: as many measures as fit [targetWidth] at natural
     * spacing, at least one, justified to it; every measure at natural spacing when null.
     */
    fun layoutSystem(score: Score, firstMeasure: Int, targetWidth: Double?, showTimeSignature: Boolean): SystemLayout {
        require(firstMeasure in 0 until score.measureCount) { "no measure $firstMeasure in ${score.measureCount}" }
        val frames = score.staves.mapIndexed { index, staff -> StaffFrame(index, staff.clef, 0.0 - STAFF_DISTANCE * index) }
        val elements = ArrayList<Element>()
        val anchors = LinkedHashMap<String, NoteAnchor>()
        val timeAxis = ArrayList<TimePoint>()

        val headerEnd = placeHeader(score, frames, showTimeSignature, elements)

        val plans = ArrayList<MeasurePlan>()
        var measure = firstMeasure
        while (measure < score.measureCount) {
            val plan = planMeasure(score, measure, frames)
            val trailing = barlineWidth(final = measure == score.measureCount - 1) + RIGHT_MARGIN
            val width = headerEnd + plans.sumOf { it.naturalWidth + it.barlineAdvance } + plan.naturalWidth + trailing
            if (plans.isNotEmpty() && targetWidth != null && width > targetWidth) break
            plans += plan
            measure++
        }
        val lastIsFinal = plans.last().measure == score.measureCount - 1
        val fixed = headerEnd + plans.sumOf { it.fixedWidth } + plans.dropLast(1).sumOf { it.barlineAdvance } +
            barlineWidth(lastIsFinal) + RIGHT_MARGIN
        val stretchable = plans.sumOf { it.stretchableWidth }
        val stretch = if (targetWidth == null || stretchable <= 0.0) 1.0 else ((targetWidth - fixed) / stretchable).coerceIn(1.0, MAX_STRETCH)

        var x = headerEnd
        for (plan in plans) {
            val barlineX = placeMeasure(plan, x, stretch, frames, elements, anchors, timeAxis)
            val final = plan.measure == score.measureCount - 1
            x = placeBarline(barlineX, final, frames, elements)
            if (plan === plans.last()) {
                timeAxis += TimePoint(plan.end, barlineX - BravuraMetrics.of(Glyph.NOTEHEAD_BLACK).width - Spacing.CUE_GAP)
            } else {
                x += MEASURE_START_GAP
            }
        }
        val width = x + RIGHT_MARGIN

        val staffLines = frames.flatMap { frame ->
            STAFF_LINE_POSITIONS.map { line ->
                val y = frame.baselineY + line.y
                LineElement(0.0, y, width, y, BravuraMetrics.STAFF_LINE_THICKNESS, Role.STAFF_LINE)
            }
        }
        val all = staffLines + elements
        val first = plans.first()
        return SystemLayout(
            width = width,
            top = max(ENVELOPE_TOP, all.maxOf(::topOf)),
            bottom = min(frames.last().baselineY + ENVELOPE_BOTTOM, all.minOf(::bottomOf)),
            staves = frames,
            measures = first.measure..plans.last().measure,
            ticks = TickRange(first.start, plans.last().end),
            elements = all,
            anchors = anchors,
            timeAxis = timeAxis,
        )
    }

    // Header: brace, clefs, key signature, time signature.

    /** Places everything before the first measure and returns the x its first column starts at. */
    private fun placeHeader(score: Score, frames: List<StaffFrame>, showTimeSignature: Boolean, elements: MutableList<Element>): Double {
        var x = LEFT_MARGIN
        if (frames.size > 1) x = placeBrace(frames, x, elements) + BRACE_GAP

        var clefRight = x
        for (frame in frames) clefRight = max(clefRight, placeClef(frame, x, elements))
        x = clefRight + CLEF_GAP

        val keySignatureWidth = placeKeySignature(score, frames, x, elements)
        if (keySignatureWidth > 0.0) x += keySignatureWidth + KEY_SIGNATURE_GAP

        if (showTimeSignature) {
            var signatureRight = x
            for (frame in frames) signatureRight = max(signatureRight, placeTimeSignature(score.timeSignature, frame, x, elements))
            x = signatureRight + TIME_SIGNATURE_GAP
        }
        return x
    }

    /** The brace spans from the lowest staff's bottom line to the top staff's top line. */
    private fun placeBrace(frames: List<StaffFrame>, x: Double, elements: MutableList<Element>): Double {
        val metrics = BravuraMetrics.of(Glyph.BRACE)
        val bottom = frames.last().baselineY
        val scale = (StaffPosition.TOP_LINE.y - bottom) / metrics.height
        elements += GlyphElement(Glyph.BRACE, x - metrics.left * scale, bottom - metrics.bottom * scale, Role.BRACE, scale = scale)
        return x + metrics.width * scale
    }

    private fun placeClef(frame: StaffFrame, x: Double, elements: MutableList<Element>): Double {
        val glyph = when (frame.clef) {
            Clef.TREBLE -> Glyph.G_CLEF
            Clef.BASS -> Glyph.F_CLEF
        }
        // A G clef curls around the G line, the second from the bottom; an F clef's dots
        // straddle the F line, the fourth. Both glyphs have their origin on that line.
        val line = when (frame.clef) {
            Clef.TREBLE -> StaffPosition(2)
            Clef.BASS -> StaffPosition(6)
        }
        val metrics = BravuraMetrics.of(glyph)
        elements += GlyphElement(glyph, x - metrics.left, frame.baselineY + line.y, Role.CLEF)
        return x + metrics.width
    }

    /** The key's accidentals on every staff, in signature order; returns the width taken, 0 for C major. */
    private fun placeKeySignature(score: Score, frames: List<StaffFrame>, x: Double, elements: MutableList<Element>): Double {
        val steps = score.keySignature.alteredSteps
        if (steps.isEmpty()) return 0.0
        val sharps = score.keySignature.fifths > 0
        val glyph = if (sharps) Glyph.ACCIDENTAL_SHARP else Glyph.ACCIDENTAL_FLAT
        val metrics = BravuraMetrics.of(glyph)
        for (frame in frames) {
            var accidentalX = x
            for (index in steps.indices) {
                val position = StaffPosition.ofKeySignature(frame.clef, index, sharps)
                elements += GlyphElement(glyph, accidentalX - metrics.left, frame.baselineY + position.y, Role.KEY_SIGNATURE)
                accidentalX += metrics.width + KEY_ACCIDENTAL_GAP
            }
        }
        return steps.size * metrics.width + (steps.size - 1) * KEY_ACCIDENTAL_GAP
    }

    /** Numerator centred over denominator, each two spaces tall, filling the staff. */
    private fun placeTimeSignature(timeSignature: TimeSignature, frame: StaffFrame, x: Double, elements: MutableList<Element>): Double {
        val numerator = Glyph.timeSigDigits(timeSignature.beatsPerMeasure)
        val denominator = Glyph.timeSigDigits(timeSignature.beatUnit)
        fun widthOf(digits: List<Glyph>) = digits.sumOf { BravuraMetrics.of(it).width }
        val column = max(widthOf(numerator), widthOf(denominator))

        fun place(digits: List<Glyph>, y: Double) {
            var digitX = x + (column - widthOf(digits)) / 2
            for (digit in digits) {
                val metrics = BravuraMetrics.of(digit)
                elements += GlyphElement(digit, digitX - metrics.left, frame.baselineY + y, Role.TIME_SIGNATURE)
                digitX += metrics.width
            }
        }
        place(numerator, StaffPosition(6).y)
        place(denominator, StaffPosition(2).y)
        return x + column
    }

    // Measures: planned at natural spacing first, so a system knows what fits, then placed.

    private class PlannedNote(val note: ScoreNote, val frame: StaffFrame, val position: StaffPosition, val head: Glyph, val accidental: Glyph?)

    private class Column(val onset: Ticks, val notes: List<PlannedNote>) {
        val accidentalRoom: Double = notes.maxOf { planned ->
            planned.accidental?.let { BravuraMetrics.of(it).width + Spacing.ACCIDENTAL_GAP } ?: 0.0
        }
        val advance: Double = notes.minOf { Spacing.advanceFor(it.note.duration, BravuraMetrics.of(it.head).width) }
    }

    private class MeasurePlan(val measure: Int, val start: Ticks, val end: Ticks, val columns: List<Column>, val resting: List<StaffFrame>) {
        val fixedWidth: Double = columns.sumOf { it.accidentalRoom }
        val stretchableWidth: Double = if (columns.isEmpty()) EMPTY_MEASURE_WIDTH else columns.sumOf { it.advance }
        val naturalWidth: Double get() = fixedWidth + stretchableWidth
        val barlineAdvance: Double get() = BravuraMetrics.THIN_BARLINE_THICKNESS / 2 + MEASURE_START_GAP
    }

    private fun planMeasure(score: Score, measure: Int, frames: List<StaffFrame>): MeasurePlan {
        val start = score.measureStart(measure)
        val states = frames.map { AccidentalState(score.keySignature) }
        val columns = score.notesInMeasure(measure)
            .sortedWith(compareBy({ it.onset }, { it.staff }, { it.pitch }))
            .groupBy { it.onset }
            .map { (onset, notes) ->
                Column(
                    onset,
                    notes.map { note ->
                        val frame = frames[note.staff]
                        PlannedNote(note, frame, StaffPosition.of(note.spelling, frame.clef), headFor(note.duration), states[note.staff].accidentalFor(note.spelling))
                    },
                )
            }
        val sounding = columns.flatMap { column -> column.notes.map { it.frame.index } }.toSet()
        return MeasurePlan(measure, start, start + score.timeSignature.ticksPerMeasure, columns, frames.filter { it.index !in sounding })
    }

    /** Places one measure's columns and rests from [x] and returns where its barline goes. */
    private fun placeMeasure(
        plan: MeasurePlan,
        x: Double,
        stretch: Double,
        frames: List<StaffFrame>,
        elements: MutableList<Element>,
        anchors: MutableMap<String, NoteAnchor>,
        timeAxis: MutableList<TimePoint>,
    ): Double {
        var columnX = x
        for (column in plan.columns) {
            val headX = columnX + column.accidentalRoom
            for (planned in column.notes) placeNote(planned, headX, elements, anchors)
            timeAxis += TimePoint(column.onset, headX)
            columnX = headX + column.advance * stretch
        }
        if (plan.columns.isEmpty()) {
            timeAxis += TimePoint(plan.start, x)
            columnX = x + EMPTY_MEASURE_WIDTH * stretch
        }
        val barlineX = columnX
        val rest = BravuraMetrics.of(Glyph.REST_WHOLE)
        for (frame in plan.resting) {
            val restX = (x + barlineX) / 2 - rest.width / 2
            elements += GlyphElement(Glyph.REST_WHOLE, restX - rest.left, frame.baselineY + WHOLE_REST_POSITION.y, Role.REST, ticks = plan.start)
        }
        return barlineX
    }

    private fun placeNote(planned: PlannedNote, headX: Double, elements: MutableList<Element>, anchors: MutableMap<String, NoteAnchor>) {
        val note = planned.note
        val frame = planned.frame
        val head = BravuraMetrics.of(planned.head)
        val y = frame.baselineY + planned.position.y

        elements += GlyphElement(planned.head, headX - head.left, y, Role.NOTEHEAD, note.id, note.onset)

        planned.accidental?.let { accidental ->
            val metrics = BravuraMetrics.of(accidental)
            elements += GlyphElement(accidental, headX - Spacing.ACCIDENTAL_GAP - metrics.right, y, Role.ACCIDENTAL, note.id, note.onset)
        }

        for (ledger in planned.position.ledgerLines) {
            elements += LineElement(
                x1 = headX - BravuraMetrics.LEDGER_LINE_EXTENSION,
                y1 = frame.baselineY + ledger.y,
                x2 = headX + head.width + BravuraMetrics.LEDGER_LINE_EXTENSION,
                y2 = frame.baselineY + ledger.y,
                thickness = BravuraMetrics.LEDGER_LINE_THICKNESS,
                role = Role.LEDGER,
                noteId = note.id,
                ticks = note.onset,
            )
        }

        stemFor(note, planned.position, frame, headX, head)?.let { elements += it }

        anchors[note.id] = NoteAnchor(note.id, headX, planned.position, head.width, frame.index, frame.baselineY, note.onset)
    }

    /** A barline through every staff at [x]; the final one adds a thick line. Returns the x after it. */
    private fun placeBarline(x: Double, final: Boolean, frames: List<StaffFrame>, elements: MutableList<Element>): Double {
        val bottom = frames.last().baselineY
        val top = StaffPosition.TOP_LINE.y
        val thin = BravuraMetrics.THIN_BARLINE_THICKNESS
        elements += LineElement(x, bottom, x, top, thin, Role.BARLINE)
        if (!final) return x + thin / 2
        val thick = BravuraMetrics.THICK_BARLINE_THICKNESS
        val thickX = x + thin / 2 + FINAL_BARLINE_GAP + thick / 2
        elements += LineElement(thickX, bottom, thickX, top, thick, Role.BARLINE)
        return thickX + thick / 2
    }

    private fun barlineWidth(final: Boolean): Double {
        val thin = BravuraMetrics.THIN_BARLINE_THICKNESS
        return if (final) thin / 2 + FINAL_BARLINE_GAP + BravuraMetrics.THICK_BARLINE_THICKNESS else thin / 2
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
    private fun stemFor(note: ScoreNote, position: StaffPosition, frame: StaffFrame, headX: Double, head: GlyphMetrics): LineElement? {
        if (note.duration >= Ticks.WHOLE) return null
        val halfThickness = BravuraMetrics.STEM_THICKNESS / 2
        val middle = frame.baselineY + StaffPosition.MIDDLE_LINE.y
        val headY = frame.baselineY + position.y
        val stemX: Double
        val start: Double
        val tip: Double
        if (position.stemUp) {
            val anchor = checkNotNull(head.stemUpSE) { "${note.id}: $head has no stem anchor" }
            stemX = headX + anchor.x - halfThickness
            start = headY + anchor.y
            tip = max(start + STEM_LENGTH, middle)
        } else {
            val anchor = checkNotNull(head.stemDownNW) { "${note.id}: $head has no stem anchor" }
            stemX = headX + anchor.x + halfThickness
            start = headY + anchor.y
            tip = min(start - STEM_LENGTH, middle)
        }
        return LineElement(stemX, start, stemX, tip, BravuraMetrics.STEM_THICKNESS, Role.STEM, note.id, note.onset)
    }

    private fun topOf(element: Element): Double = when (element) {
        is GlyphElement -> element.y + BravuraMetrics.of(element.glyph).top * element.scale
        is LineElement -> max(element.y1, element.y2) + element.thickness / 2
    }

    private fun bottomOf(element: Element): Double = when (element) {
        is GlyphElement -> element.y + BravuraMetrics.of(element.glyph).bottom * element.scale
        is LineElement -> min(element.y1, element.y2) - element.thickness / 2
    }

    /** A whole rest hangs from the fourth line. */
    private val WHOLE_REST_POSITION = StaffPosition(6)

    /** Room a measure with nothing in it takes: what a whole note would. */
    private val EMPTY_MEASURE_WIDTH: Double = Spacing.advanceFor(Ticks.WHOLE, BravuraMetrics.of(Glyph.NOTEHEAD_WHOLE).width)
}
