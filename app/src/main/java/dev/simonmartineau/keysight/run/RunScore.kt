package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.score.Score

/**
 * The score of a run: a silent measure 0, then one measure per segment, on one tick line.
 *
 * Measure k of the result is segment k, so the layout engine engraves the count-in as a
 * measure of rest without knowing about runs, and the run's beat line and the score's ticks
 * coincide. Note ids are prefixed with the segment index so that they stay unique and can be
 * traced back to their segment. Every segment must share the meter, key and staves: a run is
 * read in one key on one set of staves.
 */
fun runScore(segments: List<Score>): Score {
    require(segments.isNotEmpty()) { "a run needs a segment" }
    val first = segments.first()
    segments.forEachIndexed { index, segment ->
        require(segment.measureCount == 1) { "segment ${index + 1} has ${segment.measureCount} measures, a segment is one" }
        require(segment.timeSignature == first.timeSignature) { "segment ${index + 1} is in ${segment.timeSignature}, the run in ${first.timeSignature}" }
        require(segment.keySignature == first.keySignature) { "segment ${index + 1} is in ${segment.keySignature}, the run in ${first.keySignature}" }
        require(segment.staves == first.staves) { "segment ${index + 1} is on ${segment.staves}, the run on ${first.staves}" }
    }
    val measure = first.timeSignature.ticksPerMeasure
    return Score(
        timeSignature = first.timeSignature,
        keySignature = first.keySignature,
        staves = first.staves,
        measureCount = segments.size + 1,
        notes = segments.flatMapIndexed { index, segment ->
            val k = index + 1
            segment.notes.map { note -> note.copy(id = "$k:${note.id}", onset = note.onset + measure * k) }
        },
    )
}

/** The first [measureCount] measures of this score: what a stopped run performed. A note crossing the cut is dropped. */
fun Score.firstMeasures(measureCount: Int): Score {
    require(measureCount in 1..this.measureCount) { "cannot keep $measureCount of ${this.measureCount} measures" }
    if (measureCount == this.measureCount) return this
    val end = timeSignature.ticksPerMeasure * measureCount
    return copy(measureCount = measureCount, notes = notes.filter { it.end <= end })
}

/**
 * Measure [measure] of this score on its own: one measure starting at tick 0, its notes' ids
 * without [idPrefix] when they carry it. The inverse of [runScore] for one segment, and what
 * turns a stored multi-measure score into segments. A note crossing the measure's end is a
 * malformed input and is refused.
 */
fun Score.measureAsScore(measure: Int, idPrefix: String = ""): Score {
    val start = measureStart(measure)
    return copy(
        measureCount = 1,
        notes = notesInMeasure(measure).map { note ->
            note.copy(id = note.id.removePrefix(idPrefix), onset = note.onset - start)
        },
    )
}
