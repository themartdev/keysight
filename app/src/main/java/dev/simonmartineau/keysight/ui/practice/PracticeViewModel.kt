package dev.simonmartineau.keysight.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.exercise.Exercise
import dev.simonmartineau.keysight.exercise.ExerciseRepository
import dev.simonmartineau.keysight.exercise.ExerciseSelector
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.exercise.adaptedTo
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.midi.MidiDeviceManager
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunContext
import dev.simonmartineau.keysight.run.RunController
import dev.simonmartineau.keysight.run.RunState
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.SegmentSource
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.settings.ContentSettings
import dev.simonmartineau.keysight.settings.RunSettings
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
 * app leaving the foreground aborts a running run, and settings changes rebuild the run that
 * is waiting to start.
 *
 * A run's content is bundled measures chained in one key: the selector picks them, each is
 * adapted to the content settings, and the adaptation always starts from the bundled
 * exercise, never from an already adapted score. An open-ended run starts with a first batch
 * and draws the rest from a [SegmentSource] over the same selector as it goes.
 */
class PracticeViewModel(
    private val midi: MidiDeviceManager,
    private val exercises: ExerciseRepository,
    private val settings: RunSettings,
    private val contentSettings: ContentSettings,
    private val themeSettings: ThemeSettings,
    controllerFactory: (CoroutineScope) -> RunController,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val controller = controllerFactory(viewModelScope)

    val state: StateFlow<RunState?> = controller.state
    val connection: StateFlow<MidiConnection> = midi.connection
    val config: StateFlow<RunConfig> = settings.config
    val content: StateFlow<ContentConfig> = contentSettings.config
    val theme: StateFlow<ThemeMode> = themeSettings.mode

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    private var pack: List<Exercise> = emptyList()
    private var selector: ExerciseSelector? = null

    /** The bundled exercises the waiting or running run was adapted from, one per initial segment. */
    private var chosen: List<Exercise> = emptyList()

    init {
        viewModelScope.launch {
            pack = runCatching { exercises.all() }.getOrElse {
                _loadError.value = it.message ?: "could not load the exercises"
                return@launch
            }
            selector = ExerciseSelector(pack, random)
            next()
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
        if (state.value is RunState.Ready && connection.value is MidiConnection.Connected) controller.start()
    }

    fun stop() {
        if (controller.isRunning) controller.stop()
    }

    /** A new run of fresh measures. */
    fun next() {
        val selector = selector ?: return
        load(selector.nextRun(initialSegmentCount(), previous = chosen.lastOrNull()))
    }

    /** The same measures again, from the start; an open-ended run repeats its first batch. */
    fun retry() {
        if (chosen.isEmpty()) return
        load(chosen)
    }

    fun onBackgrounded() {
        if (controller.isRunning) controller.abort(AbortReason.BACKGROUNDED)
    }

    fun setMode(mode: VisibilityMode) = updateConfig(settings.config.value.copy(mode = mode))

    fun setLookaheadBeats(beats: Double) = updateConfig(settings.config.value.copy(lookaheadBeats = beats))

    fun setTempo(bpm: Double) = updateConfig(settings.config.value.copy(tempoBpm = bpm))

    fun setMetronome(mode: MetronomeMode) = updateConfig(settings.config.value.copy(metronome = mode))

    /** [count] segments, or null for a run that goes on until Stop. */
    fun setSegmentCount(count: Int?) = updateConfig(settings.config.value.copy(segmentCount = count))

    fun setKey(key: KeySignature) = updateContent(contentSettings.config.value.copy(keySignature = key))

    fun setHands(hands: Hands) = updateContent(contentSettings.config.value.copy(hands = hands))

    fun setTheme(mode: ThemeMode) = themeSettings.update(mode)

    private fun updateConfig(config: RunConfig) {
        settings.update(config)
        reloadIfReady()
    }

    private fun updateContent(content: ContentConfig) {
        contentSettings.update(content)
        reloadIfReady()
    }

    /** Rebuilds the waiting run under the new settings, picking new measures when the length changed. */
    private fun reloadIfReady() {
        if (state.value !is RunState.Ready || chosen.isEmpty()) return
        if (chosen.size == initialSegmentCount()) load(chosen) else next()
    }

    private fun initialSegmentCount(): Int =
        settings.config.value.segmentCount ?: (SegmentSource.SEGMENTS_AHEAD + SegmentSource.SEGMENT_BATCH)

    /** Adapts [bundled] to the content settings, chains them into a run and hands it to the controller. */
    private fun load(bundled: List<Exercise>) {
        chosen = bundled
        val content = contentSettings.config.value
        val config = settings.config.value
        val source = if (config.isOpenEnded) segmentSource(content) else null
        controller.load(RunContext(bundled.map { it.segment(content) }, config), source)
    }

    /** More measures for an open-ended run, chosen by the selector and adapted like the first ones. */
    private fun segmentSource(content: ContentConfig) = SegmentSource { count, previous ->
        val selector = selector ?: return@SegmentSource emptyList()
        selector.nextRun(count, previous = pack.firstOrNull { it.id == previous.exerciseId }).map { it.segment(content) }
    }

    private fun Exercise.segment(content: ContentConfig): Segment =
        Segment(id, adaptedTo(content.keySignature, content.hands, random).score)

    override fun onCleared() {
        controller.endSession()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PracticeViewModel(
                    midi = container.midiDeviceManager,
                    exercises = container.exerciseRepository,
                    settings = container.runSettings,
                    contentSettings = container.contentSettings,
                    themeSettings = container.themeSettings,
                    controllerFactory = container::runController,
                )
            }
        }
    }
}
