package dev.simonmartineau.keysight.data

import dev.simonmartineau.keysight.data.entity.RunEntity
import dev.simonmartineau.keysight.data.entity.SegmentEntity
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.RunStatus
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.measureAsScore
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.timing.RunTimeline
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * One row of the `attempts` table of schema versions 1 and 2, as the migration to version 3
 * reads it.
 *
 * Two shapes exist. Rows written before Round 6 hold a `FlashConfig` snapshot
 * (`tempoBpm, countInMeasures, previewDurationBeats, metronomeDuringAttempt`) and the score of
 * one exercise. Rows written by the first half of Round 6 hold a `RunConfig` snapshot, the run's
 * score with its resting measure 0 and `k:` prefixed note ids, and the segments' exercise ids
 * in [exerciseId] separated by commas.
 */
data class LegacyAttemptRow(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val startedAtEpochMillis: Long,
    val startedAtNanos: Long,
    val status: String,
    val abortReason: String?,
    val tempoBpm: Double,
    val previewDurationBeats: Double,
    val configJson: String,
    val scoreJson: String,
)

/** A legacy attempt as schema 3 keeps it: one run and its segments. Its MIDI moves by id. */
data class ConvertedRun(val run: RunEntity, val segments: List<SegmentEntity>)

private const val EXERCISE_ID_SEPARATOR = ","

/**
 * The run this attempt was: one segment per measure of its score, on the run's beat line.
 *
 * A pre-Round 6 attempt counted in `countInMeasures` measures where a run counts in exactly
 * one, so its anchor moves forward by the extra count-in so that the performance starts on the
 * same beat and the raw MIDI, which is not touched, still lands where it was played. A row
 * whose snapshots cannot be read becomes a run with one segment holding the score text as it
 * is and a configuration rebuilt from the plain columns, so that nothing is lost.
 */
fun LegacyAttemptRow.toRun(): ConvertedRun = runCatching { convert() }.getOrElse { fallback() }

private fun LegacyAttemptRow.convert(): ConvertedRun {
    val config = keySightJson.parseToJsonElement(configJson) as JsonObject
    val score = keySightJson.decodeFromString(Score.serializer(), scoreJson)
    val exerciseIds = exerciseId.split(EXERCISE_ID_SEPARATOR)
    return if ("mode" in config) runShaped(score, exerciseIds) else flashShaped(config, score)
}

private fun LegacyAttemptRow.runShaped(score: Score, exerciseIds: List<String>): ConvertedRun {
    require(score.measureCount >= 2) { "a run score has a count-in measure and a segment" }
    val segments = (1 until score.measureCount).map { measure ->
        segmentEntity(measure, exerciseIds.getOrNull(measure - 1) ?: exerciseId, score.measureAsScore(measure, idPrefix = "$measure:"))
    }
    return ConvertedRun(runEntity(startedAtNanos, tempoBpm, configJson), segments)
}

private fun LegacyAttemptRow.flashShaped(config: JsonObject, score: Score): ConvertedRun {
    val tempo = config["tempoBpm"]?.jsonPrimitive?.doubleOrNull ?: tempoBpm
    val countInMeasures = config["countInMeasures"]?.jsonPrimitive?.intOrNull ?: 1
    val preview = config["previewDurationBeats"]?.jsonPrimitive?.doubleOrNull ?: previewDurationBeats
    val throughout = config["metronomeDuringAttempt"]?.jsonPrimitive?.booleanOrNull ?: false
    val runConfig = RunConfig(
        tempoBpm = tempo,
        metronome = if (throughout) MetronomeMode.THROUGHOUT else MetronomeMode.COUNT_IN_ONLY,
        mode = VisibilityMode.FLASH,
        lookaheadBeats = if (preview.isFinite() && preview > 0.0) preview else RunConfig.DEFAULT.lookaheadBeats,
        segmentCount = score.measureCount,
    )
    val extraCountInBeats = (countInMeasures - 1) * score.timeSignature.beatsPerMeasure.toDouble()
    val anchor = startedAtNanos + RunTimeline(tempo, score.timeSignature, segmentCount = 2, metronomeThroughout = false).nanosAtBeat(extraCountInBeats)
    val segments = (0 until score.measureCount).map { measure ->
        segmentEntity(measure + 1, exerciseId, score.measureAsScore(measure))
    }
    return ConvertedRun(runEntity(anchor, tempo, keySightJson.encodeToString(RunConfig.serializer(), runConfig)), segments)
}

private fun LegacyAttemptRow.fallback(): ConvertedRun {
    val config = RunConfig(
        tempoBpm = if (tempoBpm > 0.0) tempoBpm else RunConfig.DEFAULT.tempoBpm,
        metronome = MetronomeMode.COUNT_IN_ONLY,
        mode = VisibilityMode.FLASH,
        lookaheadBeats = if (previewDurationBeats.isFinite() && previewDurationBeats > 0.0) previewDurationBeats else RunConfig.DEFAULT.lookaheadBeats,
        segmentCount = 1,
    )
    val segment = SegmentEntity(segmentId(id, 1), id, segmentIndex = 1, exerciseId = exerciseId, scoreJson = scoreJson)
    return ConvertedRun(runEntity(startedAtNanos, config.tempoBpm, keySightJson.encodeToString(RunConfig.serializer(), config)), listOf(segment))
}

private fun LegacyAttemptRow.runEntity(anchorNanos: Long, tempo: Double, configJson: String) = RunEntity(
    id = id,
    sessionId = sessionId,
    startedAtEpochMillis = startedAtEpochMillis,
    startedAtNanos = anchorNanos,
    status = runCatching { RunStatus.valueOf(status) }.getOrDefault(if (abortReason == null) RunStatus.COMPLETED else RunStatus.ABORTED),
    abortReason = abortReason?.let { reason -> runCatching { AbortReason.valueOf(reason) }.getOrNull() },
    tempoBpm = tempo,
    configJson = configJson,
)

private fun LegacyAttemptRow.segmentEntity(segmentIndex: Int, exerciseId: String, score: Score) = SegmentEntity(
    id = segmentId(id, segmentIndex),
    runId = id,
    segmentIndex = segmentIndex,
    exerciseId = exerciseId,
    scoreJson = keySightJson.encodeToString(Score.serializer(), score),
)
