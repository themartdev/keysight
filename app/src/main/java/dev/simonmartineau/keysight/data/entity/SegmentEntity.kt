package dev.simonmartineau.keysight.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One performed segment of a run, with the score it showed and where it came from.
 *
 * [segmentIndex] counts from 1, segment 0 being the run's count-in; the segment starts at beat
 * `segmentIndex * beatsPerMeasure` of the meter in [scoreJson]. [scoreJson] is the segment's
 * own one-measure score, starting at tick 0, so a segment re-evaluates on its own and the
 * run's score is rebuilt by chaining them.
 *
 * A generated segment carries [generatorVersion], [seed] and [exerciseConfigJson], which
 * reproduce it; a segment of the bundled content of earlier rounds carries [exerciseId]
 * instead. The score is stored either way, because generators change and history must
 * re-evaluate on its own. Schema version 4 made [exerciseId] nullable and added the rest.
 */
@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index(value = ["runId", "segmentIndex"], unique = true)],
)
data class SegmentEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val segmentIndex: Int,
    val exerciseId: String?,
    val scoreJson: String,
    val generatorVersion: Int? = null,
    val seed: Long? = null,
    val exerciseConfigJson: String? = null,
)
