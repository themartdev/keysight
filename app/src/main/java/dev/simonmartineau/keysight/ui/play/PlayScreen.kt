package dev.simonmartineau.keysight.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.history.RunDigest
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.beatsLabel
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.settings.NotesLadder
import dev.simonmartineau.keysight.settings.RunChoices
import dev.simonmartineau.keysight.ui.history.accuracyLabel
import dev.simonmartineau.keysight.ui.history.whenLabel
import dev.simonmartineau.keysight.ui.practice.barsLabel
import dev.simonmartineau.keysight.ui.practice.tempoLabel
import dev.simonmartineau.keysight.ui.shell.MidiStatus
import dev.simonmartineau.keysight.ui.theme.Metrics
import dev.simonmartineau.keysight.ui.theme.Param
import dev.simonmartineau.keysight.ui.theme.ParamGrid
import dev.simonmartineau.keysight.ui.theme.PrimaryButton
import dev.simonmartineau.keysight.ui.theme.SectionHeading
import dev.simonmartineau.keysight.ui.theme.VerticalHairline
import dev.simonmartineau.keysight.ui.theme.palette
import dev.simonmartineau.keysight.ui.theme.type
import java.time.ZoneId

/** The settings as a preset list with a preview of the music; [onStart] starts a run with them. */
@Composable
fun PlayScreen(container: AppContainer, onStart: () -> Unit) {
    val viewModel: PlayViewModel = viewModel(factory = PlayViewModel.factory(container))
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val adaptEnabled by viewModel.adaptEnabled.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val lastRun by viewModel.lastRun.collectAsStateWithLifecycle()
    PlayContent(
        connection = connection,
        config = config,
        content = content,
        adaptEnabled = adaptEnabled,
        level = level,
        lastRun = lastRun,
        previewSeed = viewModel.previewSeed,
        now = System.currentTimeMillis(),
        actions = PlayActions(
            start = onStart,
            setMode = viewModel::setMode,
            setLookaheadBeats = viewModel::setLookaheadBeats,
            setTempo = viewModel::setTempo,
            setMetronome = viewModel::setMetronome,
            setSegmentCount = viewModel::setSegmentCount,
            setKey = viewModel::setKey,
            setHands = viewModel::setHands,
            setAccompaniment = viewModel::setAccompaniment,
            setLevel = viewModel::setLevel,
        ),
    )
}

class PlayActions(
    val start: () -> Unit,
    val setMode: (VisibilityMode) -> Unit,
    val setLookaheadBeats: (Double) -> Unit,
    val setTempo: (Double) -> Unit,
    val setMetronome: (MetronomeMode) -> Unit,
    val setSegmentCount: (Int?) -> Unit,
    val setKey: (KeySignature) -> Unit,
    val setHands: (Hands) -> Unit,
    val setAccompaniment: (Accompaniment) -> Unit,
    val setLevel: (MusicalLevel) -> Unit,
)

/** The parameter whose picker is open. */
private enum class Picker { KEY, HANDS, ACCOMPANIMENT, LENGTH, TEMPO, CLICK, LOOKAHEAD, NOTES }

/** The technique modes, listed so the player knows they are coming; none is built. */
private val TECHNIQUE_MODES = listOf(
    "Hanon" to "The exercises, hands together, at a set tempo.",
    "Scales & arpeggios" to "Every key, both hands, fingering checked.",
    "Chords" to "Triads and sevenths read as one grip.",
)

/**
 * The page: the preset list on paper down the left, the chosen mode's parameters, its
 * preview and Start on the right. Every choice is written as it is made; there is no save.
 */
@Composable
fun PlayContent(
    connection: MidiConnection,
    config: RunConfig,
    content: ContentConfig,
    adaptEnabled: Boolean,
    level: MusicalLevel?,
    lastRun: RunDigest?,
    previewSeed: Long,
    now: Long,
    actions: PlayActions,
) {
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    Row(Modifier.fillMaxSize()) {
        PresetPane(config.mode, actions.setMode)
        VerticalHairline(Modifier.fillMaxHeight())
        ModePane(connection, config, content, adaptEnabled, level, lastRun, previewSeed, now, actions, onPick = { picker = it }, Modifier.weight(1f))
    }
    picker?.let { open ->
        Pickers(open, config, content, adaptEnabled, level, actions, onDismiss = { picker = null })
    }
}

@Composable
private fun PresetPane(mode: VisibilityMode, onMode: (VisibilityMode) -> Unit) {
    val palette = MaterialTheme.palette
    Column(
        Modifier
            .width(Metrics.PresetPaneWidth)
            .fillMaxHeight()
            .background(palette.paper)
            .padding(Metrics.PanePadding),
    ) {
        SectionHeading("Sight reading")
        Spacer(Modifier.height(Metrics.GapTight))
        VisibilityMode.entries.forEach { candidate ->
            PresetRow(candidate.label, modeDescription(candidate), selected = candidate == mode, onClick = { onMode(candidate) })
        }
        Spacer(Modifier.height(Metrics.GapBlocks))
        SectionHeading("Technique")
        Spacer(Modifier.height(Metrics.GapTight))
        TECHNIQUE_MODES.forEach { (title, description) ->
            PresetRow(title, description, selected = false, onClick = {}, enabled = false)
        }
    }
}

@Composable
private fun ModePane(
    connection: MidiConnection,
    config: RunConfig,
    content: ContentConfig,
    adaptEnabled: Boolean,
    level: MusicalLevel?,
    lastRun: RunDigest?,
    previewSeed: Long,
    now: Long,
    actions: PlayActions,
    onPick: (Picker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = MaterialTheme.palette
    val connected = connection is MidiConnection.Connected
    Column(modifier.fillMaxHeight().padding(Metrics.PanePadding)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(config.mode.label, style = MaterialTheme.type.paneTitle, color = palette.ink, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(modeDescription(config.mode), style = MaterialTheme.type.body, color = palette.onSurfaceMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Metrics.GapPanes))
            Column(horizontalAlignment = Alignment.End) {
                MidiStatus(connection)
                Spacer(Modifier.height(Metrics.GapTight))
                PrimaryButton(if (connected) "Start" else "No keyboard", onClick = actions.start, enabled = connected)
                Spacer(Modifier.height(6.dp))
                Text(lastRunLine(lastRun, now), style = MaterialTheme.type.meta, color = palette.onSurfaceFaint, maxLines = 1)
            }
        }
        Spacer(Modifier.height(Metrics.GapControls))
        ParamGrid(params(config, content, adaptEnabled, level, onPick))
        Spacer(Modifier.height(Metrics.GapControls))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val exercise = content.exerciseConfig.let { base -> level?.applyTo(base) ?: base }
            val score = remember(previewSeed, exercise) { previewScore(previewSeed, exercise) }
            ScorePreview(score, Modifier.fillMaxSize())
        }
    }
}

/** The cells: key, hands, length, tempo, click, then the lookahead in Flash and the notes otherwise; both when both matter. */
private fun params(config: RunConfig, content: ContentConfig, adaptEnabled: Boolean, level: MusicalLevel?, onPick: (Picker) -> Unit): List<Param> {
    val notes = Param(if (adaptEnabled) "Notes, adapting" else "Notes", level?.let(NotesLadder::shortLabel) ?: "") { onPick(Picker.NOTES) }
    val lookahead = Param("Lookahead", lookaheadValue(config.lookaheadBeats)) { onPick(Picker.LOOKAHEAD) }
    return listOf(
        Param("Key", content.keySignature.majorName) { onPick(Picker.KEY) },
        Param("Hands", handsValue(content)) { onPick(Picker.HANDS) },
        Param("Length", lengthValue(config.segmentCount)) { onPick(Picker.LENGTH) },
        Param("Tempo", tempoLabel(config.tempoBpm)) { onPick(Picker.TEMPO) },
        Param("Click", clickValue(config.metronome)) { onPick(Picker.CLICK) },
    ) + when {
        config.mode != VisibilityMode.FLASH -> listOf(notes)
        adaptEnabled -> listOf(lookahead)
        else -> listOf(lookahead, notes)
    }
}

@Composable
private fun Pickers(
    open: Picker,
    config: RunConfig,
    content: ContentConfig,
    adaptEnabled: Boolean,
    level: MusicalLevel?,
    actions: PlayActions,
    onDismiss: () -> Unit,
) {
    when (open) {
        Picker.KEY -> PickerDialog("Key", KeySignature.ALL, content.keySignature, { it.majorName }, actions.setKey, onDismiss)
        Picker.HANDS -> PickerDialog(
            "Hands",
            Hands.entries.flatMap { hands -> if (hands == Hands.BOTH) Accompaniment.entries.map { hands to it } else listOf(hands to Accompaniment.NONE) },
            content.hands to (if (content.hands == Hands.BOTH) content.accompaniment else Accompaniment.NONE),
            { (hands, accompaniment) -> if (hands == Hands.BOTH) "${hands.label}, ${accompaniment.label.lowercase()}" else hands.label },
            { (hands, accompaniment) -> actions.setHands(hands); actions.setAccompaniment(accompaniment) },
            onDismiss,
        )
        Picker.ACCOMPANIMENT -> PickerDialog("Other hand", Accompaniment.entries, content.accompaniment, { it.label }, actions.setAccompaniment, onDismiss)
        Picker.LENGTH -> PickerDialog("Length", RunChoices.SEGMENT_COUNTS, config.segmentCount, ::lengthValue, actions.setSegmentCount, onDismiss)
        Picker.TEMPO -> PickerDialog("Tempo", RunChoices.TEMPOS_BPM, config.tempoBpm, ::tempoLabel, actions.setTempo, onDismiss)
        Picker.CLICK -> PickerDialog("Click", MetronomeMode.entries, config.metronome, ::clickValue, actions.setMetronome, onDismiss)
        Picker.LOOKAHEAD -> PickerDialog("Lookahead", RunChoices.LOOKAHEAD_BEATS, config.lookaheadBeats, ::lookaheadValue, actions.setLookaheadBeats, onDismiss)
        Picker.NOTES -> if (adaptEnabled) {
            val current = level ?: MusicalLevel.DEFAULT
            PickerDialog(
                "Notes, adapting",
                listOf(current),
                current,
                NotesLadder::shortLabel,
                onPick = {},
                onDismiss = onDismiss,
                description = { "The controller moves the level as you play. Turn adaptation off in Settings to pick it yourself." },
            )
        } else {
            PickerDialog("Notes", NotesLadder.LEVELS, content.level, NotesLadder::shortLabel, actions.setLevel, onDismiss, description = { it.description })
        }
    }
}

/** What each mode does, in the vocabulary of the run's own description. */
fun modeDescription(mode: VisibilityMode): String = when (mode) {
    VisibilityMode.FLASH -> "Read the bar ahead, then play it from memory: the notes vanish as the bar starts."
    VisibilityMode.READ_AHEAD -> "Everything is visible except the bar being played."
    VisibilityMode.OPEN_SCORE -> "Plain sight reading: the notes stay on the page."
}

fun handsValue(content: ContentConfig): String =
    if (content.hands == Hands.BOTH) "Both, ${content.accompaniment.label.lowercase()}" else content.hands.label

fun lengthValue(count: Int?): String = count?.let(::barsLabel) ?: "Open, until Stop"

fun clickValue(mode: MetronomeMode): String = when (mode) {
    MetronomeMode.COUNT_IN_ONLY -> "Count-in only"
    MetronomeMode.THROUGHOUT -> "Throughout"
}

fun lookaheadValue(beats: Double): String = "${beats.beatsLabel()} ${if (beats == 1.0) "beat" else "beats"}"

/** "Last run: Today 18:42, 91 · 84", or "No run in this mode yet". */
fun lastRunLine(run: RunDigest?, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (run == null) return "No run in this mode yet"
    val accuracy = accuracyLabel(run.pooled)
    return "Last run: ${whenLabel(run.startedAtEpochMillis, now, zone)}" + if (accuracy.isEmpty()) "" else ", $accuracy"
}
