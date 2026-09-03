package dev.simonmartineau.keysight.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.settings.ThemeMode
import dev.simonmartineau.keysight.ui.shell.MidiStatus
import dev.simonmartineau.keysight.ui.shell.ScreenScaffold

/** The app's own settings, the ones no run depends on: the theme, and the keyboard as it is. */
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val theme by container.themeSettings.mode.collectAsStateWithLifecycle()
    val connection by container.midiDeviceManager.connection.collectAsStateWithLifecycle()
    SettingsContent(theme, connection, onTheme = container.themeSettings::update, onBack = onBack)
}

@Composable
fun SettingsContent(theme: ThemeMode, connection: MidiConnection, onTheme: (ThemeMode) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(title = "Settings", backLabel = "Practice", onBack = onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionLabel("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(selected = mode == theme, onClick = { onTheme(mode) }, label = { Text(mode.label()) })
                }
            }
            Spacer(Modifier.height(24.dp))
            SectionLabel("Keyboard")
            MidiStatus(connection)
            Spacer(Modifier.height(4.dp))
            Text(
                "The first MIDI keyboard plugged in is used. A keyboard leaving during a run stops it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
