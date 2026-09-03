package dev.simonmartineau.keysight.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.score.KeySignature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Content settings in a private preferences file. Read once at construction, written on every change. */
class SharedPreferencesContentSettings(private val prefs: SharedPreferences) : ContentSettings {

    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    private val _config = MutableStateFlow(read())
    override val config: StateFlow<ContentConfig> = _config.asStateFlow()

    override fun update(config: ContentConfig) {
        prefs.edit {
            putInt(KEY_KEY_SIGNATURE, config.keySignature.fifths)
            putString(KEY_HANDS, config.hands.name)
        }
        _config.value = config
    }

    private fun read(): ContentConfig {
        val defaults = ContentConfig.DEFAULT
        return runCatching {
            ContentConfig(
                keySignature = KeySignature(prefs.getInt(KEY_KEY_SIGNATURE, defaults.keySignature.fifths)),
                hands = Hands.valueOf(prefs.getString(KEY_HANDS, null) ?: defaults.hands.name),
            )
        }.getOrDefault(defaults)
    }

    private companion object {
        const val FILE = "content_settings"
        const val KEY_KEY_SIGNATURE = "keySignature"
        const val KEY_HANDS = "hands"
    }
}
