package dev.simonmartineau.keysight

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * Which screen is up. There are three and one way between them: practice opens history,
 * history opens a run's page, and back retraces the step. The current session id travels
 * along so history can name the session the practice screen is recording into.
 */
sealed interface Screen {

    data object Practice : Screen

    /** The history list, [currentSessionId] being the practice screen's session, expanded on arrival. */
    data class History(val currentSessionId: String?) : Screen

    /** One stored run's page, opened from the history list. */
    data class Run(val runId: String, val currentSessionId: String?) : Screen

    companion object {
        /** Survives a configuration change: the kind of screen and its ids. */
        val Saver: Saver<Screen, Any> = listSaver(
            save = { screen ->
                when (screen) {
                    Practice -> listOf("practice")
                    is History -> listOf("history", screen.currentSessionId)
                    is Run -> listOf("run", screen.runId, screen.currentSessionId)
                }
            },
            restore = { saved ->
                when (saved[0]) {
                    "history" -> History(saved[1])
                    "run" -> Run(saved[1]!!, saved[2])
                    else -> Practice
                }
            },
        )
    }
}
