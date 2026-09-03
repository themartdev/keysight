package dev.simonmartineau.keysight.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.evaluation.NoteOutcome
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.ui.theme.OutcomeGlyph
import dev.simonmartineau.keysight.ui.theme.OutcomeMark
import dev.simonmartineau.keysight.ui.theme.outcomeColors

/**
 * Stand-in for engraved notation until round 3: one cell per note, as wide as the note is
 * long, with the pitch name and a notehead that is filled for a quarter and hollow for
 * anything longer. The same cells carry the evaluation marks on the result screen.
 */
data class NoteCell(
    val label: String,
    val weightBeats: Float,
    val hollow: Boolean,
    val mark: Mark? = null,
) {
    sealed interface Mark {
        data object Correct : Mark
        data class WrongPitch(val played: String) : Mark
        data object Missing : Mark
        data object Extra : Mark
    }
}

/** Cells for a score as the player will read it. */
fun Score.toNoteCells(): List<NoteCell> = notesInPerformanceOrder.map { note -> note.toCell(timeSignature.beatsOf(note.duration)) }

/** Cells for a score as the player performed it: extras appear where they were played. */
fun Score.toNoteCells(outcomes: List<NoteOutcome>): List<NoteCell> = outcomes.map { outcome ->
    when (outcome) {
        is NoteOutcome.Correct -> outcome.expected.toCell(beats(outcome.expected), NoteCell.Mark.Correct)
        is NoteOutcome.WrongPitch -> outcome.expected.toCell(beats(outcome.expected), NoteCell.Mark.WrongPitch(outcome.played.pitch.toString()))
        is NoteOutcome.Missing -> outcome.expected.toCell(beats(outcome.expected), NoteCell.Mark.Missing)
        is NoteOutcome.Extra -> NoteCell(outcome.played.pitch.toString(), EXTRA_CELL_BEATS, hollow = false, mark = NoteCell.Mark.Extra)
    }
}

private const val EXTRA_CELL_BEATS = 0.6f

private fun Score.beats(note: ScoreNote): Float = timeSignature.beatsOf(note.duration).toFloat()

private fun ScoreNote.toCell(beats: Double, mark: NoteCell.Mark? = null) =
    NoteCell(label = spelling.toString(), weightBeats = beats.toFloat(), hollow = duration > Ticks.QUARTER, mark = mark)

private fun ScoreNote.toCell(beats: Float, mark: NoteCell.Mark?) = toCell(beats.toDouble(), mark)

@Composable
fun NotationPlaceholder(cells: List<NoteCell>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.forEach { cell ->
            NoteCellView(cell, Modifier.weight(cell.weightBeats))
        }
    }
}

@Composable
private fun NoteCellView(cell: NoteCell, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.outcomeColors
    val accent: Color? = when (cell.mark) {
        NoteCell.Mark.Correct -> colors.correct
        is NoteCell.Mark.WrongPitch -> colors.wrong
        NoteCell.Mark.Missing -> colors.missing
        NoteCell.Mark.Extra -> colors.extra
        null -> null
    }
    val ink = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(if (accent != null) 2.dp else 1.dp, accent ?: MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Notehead(hollow = cell.hollow, color = if (cell.mark == NoteCell.Mark.Missing) colors.missing else ink)
            Spacer(Modifier.height(8.dp))
            Text(
                text = cell.label,
                style = MaterialTheme.typography.titleLarge,
                color = if (cell.mark == NoteCell.Mark.Missing) colors.missing else ink,
                maxLines = 1,
            )
            when (val mark = cell.mark) {
                null -> Unit
                NoteCell.Mark.Correct -> MarkIcon(OutcomeGlyph.CHECK, accent!!)
                is NoteCell.Mark.WrongPitch -> {
                    MarkIcon(OutcomeGlyph.CROSS, accent!!)
                    Text(
                        text = "played ${mark.played}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
                NoteCell.Mark.Missing -> Text(
                    text = "missed",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent!!,
                    modifier = Modifier.padding(top = 4.dp),
                )
                NoteCell.Mark.Extra -> MarkIcon(OutcomeGlyph.PLUS, accent!!)
            }
        }
    }
}

@Composable
private fun MarkIcon(glyph: OutcomeGlyph, tint: Color) {
    OutcomeMark(glyph, tint, Modifier.padding(top = 6.dp).size(20.dp))
}

@Composable
private fun Notehead(hollow: Boolean, color: Color) {
    Canvas(Modifier.size(width = 22.dp, height = 16.dp)) {
        if (hollow) {
            drawOval(color = color, style = Stroke(width = 2.dp.toPx()))
        } else {
            drawOval(color = color)
        }
    }
}
