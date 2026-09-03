package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RhythmAnalysisTest {

    private val notes = Fixtures.cdef.notes
    private val beats = notes.associate { it.id to Fixtures.cdef.timeSignature.beatsOf(it.onset) }

    private fun played(midi: Int, beat: Double) = PlayedNote(Pitch(midi), beat, beat + 0.5, velocity = 80, onsetNanos = 0L)

    private fun correct(index: Int, beat: Double) = NoteOutcome.Correct(notes[index], played(notes[index].pitch.midiNoteNumber, beat))

    private fun analyse(vararg outcomes: NoteOutcome, phase: Double = 0.0) = RhythmAnalysis.analyse(outcomes.toList(), beats, phase)

    @Test
    fun `notes within the tolerance are on time, the rest early or late`() {
        val result = analyse(correct(0, 0.0), correct(1, 1.125), correct(2, 1.8), correct(3, 3.2))

        assertEquals(
            listOf(TimingJudgement.ON_TIME, TimingJudgement.ON_TIME, TimingJudgement.EARLY, TimingJudgement.LATE),
            result.timings.map { it.judgement },
        )
        assertEquals(0.5, result.accuracy)
        assertEquals(1, result.earlyCount)
        assertEquals(1, result.lateCount)
        assertEquals(-0.2, result.timings[2].errorBeats, 1e-9)
    }

    @Test
    fun `errors are measured after the phase`() {
        val result = analyse(correct(0, 0.2), correct(1, 1.2), correct(2, 2.2), correct(3, 3.2), phase = 0.2)

        assertEquals(1.0, result.accuracy)
        assertEquals(0.2, result.phaseBeats)
        result.timings.forEach { assertEquals(0.0, it.errorBeats, 1e-9) }
    }

    @Test
    fun `wrong pitches are timed, missing and extra notes are not`() {
        val result = analyse(
            NoteOutcome.WrongPitch(notes[0], played(61, 0.0)),
            NoteOutcome.Missing(notes[1]),
            NoteOutcome.Extra(played(70, 1.5)),
            correct(2, 2.0),
            correct(3, 3.0),
        )

        assertEquals(listOf("n1", "n3", "n4"), result.timings.map { it.noteId })
        assertEquals(3, result.matchedCount)
        assertEquals(1.0, result.accuracy)
    }

    @Test
    fun `a steady performance five percent fast has that tempo ratio`() {
        val result = analyse(correct(0, 0.0), correct(1, 1 / 1.05), correct(2, 2 / 1.05), correct(3, 3 / 1.05))

        assertEquals(1.05, result.tempoRatio!!, 1e-9)
    }

    @Test
    fun `the tempo ratio needs two distinct beats`() {
        assertNull(analyse(correct(0, 0.0)).tempoRatio)
        assertNull(analyse().tempoRatio)
        assertEquals(1.0, analyse(correct(0, 0.0), correct(3, 3.0)).tempoRatio!!, 1e-9)
    }

    @Test
    fun `a gap longer than notated is a pause, across a missing note too`() {
        val result = analyse(correct(0, 0.0), NoteOutcome.Missing(notes[1]), correct(2, 2.7), correct(3, 3.0))

        assertEquals(listOf(Pause("n3", 0.7)), result.pauses.map { it.copy(extraBeats = Math.round(it.extraBeats * 1000) / 1000.0) })
        assertEquals(Continuity.HESITANT, result.continuity)
    }

    @Test
    fun `a rest between two notes is notated silence, not a pause`() {
        val result = analyse(correct(0, 0.0), correct(2, 2.0), correct(3, 3.0))

        assertEquals(emptyList(), result.pauses)
        assertEquals(Continuity.GOOD, result.continuity)
        assertEquals(1.0, result.tempoRatio!!, 1e-9)
        assertEquals(listOf(Pause("n3", 0.6)), analyse(correct(0, 0.0), correct(2, 2.6), correct(3, 3.6)).pauses.map { it.copy(extraBeats = Math.round(it.extraBeats * 1000) / 1000.0) })
    }

    @Test
    fun `continuity is good when every note holds the pulse`() {
        assertEquals(Continuity.GOOD, analyse(correct(0, 0.1), correct(1, 0.9), correct(2, 2.3), correct(3, 3.0)).continuity)
    }

    @Test
    fun `one note off the pulse is hesitant`() {
        assertEquals(Continuity.HESITANT, analyse(correct(0, 0.0), correct(1, 1.0), correct(2, 2.7), correct(3, 3.7)).continuity)
    }

    @Test
    fun `two pauses or most notes off the pulse is lost`() {
        assertEquals(Continuity.LOST, analyse(correct(0, 0.0), correct(1, 1.6), correct(2, 3.2), correct(3, 4.2)).continuity)
        assertEquals(Continuity.LOST, analyse(correct(0, 0.0), correct(1, 1.6), correct(2, 2.6), correct(3, 3.6)).continuity)
        assertEquals(Continuity.HESITANT, analyse(correct(0, 0.0), correct(1, 1.0), correct(2, 2.6), correct(3, 3.6)).continuity)
        assertEquals(Continuity.LOST, analyse(NoteOutcome.Missing(notes[0])).continuity)
    }

    @Test
    fun `nothing matched scores zero`() {
        val result = analyse(NoteOutcome.Missing(notes[0]), NoteOutcome.Extra(played(60, 0.0)))

        assertEquals(0, result.matchedCount)
        assertEquals(0.0, result.accuracy)
        assertEquals(emptyList(), result.pauses)
    }
}
