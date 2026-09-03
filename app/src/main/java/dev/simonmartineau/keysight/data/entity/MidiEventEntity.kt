package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.simonmartineau.keysight.midi.MidiEventType

/**
 * A raw MIDI event exactly as it was received.
 *
 * Nothing here is derived. Evaluations are recomputable from these rows, so a better evaluator
 * can re-score old attempts instead of losing them.
 *
 * [timestampNanos] is absolute (`System.nanoTime` base) and [offsetFromAttemptStartNanos] is the
 * same instant relative to the attempt clock, which is what evaluation actually reads.
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
    val offsetFromAttemptStartNanos: Long,
    val type: MidiEventType,
    val channel: Int,
    val midiNoteNumber: Int,
    val velocity: Int,
)
