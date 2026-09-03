package dev.simonmartineau.keysight.session

/** One continuous stretch of practice. [endedAtEpochMillis] is null while it is still running. */
data class Session(
    val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
)
