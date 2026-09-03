package dev.simonmartineau.keysight.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.settings.ThemeMode
import dev.simonmartineau.keysight.ui.shell.connectionLabel
import dev.simonmartineau.keysight.ui.theme.Chip
import dev.simonmartineau.keysight.ui.theme.Hairline
import dev.simonmartineau.keysight.ui.theme.Metrics
import dev.simonmartineau.keysight.ui.theme.Switch
import dev.simonmartineau.keysight.ui.theme.palette
import dev.simonmartineau.keysight.ui.theme.type

/** The width of the one column of settings. */
private val SettingsWidth = 620.dp

/** The app's own settings: the keyboard, the theme, adaptive difficulty, and what is kept. */
@Composable
fun SettingsScreen(container: AppContainer) {
    val theme by container.themeSettings.mode.collectAsStateWithLifecycle()
    val connection by container.midiDeviceManager.connection.collectAsStateWithLifecycle()
    val adaptEnabled by container.runSettings.adaptEnabled.collectAsStateWithLifecycle()
    SettingsContent(
        theme = theme,
        connection = connection,
        adaptEnabled = adaptEnabled,
        onTheme = container.themeSettings::update,
        onAdapt = container.runSettings::setAdaptEnabled,
    )
}

@Composable
fun SettingsContent(
    theme: ThemeMode,
    connection: MidiConnection,
    adaptEnabled: Boolean,
    onTheme: (ThemeMode) -> Unit,
    onAdapt: (Boolean) -> Unit,
) {
    val palette = MaterialTheme.palette
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Metrics.PagePaddingSides, top = Metrics.PagePaddingTop, end = Metrics.PagePaddingSides, bottom = Metrics.PagePaddingBottom),
    ) {
        Text("Settings", style = MaterialTheme.type.screenTitle, color = palette.ink)
        Spacer(Modifier.height(Metrics.GapBlocks))
        Column(Modifier.width(SettingsWidth)) {
            SettingRow("Keyboard", "The first MIDI keyboard plugged in is used. One leaving during a run stops it.") {
                Text(connectionLabel(connection), style = MaterialTheme.type.body, color = palette.onSurfaceMuted)
            }
            Hairline()
            SettingRow("Theme", null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Metrics.GapTight)) {
                    listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { mode ->
                        Chip(mode.label(), onClick = { onTheme(mode) }, selected = mode == theme)
                    }
                }
            }
            Hairline()
            SettingRow(
                "Adapt difficulty as I play",
                "On, the level moves with your playing, one dimension at a time, and every move is announced. Off, the music is the Notes you pick on Play.",
            ) {
                Switch(checked = adaptEnabled, onCheckedChange = onAdapt)
            }
            Hairline()
            SettingRow("Keep raw MIDI", "Every note you play is stored with its run, so a run can be judged again by a better evaluator.") {
                Text("Always", style = MaterialTheme.type.body, color = palette.onSurfaceMuted)
            }
            Hairline()
        }
    }
}

/** One setting: its name and a line about it on the left, its control on the right. */
@Composable
private fun SettingRow(title: String, description: String?, control: @Composable () -> Unit) {
    val palette = MaterialTheme.palette
    Row(Modifier.fillMaxWidth().padding(vertical = Metrics.GapControls), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.type.body, color = palette.ink)
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.type.meta, color = palette.onSurfaceFaint)
            }
        }
        Spacer(Modifier.width(Metrics.GapPanes))
        control()
    }
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
