package dev.simonmartineau.keysight.session

import dev.simonmartineau.keysight.exercise.ExerciseTag

/**
 * The compact end-of-session readout.
 *
 * Practice feedback stays deliberately small: a handful of numbers plus what went well and what
 * did not, rather than an analytics dashboard.
 */
data class SessionSummary(
    val sessionId: String,
    val exerciseCount: Int,
    val pitchAccuracy: Double,
    val averagePreviewBeats: Double,
    val strongTags: List<ExerciseTag>,
    val weakTags: List<ExerciseTag>,
)
