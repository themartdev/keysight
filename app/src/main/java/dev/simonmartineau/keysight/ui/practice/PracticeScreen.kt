package dev.simonmartineau.keysight.ui.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.difficulty.Decision
import dev.simonmartineau.keysight.evaluation.RunEvaluation
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
import dev.simonmartineau.keysight.ui.notation.RunPage
import dev.simonmartineau.keysight.ui.shell.MidiStatus
import dev.simonmartineau.keysight.ui.shell.StageScaffold
import kotlin.math.floor

/**
 * The practice destination; [onHistory] opens history with this destination's session, if a
 * run has been recorded into one, and [onSettings] the app's settings.
 */
@Composable
fun PracticeScreen(container: AppContainer, onHistory: (sessionId: String?) -> Unit, onSettings: () -> Unit) {
    val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
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
            history = { onHistory(sessionId) },
            settings = onSettings,
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
    val history: () -> Unit,
    val settings: () -> Unit,
)

/**
 * The music stand. The stage is the score from Ready through Running to the summary, in one
 * box that never changes size, so the staff the player reads is the staff they were shown;
 * the strip above it says what the run is and carries the one action of the moment. The
 * setup is a sheet over the stage, opened from the strip's run line. [nextRun] is what the
 * difficulty controller decided when the run on screen ended, if it moved anything.
 */
@Composable
fun PracticeContent(
    state: RunState?,
    connection: MidiConnection,
    config: RunConfig,
    content: ContentConfig,
    actions: PracticeActions,
    nextRun: Decision? = null,
) {
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    val beat = rememberRunBeat(state as? RunState.Running)
    StageScaffold(
        strip = { PracticeStrip(state, connection, config, beat, actions, onSetup = { setupOpen = true }) },
        stage = { Stage(state, beat, nextRun) },
    )
    if (setupOpen) {
        SetupSheet(config, content, level = (state as? RunState.Ready)?.let { levelLine(it.context) }, actions, onDismiss = { setupOpen = false })
    }
}

/**
 * The beat of the run on screen, from one frame loop: the frame time is on the same
 * `System.nanoTime` base as the run clock, so the timeline gives the beat and the mask, the
 * cursor, the page turn and the beat dots all follow from that one number and none of them
 * can drift from the metronome. Before a run starts the beat is zero and nothing reads it.
 */
@Composable
private fun rememberRunBeat(running: RunState.Running?): State<Double> {
    val startedAtNanos = running?.startedAtNanos
    val beat = remember(startedAtNanos) { mutableDoubleStateOf(0.0) }
    if (running != null) {
        LaunchedEffect(startedAtNanos) {
            val timeline = running.context.timeline
            while (true) {
                withFrameNanos { frameNanos ->
                    beat.doubleValue = timeline.beatAtNanos(frameNanos - running.startedAtNanos)
                }
            }
        }
    }
    return beat
}

@Composable
private fun RowScope.PracticeStrip(
    state: RunState?,
    connection: MidiConnection,
    config: RunConfig,
    beat: State<Double>,
    actions: PracticeActions,
    onSetup: () -> Unit,
) {
    val connected = connection is MidiConnection.Connected
    MidiStatus(connection, compact = true)
    when (state) {
        null -> Spacer(Modifier.weight(1f))
        is RunState.Ready -> {
            StripLines(readyLine(config, state.context.score), levelLine(state.context), Modifier.weight(1f).clickable(onClick = onSetup))
            Button(onClick = actions.start, enabled = connected) { Text("Start") }
            OverflowMenu(actions)
        }
        is RunState.Running -> {
            val timeline = state.context.timeline
            val beatsPerMeasure = timeline.timeSignature.beatsPerMeasure
            val now = beat.value
            val lit = if (now >= 0.0 && now < timeline.clickEndBeat) floor(now).toInt() % beatsPerMeasure else -1
            BeatIndicator(beatsPerMeasure, lit)
            Spacer(Modifier.weight(1f))
            val stopping = state.lastSegment < state.context.lastSegment
            OutlinedButton(onClick = actions.stop, enabled = !stopping) {
                Text(if (stopping) "Finishing the bar" else "Stop")
            }
        }
        is RunState.Summary -> {
            StripLines(notesLine(state.evaluation.pitch), scoreLine(state.evaluation.pitch, state.evaluation.rhythm), Modifier.weight(1f))
            Button(onClick = actions.next) { Text("Next") }
            OverflowMenu(actions)
        }
        is RunState.Aborted -> {
            StripLines("Run stopped", abortMessage(state.reason), Modifier.weight(1f))
            OutlinedButton(onClick = actions.retry) { Text("Try again") }
            Button(onClick = actions.next) { Text("Next") }
            OverflowMenu(actions)
        }
    }
}

/** Two lines of small text that fit the strip, each cut to its line. */
@Composable
private fun StripLines(first: String, second: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        Text(first, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        second?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The way to every other destination, so nothing else takes room from the stage. */
@Composable
private fun OverflowMenu(actions: PracticeActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("History") }, onClick = { open = false; actions.history() })
            DropdownMenuItem(text = { Text("Settings") }, onClick = { open = false; actions.settings() })
        }
    }
}

@Composable
private fun Stage(state: RunState?, beat: State<Double>, nextRun: Decision?) {
    when (state) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is RunState.Ready -> RunPage(
            state.context.score,
            Modifier.fillMaxSize(),
            mask = runMaskBeforeStart(state.context.timeline, state.context.policy),
        )
        is RunState.Running -> RunStage(state, beat)
        is RunState.Summary -> {
            val performed = state.performed
            RunSummaryContent(
                score = performed.score,
                evaluation = state.evaluation,
                linesBelow = summaryRemarks(state.evaluation, performed.segments) + listOfNotNull(nextRun?.let(::nextRunLine)),
            )
        }
        is RunState.Aborted -> {
            val context = state.context
            val performed = state.lastSegment?.takeIf { it in context.timeline.performedSegments }?.let(context::performed)
            if (performed == null) {
                RunPage(context.score, Modifier.fillMaxSize(), mask = runMaskBeforeStart(context.timeline, context.policy))
            } else {
                RunSummaryContent(
                    score = performed.score,
                    evaluation = state.evaluation,
                    linesBelow = summaryRemarks(state.evaluation, performed.segments) + listOfNotNull(nextRun?.let(::nextRunLine)),
                )
            }
        }
    }
}

/**
 * The page during a run, drawn from the [beat] of the frame: the mask, the cursor and the
 * page turn all follow from it. The marks are the committed segments' outcomes; they appear
 * behind the cursor as each commit lands, and the mask keeps them off a hidden bar.
 */
@Composable
private fun RunStage(state: RunState.Running, beat: State<Double>) {
    val context = state.context
    val timeline = context.timeline
    val marks = rememberMarks(context, state.evaluation)
    val now = beat.value
    val mask = runMask(timeline, context.policy, now, state.lastSegment)
    val ticks = timeline.ticksAt(now)
    val cursorShown = now >= 0.0 && now < timeline.segmentEndBeat(state.lastSegment)

    RunPage(
        score = context.score,
        modifier = Modifier.fillMaxSize(),
        mask = mask,
        focusTicks = ticks,
        cursorTicks = if (cursorShown) ticks else null,
        marks = marks,
    )
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
