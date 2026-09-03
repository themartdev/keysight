package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One recorded attempt at an exercise.
 *
 * [configSnapshot] is the serialised [dev.simonmartineau.keysight.attempt.FlashConfig] as it stood
 * when the attempt ran. Tempo and preview duration are also stored as plain columns so history
 * can be queried without parsing every snapshot.
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
    val tempoBpm: Double,
    val previewDurationBeats: Double,
    val configSnapshot: String,
)
