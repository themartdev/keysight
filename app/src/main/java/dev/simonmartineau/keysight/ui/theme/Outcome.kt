package dev.simonmartineau.keysight.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** The colours the evaluation marks use; fixed per theme so correct and wrong always read the same. */
data class OutcomeColors(val correct: Color, val wrong: Color, val extra: Color, val missing: Color)

val MaterialTheme.outcomeColors: OutcomeColors
    @Composable
    get() = if (isSystemInDarkTheme()) {
        OutcomeColors(correct = CorrectDark, wrong = WrongDark, extra = AmberLight, missing = colorScheme.onSurfaceVariant)
    } else {
        OutcomeColors(correct = Correct, wrong = Wrong, extra = Amber, missing = colorScheme.onSurfaceVariant)
    }

enum class OutcomeGlyph { CHECK, CROSS, PLUS }

/** A stroked check, cross or plus, drawn rather than pulled from an icon pack so the weight matches the notehead. */
@Composable
fun OutcomeMark(glyph: OutcomeGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        val inset = w * 0.2f
        val path = Path()
        when (glyph) {
            OutcomeGlyph.CHECK -> {
                path.moveTo(inset, h * 0.55f)
                path.lineTo(w * 0.42f, h - inset)
                path.lineTo(w - inset, inset)
            }
            OutcomeGlyph.CROSS -> {
                path.moveTo(inset, inset)
                path.lineTo(w - inset, h - inset)
                path.moveTo(w - inset, inset)
                path.lineTo(inset, h - inset)
            }
            OutcomeGlyph.PLUS -> {
                path.moveTo(w / 2, inset)
                path.lineTo(w / 2, h - inset)
                path.moveTo(inset, h / 2)
                path.lineTo(w - inset, h / 2)
            }
        }
        drawPath(path, color, style = stroke)
    }
}
