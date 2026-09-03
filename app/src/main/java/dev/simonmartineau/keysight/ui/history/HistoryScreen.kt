package dev.simonmartineau.keysight.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.history.SessionSummary
import dev.simonmartineau.keysight.history.StoredRun
import dev.simonmartineau.keysight.ui.practice.levelLine
import dev.simonmartineau.keysight.ui.practice.scoreLine
import dev.simonmartineau.keysight.ui.practice.summaryHeader

/**
 * Past sessions, newest first. Arriving from practice expands [currentSessionId], so the
 * session being played is the summary on top; a tap expands or collapses any other.
 */
@Composable
fun HistoryScreen(container: AppContainer, currentSessionId: String?, onOpenRun: (runId: String) -> Unit, onBack: () -> Unit) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    LaunchedEffect(currentSessionId) { viewModel.expand(currentSessionId) }

    HistoryContent(
        sessions = sessions,
        currentSessionId = currentSessionId,
        expanded = expanded,
        summary = summary,
        onToggle = viewModel::toggle,
        onOpenRun = onOpenRun,
        onBack = onBack,
    )
}

/**
 * The list. [sessions] is null while loading; [summary] is the expanded session's, null
 * while it loads. An expanded session shows its summary, then its runs, each a row that
 * opens the run's page; the weakest bars and the moves open the run they name.
 */
@Composable
fun HistoryContent(
    sessions: List<SessionRecord>?,
    currentSessionId: String?,
    expanded: String?,
    summary: SessionSummary?,
    onToggle: (String) -> Unit,
    onOpenRun: (runId: String) -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = "History", backLabel = "Practice", onBack = onBack) {
        when {
            sessions == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing recorded yet. Every run you play ends up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sessions, key = { it.id }) { session ->
                    val open = session.id == expanded
                    SessionCard(
                        title = sessionTitle(session, currentSessionId),
                        expanded = open,
                        summary = summary?.takeIf { open && it.session.id == session.id },
                        onToggle = { onToggle(session.id) },
                        onOpenRun = onOpenRun,
                    )
                }
            }
        }
    }
}

/** A title row with a back button, and the content below it: the frame both history screens share. */
@Composable
internal fun ScreenScaffold(title: String, backLabel: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Text(backLabel) }
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SessionCard(title: String, expanded: Boolean, summary: SessionSummary?, onToggle: () -> Unit, onOpenRun: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (expanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                if (summary == null) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    SessionSummaryBlock(summary, onOpenRun, Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    summary.runs.forEachIndexed { index, run ->
                        HorizontalDivider()
                        RunRow(index + 1, run, Modifier.clickable { onOpenRun(run.record.id) })
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * The session pooled: runs and bars, the score line, the level at its start and its end,
 * every move as it was announced, and the weakest bars. Each move and each bar names a run
 * of the list below and opens its page.
 */
@Composable
fun SessionSummaryBlock(summary: SessionSummary, onOpenRun: (String) -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Text(sessionCountsLine(summary), style = MaterialTheme.typography.bodyMedium, color = muted)
        sessionScoreLine(summary)?.let { line ->
            Spacer(Modifier.height(4.dp))
            Text(line, style = MaterialTheme.typography.titleMedium)
        }
        val levelLines = sessionLevelLines(summary)
        if (levelLines.isNotEmpty()) Spacer(Modifier.height(8.dp))
        levelLines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
        summary.moves.forEach { move ->
            Text(
                move.line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onOpenRun(move.runId) }
                    .padding(vertical = 2.dp),
            )
        }
        if (summary.weakestBars.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(if (summary.weakestBars.size == 1) "Weakest bar" else "Weakest bars", style = MaterialTheme.typography.labelMedium, color = muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                summary.weakestBars.forEach { bar ->
                    SuggestionChip(onClick = { onOpenRun(bar.runId) }, label = { Text(bar.label) })
                }
            }
        }
    }
}

/** One run of a session: when, what it was, the level it was read at, how it went, and why it stopped if it did. */
@Composable
private fun RunRow(runIndex: Int, run: StoredRun, modifier: Modifier = Modifier) {
    val record = run.record
    val evaluation = run.evaluation
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(Modifier.width(64.dp)) {
            Text("Run $runIndex", style = MaterialTheme.typography.labelMedium, color = muted)
            Text(timeLabel(record.startedAtEpochMillis), style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.weight(1f)) {
            Text(summaryHeader(record.config, record.score, record.segments.size), style = MaterialTheme.typography.bodyMedium)
            levelLine(record.segments)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
            if (evaluation.committedCount > 0) {
                Text(scoreLine(evaluation.pitch, evaluation.rhythm), style = MaterialTheme.typography.bodyMedium, color = muted)
            }
            record.abortReason?.let { reason ->
                Text(stoppedLine(reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
