package dev.simonmartineau.keysight.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.history.DayCount
import dev.simonmartineau.keysight.history.SessionDigest
import dev.simonmartineau.keysight.history.SessionSummary
import dev.simonmartineau.keysight.history.StoredRun
import dev.simonmartineau.keysight.ui.practice.levelLine
import dev.simonmartineau.keysight.ui.practice.scoreLine
import dev.simonmartineau.keysight.ui.practice.summaryHeader
import dev.simonmartineau.keysight.ui.theme.Chip
import dev.simonmartineau.keysight.ui.theme.Hairline
import dev.simonmartineau.keysight.ui.theme.Metrics
import dev.simonmartineau.keysight.ui.theme.SectionHeading
import dev.simonmartineau.keysight.ui.theme.SessionRow
import dev.simonmartineau.keysight.ui.theme.SessionRowHeader
import dev.simonmartineau.keysight.ui.theme.Sparkbars
import dev.simonmartineau.keysight.ui.theme.palette
import dev.simonmartineau.keysight.ui.theme.type

/**
 * Past sessions as a table, newest first, under a fortnight of bars per day. Arriving from
 * a run expands [currentSessionId]; a row with one run opens that run's page, any other
 * expands to its summary and its runs.
 */
@Composable
fun HistoryScreen(container: AppContainer, currentSessionId: String?, onOpenRun: (runId: String) -> Unit) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val sessions by viewModel.digests.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    val expanded by viewModel.expanded.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    LaunchedEffect(currentSessionId) { viewModel.expand(currentSessionId) }

    HistoryContent(
        sessions = sessions,
        days = days,
        currentSessionId = currentSessionId,
        expanded = expanded,
        summary = summary,
        now = System.currentTimeMillis(),
        onToggle = viewModel::toggle,
        onOpenRun = onOpenRun,
    )
}

/**
 * The page. [sessions] is null while loading; [summary] is the expanded session's, null
 * while it loads. An expanded session shows its pooled summary, then its runs, each a row
 * that opens the run's page; the weakest bars and the moves open the run they name.
 */
@Composable
fun HistoryContent(
    sessions: List<SessionDigest>?,
    days: List<DayCount>,
    currentSessionId: String?,
    expanded: String?,
    summary: SessionSummary?,
    now: Long,
    onToggle: (String) -> Unit,
    onOpenRun: (runId: String) -> Unit,
) {
    val palette = MaterialTheme.palette
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Metrics.PagePaddingSides,
            top = Metrics.PagePaddingTop,
            end = Metrics.PagePaddingSides,
            bottom = Metrics.PagePaddingBottom,
        ),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("History", style = MaterialTheme.type.screenTitle, color = palette.ink, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("Bars per day, ${HistoryViewModel.CHART_DAYS} days", style = MaterialTheme.type.micro, color = palette.onSurfaceFaint)
                    Spacer(Modifier.height(6.dp))
                    Sparkbars(days.map { it.bars }, height = 44.dp)
                }
            }
            Spacer(Modifier.height(Metrics.GapBlocks))
        }
        when {
            sessions == null -> Unit
            sessions.isEmpty() -> item {
                Text("Nothing recorded yet. Every run you play ends up here.", style = MaterialTheme.type.body, color = palette.onSurfaceMuted)
            }
            else -> {
                item { SessionRowHeader() }
                items(sessions, key = { it.session.id }) { session ->
                    val open = session.session.id == expanded
                    SessionRow(
                        whenText = if (session.session.id == currentSessionId) "This session" else whenLabel(session.session.startedAtEpochMillis, now),
                        what = whatLabel(session),
                        runs = runsLabel(session.runCount),
                        accuracy = accuracyLabel(session.pooled),
                        expanded = open,
                        onClick = {
                            val only = session.runs.singleOrNull()
                            if (only != null) onOpenRun(only.id) else onToggle(session.session.id)
                        },
                    )
                    if (open) {
                        ExpandedSession(summary?.takeIf { it.session.id == session.session.id }, onOpenRun)
                    }
                }
            }
        }
    }
}

/** The expanded state of a row: the session pooled, then its runs. [summary] is null while it loads. */
@Composable
private fun ExpandedSession(summary: SessionSummary?, onOpenRun: (String) -> Unit) {
    val palette = MaterialTheme.palette
    Column(Modifier.fillMaxWidth().padding(start = 160.dp, top = Metrics.GapControls, bottom = Metrics.GapBlocks)) {
        if (summary == null) {
            Text("Reading the session", style = MaterialTheme.type.meta, color = palette.onSurfaceFaint)
        } else {
            SessionSummaryBlock(summary, onOpenRun)
            Spacer(Modifier.height(Metrics.GapControls))
            SectionHeading("Runs")
            summary.runs.forEachIndexed { index, run ->
                RunLine(index + 1, run, onClick = { onOpenRun(run.record.id) })
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
    val palette = MaterialTheme.palette
    Column(modifier) {
        Text(sessionCountsLine(summary), style = MaterialTheme.type.meta, color = palette.onSurfaceMuted)
        sessionScoreLine(summary)?.let { line ->
            Spacer(Modifier.height(4.dp))
            Text(line, style = MaterialTheme.type.lead, color = palette.ink)
        }
        val levelLines = sessionLevelLines(summary)
        if (levelLines.isNotEmpty()) Spacer(Modifier.height(Metrics.GapTight))
        levelLines.forEach { Text(it, style = MaterialTheme.type.body, color = palette.onSurfaceMuted) }
        summary.moves.forEach { move ->
            Text(
                move.line,
                style = MaterialTheme.type.body,
                color = palette.ink,
                modifier = Modifier
                    .clickable(role = Role.Button) { onOpenRun(move.runId) }
                    .padding(vertical = 2.dp),
            )
        }
        if (summary.weakestBars.isNotEmpty()) {
            Spacer(Modifier.height(Metrics.GapTight))
            Text(if (summary.weakestBars.size == 1) "Weakest bar" else "Weakest bars", style = MaterialTheme.type.micro, color = palette.onSurfaceFaint)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Metrics.GapTight)) {
                summary.weakestBars.forEach { bar ->
                    Chip(bar.label, onClick = { onOpenRun(bar.runId) })
                }
            }
        }
    }
}

/** One run of a session: when, what it was, the level it was read at, how it went, and why it stopped if it did. */
@Composable
private fun RunLine(runIndex: Int, run: StoredRun, onClick: () -> Unit) {
    val palette = MaterialTheme.palette
    val record = run.record
    val evaluation = run.evaluation
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (pressed) palette.paperDim else Color.Transparent)
                .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(vertical = 10.dp),
        ) {
            Column(Modifier.width(96.dp)) {
                Text("Run $runIndex", style = MaterialTheme.type.micro, color = palette.onSurfaceFaint)
                Text(timeLabel(record.startedAtEpochMillis), style = MaterialTheme.type.body, color = palette.ink)
            }
            Column(Modifier.weight(1f)) {
                Text(summaryHeader(record.config, record.score, record.segments.size), style = MaterialTheme.type.body, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                levelLine(record.segments)?.let { Text(it, style = MaterialTheme.type.meta, color = palette.onSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (evaluation.committedCount > 0) {
                    Text(scoreLine(evaluation.pitch, evaluation.rhythm), style = MaterialTheme.type.meta, color = palette.onSurfaceMuted, maxLines = 1)
                }
                record.abortReason?.let { reason ->
                    Text(stoppedLine(reason), style = MaterialTheme.type.meta, color = palette.onSurfaceMuted)
                }
            }
        }
        Hairline()
    }
}
