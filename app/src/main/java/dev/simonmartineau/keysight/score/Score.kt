package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/**
 * The notes of one exercise, plus the context needed to read them.
 *
 * A score is valid by construction: ids are unique, every note is on one of the [staves],
 * nothing runs past the last measure, and within one voice notes either follow each other or
 * start together as a chord.
 *
 * [staves] defaults to a single treble staff, which is what every score before the grand staff
 * existed was; stored snapshots from then carry no `staves` and still read as they were.
 */
@Serializable
data class Score(
    val timeSignature: TimeSignature,
    val keySignature: KeySignature,
    val staves: List<Staff> = listOf(Staff(Clef.TREBLE)),
    val measureCount: Int,
    val notes: List<ScoreNote>,
) {
    init {
        require(measureCount > 0) { "measureCount must be positive" }
        require(staves.isNotEmpty()) { "a score needs at least one staff" }

        val duplicateIds = notes.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "duplicate note ids: $duplicateIds" }

        val end = totalTicks
        notes.forEach { note ->
            require(note.end <= end) { "note ${note.id} ends at ${note.end}, past $end" }
            require(note.staff in staves.indices) { "note ${note.id} is on staff ${note.staff} of ${staves.size}" }
        }

        notes.groupBy { it.voice }.forEach { (voice, voiceNotes) ->
            voiceNotes.sortedBy { it.onset }.zipWithNext().forEach { (earlier, later) ->
                require(earlier.onset == later.onset || earlier.end <= later.onset) {
                    "notes ${earlier.id} and ${later.id} overlap in voice $voice"
                }
            }
        }
    }

    val totalTicks: Ticks get() = timeSignature.ticksPerMeasure * measureCount

    /** Total notated length in beats of [TimeSignature.beatUnit]. */
    val totalBeats: Double get() = (timeSignature.beatsPerMeasure * measureCount).toDouble()

    /** Notes in the order a performer sounds them; ties within a chord broken by pitch. */
    val notesInPerformanceOrder: List<ScoreNote>
        get() = notes.sortedWith(compareBy({ it.onset }, { it.pitch }, { it.voice }))

    /** The same notes grouped into what sounds together, in performance order. */
    val chordsInPerformanceOrder: List<List<ScoreNote>>
        get() = notesInPerformanceOrder.groupBy { it.onset }.values.toList()

    fun staffOf(note: ScoreNote): Staff = staves[note.staff]

    fun notesOn(staff: Int): List<ScoreNote> = notes.filter { it.staff == staff }

    /** The measure [ticks] falls in, 0-based; the score's end counts as the last measure. */
    fun measureOf(ticks: Ticks): Int =
        (ticks.value / timeSignature.ticksPerMeasure.value).coerceIn(0, measureCount - 1)

    fun measureStart(measure: Int): Ticks {
        require(measure in 0 until measureCount) { "no measure $measure in $measureCount" }
        return timeSignature.ticksPerMeasure * measure
    }

    fun notesInMeasure(measure: Int): List<ScoreNote> {
        val start = measureStart(measure)
        val end = start + timeSignature.ticksPerMeasure
        return notes.filter { it.onset >= start && it.onset < end }
    }
}
