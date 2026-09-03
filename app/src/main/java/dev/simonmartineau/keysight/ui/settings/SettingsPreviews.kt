package dev.simonmartineau.keysight.ui.settings

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.settings.ThemeMode
import dev.simonmartineau.keysight.ui.theme.KeySightTheme

@Preview(name = "Settings, phone", showBackground = true, widthDp = 764, heightDp = 366)
@Composable
private fun SettingsPreview() {
    KeySightTheme {
        SettingsContent(theme = ThemeMode.SYSTEM, connection = MidiConnection.Connected("Roland FP-30X"), adaptEnabled = false, onTheme = {}, onAdapt = {})
    }
}

@Preview(name = "Settings, dark, adapting, no keyboard", showBackground = true, widthDp = 1200, heightDp = 776, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsDarkPreview() {
    KeySightTheme(darkTheme = true) {
        SettingsContent(theme = ThemeMode.DARK, connection = MidiConnection.NoDevice, adaptEnabled = true, onTheme = {}, onAdapt = {})
    }
}
