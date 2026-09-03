package dev.simonmartineau.keysight.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.history.SessionDigest
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.ui.history.accuracyLabel
import dev.simonmartineau.keysight.ui.history.percentValue
import dev.simonmartineau.keysight.ui.history.whatLabel
import dev.simonmartineau.keysight.ui.history.whenLabel
import dev.simonmartineau.keysight.ui.shell.MidiStatus
import dev.simonmartineau.keysight.ui.theme.Chip
import dev.simonmartineau.keysight.ui.theme.DoubleRule
import dev.simonmartineau.keysight.ui.theme.Hairline
import dev.simonmartineau.keysight.ui.theme.Metrics
import dev.simonmartineau.keysight.ui.theme.PrimaryButton
import dev.simonmartineau.keysight.ui.theme.QuietButton
import dev.simonmartineau.keysight.ui.theme.SectionHeading
import dev.simonmartineau.keysight.ui.theme.Sparkbars
import dev.simonmartineau.keysight.ui.theme.StatNumber
import dev.simonmartineau.keysight.ui.theme.TABULAR_FIGURES
import dev.simonmartineau.keysight.ui.theme.TextAction
import dev.simonmartineau.keysight.ui.theme.VerticalHairline
import dev.simonmartineau.keysight.ui.theme.palette
import dev.simonmartineau.keysight.ui.theme.type

/**
 * The launcher: one tap into a run. [onStart] starts a run with the settings as they are,
 * [onChangeSettings] opens Play, [onHistory] opens history with a session expanded.
 */
@Composable
fun HomeScreen(container: AppContainer, onStart: () -> Unit, onChangeSettings: () -> Unit, onHistory: (sessionId: String?) -> Unit) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    HomeContent(
        greeting = greeting(viewModel.hour),
        connection = connection,
        config = config,
        content = content,
        level = level,
        dashboard = dashboard,
        now = System.currentTimeMillis(),
        onStart = onStart,
        onQuickStart = { viewModel.apply(it); onStart() },
        onChangeSettings = onChangeSettings,
        onHistory = onHistory,
    )
}

/**
 * The page: the greeting and the keyboard across the top, then the left column that
 * resumes, and the right column that looks back, a hairline between. [dashboard] is null
 * while history loads; [level] null until the controller's is known.
 */
@Composable
fun HomeContent(
    greeting: String,
    connection: MidiConnection,
    config: RunConfig,
    content: ContentConfig,
    level: MusicalLevel?,
    dashboard: Dashboard?,
    now: Long,
    onStart: () -> Unit,
    onQuickStart: (QuickStart) -> Unit,
    onChangeSettings: () -> Unit,
    onHistory: (sessionId: String?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Metrics.PagePaddingSides, top = Metrics.PagePaddingTop, end = Metrics.PagePaddingSides, bottom = Metrics.PagePaddingBottom),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(greeting, style = MaterialTheme.type.screenTitle, color = MaterialTheme.palette.ink, modifier = Modifier.weight(1f))
            MidiStatus(connection)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().weight(1f)) {
            ResumeColumn(config, content, level, onStart, onQuickStart, onChangeSettings, Modifier.weight(1.2f).fillMaxHeight())
            Spacer(Modifier.width(Metrics.GapPanes / 2))
            VerticalHairline(Modifier.fillMaxHeight())
            Spacer(Modifier.width(Metrics.GapPanes / 2))
            LookBackColumn(dashboard, now, onHistory, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun ResumeColumn(
    config: RunConfig,
    content: ContentConfig,
    level: MusicalLevel?,
    onStart: () -> Unit,
    onQuickStart: (QuickStart) -> Unit,
    onChangeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = MaterialTheme.palette
    Column(modifier) {
        SectionHeading("Pick up where you left off")
        Spacer(Modifier.height(Metrics.GapControls))
        Text(resumeLead(config, content), style = MaterialTheme.type.lead, color = palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(
            level?.let { resumeBody(config, it) } ?: "",
            style = MaterialTheme.type.body,
            color = palette.onSurfaceMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Metrics.GapBlocks))
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.GapControls), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton("Start", onClick = onStart)
            QuietButton("Change settings", onClick = onChangeSettings)
        }
        Spacer(Modifier.height(Metrics.GapBlocks))
        DoubleRule()
        Spacer(Modifier.height(Metrics.GapBlocks))
        SectionHeading("Or start something else")
        Spacer(Modifier.height(Metrics.GapTight))
        Row(horizontalArrangement = Arrangement.spacedBy(Metrics.GapTight), verticalAlignment = Alignment.CenterVertically) {
            quickStarts(config, content).forEach { quickStart ->
                Chip(quickStart.label, onClick = { onQuickStart(quickStart) })
            }
            Chip(UNBUILT_MODES, onClick = {}, available = false)
        }
    }
}

@Composable
private fun LookBackColumn(dashboard: Dashboard?, now: Long, onHistory: (sessionId: String?) -> Unit, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.palette
    Column(modifier) {
        when {
            dashboard == null -> Unit
            !dashboard.hasHistory -> Text("Nothing recorded yet", style = MaterialTheme.type.body, color = palette.onSurfaceMuted)
            else -> {
                val pitch = dashboard.week.pitchAccuracy
                val rhythm = dashboard.week.rhythmAccuracy
                if (pitch == null) {
                    Text("No bars read in the last $DASHBOARD_DAYS days", style = MaterialTheme.type.body, color = palette.onSurfaceMuted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.GapPanes)) {
                        StatNumber("Pitch %", percentValue(pitch))
                        if (rhythm != null) StatNumber("Rhythm %", percentValue(rhythm))
                        StatNumber("Bars, $DASHBOARD_DAYS days", dashboard.weekBars.toString())
                    }
                }
                Spacer(Modifier.height(Metrics.GapControls))
                Sparkbars(dashboard.days.map { it.bars }, height = 36.dp)
                Spacer(Modifier.height(20.dp))
                DoubleRule()
                Spacer(Modifier.height(20.dp))
                SectionHeading("Recent sessions", trailing = { TextAction("All history", onClick = { onHistory(null) }) })
                Spacer(Modifier.height(4.dp))
                dashboard.recent.forEach { session ->
                    SessionLine(session, now, onClick = { onHistory(session.session.id) })
                }
            }
        }
    }
}

/** One recent session in a line: when, what, and its two accuracies at the end; a hairline under. */
@Composable
private fun SessionLine(session: SessionDigest, now: Long, onClick: () -> Unit) {
    val palette = MaterialTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (pressed) palette.paperDim else Color.Transparent)
                .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(whenLabel(session.session.startedAtEpochMillis, now), style = MaterialTheme.type.meta, color = palette.onSurfaceMuted, maxLines = 1, modifier = Modifier.width(96.dp))
            Text(whatLabel(session), style = MaterialTheme.type.body, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Metrics.GapControls))
            Text(
                accuracyLabel(session.pooled),
                style = MaterialTheme.type.meta.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = palette.onSurfaceMuted,
                maxLines = 1,
            )
        }
        Hairline()
    }
}
