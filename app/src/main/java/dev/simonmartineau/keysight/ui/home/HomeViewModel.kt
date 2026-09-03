package dev.simonmartineau.keysight.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.difficulty.DifficultyState
import dev.simonmartineau.keysight.difficulty.DifficultyStore
import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.history.HistoryReader
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.midi.MidiDeviceManager
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
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
import java.time.Instant
import java.time.ZoneId

/**
 * Home's glue: the settings as they stand, the level the next run reads at, the keyboard,
 * and the dashboard pooled from history. The quick-start chips write one setting each; the
 * screen then starts a run, which the practice screen builds from the settings.
 */
class HomeViewModel(
    reader: HistoryReader,
    private val runSettings: RunSettings,
    private val contentSettings: ContentSettings,
    private val difficultyStore: DifficultyStore,
    midi: MidiDeviceManager,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    val connection: StateFlow<MidiConnection> = midi.connection
    val config: StateFlow<RunConfig> = runSettings.config
    val content: StateFlow<ContentConfig> = contentSettings.config

    private val controllerLevel = MutableStateFlow<MusicalLevel?>(null)

    /** The level the next run reads at: the controller's while it adapts, the player's otherwise; null until the controller's is known. */
    val level: StateFlow<MusicalLevel?> = combine(runSettings.adaptEnabled, contentSettings.config, controllerLevel) { adapt, content, controller ->
        if (adapt) controller else content.level
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    /** Null until history is read. */
    val dashboard: StateFlow<Dashboard?> = combine(reader.sessions(), reader.runDigests(0L)) { sessions, runs ->
        dashboardOf(sessions, runs, Instant.ofEpochMilli(now()).atZone(zone).toLocalDate(), zone)
    }.flowOn(dispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    /** The hour of the day, for the greeting. */
    val hour: Int get() = Instant.ofEpochMilli(now()).atZone(zone).hour

    init {
        viewModelScope.launch { controllerLevel.value = (difficultyStore.load() ?: DifficultyState.DEFAULT).level }
    }

    fun setMode(mode: VisibilityMode) = runSettings.update(runSettings.config.value.copy(mode = mode))

    fun setHands(hands: Hands) = contentSettings.update(contentSettings.config.value.copy(hands = hands))

    /** Writes the one setting a quick-start chip stands for. */
    fun apply(quickStart: QuickStart) = when (quickStart) {
        is QuickStart.Mode -> setMode(quickStart.mode)
        is QuickStart.WithHands -> setHands(quickStart.hands)
    }

    companion object {
        private const val STOP_AFTER_MILLIS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
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
