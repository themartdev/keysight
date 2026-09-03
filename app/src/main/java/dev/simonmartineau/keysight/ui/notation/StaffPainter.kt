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
import dev.simonmartineau.keysight.notation.Element
import dev.simonmartineau.keysight.notation.Glyph
import dev.simonmartineau.keysight.notation.GlyphElement
import dev.simonmartineau.keysight.notation.LineElement
import dev.simonmartineau.keysight.notation.NoteMark
import dev.simonmartineau.keysight.notation.Spacing
import dev.simonmartineau.keysight.notation.StaffLayout
import dev.simonmartineau.keysight.notation.StaffPosition
import dev.simonmartineau.keysight.ui.theme.OutcomeGlyph
import dev.simonmartineau.keysight.ui.theme.drawOutcomeMark

/** Ink for the engraving and one colour per kind of outcome. */
data class StaffColors(
    val ink: Color,
    val correct: Color,
    val wrong: Color,
    val missing: Color,
    val extra: Color,
)

/** Vertical centre of the row of outcome marks under the staff, in staff spaces. */
private const val MARK_ROW_Y = -5.25

/** Side of the square an outcome mark is drawn in, in staff spaces. */
private const val MARK_SIZE = 1.5

private const val MARK_STROKE = 0.18

/** Side of the early/late triangle beside a mark, and its distance from the mark's edge. */
private const val TIMING_ARROW_SIZE = 0.6
private const val TIMING_ARROW_GAP = 0.15

/**
 * Paints a [StaffLayout] at [staffSpace] pixels per staff space, with the layout's origin
 * (left edge, bottom staff line) at [origin].
 *
 * Glyphs go through the native canvas: SMuFL glyphs are positioned by their baseline
 * origin, which is exactly what `Canvas.drawText` takes, and one em is one staff height, so
 * the text size is four staff spaces. Lines are drawn as Compose lines.
 *
 * [marks] tint the elements of the notes they name and add what the layout does not have:
 * the mark under each note, the pitch played instead of a wrong one, and the notes that
 * were played but not written.
 */
fun DrawScope.drawStaff(
    layout: StaffLayout,
    staffSpace: Float,
    origin: Offset,
    typeface: Typeface,
    colors: StaffColors,
    marks: List<NoteMark> = emptyList(),
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
        val color = element.noteId?.let(tints::get) ?: colors.ink
        painter.draw(element, color)
    }

    for (mark in marks) {
        when (mark) {
            is NoteMark.Correct -> {
                val anchor = layout.anchors.getValue(mark.noteId)
                val centre = anchor.x + anchor.headWidth / 2
                painter.drawMark(OutcomeGlyph.CHECK, centre, colors.correct)
                mark.timing?.let { painter.drawTimingArrow(it, centre, colors.correct) }
            }
            is NoteMark.Missing -> {
                val anchor = layout.anchors.getValue(mark.noteId)
                painter.drawMark(OutcomeGlyph.DASH, anchor.x + anchor.headWidth / 2, colors.missing)
            }
            is NoteMark.WrongPitch -> {
                val anchor = layout.anchors.getValue(mark.noteId)
                painter.drawLooseNote(
                    x = anchor.x + anchor.headWidth + Spacing.CUE_GAP,
                    position = mark.played,
                    alteration = mark.playedAlteration,
                    scale = Spacing.CUE_SCALE,
                    color = colors.wrong,
                )
                val centre = anchor.x + anchor.headWidth / 2
                painter.drawMark(OutcomeGlyph.CROSS, centre, colors.wrong)
                mark.timing?.let { painter.drawTimingArrow(it, centre, colors.wrong) }
            }
            is NoteMark.Extra -> {
                val headWidth = painter.drawLooseNote(mark.x, mark.position, mark.alteration, scale = 1.0, color = colors.extra)
                painter.drawMark(OutcomeGlyph.PLUS, mark.x + headWidth / 2, colors.extra)
            }
        }
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
            is GlyphElement -> drawGlyph(element.glyph, element.x, element.y, scale = 1.0, color = color)
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
     * A black notehead that is not in the layout, with its accidental and ledger lines,
     * left edge of the accidental (or head) at [x]. Returns the head's width at [scale].
     */
    fun drawLooseNote(x: Double, position: StaffPosition, alteration: Int, scale: Double, color: Color): Double {
        val head = BravuraMetrics.of(Glyph.NOTEHEAD_BLACK)
        val headWidth = head.width * scale
        var headX = x
        Glyph.accidentalFor(alteration)?.let { accidental ->
            val metrics = BravuraMetrics.of(accidental)
            drawGlyph(accidental, x - metrics.left * scale, position.y, scale, color)
            headX = x + (metrics.width + Spacing.ACCIDENTAL_GAP) * scale
        }
        for (ledger in position.ledgerLines) {
            val extension = BravuraMetrics.LEDGER_LINE_EXTENSION * scale
            scope.drawLine(
                color = color,
                start = Offset(px(headX - extension), py(ledger.y)),
                end = Offset(px(headX + headWidth + extension), py(ledger.y)),
                strokeWidth = (BravuraMetrics.LEDGER_LINE_THICKNESS * staffSpace).toFloat(),
            )
        }
        drawGlyph(Glyph.NOTEHEAD_BLACK, headX - head.left * scale, position.y, scale, color)
        return headWidth
    }

    /** An outcome mark in the row under the staff, centred on [centerX]. */
    fun drawMark(glyph: OutcomeGlyph, centerX: Double, color: Color) {
        val half = MARK_SIZE / 2
        val bounds = Rect(
            left = px(centerX - half),
            top = py(MARK_ROW_Y + half),
            right = px(centerX + half),
            bottom = py(MARK_ROW_Y - half),
        )
        scope.drawOutcomeMark(glyph, color, bounds, (MARK_STROKE * staffSpace).toFloat())
    }

    /**
     * A small filled triangle beside the mark centred on [markCenterX]: on its left pointing
     * left for an early note, on its right pointing right for a late one.
     */
    fun drawTimingArrow(timing: TimingJudgement, markCenterX: Double, color: Color) {
        val half = TIMING_ARROW_SIZE / 2
        val direction = when (timing) {
            TimingJudgement.EARLY -> -1.0
            TimingJudgement.LATE -> 1.0
            TimingJudgement.ON_TIME -> return
        }
        val edge = markCenterX + direction * (MARK_SIZE / 2 + TIMING_ARROW_GAP)
        val tip = edge + direction * TIMING_ARROW_SIZE
        val path = Path().apply {
            moveTo(px(edge), py(MARK_ROW_Y + half))
            lineTo(px(tip), py(MARK_ROW_Y))
            lineTo(px(edge), py(MARK_ROW_Y - half))
            close()
        }
        scope.drawPath(path, color)
    }
}
