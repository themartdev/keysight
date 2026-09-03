package dev.simonmartineau.keysight.ui.notation

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import dev.simonmartineau.keysight.evaluation.TimingJudgement
import dev.simonmartineau.keysight.notation.BravuraMetrics
import dev.simonmartineau.keysight.notation.Cursor
import dev.simonmartineau.keysight.notation.Element
import dev.simonmartineau.keysight.notation.Glyph
import dev.simonmartineau.keysight.notation.GlyphElement
import dev.simonmartineau.keysight.notation.LineElement
import dev.simonmartineau.keysight.notation.Mask
import dev.simonmartineau.keysight.notation.NoteMark
import dev.simonmartineau.keysight.notation.PageLayout
import dev.simonmartineau.keysight.notation.Spacing
import dev.simonmartineau.keysight.notation.StaffPosition
import dev.simonmartineau.keysight.notation.SystemLayout
import dev.simonmartineau.keysight.ui.theme.OutcomeGlyph
import dev.simonmartineau.keysight.ui.theme.drawOutcomeMark

/** Ink for the engraving, one colour per kind of outcome, and the cursor's. */
data class StaffColors(
    val ink: Color,
    val correct: Color,
    val wrong: Color,
    val missing: Color,
    val extra: Color,
    val cursor: Color,
)

/** Vertical centre of the row of outcome marks under a staff, in staff spaces from its bottom line. */
private const val MARK_ROW_Y = -5.25

/** Side of the square an outcome mark is drawn in, in staff spaces. */
private const val MARK_SIZE = 1.5

private const val MARK_STROKE = 0.18

/** Side of the early/late triangle beside a mark, and its distance from the mark's edge. */
private const val TIMING_ARROW_SIZE = 0.6
private const val TIMING_ARROW_GAP = 0.15

/** The cursor's thickness, and how far it runs past the system's outer staff lines. */
private const val CURSOR_THICKNESS = 0.16
private const val CURSOR_OVERHANG = 1.5
private const val CURSOR_ALPHA = 0.55f

/**
 * Paints the [systems] of a [PageLayout] at [staffSpace] pixels per staff space, with the
 * origin of the first painted system (its left edge, top staff bottom line) at [origin] and
 * the others below it as the page places them. A system narrower than the page is centred on
 * it. The [cursor], when given, is drawn on its system if that system is painted.
 */
fun DrawScope.drawPage(
    page: PageLayout,
    systems: IntRange,
    staffSpace: Float,
    origin: Offset,
    typeface: Typeface,
    colors: StaffColors,
    marks: List<NoteMark> = emptyList(),
    mask: Mask = Mask.NONE,
    cursor: Cursor? = null,
) {
    val firstY = page.systems[systems.first].y
    for (index in systems) {
        val placed = page.systems[index]
        val systemOrigin = Offset(
            origin.x + ((page.width - placed.layout.width) / 2 * staffSpace).toFloat(),
            origin.y - ((placed.y - firstY) * staffSpace).toFloat(),
        )
        val systemMarks = marks.filter { mark ->
            when (mark) {
                is NoteMark.Extra -> mark.system == index
                is NoteMark.Correct -> mark.noteId in placed.layout.anchors
                is NoteMark.Missing -> mark.noteId in placed.layout.anchors
                is NoteMark.WrongPitch -> mark.noteId in placed.layout.anchors
            }
        }
        val cursorX = cursor?.takeIf { it.system == index }?.x
        drawSystem(placed.layout, staffSpace, systemOrigin, typeface, colors, systemMarks, mask, cursorX)
    }
}

/**
 * Paints a [SystemLayout] at [staffSpace] pixels per staff space, with the layout's origin
 * (left edge, top staff's bottom line) at [origin].
 *
 * Glyphs go through the native canvas: SMuFL glyphs are positioned by their baseline
 * origin, which is exactly what `Canvas.drawText` takes, and one em is one staff height, so
 * the text size is four staff spaces. Lines are drawn as Compose lines.
 *
 * [marks] tint the elements of the notes they name and add what the layout does not have:
 * the mark under each note, the pitch played instead of a wrong one, and the notes that
 * were played but not written. [mask] leaves out the notes, rests and marks of hidden time.
 * [cursorX] draws the beat cursor through every staff of the system.
 */
fun DrawScope.drawSystem(
    layout: SystemLayout,
    staffSpace: Float,
    origin: Offset,
    typeface: Typeface,
    colors: StaffColors,
    marks: List<NoteMark> = emptyList(),
    mask: Mask = Mask.NONE,
    cursorX: Double? = null,
) {
    val painter = GlyphPainter(this, staffSpace, origin, typeface)
    val tints = HashMap<String, Color>()
    for (mark in marks) {
        when (mark) {
            is NoteMark.Correct -> tints[mark.noteId] = colors.correct
            is NoteMark.WrongPitch -> tints[mark.noteId] = colors.wrong
            is NoteMark.Missing -> tints[mark.noteId] = colors.missing
            is NoteMark.Extra -> Unit
        }
    }

    for (element in layout.elements) {
        if (mask.hides(element)) continue
        val color = element.noteId?.let(tints::get) ?: colors.ink
        painter.draw(element, color)
    }

    for (mark in marks) {
        when (mark) {
            is NoteMark.Correct -> {
                val anchor = layout.anchors.getValue(mark.noteId)
                if (mask.hides(anchor.ticks)) continue
                val centre = anchor.x + anchor.headWidth / 2
                painter.drawMark(OutcomeGlyph.CHECK, centre, anchor.baselineY, colors.correct)
                mark.timing?.let { painter.drawTimingArrow(it, centre, anchor.baselineY, colors.correct) }
            }
            is NoteMark.Missing -> {
                val anchor = layout.anchors.getValue(mark.noteId)
                if (mask.hides(anchor.ticks)) continue
                painter.drawMark(OutcomeGlyph.DASH, anchor.x + anchor.headWidth / 2, anchor.baselineY, colors.missing)
            }
            is NoteMark.WrongPitch -> {
                val anchor = layout.anchors.getValue(mark.noteId)
                if (mask.hides(anchor.ticks)) continue
                painter.drawLooseNote(
                    x = anchor.x + anchor.headWidth + Spacing.CUE_GAP,
                    position = mark.played,
                    baselineY = anchor.baselineY,
                    accidental = mark.accidental,
                    scale = Spacing.CUE_SCALE,
                    color = colors.wrong,
                )
                val centre = anchor.x + anchor.headWidth / 2
                painter.drawMark(OutcomeGlyph.CROSS, centre, anchor.baselineY, colors.wrong)
                mark.timing?.let { painter.drawTimingArrow(it, centre, anchor.baselineY, colors.wrong) }
            }
            is NoteMark.Extra -> {
                if (mask.hides(mark.ticks)) continue
                val baselineY = layout.staves[mark.staff].baselineY
                val headWidth = painter.drawLooseNote(mark.x, mark.position, baselineY, mark.accidental, scale = 1.0, color = colors.extra)
                painter.drawMark(OutcomeGlyph.PLUS, mark.x + headWidth / 2, baselineY, colors.extra)
            }
        }
    }

    cursorX?.let { x ->
        painter.drawCursor(x, layout.staves.last().baselineY - CURSOR_OVERHANG, StaffPosition.TOP_LINE.y + CURSOR_OVERHANG, colors.cursor)
    }
}

private class GlyphPainter(
    private val scope: DrawScope,
    private val staffSpace: Float,
    private val origin: Offset,
    typeface: Typeface,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        textSize = 4 * staffSpace
    }

    private fun px(x: Double): Float = origin.x + (x * staffSpace).toFloat()

    private fun py(y: Double): Float = origin.y - (y * staffSpace).toFloat()

    fun draw(element: Element, color: Color) {
        when (element) {
            is GlyphElement -> drawGlyph(element.glyph, element.x, element.y, element.scale, color)
            is LineElement -> scope.drawLine(
                color = color,
                start = Offset(px(element.x1), py(element.y1)),
                end = Offset(px(element.x2), py(element.y2)),
                strokeWidth = (element.thickness * staffSpace).toFloat(),
            )
        }
    }

    fun drawGlyph(glyph: Glyph, x: Double, y: Double, scale: Double, color: Color) {
        paint.color = color.toArgb()
        paint.textSize = (4 * staffSpace * scale).toFloat()
        scope.drawContext.canvas.nativeCanvas.drawText(glyph.text, px(x), py(y), paint)
    }

    /**
     * A black notehead that is not in the layout, on the staff whose bottom line is at
     * [baselineY], with its accidental and ledger lines, left edge of the accidental (or
     * head) at [x]. Returns the head's width at [scale].
     */
    fun drawLooseNote(x: Double, position: StaffPosition, baselineY: Double, accidental: Glyph?, scale: Double, color: Color): Double {
        val head = BravuraMetrics.of(Glyph.NOTEHEAD_BLACK)
        val headWidth = head.width * scale
        val y = baselineY + position.y
        var headX = x
        accidental?.let { glyph ->
            val metrics = BravuraMetrics.of(glyph)
            drawGlyph(glyph, x - metrics.left * scale, y, scale, color)
            headX = x + (metrics.width + Spacing.ACCIDENTAL_GAP) * scale
        }
        for (ledger in position.ledgerLines) {
            val extension = BravuraMetrics.LEDGER_LINE_EXTENSION * scale
            scope.drawLine(
                color = color,
                start = Offset(px(headX - extension), py(baselineY + ledger.y)),
                end = Offset(px(headX + headWidth + extension), py(baselineY + ledger.y)),
                strokeWidth = (BravuraMetrics.LEDGER_LINE_THICKNESS * staffSpace).toFloat(),
            )
        }
        drawGlyph(Glyph.NOTEHEAD_BLACK, headX - head.left * scale, y, scale, color)
        return headWidth
    }

    /** An outcome mark in the row under the staff whose bottom line is at [baselineY], centred on [centerX]. */
    fun drawMark(glyph: OutcomeGlyph, centerX: Double, baselineY: Double, color: Color) {
        val half = MARK_SIZE / 2
        val rowY = baselineY + MARK_ROW_Y
        val bounds = Rect(
            left = px(centerX - half),
            top = py(rowY + half),
            right = px(centerX + half),
            bottom = py(rowY - half),
        )
        scope.drawOutcomeMark(glyph, color, bounds, (MARK_STROKE * staffSpace).toFloat())
    }

    /**
     * A small filled triangle beside the mark centred on [markCenterX]: on its left pointing
     * left for an early note, on its right pointing right for a late one.
     */
    fun drawTimingArrow(timing: TimingJudgement, markCenterX: Double, baselineY: Double, color: Color) {
        val half = TIMING_ARROW_SIZE / 2
        val rowY = baselineY + MARK_ROW_Y
        val direction = when (timing) {
            TimingJudgement.EARLY -> -1.0
            TimingJudgement.LATE -> 1.0
            TimingJudgement.ON_TIME -> return
        }
        val edge = markCenterX + direction * (MARK_SIZE / 2 + TIMING_ARROW_GAP)
        val tip = edge + direction * TIMING_ARROW_SIZE
        val path = Path().apply {
            moveTo(px(edge), py(rowY + half))
            lineTo(px(tip), py(rowY))
            lineTo(px(edge), py(rowY - half))
            close()
        }
        scope.drawPath(path, color)
    }

    /** The beat cursor: a thin translucent line at [x] from [bottomY] up to [topY]. */
    fun drawCursor(x: Double, bottomY: Double, topY: Double, color: Color) {
        scope.drawLine(
            color = color.copy(alpha = CURSOR_ALPHA),
            start = Offset(px(x), py(bottomY)),
            end = Offset(px(x), py(topY)),
            strokeWidth = (CURSOR_THICKNESS * staffSpace).toFloat(),
        )
    }
}
