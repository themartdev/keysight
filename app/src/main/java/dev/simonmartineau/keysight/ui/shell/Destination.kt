package dev.simonmartineau.keysight.ui.shell

/**
 * Where the app can be. [Practice] is the root and never leaves the back stack: the session
 * is that destination's lifetime. A mode, something that produces a run the evaluator judges,
 * lives inside [Practice] as a row of its setup sheet; a tool, anything that does not, is a
 * destination of its own that fills the same [StageScaffold].
 */
sealed interface Destination {

    data object Practice : Destination

    /** The history list, [currentSessionId] being the practice destination's session, expanded on arrival. */
    data class History(val currentSessionId: String?) : Destination

    /** One stored run's page, opened from the history list. */
    data class Run(val runId: String) : Destination

    /** The app's own settings: theme, the keyboard. */
    data object Settings : Destination

    companion object {
        /** A destination as the strings that survive a configuration change. */
        fun save(destination: Destination): List<String?> = when (destination) {
            Practice -> listOf("practice")
            is History -> listOf("history", destination.currentSessionId)
            is Run -> listOf("run", destination.runId)
            Settings -> listOf("settings")
        }

        fun restore(saved: List<String?>): Destination = when (saved[0]) {
            "history" -> History(saved[1])
            "run" -> Run(saved[1]!!)
            "settings" -> Settings
            else -> Practice
        }
    }
}
