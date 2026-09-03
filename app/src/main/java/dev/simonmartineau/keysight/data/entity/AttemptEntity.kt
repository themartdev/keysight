package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.RunStatus

/**
 * One recorded run, in the row shape of the attempts it replaced.
 *
 * [configJson] and [scoreJson] are the run configuration and the score exactly as they stood
 * when the run was performed, so history stays re-evaluable when bundled content changes.
 * [startedAtNanos] is the run clock anchor for the raw MIDI timestamps. Tempo and the
 * lookahead are also plain columns so history can be queried without parsing snapshots.
 *
 * Rows written before Round 6 hold a flash configuration in [configJson] and a preview
 * duration in [previewDurationBeats]; rows since hold a run configuration and the lookahead,
 * infinite when the mode shows everything. Schema version 3 replaces this table with runs
 * and segments and migrates both shapes.
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
    /** The bundled exercise ids of the run's segments, comma separated. */
    val exerciseId: String,
    val startedAtEpochMillis: Long,
    val startedAtNanos: Long,
    val status: RunStatus,
    val abortReason: AbortReason?,
    val tempoBpm: Double,
    val previewDurationBeats: Double,
    val configJson: String,
    val scoreJson: String,
)
