package dev.simonmartineau.keysight.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.ui.practice.RunSummaryContent
import dev.simonmartineau.keysight.ui.theme.Metrics
import dev.simonmartineau.keysight.ui.theme.QuietButton
import dev.simonmartineau.keysight.ui.theme.palette
import dev.simonmartineau.keysight.ui.theme.type

/** A stored run's page: the practice summary of that run, at the current evaluator version, the score open. */
@Composable
fun RunScreen(container: AppContainer, runId: String, onBack: () -> Unit) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
    val page by viewModel.runPage.collectAsStateWithLifecycle()

    LaunchedEffect(runId) { viewModel.openRun(runId) }

    RunContent(page, onBack)
}

@Composable
fun RunContent(page: RunPageState, onBack: () -> Unit) {
    val palette = MaterialTheme.palette
    val title = (page as? RunPageState.Loaded)?.let { dateTimeLabel(it.run.record.startedAtEpochMillis) } ?: "Run"
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Metrics.PagePaddingSides, top = Metrics.PagePaddingTop, end = Metrics.PagePaddingSides, bottom = Metrics.PagePaddingBottom),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.type.screenTitle, color = palette.ink, modifier = Modifier.weight(1f))
            QuietButton("History", onClick = onBack)
        }
        Spacer(Modifier.height(Metrics.GapControls))
        when (page) {
            RunPageState.Loading -> Unit
            RunPageState.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This run is no longer in history.", style = MaterialTheme.type.body, color = palette.onSurfaceMuted)
            }
            is RunPageState.Loaded -> {
                val record = page.run.record
                RunSummaryContent(
                    config = record.config,
                    score = record.score,
                    segments = record.segments,
                    evaluation = page.run.evaluation,
                    linesAfter = listOfNotNull(record.abortReason?.let(::stoppedLine)),
                )
            }
        }
    }
}
