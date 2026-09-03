package dev.simonmartineau.keysight.di

import android.content.Context
import dev.simonmartineau.keysight.attempt.AttemptController
import dev.simonmartineau.keysight.attempt.AttemptHistory
import dev.simonmartineau.keysight.audio.AudioTrackMetronome
import dev.simonmartineau.keysight.audio.Metronome
import dev.simonmartineau.keysight.data.KeySightDatabase
import dev.simonmartineau.keysight.data.RoomAttemptHistory
import dev.simonmartineau.keysight.data.keySightJson
import dev.simonmartineau.keysight.exercise.AndroidAssetSource
import dev.simonmartineau.keysight.exercise.BundledExerciseRepository
import dev.simonmartineau.keysight.exercise.ExerciseRepository
import dev.simonmartineau.keysight.midi.MidiDeviceManager
import dev.simonmartineau.keysight.settings.FlashSettings
import dev.simonmartineau.keysight.settings.SharedPreferencesFlashSettings
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

    /** Outlives any screen, for work that must finish once started: recording an attempt, closing a session. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: KeySightDatabase by lazy { KeySightDatabase.build(appContext) }

    val attemptHistory: AttemptHistory by lazy { RoomAttemptHistory(database) }

    val midiDeviceManager: MidiDeviceManager by lazy { MidiDeviceManager(appContext, clock) }

    val metronome: Metronome by lazy { AudioTrackMetronome(appContext, clock) }

    val exerciseRepository: ExerciseRepository by lazy {
        BundledExerciseRepository(AndroidAssetSource(appContext.assets), keySightJson)
    }

    val flashSettings: FlashSettings by lazy { SharedPreferencesFlashSettings(appContext) }

    val themeSettings: ThemeSettings by lazy { SharedPreferencesThemeSettings(appContext) }

    /** One controller per practice screen; [scope] is the screen's main-thread scope. */
    fun attemptController(scope: CoroutineScope): AttemptController =
        AttemptController(scope, appScope, clock, metronome, attemptHistory)
}
