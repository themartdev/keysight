package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A raw MIDI channel message exactly as it was received: the three bytes and the timestamp.
 *
 * Nothing here is derived, so evaluations are recomputable from these rows and a better
 * evaluator can re-score old attempts instead of losing them.
 */
@Entity(
    tableName = "midi_events",
    foreignKeys = [
        ForeignKey(
            entity = AttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("attemptId")],
)
data class MidiEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val attemptId: String,
    val timestampNanos: Long,
    val status: Int,
    val data1: Int,
    val data2: Int,
)
