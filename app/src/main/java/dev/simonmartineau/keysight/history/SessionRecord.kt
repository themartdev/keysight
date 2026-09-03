package dev.simonmartineau.keysight.history

/**
 * One practice session as history lists it: opened by the first run recorded after the app
 * was opened, closed when the practice screen was left. [endedAtEpochMillis] is null while
 * the session is going on, and stays null for a session the process died in.
 */
data class SessionRecord(
    val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
)
