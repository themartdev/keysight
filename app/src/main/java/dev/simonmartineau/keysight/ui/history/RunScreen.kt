package dev.simonmartineau.keysight.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.ui.practice.RunSummaryContent

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
    val title = (page as? RunPageState.Loaded)?.let { dateTimeLabel(it.run.record.startedAtEpochMillis) } ?: "Run"
    ScreenScaffold(title = title, backLabel = "History", onBack = onBack) {
        when (page) {
            RunPageState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            RunPageState.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "This run is no longer in history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
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
