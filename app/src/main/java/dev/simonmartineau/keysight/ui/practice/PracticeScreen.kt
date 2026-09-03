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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.difficulty.Decision
import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.notation.NoteMark
import dev.simonmartineau.keysight.notation.PageLayout
import dev.simonmartineau.keysight.notation.noteMarks
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunContext
import dev.simonmartineau.keysight.run.RunState
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.runMask
import dev.simonmartineau.keysight.run.runMaskBeforeStart
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.settings.RunChoices
import dev.simonmartineau.keysight.settings.ThemeMode
import dev.simonmartineau.keysight.evaluation.RunEvaluation
import dev.simonmartineau.keysight.run.beatsLabel
import dev.simonmartineau.keysight.ui.notation.RunPage
import kotlin.math.floor

/** The practice screen; [onHistory] opens history with this screen's session, if a run has been recorded into one. */
@Composable
fun PracticeScreen(container: AppContainer, onHistory: (sessionId: String?) -> Unit) {
    val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val nextRun by viewModel.nextRun.collectAsStateWithLifecycle()
    val sessionId by viewModel.sessionId.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        onStopOrDispose { viewModel.onBackgrounded() }
    }

    PracticeContent(
        state = state,
        connection = connection,
        config = config,
        content = content,
        theme = theme,
        nextRun = nextRun,
        actions = PracticeActions(
            start = viewModel::start,
            stop = viewModel::stop,
            next = viewModel::next,
            retry = viewModel::retry,
            setMode = viewModel::setMode,
            setLookaheadBeats = viewModel::setLookaheadBeats,
            setTempo = viewModel::setTempo,
            setMetronome = viewModel::setMetronome,
            setSegmentCount = viewModel::setSegmentCount,
            setKey = viewModel::setKey,
            setHands = viewModel::setHands,
            setAccompaniment = viewModel::setAccompaniment,
            setTheme = viewModel::setTheme,
            history = { onHistory(sessionId) },
        ),
    )
}

class PracticeActions(
    val start: () -> Unit,
    val stop: () -> Unit,
    val next: () -> Unit,
    val retry: () -> Unit,
    val setMode: (VisibilityMode) -> Unit,
    val setLookaheadBeats: (Double) -> Unit,
    val setTempo: (Double) -> Unit,
    val setMetronome: (MetronomeMode) -> Unit,
    val setSegmentCount: (Int?) -> Unit,
    val setKey: (KeySignature) -> Unit,
    val setHands: (Hands) -> Unit,
    val setAccompaniment: (Accompaniment) -> Unit,
    val setTheme: (ThemeMode) -> Unit,
    val history: () -> Unit,
)

/**
 * The one screen. While a run is running the settings row gives way to the page, so the two
 * systems being read get the room; settings come back with the summary. [nextRun] is what
 * the difficulty controller decided when the run on screen ended, if it moved anything.
 */
@Composable
fun PracticeContent(
    state: RunState?,
    connection: MidiConnection,
    config: RunConfig,
    content: ContentConfig,
    theme: ThemeMode,
    actions: PracticeActions,
    nextRun: Decision? = null,
) {
    val settingsShown = state == null || state is RunState.Ready || state.isTerminal
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                MidiStatusRow(connection, Modifier.weight(1f))
                if (settingsShown) {
                    TextButton(onClick = actions.history) { Text("History") }
                }
                ThemeMenu(theme, actions.setTheme)
            }
            if (settingsShown) {
                Spacer(Modifier.height(4.dp))
                SettingsRow(config, content, actions)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Stage(state, nextRun)
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
private fun SettingsRow(config: RunConfig, content: ContentConfig, actions: PracticeActions) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            VisibilityMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == config.mode,
                    onClick = { actions.setMode(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        Text("Lookahead, beats", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            RunChoices.LOOKAHEAD_BEATS.forEach { beats ->
                FilterChip(
                    selected = beats == config.lookaheadBeats,
                    onClick = { actions.setLookaheadBeats(beats) },
                    enabled = config.mode == VisibilityMode.FLASH,
                    label = { Text(beats.beatsLabel()) },
                )
            }
        }
        Text("Length", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            RunChoices.SEGMENT_COUNTS.forEach { count ->
                FilterChip(
                    selected = count == config.segmentCount,
                    onClick = { actions.setSegmentCount(count) },
                    label = { Text(count?.let(::barsLabel) ?: "Open") },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ChoiceMenu(content.keySignature.majorName, KeySignature.ALL, { it.majorName }, actions.setKey)
            ChoiceMenu(content.hands.label, Hands.entries, { it.label }, actions.setHands)
        }
        if (content.hands == Hands.BOTH) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Accompaniment.entries.forEach { accompaniment ->
                    FilterChip(
                        selected = accompaniment == content.accompaniment,
                        onClick = { actions.setAccompaniment(accompaniment) },
                        label = { Text(accompaniment.label) },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ChoiceMenu(config.tempoBpm.bpmLabel(), RunChoices.TEMPOS_BPM, { it.bpmLabel() }, actions.setTempo)
            Spacer(Modifier.weight(1f))
            Text(
                "Click while playing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = config.metronome == MetronomeMode.THROUGHOUT,
                onCheckedChange = { actions.setMetronome(if (it) MetronomeMode.THROUGHOUT else MetronomeMode.COUNT_IN_ONLY) },
            )
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
private fun <T> ChoiceMenu(current: String, choices: List<T>, label: (T) -> String, onChoice: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
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

@Composable
private fun Stage(state: RunState?, nextRun: Decision?) {
    when (state) {
        null -> CircularProgressIndicator()
        is RunState.Ready -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                RunPage(state.context.score, Modifier.fillMaxSize(), mask = runMaskBeforeStart(state.context.timeline, state.context.policy))
            }
            Spacer(Modifier.height(8.dp))
            listOfNotNull(state.context.config.description(), levelLine(state.context)).forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        is RunState.Running -> RunStage(state)
        is RunState.Summary -> SummaryPanel(state, nextRun)
        is RunState.Aborted -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Run stopped", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(abortMessage(state.reason), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            nextRun?.let(::nextRunLine)?.let { line ->
                Spacer(Modifier.height(8.dp))
                Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

/** What the run will do, in one line, for the player about to start it. */
private fun RunConfig.description(): String {
    val bars = segmentCount?.let(::barsLabel) ?: "Until you stop"
    return when (mode) {
        VisibilityMode.FLASH -> "$bars. Each bar shows ${lookaheadBeats.beatsLabel()} ${if (lookaheadBeats == 1.0) "beat" else "beats"} ahead and disappears as you play it."
        VisibilityMode.READ_AHEAD -> "$bars. Every bar stays visible except the one you are playing."
        VisibilityMode.OPEN_SCORE -> "$bars. The score stays open; the cursor follows the beat."
    }
}

/**
 * The page during a run. One frame loop reads the frame time, which is on the same
 * `System.nanoTime` base as the run clock, and derives the beat from the timeline; the mask,
 * the cursor, the page turn and the beat dots all follow from that one number, so none of
 * them can drift from the metronome. The marks are the committed segments' outcomes; they
 * appear behind the cursor as each commit lands, and the mask keeps them off a hidden bar.
 */
@Composable
private fun RunStage(state: RunState.Running) {
    val context = state.context
    val timeline = context.timeline
    val marks = rememberMarks(context, state.evaluation)
    var beat by remember(state.startedAtNanos) { mutableDoubleStateOf(0.0) }
    LaunchedEffect(state.startedAtNanos) {
        while (true) {
            withFrameNanos { frameNanos ->
                beat = timeline.beatAtNanos(frameNanos - state.startedAtNanos)
            }
        }
    }
    val mask = runMask(timeline, context.policy, beat, state.lastSegment)
    val ticks = timeline.ticksAt(beat)
    val cursorShown = beat >= 0.0 && beat < timeline.segmentEndBeat(state.lastSegment)
    val beatsPerMeasure = timeline.timeSignature.beatsPerMeasure
    val lit = if (beat >= 0.0 && beat < timeline.clickEndBeat) floor(beat).toInt() % beatsPerMeasure else -1

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            RunPage(
                score = context.score,
                modifier = Modifier.fillMaxSize(),
                mask = mask,
                focusTicks = ticks,
                cursorTicks = if (cursorShown) ticks else null,
                marks = marks,
            )
        }
        Spacer(Modifier.height(16.dp))
        BeatIndicator(beatsPerMeasure, lit)
    }
}

/** The marks of [evaluation] on the pages of [context]'s score, rebuilt only when a commit lands. */
@Composable
private fun rememberMarks(context: RunContext, evaluation: RunEvaluation): (PageLayout) -> List<NoteMark> {
    val score = context.score
    return remember(score, evaluation) {
        val outcomes = evaluation.pitch.outcomes
        val rhythm = evaluation.rhythm
        val marks: (PageLayout) -> List<NoteMark> = { page -> noteMarks(page, score, outcomes, rhythm) }
        marks
    }
}

@Composable
private fun SummaryPanel(summary: RunState.Summary, nextRun: Decision?) {
    val performed = summary.performed
    RunSummaryContent(
        config = summary.context.config,
        score = performed.score,
        segments = performed.segments,
        evaluation = summary.evaluation,
        linesAfter = listOfNotNull(nextRun?.let(::nextRunLine)),
    )
}

@Composable
private fun ActionBar(state: RunState?, connection: MidiConnection, actions: PracticeActions) {
    val connected = connection is MidiConnection.Connected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            null -> Unit
            is RunState.Ready -> Button(onClick = actions.start, enabled = connected, modifier = Modifier.weight(1f)) {
                Text(if (connected) "Start" else "Connect a keyboard to start")
            }
            is RunState.Running -> {
                val stopping = state.lastSegment < state.context.lastSegment
                OutlinedButton(onClick = actions.stop, enabled = !stopping, modifier = Modifier.weight(1f)) {
                    Text(if (stopping) "Finishing the bar" else "Stop")
                }
            }
            is RunState.Summary -> Button(onClick = actions.next, modifier = Modifier.weight(1f)) { Text("Next") }
            is RunState.Aborted -> {
                OutlinedButton(onClick = actions.retry, modifier = Modifier.weight(1f)) { Text("Try again") }
                Button(onClick = actions.next, modifier = Modifier.weight(1f)) { Text("Next") }
            }
        }
    }
}
