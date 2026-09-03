package dev.simonmartineau.keysight.data

import dev.simonmartineau.keysight.data.entity.AttemptEntity
import dev.simonmartineau.keysight.data.entity.EvaluationResultEntity
import dev.simonmartineau.keysight.data.entity.MidiEventEntity
import dev.simonmartineau.keysight.evaluation.EvaluationResult
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunRecord
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.score.Score

/*
 * Pure conversions between the domain and the Room rows. They have no Room dependency beyond
 * the entity classes, which is what lets them be proven on the JVM.
 *
 * A run is stored as one attempt row until schema version 3: the exercise column holds the
 * segments' ids joined by commas, the config snapshot is the run configuration, and the
 * preview column holds the lookahead, unbounded as infinity.
 */

private const val EXERCISE_ID_SEPARATOR = ","

fun RunRecord.toEntity(): AttemptEntity = AttemptEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseIds.joinToString(EXERCISE_ID_SEPARATOR),
    startedAtEpochMillis = startedAtEpochMillis,
    startedAtNanos = startedAtNanos,
    status = status,
    abortReason = abortReason,
    tempoBpm = config.tempoBpm,
    previewDurationBeats = if (config.mode == VisibilityMode.FLASH) config.lookaheadBeats else Double.POSITIVE_INFINITY,
    configJson = keySightJson.encodeToString(RunConfig.serializer(), config),
    scoreJson = keySightJson.encodeToString(Score.serializer(), score),
)

fun RunRecord.toMidiEventEntities(): List<MidiEventEntity> = events.map { it.toEntity(id) }

fun AttemptEntity.toRecord(events: List<MidiEventEntity>): RunRecord = RunRecord(
    id = id,
    sessionId = sessionId,
    exerciseIds = exerciseId.split(EXERCISE_ID_SEPARATOR),
    startedAtEpochMillis = startedAtEpochMillis,
    startedAtNanos = startedAtNanos,
    status = status,
    abortReason = abortReason,
    config = keySightJson.decodeFromString(RunConfig.serializer(), configJson),
    score = keySightJson.decodeFromString(Score.serializer(), scoreJson),
    events = events.map { it.toMidiEvent() },
)

fun MidiEvent.toEntity(attemptId: String): MidiEventEntity = MidiEventEntity(
    attemptId = attemptId,
    timestampNanos = timestampNanos,
    status = status,
    data1 = data1,
    data2 = data2,
)

fun MidiEventEntity.toMidiEvent(): MidiEvent = MidiEvent(timestampNanos, status, data1, data2)

fun EvaluationResult.toEntity(attemptId: String, evaluatedAtEpochMillis: Long): EvaluationResultEntity =
    EvaluationResultEntity(
        attemptId = attemptId,
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
