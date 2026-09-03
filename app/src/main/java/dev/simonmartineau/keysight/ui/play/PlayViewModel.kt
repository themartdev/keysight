package dev.simonmartineau.keysight.ui.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.difficulty.DifficultyState
import dev.simonmartineau.keysight.difficulty.DifficultyStore
import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.history.HistoryReader
import dev.simonmartineau.keysight.history.RunDigest
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.midi.MidiDeviceManager
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.settings.ContentSettings
import dev.simonmartineau.keysight.settings.RunSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Play's glue: the settings, written the moment a choice is made, the level the next run
 * reads at, the keyboard, and the last run in the chosen mode. The preview is drawn from
 * [previewSeed], one per visit, so the page holds still while a parameter changes and only
 * the music the parameter governs moves; the run itself draws its own seed.
 */
class PlayViewModel(
    reader: HistoryReader,
    private val runSettings: RunSettings,
    private val contentSettings: ContentSettings,
    private val difficultyStore: DifficultyStore,
    midi: MidiDeviceManager,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    random: Random = Random.Default,
) : ViewModel() {

    val connection: StateFlow<MidiConnection> = midi.connection
    val config: StateFlow<RunConfig> = runSettings.config
    val content: StateFlow<ContentConfig> = contentSettings.config
    val adaptEnabled: StateFlow<Boolean> = runSettings.adaptEnabled

    val previewSeed: Long = random.nextLong()

    private val controllerLevel = MutableStateFlow<MusicalLevel?>(null)

    /** The level the next run reads at: the controller's while it adapts, the player's otherwise; null until the controller's is known. */
    val level: StateFlow<MusicalLevel?> = combine(runSettings.adaptEnabled, contentSettings.config, controllerLevel) { adapt, content, controller ->
        if (adapt) controller else content.level
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    /** The most recent run in the chosen mode, or null when there was none. */
    val lastRun: StateFlow<RunDigest?> = combine(reader.runDigests(0L), runSettings.config) { runs, config ->
        runs.lastOrNull { it.config.mode == config.mode }
    }.flowOn(dispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    init {
        viewModelScope.launch { controllerLevel.value = (difficultyStore.load() ?: DifficultyState.DEFAULT).level }
    }

    fun setMode(mode: VisibilityMode) = updateConfig { copy(mode = mode) }

    fun setLookaheadBeats(beats: Double) = updateConfig { copy(lookaheadBeats = beats) }

    fun setTempo(bpm: Double) = updateConfig { copy(tempoBpm = bpm) }

    fun setMetronome(mode: MetronomeMode) = updateConfig { copy(metronome = mode) }

    /** [count] segments, or null for a run that goes on until Stop. */
    fun setSegmentCount(count: Int?) = updateConfig { copy(segmentCount = count) }

    fun setKey(key: KeySignature) = updateContent { copy(keySignature = key) }

    fun setHands(hands: Hands) = updateContent { copy(hands = hands) }

    fun setAccompaniment(accompaniment: Accompaniment) = updateContent { copy(accompaniment = accompaniment) }

    /** The level picked by hand; read only while the controller is not adapting. */
    fun setLevel(level: MusicalLevel) = updateContent { copy(level = level) }

    private fun updateConfig(change: RunConfig.() -> RunConfig) = runSettings.update(runSettings.config.value.change())

    private fun updateContent(change: ContentConfig.() -> ContentConfig) = contentSettings.update(contentSettings.config.value.change())

    companion object {
        private const val STOP_AFTER_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PlayViewModel(
                    reader = container.historyReader(),
                    runSettings = container.runSettings,
                    contentSettings = container.contentSettings,
                    difficultyStore = container.difficultyStore,
                    midi = container.midiDeviceManager,
                )
            }
        }
    }
}
