package dev.simonmartineau.keysight.settings

import dev.simonmartineau.keysight.attempt.FlashConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The player's current flash configuration, kept across launches. */
interface FlashSettings {

    val config: StateFlow<FlashConfig>

    fun update(config: FlashConfig)
}

/** The values the settings row offers. */
object FlashChoices {
    val TEMPOS_BPM = listOf(60.0, 72.0, 84.0, 96.0, 108.0, 120.0)
    val PREVIEW_BEATS = FlashConfig.PREVIEW_LADDER_BEATS
}

class InMemoryFlashSettings(initial: FlashConfig = FlashConfig.DEFAULT) : FlashSettings {

    private val _config = MutableStateFlow(initial)
    override val config: StateFlow<FlashConfig> = _config.asStateFlow()

    override fun update(config: FlashConfig) {
        _config.value = config
    }
}
