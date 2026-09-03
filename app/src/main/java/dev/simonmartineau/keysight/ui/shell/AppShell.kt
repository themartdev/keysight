package dev.simonmartineau.keysight.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.simonmartineau.keysight.di.AppContainer
import dev.simonmartineau.keysight.ui.history.HistoryScreen
import dev.simonmartineau.keysight.ui.history.RunScreen
import dev.simonmartineau.keysight.ui.practice.PracticeScreen
import dev.simonmartineau.keysight.ui.settings.SettingsScreen
import dev.simonmartineau.keysight.ui.theme.KeySightTheme

/**
 * The whole app: the theme, the back stack and the destination on top of it. Every
 * destination gets the container and the ways out of it, nothing else.
 */
@Composable
fun AppShell(container: AppContainer) {
    val theme by container.themeSettings.mode.collectAsStateWithLifecycle()
    val stack = rememberBackStack()
    BackHandler(enabled = stack.canGoBack) { stack.pop() }
    KeySightTheme(darkTheme = theme.resolvesDark(isSystemInDarkTheme())) {
        when (val current = stack.current) {
            Destination.Practice -> PracticeScreen(
                container = container,
                onHistory = { stack.push(Destination.History(it)) },
                onSettings = { stack.push(Destination.Settings) },
            )
            is Destination.History -> HistoryScreen(
                container = container,
                currentSessionId = current.currentSessionId,
                onOpenRun = { stack.push(Destination.Run(it)) },
                onBack = stack::pop,
            )
            is Destination.Run -> RunScreen(container, current.runId, onBack = stack::pop)
            Destination.Settings -> SettingsScreen(container, onBack = stack::pop)
        }
    }
}
