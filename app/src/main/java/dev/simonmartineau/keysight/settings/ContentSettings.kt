package dev.simonmartineau.keysight.settings

import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.score.KeySignature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the player reads, as opposed to how the run is presented: the key, the staves and, on
 * the grand staff, what the other hand does, and the [level] of the music, the generator
 * dimensions the controller would otherwise own, picked by hand from [NotesLadder]. When
 * the controller is adapting, its own level is laid over [exerciseConfig] and this one waits.
 */
data class ContentConfig(
    val keySignature: KeySignature,
    val hands: Hands,
    val accompaniment: Accompaniment = Accompaniment.NONE,
    val level: MusicalLevel = MusicalLevel.DEFAULT,
) {
    /** The generator configuration of a run read with these settings: the choices and the level over [ExerciseConfig.DEFAULT]. */
    val exerciseConfig: ExerciseConfig
        get() = level.applyTo(
            ExerciseConfig.DEFAULT.copy(
                keySignature = keySignature,
                hands = hands,
                accompaniment = if (hands == Hands.BOTH) accompaniment else Accompaniment.NONE,
            ),
        )

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
