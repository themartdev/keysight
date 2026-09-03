package dev.simonmartineau.keysight.settings

import dev.simonmartineau.keysight.run.RunConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The player's current run configuration, kept across launches. */
interface RunSettings {

    val config: StateFlow<RunConfig>

    fun update(config: RunConfig)
}

/** The values the settings row offers. */
object RunChoices {
    val TEMPOS_BPM = listOf(60.0, 72.0, 84.0, 96.0, 108.0, 120.0)
    val LOOKAHEAD_BEATS = RunConfig.LOOKAHEAD_LADDER_BEATS
}

class InMemoryRunSettings(initial: RunConfig = RunConfig.DEFAULT) : RunSettings {

    private val _config = MutableStateFlow(initial)
    override val config: StateFlow<RunConfig> = _config.asStateFlow()

    override fun update(config: RunConfig) {
        _config.value = config
    }
}
