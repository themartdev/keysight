package dev.simonmartineau.keysight.ui.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.settings.ThemeMode
import dev.simonmartineau.keysight.ui.theme.KeySightTheme

@Preview(name = "Settings, keyboard connected", showBackground = true)
@Composable
private fun SettingsPreview() {
    KeySightTheme { SettingsContent(ThemeMode.SYSTEM, MidiConnection.Connected("Preview keyboard"), onTheme = {}, onBack = {}) }
}

@Preview(name = "Settings, keyboard failed, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsFailedDarkPreview() {
    KeySightTheme(darkTheme = true) {
        SettingsContent(ThemeMode.DARK, MidiConnection.Failed("Preview keyboard", "no output port"), onTheme = {}, onBack = {})
    }
}
