package dev.simonmartineau.keysight

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.simonmartineau.keysight.ui.history.HistoryScreen
import dev.simonmartineau.keysight.ui.history.RunScreen
import dev.simonmartineau.keysight.ui.practice.PracticeScreen
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
            var screen: Screen by rememberSaveable(stateSaver = Screen.Saver) { mutableStateOf(Screen.Practice) }
            KeySightTheme(darkTheme = theme.resolvesDark(isSystemInDarkTheme())) {
                when (val current = screen) {
                    Screen.Practice -> PracticeScreen(container, onHistory = { screen = Screen.History(it) })
                    is Screen.History -> {
                        BackHandler { screen = Screen.Practice }
                        HistoryScreen(
                            container = container,
                            currentSessionId = current.currentSessionId,
                            onOpenRun = { screen = Screen.Run(it, current.currentSessionId) },
                            onBack = { screen = Screen.Practice },
                        )
                    }
                    is Screen.Run -> {
                        val back = Screen.History(current.currentSessionId)
                        BackHandler { screen = back }
                        RunScreen(container, current.runId, onBack = { screen = back })
                    }
                }
            }
        }
    }
}
