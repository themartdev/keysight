package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A scored attempt, keyed by which evaluator produced it.
 *
 * The evaluator version is part of the primary key so that re-scoring an old attempt with an
 * improved evaluator adds a row rather than destroying the original judgement.
 *
 * [outcomesJson] holds the per-note detail; the summary columns exist so that trends can be
 * queried without deserialising every attempt.
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
    indices = [Index("attemptId")],
)
data class EvaluationResultEntity(
    val attemptId: String,
    val evaluatorVersion: Int,
    val pitchAccuracy: Double,
    val correctCount: Int,
    val expectedCount: Int,
    val extraCount: Int,
    val outcomesJson: String,
)
