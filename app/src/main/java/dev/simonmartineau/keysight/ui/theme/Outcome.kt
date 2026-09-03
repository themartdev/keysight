package dev.simonmartineau.keysight.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** The colours the evaluation marks use; fixed per theme so correct and wrong always read the same. */
data class OutcomeColors(val correct: Color, val wrong: Color, val extra: Color, val missing: Color)

val MaterialTheme.outcomeColors: OutcomeColors
    @Composable
    get() = if (LocalDarkTheme.current) {
        OutcomeColors(correct = CorrectDark, wrong = WrongDark, extra = ExtraDark, missing = palette.onSurfaceMuted)
    } else {
        OutcomeColors(correct = Correct, wrong = Wrong, extra = Extra, missing = palette.onSurfaceMuted)
    }

enum class OutcomeGlyph { CHECK, CROSS, PLUS, DASH }

/**
 * A stroked check, cross, plus or dash inside [bounds], drawn rather than pulled from an
 * icon pack so the weight can follow the staff size.
 */
fun DrawScope.drawOutcomeMark(glyph: OutcomeGlyph, color: Color, bounds: Rect, strokeWidth: Float) {
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val inset = bounds.width * 0.2f
    val left = bounds.left
    val top = bounds.top
    val right = bounds.right
    val bottom = bounds.bottom
    val path = Path()
    when (glyph) {
        OutcomeGlyph.CHECK -> {
            path.moveTo(left + inset, top + bounds.height * 0.55f)
            path.lineTo(left + bounds.width * 0.42f, bottom - inset)
            path.lineTo(right - inset, top + inset)
        }
        OutcomeGlyph.CROSS -> {
            path.moveTo(left + inset, top + inset)
            path.lineTo(right - inset, bottom - inset)
            path.moveTo(right - inset, top + inset)
            path.lineTo(left + inset, bottom - inset)
        }
        OutcomeGlyph.PLUS -> {
            path.moveTo(bounds.center.x, top + inset)
            path.lineTo(bounds.center.x, bottom - inset)
            path.moveTo(left + inset, bounds.center.y)
            path.lineTo(right - inset, bounds.center.y)
        }
        OutcomeGlyph.DASH -> {
            path.moveTo(left + inset, bounds.center.y)
            path.lineTo(right - inset, bounds.center.y)
        }
    }
    drawPath(path, color, style = stroke)
}
