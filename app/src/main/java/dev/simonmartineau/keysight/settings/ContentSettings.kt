package dev.simonmartineau.keysight.settings

import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.score.KeySignature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the player reads, as opposed to how the flash works: the key and the staves. */
data class ContentConfig(
    val keySignature: KeySignature,
    val hands: Hands,
) {
    companion object {
        val DEFAULT = ContentConfig(KeySignature.C_MAJOR, Hands.RIGHT)
    }
}

interface ContentSettings {
    val config: StateFlow<ContentConfig>

    fun update(config: ContentConfig)
}

class InMemoryContentSettings(initial: ContentConfig = ContentConfig.DEFAULT) : ContentSettings {

    private val _config = MutableStateFlow(initial)
    override val config: StateFlow<ContentConfig> = _config.asStateFlow()

    override fun update(config: ContentConfig) {
        _config.value = config
    }
}
