package dev.simonmartineau.keysight.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.simonmartineau.keysight.attempt.AbortReason
import dev.simonmartineau.keysight.attempt.AttemptController
import dev.simonmartineau.keysight.attempt.AttemptState
import dev.simonmartineau.keysight.attempt.FlashConfig
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.exercise.Exercise
import dev.simonmartineau.keysight.exercise.ExerciseRepository
import dev.simonmartineau.keysight.exercise.ExerciseSelector
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.exercise.adaptedTo
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.midi.MidiDeviceManager
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.settings.ContentSettings
import dev.simonmartineau.keysight.settings.FlashSettings
import dev.simonmartineau.keysight.settings.ThemeMode
import dev.simonmartineau.keysight.settings.ThemeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The practice screen's glue: the keyboard feeds the controller, the keyboard leaving or the
 * app leaving the foreground aborts a running attempt, and settings changes re-time or
 * re-adapt the exercise that is waiting to start.
 *
 * The selector picks bundled exercises; what the controller gets is the bundled exercise
 * adapted to the content settings, and the adaptation always starts from the bundled one,
 * never from an already adapted score.
 */
class PracticeViewModel(
    private val midi: MidiDeviceManager,
    private val exercises: ExerciseRepository,
    private val settings: FlashSettings,
    private val contentSettings: ContentSettings,
    private val themeSettings: ThemeSettings,
    controllerFactory: (CoroutineScope) -> AttemptController,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val controller = controllerFactory(viewModelScope)

    val state: StateFlow<AttemptState?> = controller.state
    val connection: StateFlow<MidiConnection> = midi.connection
    val config: StateFlow<FlashConfig> = settings.config
    val content: StateFlow<ContentConfig> = contentSettings.config
    val theme: StateFlow<ThemeMode> = themeSettings.mode

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private var selector: ExerciseSelector? = null

    /** The bundled exercise the waiting or running attempt was adapted from. */
    private var chosen: Exercise? = null

    init {
        viewModelScope.launch {
            val pack = runCatching { exercises.all() }.getOrElse {
                _loadError.value = it.message ?: "could not load the exercises"
                return@launch
            }
            val chosen = ExerciseSelector(pack, random)
            selector = chosen
            load(chosen.next(previous = null), settings.config.value)
        }
        viewModelScope.launch { midi.events.collect(controller::onMidi) }
        viewModelScope.launch {
            midi.connection.collect { connection ->
                if (connection !is MidiConnection.Connected && controller.isRunning) {
                    controller.abort(AbortReason.MIDI_DISCONNECTED)
                }
            }
        }
    }

    fun start() {
        if (state.value is AttemptState.Ready && connection.value is MidiConnection.Connected) controller.start()
    }

    fun cancel() {
        if (controller.isRunning) controller.abort(AbortReason.CANCELLED)
    }

    fun next() {
        val selector = selector ?: return
        load(selector.next(previous = chosen), settings.config.value)
    }

    fun retry() {
        val current = state.value?.context?.exercise ?: return
        controller.load(current, settings.config.value)
    }

    fun onBackgrounded() {
        if (controller.isRunning) controller.abort(AbortReason.BACKGROUNDED)
    }

    fun setPreviewBeats(beats: Double) = updateConfig(settings.config.value.copy(previewDurationBeats = beats))

    fun setTempo(bpm: Double) = updateConfig(settings.config.value.copy(tempoBpm = bpm))

    fun setMetronomeDuringAttempt(enabled: Boolean) = updateConfig(settings.config.value.copy(metronomeDuringAttempt = enabled))

    fun setKey(key: KeySignature) = updateContent(contentSettings.config.value.copy(keySignature = key))

    fun setHands(hands: Hands) = updateContent(contentSettings.config.value.copy(hands = hands))

    fun setTheme(mode: ThemeMode) = themeSettings.update(mode)

    private fun updateConfig(config: FlashConfig) {
        settings.update(config)
        val current = state.value
        if (current is AttemptState.Ready) controller.load(current.context.exercise, config)
    }

    private fun updateContent(content: ContentConfig) {
        contentSettings.update(content)
        val bundled = chosen ?: return
        if (state.value is AttemptState.Ready) load(bundled, settings.config.value)
    }

    /** Adapts [bundled] to the content settings and hands it to the controller. */
    private fun load(bundled: Exercise, config: FlashConfig) {
        chosen = bundled
        val content = contentSettings.config.value
        controller.load(bundled.adaptedTo(content.keySignature, content.hands, random), config)
    }

    override fun onCleared() {
        controller.endSession()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PracticeViewModel(
                    midi = container.midiDeviceManager,
                    exercises = container.exerciseRepository,
                    settings = container.flashSettings,
                    contentSettings = container.contentSettings,
                    themeSettings = container.themeSettings,
                    controllerFactory = container::attemptController,
                )
            }
        }
    }
}
