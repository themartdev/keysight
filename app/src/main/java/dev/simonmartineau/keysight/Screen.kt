package dev.simonmartineau.keysight

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * Where the app can be. [Home] is the launch destination and the only one back leaves the
 * app from. The rail moves between [Home], [Play], [History] and [Settings]; a [Run] is
 * full-bleed, no rail, and returns to the screen that started it; [RunDetail] is one stored
 * run's page, opened from history and returning to it. There is no stack: every screen knows
 * its own way [back].
 */
sealed interface Screen {

    /** The launcher: the current settings read back, Start, and the week at a glance. */
    data object Home : Screen

    /** The settings as a preset list, with a preview of the music. */
    data object Play : Screen

    /** The app's own settings: keyboard, theme, adaptive difficulty. */
    data object Settings : Screen

    /** The practice screen, started [from] [Home] or [Play]. */
    data class Run(val from: Screen) : Screen {
        init {
            require(from == Home || from == Play) { "a run starts from Home or Play, not $from" }
        }
    }

    /** The session list, [currentSessionId] expanded on arrival when it names one. */
    data class History(val currentSessionId: String?) : Screen

    /** One stored run's page, opened from the history list. */
    data class RunDetail(val runId: String, val currentSessionId: String?) : Screen

    /** The screen back leads to; null on [Home], where back leaves the app. */
    val back: Screen?
        get() = when (this) {
            Home -> null
            Play, Settings, is History -> Home
            is Run -> from
            is RunDetail -> History(currentSessionId)
        }

    /** Whether the rail is drawn beside the screen; a run has the whole window. */
    val hasRail: Boolean get() = this !is Run

    /** The rail's tab this screen belongs to. */
    val tab: Tab
        get() = when (this) {
            Home -> Tab.HOME
            Play -> Tab.PLAY
            is History, is RunDetail -> Tab.HISTORY
            Settings -> Tab.SETTINGS
            is Run -> from.tab
        }

    /** The rail's four destinations, in rail order; [SETTINGS] is pinned to the bottom. */
    enum class Tab(val label: String) {
        HOME("Home"),
        PLAY("Play"),
        HISTORY("History"),
        SETTINGS("Settings");

        /** The screen the tab opens. */
        val screen: Screen
            get() = when (this) {
                HOME -> Home
                PLAY -> Play
                HISTORY -> History(null)
                SETTINGS -> Settings
            }
    }

    companion object {
        /** Survives a configuration change: the kind of screen and its ids. */
        val Saver: Saver<Screen, Any> = listSaver(
            save = { screen ->
                when (screen) {
                    Home -> listOf("home")
                    Play -> listOf("play")
                    Settings -> listOf("settings")
                    is Run -> listOf("run", if (screen.from == Play) "play" else "home")
                    is History -> listOf("history", screen.currentSessionId)
                    is RunDetail -> listOf("runDetail", screen.runId, screen.currentSessionId)
                }
            },
            restore = { saved ->
                when (saved[0]) {
                    "play" -> Play
                    "settings" -> Settings
                    "run" -> Run(if (saved[1] == "play") Play else Home)
                    "history" -> History(saved[1])
                    "runDetail" -> RunDetail(saved[1]!!, saved[2])
                    else -> Home
                }
            },
        )
    }
}
