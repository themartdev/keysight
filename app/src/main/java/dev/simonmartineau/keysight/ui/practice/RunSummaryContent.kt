package dev.simonmartineau.keysight.ui.practice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.evaluation.RunEvaluation
import dev.simonmartineau.keysight.notation.PageLayout
import dev.simonmartineau.keysight.notation.noteMarks
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.ui.notation.RunSummaryPage

/**
 * The summary of one run: the header, the score line, every system of the score annotated
 * with the marks of [evaluation], then the remarks that earned their place, the weakest bars,
 * the bars the level changed at, and [linesAfter]. The practice screen shows it when a run
 * ends and history shows the same for a stored run, so the two never disagree.
 */
@Composable
fun RunSummaryContent(
    config: RunConfig,
    score: Score,
    segments: List<Segment>,
    evaluation: RunEvaluation,
    modifier: Modifier = Modifier,
    linesAfter: List<String> = emptyList(),
) {
    val pitch = evaluation.pitch
    val rhythm = evaluation.rhythm
    val marks = remember(score, evaluation) {
        { page: PageLayout -> noteMarks(page, score, pitch.outcomes, rhythm) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxSize()) {
        Text(
            summaryHeader(config, score, segments.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${pitch.correctCount} / ${pitch.expectedCount} notes correct",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            scoreLine(pitch, rhythm),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            RunSummaryPage(score, Modifier.fillMaxSize(), marks)
        }
        val lines = remarks(pitch, rhythm) + listOfNotNull(weakestBarsLine(evaluation)) + levelChangeLines(segments) + linesAfter
        lines.forEach { remark ->
            Text(
                remark,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
