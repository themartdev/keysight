package dev.simonmartineau.keysight.score

/**
 * The notes of one exercise, plus the context needed to read them.
 *
 * @param keySignatureFifths position on the circle of fifths: 0 is C major, positive counts
 *   sharps, negative counts flats. Matches the MusicXML `fifths` element.
 */
data class Score(
    val timeSignature: TimeSignature,
    val clef: Clef,
    val keySignatureFifths: Int,
    val measureCount: Int,
    val notes: List<ScoreNote>,
) {
    init {
        require(measureCount > 0) { "measureCount must be positive" }
        require(keySignatureFifths in -7..7) { "keySignatureFifths must be within -7..7" }
        val end = totalBeats
        require(notes.all { it.endBeat <= end + BEAT_EPSILON }) {
            "every note must end within the score's $end beats"
        }
    }

    /** Total notated length, in beats of [TimeSignature.beatUnit]. */
    val totalBeats: Double get() = (measureCount * timeSignature.beatsPerMeasure).toDouble()

    /** Notes in the order a performer sounds them, ties broken by pitch for stable output. */
    val notesInPerformanceOrder: List<ScoreNote>
        get() = notes.sortedWith(compareBy({ it.onsetBeat }, { it.pitch.midiNoteNumber }))

    private companion object {
        /** Beat positions are doubles; tolerate float noise when checking bounds. */
        const val BEAT_EPSILON = 1e-9
    }
}
