package dev.simonmartineau.keysight.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.difficulty.AdaptiveSegmentSource
import dev.simonmartineau.keysight.difficulty.Decision
import dev.simonmartineau.keysight.difficulty.DifficultyTracker
import dev.simonmartineau.keysight.difficulty.evidenceOf
import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.midi.MidiDeviceManager
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunContext
import dev.simonmartineau.keysight.run.RunController
import dev.simonmartineau.keysight.run.RunState
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
 * A run's content is generated: a run seed drawn when the run is built, the content settings'
 * [dev.simonmartineau.keysight.exercise.ExerciseConfig] and the difficulty controller's level
 * determine every segment, so trying the same run again regenerates the same measures and a
 * settings change while the run is waiting regenerates them under the new configuration. An
 * open-ended run keeps drawing from the same source as it goes, and that source lets the
 * controller move the level for the bars still to come. When a run ends the controller
 * decides for the next one; a lookahead it moves is written into the run settings, so the
 * player sees the chip move, and [nextRun] carries the decision for the summary.
 */
class PracticeViewModel(
    private val midi: MidiDeviceManager,
    private val settings: RunSettings,
    private val contentSettings: ContentSettings,
    private val themeSettings: ThemeSettings,
    private val difficulty: DifficultyTracker,
    controllerFactory: (CoroutineScope, onRunEnded: (RunState) -> Unit) -> RunController,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val controller = controllerFactory(viewModelScope, ::onRunEnded)

    val state: StateFlow<RunState?> = controller.state
    val connection: StateFlow<MidiConnection> = midi.connection
    val config: StateFlow<RunConfig> = settings.config
    val content: StateFlow<ContentConfig> = contentSettings.config
    val theme: StateFlow<ThemeMode> = themeSettings.mode

    /** The session this screen's runs are recorded into, once the first has been. */
    val sessionId: StateFlow<String?> = controller.sessionId

    private val _nextRun = MutableStateFlow<Decision?>(null)

    /** What the controller decided when the last run ended, until the next run is built. */
    val nextRun: StateFlow<Decision?> = _nextRun.asStateFlow()

    /** The seed of the waiting or running run, from which every segment derives. */
    private var runSeed: Long = random.nextLong()

    init {
        viewModelScope.launch {
            difficulty.restore()
            load()
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
        runSeed = random.nextLong()
        load()
    }

    /** The same measures again, from the start; an open-ended run repeats from its first bar. */
    fun retry() = load()

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

    fun setAccompaniment(accompaniment: Accompaniment) = updateContent(contentSettings.config.value.copy(accompaniment = accompaniment))

    fun setTheme(mode: ThemeMode) = themeSettings.update(mode)

    private fun updateConfig(config: RunConfig) {
        settings.update(config)
        reloadIfReady()
    }

    private fun updateContent(content: ContentConfig) {
        contentSettings.update(content)
        reloadIfReady()
    }

    /** Rebuilds the waiting run under the new settings, from the same seed. */
    private fun reloadIfReady() {
        if (state.value is RunState.Ready) load()
    }

    /** Generates the run's first segments from the current settings and level and hands it to the controller. */
    private fun load() {
        val config = settings.config.value
        val source = AdaptiveSegmentSource(runSeed, contentSettings.config.value.exerciseConfig, config, difficulty)
        val segments = source.initial(config.segmentCount ?: SegmentSource.SEGMENTS_AHEAD)
        _nextRun.value = null
        controller.load(RunContext(segments, config, runSeed), source.takeIf { config.isOpenEnded })
    }

    /** A run ended: its commits are evidence, and the controller decides for the next run. */
    private fun onRunEnded(state: RunState) {
        val runConfig = state.context.config
        val evidence = state.committed.map { evidenceOf(runConfig, it.segment, it.result) }
        val decision = difficulty.runEnded(runConfig, contentSettings.config.value.exerciseConfig, evidence)
        val moved = decision.position.runConfig
        if (moved.lookaheadBeats != runConfig.lookaheadBeats) settings.update(settings.config.value.copy(lookaheadBeats = moved.lookaheadBeats))
        _nextRun.value = decision.takeIf { it.moved }
    }

    override fun onCleared() {
        controller.endSession()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PracticeViewModel(
                    midi = container.midiDeviceManager,
                    settings = container.runSettings,
                    contentSettings = container.contentSettings,
                    themeSettings = container.themeSettings,
                    difficulty = container.difficultyTracker(),
                    controllerFactory = container::runController,
                )
            }
        }
    }
}
