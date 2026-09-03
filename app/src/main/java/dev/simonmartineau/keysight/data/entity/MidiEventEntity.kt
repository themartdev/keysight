package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A raw MIDI channel message exactly as it was received: the three bytes and the timestamp.
 *
 * Nothing here is derived, so evaluations are recomputable from these rows and a better
 * evaluator can re-score old runs instead of losing them. Events belong to the run, not to a
 * segment: which segment a note counts for is the evaluator's judgement, not raw data.
 */
@Entity(
    tableName = "midi_events",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId")],
)
data class MidiEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val timestampNanos: Long,
    val status: Int,
    val data1: Int,
    val data2: Int,
)
