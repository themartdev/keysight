package dev.simonmartineau.keysight.ui.practice

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.evaluation.RunEvaluation
import dev.simonmartineau.keysight.notation.PageLayout
import dev.simonmartineau.keysight.notation.noteMarks
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.ui.notation.RunSummaryPage

/**
 * The summary of one run: every system of [score] annotated with the marks of [evaluation],
 * [linesAbove] over it and [linesBelow] under it, all in one scroll. The practice stage
 * shows it when a run ends with the numbers in its strip and the remarks below the page;
 * history shows the same for a stored run with the numbers above the page, so the two never
 * disagree on the page itself.
 */
@Composable
fun RunSummaryContent(
    score: Score,
    evaluation: RunEvaluation,
    modifier: Modifier = Modifier,
    linesAbove: List<SummaryLine> = emptyList(),
    linesBelow: List<String> = emptyList(),
) {
    val pitch = evaluation.pitch
    val rhythm = evaluation.rhythm
    val marks = remember(score, evaluation) {
        { page: PageLayout -> noteMarks(page, score, pitch.outcomes, rhythm) }
    }
    RunSummaryPage(
        score = score,
        modifier = modifier,
        marks = marks,
        above = {
            linesAbove.forEach { line ->
                Text(
                    line.text,
                    style = if (line.headline) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyMedium,
                    color = if (line.headline) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            if (linesAbove.isNotEmpty()) Spacer(Modifier.height(8.dp))
        },
        below = {
            linesBelow.forEach { remark ->
                Text(
                    remark,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        },
    )
}

/** A line over the summary's page: the count of notes as a headline, everything else muted. */
class SummaryLine(val text: String, val headline: Boolean = false)
