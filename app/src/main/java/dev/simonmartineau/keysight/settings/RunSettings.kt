package dev.simonmartineau.keysight.settings

import dev.simonmartineau.keysight.run.RunConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The player's current run configuration, kept across launches, and whether the difficulty
 * controller may move the music as they play. [adaptEnabled] is off by default: a run then
 * reads at the level the player picked, and the controller only watches.
 */
interface RunSettings {

    val config: StateFlow<RunConfig>

    fun update(config: RunConfig)

    val adaptEnabled: StateFlow<Boolean>

    fun setAdaptEnabled(enabled: Boolean)
}

/** The values the pickers offer. */
object RunChoices {
    val TEMPOS_BPM = listOf(60.0, 72.0, 84.0, 96.0, 108.0, 120.0)
    val LOOKAHEAD_BEATS = RunConfig.LOOKAHEAD_LADDER_BEATS

    /** Run lengths in segments; null is open-ended, the run going on until the player stops. */
    val SEGMENT_COUNTS: List<Int?> = listOf(4, 8, 16, null)
}

class InMemoryRunSettings(initial: RunConfig = RunConfig.DEFAULT, adaptEnabled: Boolean = false) : RunSettings {

    private val _config = MutableStateFlow(initial)
    override val config: StateFlow<RunConfig> = _config.asStateFlow()

    private val _adaptEnabled = MutableStateFlow(adaptEnabled)
    override val adaptEnabled: StateFlow<Boolean> = _adaptEnabled.asStateFlow()

    override fun update(config: RunConfig) {
        _config.value = config
    }

    override fun setAdaptEnabled(enabled: Boolean) {
        _adaptEnabled.value = enabled
    }
}
