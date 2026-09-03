package dev.simonmartineau.keysight

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.ui.history.HistoryScreen
import dev.simonmartineau.keysight.ui.history.RunScreen
import dev.simonmartineau.keysight.ui.home.HomeScreen
import dev.simonmartineau.keysight.ui.play.PlayScreen
import dev.simonmartineau.keysight.ui.practice.PracticeScreen
import dev.simonmartineau.keysight.ui.settings.SettingsScreen
import dev.simonmartineau.keysight.ui.shell.AppFrame
import dev.simonmartineau.keysight.ui.theme.KeySightTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A practice session is hands-on-keys; the screen must not dim mid-run.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val container = (application as KeySightApplication).container
        setContent {
            val theme by container.themeSettings.mode.collectAsStateWithLifecycle()
            var screen: Screen by rememberSaveable(stateSaver = Screen.Saver) { mutableStateOf(Screen.Home) }
            KeySightTheme(darkTheme = theme.resolvesDark(isSystemInDarkTheme())) {
                screen.back?.let { back -> BackHandler { screen = back } }
                AppFrame(screen, onSelect = { screen = it.screen }) {
                    Content(screen, container, navigate = { screen = it })
                }
            }
        }
    }
}

/** The screen on the frame: each gets the container and the ways out of it, nothing else. */
@Composable
private fun Content(screen: Screen, container: AppContainer, navigate: (Screen) -> Unit) {
    when (screen) {
        Screen.Home -> HomeScreen(
            container = container,
            onStart = { navigate(Screen.Run(from = Screen.Home)) },
            onChangeSettings = { navigate(Screen.Play) },
            onHistory = { navigate(Screen.History(it)) },
        )
        Screen.Play -> PlayScreen(container, onStart = { navigate(Screen.Run(from = Screen.Play)) })
        Screen.Settings -> SettingsScreen(container)
        is Screen.Run -> PracticeScreen(container, onHistory = { navigate(Screen.History(it)) })
        is Screen.History -> HistoryScreen(
            container = container,
            currentSessionId = screen.currentSessionId,
            onOpenRun = { navigate(Screen.RunDetail(it, screen.currentSessionId)) },
        )
        is Screen.RunDetail -> RunScreen(container, screen.runId, onBack = { navigate(Screen.History(screen.currentSessionId)) })
    }
}
