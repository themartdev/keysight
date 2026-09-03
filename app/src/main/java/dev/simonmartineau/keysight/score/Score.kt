package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/**
 * The notes of one exercise, plus the context needed to read them.
 *
 * A score is valid by construction: ids are unique, nothing runs past the last measure, and
 * within one voice notes either follow each other or start together as a chord.
 */
@Serializable
data class Score(
    val timeSignature: TimeSignature,
    val clef: Clef,
    val keySignature: KeySignature,
    val measureCount: Int,
    val notes: List<ScoreNote>,
) {
    init {
        require(measureCount > 0) { "measureCount must be positive" }

        val duplicateIds = notes.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "duplicate note ids: $duplicateIds" }

        val end = totalTicks
        notes.forEach { note ->
            require(note.end <= end) { "note ${note.id} ends at ${note.end}, past $end" }
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
}
