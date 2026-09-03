package dev.simonmartineau.keysight.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The theme choice in a private preferences file, stored by name so a renamed mode falls back to the system. */
class SharedPreferencesThemeSettings(private val prefs: SharedPreferences) : ThemeSettings {

    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val _mode = MutableStateFlow(read())
    override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    override fun update(mode: ThemeMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        _mode.value = mode
    }

    private fun read(): ThemeMode {
        val name = prefs.getString(KEY_MODE, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == name } ?: ThemeMode.SYSTEM
    }

    private companion object {
        const val FILE = "appearance"
        const val KEY_MODE = "theme"
    }
}
