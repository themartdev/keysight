package dev.simonmartineau.keysight.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.simonmartineau.keysight.attempt.AbortReason
import dev.simonmartineau.keysight.attempt.AttemptState
import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.notation.Mask
import dev.simonmartineau.keysight.notation.PageLayout
import dev.simonmartineau.keysight.notation.noteMarks
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.settings.FlashChoices
import dev.simonmartineau.keysight.settings.ThemeMode
import dev.simonmartineau.keysight.ui.notation.Page

@Composable
fun PracticeScreen(container: AppContainer) {
    val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        onStopOrDispose { viewModel.onBackgrounded() }
    }

    PracticeContent(
        state = state,
        connection = connection,
        config = config,
        content = content,
        theme = theme,
        loadError = loadError,
        actions = PracticeActions(
            start = viewModel::start,
            cancel = viewModel::cancel,
            next = viewModel::next,
            retry = viewModel::retry,
            setPreviewBeats = viewModel::setPreviewBeats,
            setTempo = viewModel::setTempo,
            setMetronomeDuringAttempt = viewModel::setMetronomeDuringAttempt,
            setKey = viewModel::setKey,
            setHands = viewModel::setHands,
            setTheme = viewModel::setTheme,
        ),
    )
}

class PracticeActions(
    val start: () -> Unit,
    val cancel: () -> Unit,
    val next: () -> Unit,
    val retry: () -> Unit,
    val setPreviewBeats: (Double) -> Unit,
    val setTempo: (Double) -> Unit,
    val setMetronomeDuringAttempt: (Boolean) -> Unit,
    val setKey: (KeySignature) -> Unit,
    val setHands: (Hands) -> Unit,
    val setTheme: (ThemeMode) -> Unit,
)

@Composable
fun PracticeContent(
    state: AttemptState?,
    connection: MidiConnection,
    config: FlashConfig,
    content: ContentConfig,
    theme: ThemeMode,
    loadError: String?,
    actions: PracticeActions,
) {
    val settingsEnabled = state == null || state is AttemptState.Ready || state.isTerminal
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                MidiStatusRow(connection, Modifier.weight(1f))
                ThemeMenu(theme, actions.setTheme)
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow(config, content, settingsEnabled, actions)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Stage(state, loadError)
            }
            ActionBar(state, connection, actions)
        }
    }
}

@Composable
private fun MidiStatusRow(connection: MidiConnection, modifier: Modifier = Modifier) {
    val (color, text) = when (connection) {
        MidiConnection.NoDevice -> MaterialTheme.colorScheme.outline to "Connect a MIDI keyboard"
        is MidiConnection.Connecting -> MaterialTheme.colorScheme.secondary to "Connecting to ${connection.deviceName}"
        is MidiConnection.Connected -> MaterialTheme.colorScheme.primary to connection.deviceName
        is MidiConnection.Failed -> MaterialTheme.colorScheme.error to "${connection.deviceName}: ${connection.message}"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsRow(config: FlashConfig, content: ContentConfig, enabled: Boolean, actions: PracticeActions) {
    Column {
        Text("Preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FlashChoices.PREVIEW_BEATS.forEach { beats ->
                FilterChip(
                    selected = beats == config.previewDurationBeats,
                    onClick = { actions.setPreviewBeats(beats) },
                    enabled = enabled,
                    label = { Text(beats.beatsLabel()) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ChoiceMenu(content.keySignature.majorName, KeySignature.ALL, enabled, { it.majorName }, actions.setKey)
            ChoiceMenu(content.hands.label, Hands.entries, enabled, { it.label }, actions.setHands)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ChoiceMenu(config.tempoBpm.bpmLabel(), FlashChoices.TEMPOS_BPM, enabled, { it.bpmLabel() }, actions.setTempo)
            Spacer(Modifier.weight(1f))
            Text(
                "Click while playing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Switch(checked = config.metronomeDuringAttempt, onCheckedChange = actions.setMetronomeDuringAttempt, enabled = enabled)
        }
    }
}

private fun Double.bpmLabel(): String = "${toInt()} bpm"

@Composable
private fun ThemeMenu(theme: ThemeMode, onTheme: (ThemeMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(theme.label())
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    onClick = {
                        open = false
                        onTheme(mode)
                    },
                )
            }
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System theme"
    ThemeMode.LIGHT -> "Light theme"
    ThemeMode.DARK -> "Dark theme"
}

/** An outlined button showing [current] that opens a menu of [choices]. */
@Composable
private fun <T> ChoiceMenu(current: String, choices: List<T>, enabled: Boolean, label: (T) -> String, onChoice: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled) {
            Text(current)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(label(choice)) },
                    onClick = {
                        open = false
                        onChoice(choice)
                    },
                )
            }
        }
    }
}

private fun Double.beatsLabel(): String = if (this == this.toInt().toDouble()) this.toInt().toString() else this.toString()

@Composable
private fun Stage(state: AttemptState?, loadError: String?) {
    when (state) {
        null -> if (loadError != null) {
            Text(loadError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        } else {
            CircularProgressIndicator()
        }
        is AttemptState.Ready -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ready", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "The music shows during the count-in and its notes disappear when you start playing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        is AttemptState.CountingIn -> PageStage(state.context.exercise.score, mask = if (state.notationVisible) Mask.NONE else Mask.ALL) {
            BeatIndicator(state.context.timeline, state.startedAtNanos)
        }
        is AttemptState.Performing -> PageStage(state.context.exercise.score, mask = Mask.ALL) {
            BeatIndicator(state.context.timeline, state.startedAtNanos)
        }
        is AttemptState.Evaluating -> CircularProgressIndicator()
        is AttemptState.Result -> ResultPanel(state)
        is AttemptState.Aborted -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Attempt stopped", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(state.reason.message(), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** The page filling the stage, with [below] under it. */
@Composable
private fun PageStage(score: Score, mask: Mask, evaluation: EvaluationResult? = null, below: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ExercisePage(score, mask, evaluation)
        }
        Spacer(Modifier.height(16.dp))
        below()
    }
}

private fun AbortReason.message(): String = when (this) {
    AbortReason.CANCELLED -> "You stopped it."
    AbortReason.MIDI_DISCONNECTED -> "The keyboard disconnected."
    AbortReason.BACKGROUNDED -> "The app went to the background."
}

@Composable
private fun ResultPanel(result: AttemptState.Result) {
    val pitch = result.evaluation.pitch
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        Text(
            "${pitch.correctCount} / ${pitch.expectedCount} notes correct",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            scoreLine(pitch, result.evaluation.rhythm),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ExercisePage(result.context.exercise.score, Mask.NONE, result.evaluation)
        }
        remarks(pitch, result.evaluation.rhythm).forEach { remark ->
            Text(
                remark,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The engraved exercise, laid out for the space it gets and, after an attempt, annotated
 * with the evaluator's outcomes.
 */
@Composable
private fun ExercisePage(score: Score, mask: Mask, evaluation: EvaluationResult? = null) {
    val marks = remember(score, evaluation) {
        { page: PageLayout -> if (evaluation == null) emptyList() else noteMarks(page, score, evaluation.pitch.outcomes, evaluation.rhythm) }
    }
    Page(score, Modifier.fillMaxSize(), mask, marks)
}

@Composable
private fun ActionBar(state: AttemptState?, connection: MidiConnection, actions: PracticeActions) {
    val connected = connection is MidiConnection.Connected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            null, is AttemptState.Evaluating -> Unit
            is AttemptState.Ready -> Button(onClick = actions.start, enabled = connected, modifier = Modifier.weight(1f)) {
                Text(if (connected) "Start" else "Connect a keyboard to start")
            }
            is AttemptState.CountingIn, is AttemptState.Performing ->
                OutlinedButton(onClick = actions.cancel, modifier = Modifier.weight(1f)) { Text("Stop") }
            is AttemptState.Result -> Button(onClick = actions.next, modifier = Modifier.weight(1f)) { Text("Next") }
            is AttemptState.Aborted -> {
                OutlinedButton(onClick = actions.retry, modifier = Modifier.weight(1f)) { Text("Try again") }
                Button(onClick = actions.next, modifier = Modifier.weight(1f)) { Text("Next") }
            }
        }
    }
}
