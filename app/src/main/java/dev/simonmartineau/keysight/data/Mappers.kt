package dev.simonmartineau.keysight.data

import dev.simonmartineau.keysight.data.dao.CommittedRow
import dev.simonmartineau.keysight.data.entity.DifficultyStateEntity
import dev.simonmartineau.keysight.data.entity.EvaluationResultEntity
import dev.simonmartineau.keysight.data.entity.MidiEventEntity
import dev.simonmartineau.keysight.data.entity.RunEntity
import dev.simonmartineau.keysight.data.entity.SegmentEntity
import dev.simonmartineau.keysight.difficulty.DifficultyState
import dev.simonmartineau.keysight.difficulty.SegmentEvidence
import dev.simonmartineau.keysight.difficulty.evidenceOf
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunRecord
import dev.simonmartineau.keysight.run.Segment
import dev.simonmartineau.keysight.run.SegmentOrigin
import dev.simonmartineau.keysight.score.Score

/*
 * Pure conversions between the domain and the Room rows. They have no Room dependency beyond
 * the entity classes, which is what lets them be proven on the JVM.
 */

/** The id of segment [segmentIndex] of run [runId]: segments are addressed by their run and position. */
fun segmentId(runId: String, segmentIndex: Int): String = "$runId:$segmentIndex"

fun RunRecord.toEntity(): RunEntity = RunEntity(
    id = id,
    sessionId = sessionId,
    startedAtEpochMillis = startedAtEpochMillis,
    startedAtNanos = startedAtNanos,
    status = status,
    abortReason = abortReason,
    tempoBpm = config.tempoBpm,
    configJson = keySightJson.encodeToString(RunConfig.serializer(), config),
    seed = seed,
)

fun RunRecord.toSegmentEntities(): List<SegmentEntity> = segments.mapIndexed { index, segment -> segment.toEntity(id, index + 1) }

fun RunRecord.toMidiEventEntities(): List<MidiEventEntity> = events.map { it.toEntity(id) }

fun Segment.toEntity(runId: String, segmentIndex: Int): SegmentEntity = SegmentEntity(
    id = segmentId(runId, segmentIndex),
    runId = runId,
    segmentIndex = segmentIndex,
    exerciseId = (origin as? SegmentOrigin.Bundled)?.exerciseId,
    scoreJson = keySightJson.encodeToString(Score.serializer(), score),
    generatorVersion = (origin as? SegmentOrigin.Generated)?.generatorVersion,
    seed = (origin as? SegmentOrigin.Generated)?.seed,
    exerciseConfigJson = (origin as? SegmentOrigin.Generated)?.let { keySightJson.encodeToString(ExerciseConfig.serializer(), it.config) },
)

/** A row with the generator columns is a generated segment; one with only an exercise id is bundled content. */
fun SegmentEntity.toSegment(): Segment {
    val origin = when {
        generatorVersion != null && seed != null && exerciseConfigJson != null ->
            SegmentOrigin.Generated(generatorVersion, seed, keySightJson.decodeFromString(ExerciseConfig.serializer(), exerciseConfigJson))
        exerciseId != null -> SegmentOrigin.Bundled(exerciseId)
        else -> error("segment $id has neither a generator nor an exercise id")
    }
    return Segment(origin, keySightJson.decodeFromString(Score.serializer(), scoreJson))
}

fun RunEntity.toRecord(segments: List<SegmentEntity>, events: List<MidiEventEntity>): RunRecord = RunRecord(
    id = id,
    sessionId = sessionId,
    startedAtEpochMillis = startedAtEpochMillis,
    startedAtNanos = startedAtNanos,
    status = status,
    abortReason = abortReason,
    config = keySightJson.decodeFromString(RunConfig.serializer(), configJson),
    segments = segments.sortedBy { it.segmentIndex }.map { it.toSegment() },
    events = events.map { it.toMidiEvent() },
    seed = seed,
)

fun MidiEvent.toEntity(runId: String): MidiEventEntity = MidiEventEntity(
    runId = runId,
    timestampNanos = timestampNanos,
    status = status,
    data1 = data1,
    data2 = data2,
)

fun MidiEventEntity.toMidiEvent(): MidiEvent = MidiEvent(timestampNanos, status, data1, data2)

fun EvaluationResult.toEntity(segmentId: String, evaluatedAtEpochMillis: Long): EvaluationResultEntity =
    EvaluationResultEntity(
        segmentId = segmentId,
        evaluatorVersion = evaluatorVersion,
        evaluatedAtEpochMillis = evaluatedAtEpochMillis,
        pitchAccuracy = pitch.accuracy,
        correctCount = pitch.correctCount,
        expectedCount = pitch.expectedCount,
        extraCount = pitch.extraCount,
        rhythmAccuracy = rhythm?.accuracy,
        resultJson = keySightJson.encodeToString(EvaluationResult.serializer(), this),
    )

fun EvaluationResultEntity.toResult(): EvaluationResult =
    keySightJson.decodeFromString(EvaluationResult.serializer(), resultJson)

/** A stored commit as the difficulty window sees it, through the same function a live commit goes through. */
fun CommittedRow.toEvidence(): SegmentEvidence = evidenceOf(
    runConfig = keySightJson.decodeFromString(RunConfig.serializer(), runConfigJson),
    config = exerciseConfigJson?.let { keySightJson.decodeFromString(ExerciseConfig.serializer(), it) },
    result = keySightJson.decodeFromString(EvaluationResult.serializer(), resultJson),
)

fun DifficultyState.toEntity(updatedAtEpochMillis: Long): DifficultyStateEntity =
    DifficultyStateEntity(stateJson = keySightJson.encodeToString(DifficultyState.serializer(), this), updatedAtEpochMillis = updatedAtEpochMillis)

fun DifficultyStateEntity.toState(): DifficultyState = keySightJson.decodeFromString(DifficultyState.serializer(), stateJson)
