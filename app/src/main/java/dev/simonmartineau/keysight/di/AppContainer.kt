package dev.simonmartineau.keysight.di

import android.content.Context
import dev.simonmartineau.keysight.audio.AudioTrackMetronome
import dev.simonmartineau.keysight.audio.Metronome
import dev.simonmartineau.keysight.data.KeySightDatabase
import dev.simonmartineau.keysight.data.RoomDifficultyStore
import dev.simonmartineau.keysight.data.RoomRunHistory
import dev.simonmartineau.keysight.difficulty.DifficultyStore
import dev.simonmartineau.keysight.difficulty.DifficultyTracker
import dev.simonmartineau.keysight.history.HistoryReader
import dev.simonmartineau.keysight.history.HistoryStore
import dev.simonmartineau.keysight.midi.MidiDeviceManager
import dev.simonmartineau.keysight.run.RunController
import dev.simonmartineau.keysight.run.RunHistory
import dev.simonmartineau.keysight.run.RunState
import dev.simonmartineau.keysight.settings.ContentSettings
import dev.simonmartineau.keysight.settings.RunSettings
import dev.simonmartineau.keysight.settings.SharedPreferencesContentSettings
import dev.simonmartineau.keysight.settings.SharedPreferencesRunSettings
import dev.simonmartineau.keysight.settings.SharedPreferencesThemeSettings
import dev.simonmartineau.keysight.settings.ThemeSettings
import dev.simonmartineau.keysight.timing.MonotonicClock
import dev.simonmartineau.keysight.timing.SystemMonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container, created once in [dev.simonmartineau.keysight.KeySightApplication].
 *
 * The app is a single module with a handful of long-lived collaborators, so a container built by
 * hand is easier to follow than a code-generated graph and costs nothing at build time.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val clock: MonotonicClock = SystemMonotonicClock

    /** Outlives any screen, for work that must finish once started: recording a run, closing a session. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: KeySightDatabase by lazy { KeySightDatabase.build(appContext) }

    private val roomHistory: RoomRunHistory by lazy { RoomRunHistory(database) }

    /** The write side of history, the run controller's. */
    val runHistory: RunHistory get() = roomHistory

    /** The read side of the same tables, the history screen's. */
    val historyStore: HistoryStore get() = roomHistory

    val difficultyStore: DifficultyStore by lazy { RoomDifficultyStore(database) }

    val midiDeviceManager: MidiDeviceManager by lazy { MidiDeviceManager(appContext, clock) }

    val metronome: Metronome by lazy { AudioTrackMetronome(appContext, clock) }

    val runSettings: RunSettings by lazy { SharedPreferencesRunSettings(appContext) }

    val contentSettings: ContentSettings by lazy { SharedPreferencesContentSettings(appContext) }

    val themeSettings: ThemeSettings by lazy { SharedPreferencesThemeSettings(appContext) }

    /** One controller per practice screen; [scope] is the screen's main-thread scope. */
    fun runController(scope: CoroutineScope, onRunEnded: (RunState) -> Unit): RunController =
        RunController(scope, appScope, clock, metronome, runHistory, onRunEnded = onRunEnded)

    /** One tracker per practice screen: the difficulty controller's state and evidence, saved through [appScope]. */
    fun difficultyTracker(): DifficultyTracker = DifficultyTracker(difficultyStore, appScope)

    /** History at the current evaluator version, re-evaluating what is older as it is read. */
    fun historyReader(): HistoryReader = HistoryReader(historyStore)
}
