package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * A scored segment, keyed by the evaluator that produced it.
 *
 * The evaluator version is part of the primary key so that re-scoring an old segment adds a
 * row rather than replacing the original judgement. [resultJson] is the full per-note detail;
 * the summary columns let trends be queried without deserialising every segment.
 */
@Entity(
    tableName = "evaluation_results",
    primaryKeys = ["segmentId", "evaluatorVersion"],
    foreignKeys = [
        ForeignKey(
            entity = SegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EvaluationResultEntity(
    val segmentId: String,
    val evaluatorVersion: Int,
    val evaluatedAtEpochMillis: Long,
    val pitchAccuracy: Double,
    val correctCount: Int,
    val expectedCount: Int,
    val extraCount: Int,
    val resultJson: String,
    /** Null for rows scored by evaluator version 1, which had no rhythm. Added in schema version 2. */
    val rhythmAccuracy: Double? = null,
)
