package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

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
 * Every rule a generator dimension extends is a small function here: which head a duration
 * takes, where a stem starts and how long it is, which notes are beamed and where the beam
 * goes, which ledger lines a position needs, how much room a duration gets, when an accidental
 * is written. Chords are laid out with every head at the column's x, which is right until two
 * heads a second apart need offsetting; that, rests shorter than a measure, dots and a second
 * beam are for later.
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

    /** How much of the distance between a beam's first and last heads the beam rises, up to [MAX_BEAM_RISE]. */
    const val BEAM_SLANT = 0.5
    const val MAX_BEAM_RISE = 1.0

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

    /**
     * One note as the plan sees it. [stemUp] is the note's own rule, or its beam group's when
     * it has one; a [beamed] note's stem is drawn with its group once the columns are placed.
     */
    private class PlannedNote(
        val note: ScoreNote,
        val frame: StaffFrame,
        val position: StaffPosition,
        val head: Glyph,
        val accidental: Glyph?,
        val stemUp: Boolean,
        val beamed: Boolean,
    )

    private class Column(val onset: Ticks, val notes: List<PlannedNote>) {
        val accidentalRoom: Double = notes.maxOf { planned ->
            planned.accidental?.let { BravuraMetrics.of(it).width + Spacing.ACCIDENTAL_GAP } ?: 0.0
        }
        val advance: Double = notes.minOf { Spacing.advanceFor(it.note.duration, BravuraMetrics.of(it.head).width) }
    }

    private class MeasurePlan(
        val measure: Int,
        val start: Ticks,
        val end: Ticks,
        val columns: List<Column>,
        val beams: List<List<PlannedNote>>,
        val resting: List<StaffFrame>,
    ) {
        val fixedWidth: Double = columns.sumOf { it.accidentalRoom }
        val stretchableWidth: Double = if (columns.isEmpty()) EMPTY_MEASURE_WIDTH else columns.sumOf { it.advance }
        val naturalWidth: Double get() = fixedWidth + stretchableWidth
        val barlineAdvance: Double get() = BravuraMetrics.THIN_BARLINE_THICKNESS / 2 + MEASURE_START_GAP
    }

    private fun planMeasure(score: Score, measure: Int, frames: List<StaffFrame>): MeasurePlan {
        val start = score.measureStart(measure)
        val states = frames.map { AccidentalState(score.keySignature) }
        val notes = score.notesInMeasure(measure).sortedWith(compareBy({ it.onset }, { it.staff }, { it.pitch }))
        val groups = beamGroups(notes, score.timeSignature)
        val groupStemUp = HashMap<String, Boolean>()
        for (group in groups) {
            val up = stemUpFor(group.map { StaffPosition.of(it.spelling, frames[it.staff].clef) })
            group.forEach { groupStemUp[it.id] = up }
        }
        val planned = HashMap<String, PlannedNote>()
        val columns = notes.groupBy { it.onset }.map { (onset, columnNotes) ->
            Column(
                onset,
                columnNotes.map { note ->
                    val frame = frames[note.staff]
                    val position = StaffPosition.of(note.spelling, frame.clef)
                    PlannedNote(
                        note = note,
                        frame = frame,
                        position = position,
                        head = headFor(note.duration),
                        accidental = states[note.staff].accidentalFor(note.spelling),
                        stemUp = groupStemUp[note.id] ?: position.stemUp,
                        beamed = note.id in groupStemUp,
                    ).also { planned[note.id] = it }
                },
            )
        }
        val beams = groups.map { group -> group.map { planned.getValue(it.id) } }
        val sounding = columns.flatMap { column -> column.notes.map { it.frame.index } }.toSet()
        return MeasurePlan(measure, start, start + score.timeSignature.ticksPerMeasure, columns, beams, frames.filter { it.index !in sounding })
    }

    /**
     * The beaming rule: on each staff and voice, flagged notes whose onsets fall in the same
     * beat are beamed together, so a beam never crosses a beat, and a flagged note alone in
     * its beat keeps its flag. Groups keep the order of [notes]; a chord's notes are in one
     * group together. The beat is the time signature's; compound meters need their own
     * grouping when they come.
     */
    private fun beamGroups(notes: List<ScoreNote>, timeSignature: TimeSignature): List<List<ScoreNote>> {
        fun beatOf(note: ScoreNote) = note.onset.value / timeSignature.ticksPerBeat.value
        return notes.filter { isFlagged(it.duration) }.groupBy { it.staff to it.voice }.values.flatMap { line ->
            val groups = ArrayList<MutableList<ScoreNote>>()
            for (note in line) {
                val open = groups.lastOrNull()
                if (open != null && beatOf(open.last()) == beatOf(note)) open += note else groups += mutableListOf(note)
            }
            groups.filter { group -> group.distinctBy { it.onset }.size > 1 }
        }
    }

    /** A beamed group's stems follow the head farthest from the middle line; level, they go down like a note on the line. */
    private fun stemUpFor(positions: List<StaffPosition>): Boolean {
        val middle = StaffPosition.MIDDLE_LINE.value
        val below = positions.maxOf { middle - it.value }
        val above = positions.maxOf { it.value - middle }
        return below > above
    }

    /** Places one measure's columns, beams and rests from [x] and returns where its barline goes. */
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
        for (group in plan.beams) placeBeam(group, anchors, elements)
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

        val stem = stemFor(planned, headX)
        if (stem != null && !planned.beamed) {
            elements += stemLine(planned, stem, stem.tip)
            if (isFlagged(note.duration)) elements += flagFor(planned, stem)
        }

        anchors[note.id] = NoteAnchor(note.id, headX, planned.position, head.width, frame.index, frame.baselineY, note.onset)
    }

    /**
     * A beamed group's stems and beam, once its heads are placed. The beam follows the first
     * and last heads of the group, rising [BEAM_SLANT] of their distance up to
     * [MAX_BEAM_RISE], and sits where the stem nearest it has exactly its own length; every
     * other stem is longer, and the beam's outer edge is at the stem tips.
     */
    private fun placeBeam(group: List<PlannedNote>, anchors: Map<String, NoteAnchor>, elements: MutableList<Element>) {
        val stems = group.map { planned -> planned to checkNotNull(stemFor(planned, anchors.getValue(planned.note.id).x)) { "${planned.note.id} is beamed without a stem" } }
        val direction = if (group.first().stemUp) 1.0 else -1.0
        val first = stems.first().second
        val last = stems.last().second
        val run = last.x - first.x
        val dy = last.start - first.start
        val rise = if (run > 0.0) sign(dy) * min(abs(dy) * BEAM_SLANT, MAX_BEAM_RISE) else 0.0
        val slope = if (run > 0.0) rise / run else 0.0
        val reach = stems.maxOf { (_, stem) -> direction * (stem.tip - slope * (stem.x - first.x)) }
        fun tipAt(x: Double) = direction * reach + slope * (x - first.x)

        for ((planned, stem) in stems) elements += stemLine(planned, stem, tipAt(stem.x))
        val halfStem = BravuraMetrics.STEM_THICKNESS / 2
        val inset = direction * BravuraMetrics.BEAM_THICKNESS / 2
        val x1 = first.x - halfStem
        val x2 = last.x + halfStem
        elements += BeamElement(x1, tipAt(x1) - inset, x2, tipAt(x2) - inset, BravuraMetrics.BEAM_THICKNESS, group.first().note.onset)
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

    /** Shorter than a quarter takes a flag or a beam; one of each, since a second one, for sixteenths, waits like dots and rests do. */
    private fun isFlagged(duration: Ticks): Boolean = duration < Ticks.QUARTER

    /** Where a note's stem goes: its x, where it leaves the head and where its tip would be on its own. */
    private class Stem(val x: Double, val start: Double, val tip: Double)

    /**
     * Stems go up on the right of heads below the middle line and down on the left of the
     * others, one octave long, and reach the middle line when the head is on a ledger
     * line, so a far note's stem is never a stub. Whole notes have none.
     */
    private fun stemFor(planned: PlannedNote, headX: Double): Stem? {
        if (planned.note.duration >= Ticks.WHOLE) return null
        val head = BravuraMetrics.of(planned.head)
        val halfThickness = BravuraMetrics.STEM_THICKNESS / 2
        val middle = planned.frame.baselineY + StaffPosition.MIDDLE_LINE.y
        val headY = planned.frame.baselineY + planned.position.y
        return if (planned.stemUp) {
            val anchor = checkNotNull(head.stemUpSE) { "${planned.note.id}: $head has no stem anchor" }
            val start = headY + anchor.y
            Stem(headX + anchor.x - halfThickness, start, max(start + STEM_LENGTH, middle))
        } else {
            val anchor = checkNotNull(head.stemDownNW) { "${planned.note.id}: $head has no stem anchor" }
            val start = headY + anchor.y
            Stem(headX + anchor.x + halfThickness, start, min(start - STEM_LENGTH, middle))
        }
    }

    private fun stemLine(planned: PlannedNote, stem: Stem, tip: Double) =
        LineElement(stem.x, stem.start, stem.x, tip, BravuraMetrics.STEM_THICKNESS, Role.STEM, planned.note.id, planned.note.onset)

    /** The flag hangs from the stem tip: its SMuFL anchor sits on the stem's outer left corner. */
    private fun flagFor(planned: PlannedNote, stem: Stem): GlyphElement {
        val glyph = if (planned.stemUp) Glyph.FLAG_8TH_UP else Glyph.FLAG_8TH_DOWN
        val metrics = BravuraMetrics.of(glyph)
        val anchor = checkNotNull(if (planned.stemUp) metrics.stemUpNW else metrics.stemDownSW) { "$glyph has no stem anchor" }
        val stemLeft = stem.x - BravuraMetrics.STEM_THICKNESS / 2
        return GlyphElement(glyph, stemLeft - anchor.x, stem.tip - anchor.y, Role.FLAG, planned.note.id, planned.note.onset)
    }

    private fun topOf(element: Element): Double = when (element) {
        is GlyphElement -> element.y + BravuraMetrics.of(element.glyph).top * element.scale
        is LineElement -> max(element.y1, element.y2) + element.thickness / 2
        is BeamElement -> max(element.y1, element.y2) + element.thickness / 2
    }

    private fun bottomOf(element: Element): Double = when (element) {
        is GlyphElement -> element.y + BravuraMetrics.of(element.glyph).bottom * element.scale
        is LineElement -> min(element.y1, element.y2) - element.thickness / 2
        is BeamElement -> min(element.y1, element.y2) - element.thickness / 2
    }

    /** A whole rest hangs from the fourth line. */
    private val WHOLE_REST_POSITION = StaffPosition(6)

    /** Room a measure with nothing in it takes: what a whole note would. */
    private val EMPTY_MEASURE_WIDTH: Double = Spacing.advanceFor(Ticks.WHOLE, BravuraMetrics.of(Glyph.NOTEHEAD_WHOLE).width)
}
