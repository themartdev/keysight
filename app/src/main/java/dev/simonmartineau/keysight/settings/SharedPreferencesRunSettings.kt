package dev.simonmartineau.keysight.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Run settings in a private preferences file. Read once at construction, written on every change. */
class SharedPreferencesRunSettings(private val prefs: SharedPreferences) : RunSettings {

    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val _config = MutableStateFlow(read())
    override val config: StateFlow<RunConfig> = _config.asStateFlow()

    private val _adaptEnabled = MutableStateFlow(prefs.getBoolean(KEY_ADAPT, false))
    override val adaptEnabled: StateFlow<Boolean> = _adaptEnabled.asStateFlow()

    override fun setAdaptEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ADAPT, enabled) }
        _adaptEnabled.value = enabled
    }

    override fun update(config: RunConfig) {
        prefs.edit {
            putFloat(KEY_TEMPO, config.tempoBpm.toFloat())
            putString(KEY_METRONOME, config.metronome.name)
            putString(KEY_MODE, config.mode.name)
            putFloat(KEY_LOOKAHEAD, config.lookaheadBeats.toFloat())
            putInt(KEY_SEGMENT_COUNT, config.segmentCount ?: OPEN_ENDED)
        }
        _config.value = config
    }

    private fun read(): RunConfig {
        val defaults = RunConfig.DEFAULT
        return runCatching {
            RunConfig(
                tempoBpm = prefs.getFloat(KEY_TEMPO, defaults.tempoBpm.toFloat()).toDouble(),
                metronome = MetronomeMode.valueOf(prefs.getString(KEY_METRONOME, null) ?: defaults.metronome.name),
                mode = VisibilityMode.valueOf(prefs.getString(KEY_MODE, null) ?: defaults.mode.name),
                lookaheadBeats = prefs.getFloat(KEY_LOOKAHEAD, defaults.lookaheadBeats.toFloat()).toDouble(),
                segmentCount = prefs.getInt(KEY_SEGMENT_COUNT, defaults.segmentCount ?: OPEN_ENDED).takeIf { it != OPEN_ENDED },
            )
        }.getOrDefault(defaults)
    }

    private companion object {
        const val FILE = "run_settings"
        const val KEY_TEMPO = "tempoBpm"
        const val KEY_METRONOME = "metronome"
        const val KEY_MODE = "mode"
        const val KEY_LOOKAHEAD = "lookaheadBeats"
        const val KEY_SEGMENT_COUNT = "segmentCount"
        const val KEY_ADAPT = "adaptEnabled"

        /** How an open-ended run is stored in the integer segment count. */
        const val OPEN_ENDED = 0
    }
}
