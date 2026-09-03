package dev.simonmartineau.keysight.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which colour scheme the app uses. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    /** Whether the dark scheme applies, given whether the system is currently dark. */
    fun resolvesDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** The player's theme choice, kept across launches. */
interface ThemeSettings {

    val mode: StateFlow<ThemeMode>

    fun update(mode: ThemeMode)
}

class InMemoryThemeSettings(initial: ThemeMode = ThemeMode.SYSTEM) : ThemeSettings {

    private val _mode = MutableStateFlow(initial)
    override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    override fun update(mode: ThemeMode) {
        _mode.value = mode
    }
}
