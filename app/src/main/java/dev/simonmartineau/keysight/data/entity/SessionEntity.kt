package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One continuous stretch of practice. [endedAtEpochMillis] is null while it is running. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
)
