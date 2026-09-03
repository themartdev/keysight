package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.RunStatus

/**
 * One recorded run.
 *
 * [configJson] is the run configuration exactly as it stood when the run was performed, and
 * [startedAtNanos] is the run clock anchor for the raw MIDI timestamps, so history stays
 * re-evaluable on its own. The tempo is also a plain column so history can be queried without
 * parsing the snapshot. The segments performed are [SegmentEntity] rows.
 */
@Entity(
    tableName = "runs",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class RunEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val startedAtEpochMillis: Long,
    val startedAtNanos: Long,
    val status: RunStatus,
    val abortReason: AbortReason?,
    val tempoBpm: Double,
    val configJson: String,
)
