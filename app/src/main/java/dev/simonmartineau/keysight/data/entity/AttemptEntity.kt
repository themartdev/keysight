package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.simonmartineau.keysight.attempt.AbortReason
import dev.simonmartineau.keysight.attempt.AttemptStatus

/**
 * One recorded attempt.
 *
 * [configJson] and [scoreJson] are the flash configuration and the score exactly as they stood
 * when the attempt ran, so history stays re-evaluable when bundled content changes.
 * [startedAtNanos] is the attempt clock anchor for the raw MIDI timestamps. Tempo and preview
 * duration are also plain columns so history can be queried without parsing snapshots.
 */
@Entity(
    tableName = "attempts",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class AttemptEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val startedAtEpochMillis: Long,
    val startedAtNanos: Long,
    val status: AttemptStatus,
    val abortReason: AbortReason?,
    val tempoBpm: Double,
    val previewDurationBeats: Double,
    val configJson: String,
    val scoreJson: String,
)
