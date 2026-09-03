package dev.simonmartineau.keysight.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.simonmartineau.keysight.attempt.FlashConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Flash settings in a private preferences file. Read once at construction, written on every change. */
class SharedPreferencesFlashSettings(private val prefs: SharedPreferences) : FlashSettings {

    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val _config = MutableStateFlow(read())
    override val config: StateFlow<FlashConfig> = _config.asStateFlow()

    override fun update(config: FlashConfig) {
        prefs.edit {
            putFloat(KEY_TEMPO, config.tempoBpm.toFloat())
            putInt(KEY_COUNT_IN, config.countInMeasures)
            putFloat(KEY_PREVIEW, config.previewDurationBeats.toFloat())
            putBoolean(KEY_METRONOME_DURING_ATTEMPT, config.metronomeDuringAttempt)
        }
        _config.value = config
    }

    private fun read(): FlashConfig {
        val defaults = FlashConfig.DEFAULT
        return runCatching {
            FlashConfig(
                tempoBpm = prefs.getFloat(KEY_TEMPO, defaults.tempoBpm.toFloat()).toDouble(),
                countInMeasures = prefs.getInt(KEY_COUNT_IN, defaults.countInMeasures),
                previewDurationBeats = prefs.getFloat(KEY_PREVIEW, defaults.previewDurationBeats.toFloat()).toDouble(),
                metronomeDuringAttempt = prefs.getBoolean(KEY_METRONOME_DURING_ATTEMPT, defaults.metronomeDuringAttempt),
            )
        }.getOrDefault(defaults)
    }

    private companion object {
        const val FILE = "flash_settings"
        const val KEY_TEMPO = "tempoBpm"
        const val KEY_COUNT_IN = "countInMeasures"
        const val KEY_PREVIEW = "previewDurationBeats"
        const val KEY_METRONOME_DURING_ATTEMPT = "metronomeDuringAttempt"
    }
}
