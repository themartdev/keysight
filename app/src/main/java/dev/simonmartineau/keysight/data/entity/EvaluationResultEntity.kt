package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * A scored attempt, keyed by the evaluator that produced it.
 *
 * The evaluator version is part of the primary key so that re-scoring an old attempt adds a
 * row rather than replacing the original judgement. [resultJson] is the full per-note detail;
 * the summary columns let trends be queried without deserialising every attempt.
 */
@Entity(
    tableName = "evaluation_results",
    primaryKeys = ["attemptId", "evaluatorVersion"],
    foreignKeys = [
        ForeignKey(
            entity = AttemptEntity::class,
            parentColumns = ["id"],
            childColumns = ["attemptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EvaluationResultEntity(
    val attemptId: String,
    val evaluatorVersion: Int,
    val evaluatedAtEpochMillis: Long,
    val pitchAccuracy: Double,
    val correctCount: Int,
    val expectedCount: Int,
    val extraCount: Int,
    val resultJson: String,
)
