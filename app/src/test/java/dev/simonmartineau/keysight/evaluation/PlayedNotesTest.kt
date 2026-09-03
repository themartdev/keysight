package dev.simonmartineau.keysight.evaluation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.SECOND_NANOS
import dev.simonmartineau.keysight.midi.MidiEvent
import dev.simonmartineau.keysight.score.Pitch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayedNotesTest {

    private val timeline = Fixtures.slowTimeline
    private val startedAt = 100 * SECOND_NANOS
    private val performanceStart = startedAt + 4 * SECOND_NANOS
    private val c4 = Pitch.C4
    private val d4 = Pitch(62)

    private fun extract(vararg events: MidiEvent, grace: Double = 0.5) =
        PlayedNotes.extract(events.toList(), timeline, startedAt, grace)

    @Test
    fun `a note-on and its note-off become one note on the beat line`() {
        val notes = extract(
            MidiEvent.noteOn(performanceStart + SECOND_NANOS, c4, 90),
            MidiEvent.noteOff(performanceStart + SECOND_NANOS + SECOND_NANOS / 2, c4),
        )

        assertEquals(listOf(PlayedNote(c4, 1.0, 1.5, 90, performanceStart + SECOND_NANOS)), notes)
    }

    @Test
    fun `a key still down at the end has no release`() {
        val notes = extract(MidiEvent.noteOn(performanceStart, c4, 90))

        assertNull(notes.single().releaseBeat)
    }

    @Test
    fun `a note-off for a key that is not down is ignored`() {
        assertEquals(emptyList(), extract(MidiEvent.noteOff(performanceStart, c4)))
    }

    @Test
    fun `re-striking a held key closes the first note`() {
        val notes = extract(
            MidiEvent.noteOn(performanceStart, c4, 90),
            MidiEvent.noteOn(performanceStart + SECOND_NANOS, c4, 80),
            MidiEvent.noteOff(performanceStart + 2 * SECOND_NANOS, c4),
        )

        assertEquals(listOf(0.0 to 1.0, 1.0 to 2.0), notes.map { it.onsetBeat to it.releaseBeat })
    }

    @Test
    fun `the same pitch on different channels is two keys`() {
        val notes = extract(
            MidiEvent.noteOn(performanceStart, c4, 90, channel = 0),
            MidiEvent.noteOn(performanceStart, c4, 90, channel = 1),
            MidiEvent.noteOff(performanceStart + SECOND_NANOS, c4, channel = 1),
        )

        assertEquals(listOf(null, 1.0), notes.map { it.releaseBeat })
    }

    @Test
    fun `pedal and other messages do not affect pairing`() {
        val notes = extract(
            MidiEvent.noteOn(performanceStart, c4, 90),
            MidiEvent.controlChange(performanceStart + 1, controller = 64, value = 127),
            MidiEvent(performanceStart + 2, 0xE0, 0, 64),
            MidiEvent.noteOff(performanceStart + SECOND_NANOS, c4),
        )

        assertEquals(1, notes.size)
        assertEquals(1.0, notes.single().releaseBeat)
    }

    @Test
    fun `notes played during the count-in are not performance`() {
        val notes = extract(
            MidiEvent.noteOn(startedAt + SECOND_NANOS, c4, 90),
            MidiEvent.noteOff(startedAt + 2 * SECOND_NANOS, c4),
            MidiEvent.noteOn(performanceStart, d4, 90),
        )

        assertEquals(listOf(d4), notes.map { it.pitch })
    }

    @Test
    fun `an anticipated first note inside the grace window counts`() {
        val slightlyEarly = performanceStart - SECOND_NANOS / 2
        val tooEarly = performanceStart - SECOND_NANOS / 2 - 1

        assertEquals(-0.5, extract(MidiEvent.noteOn(slightlyEarly, c4, 90)).single().onsetBeat)
        assertEquals(emptyList(), extract(MidiEvent.noteOn(tooEarly, c4, 90)))
        assertEquals(emptyList(), extract(MidiEvent.noteOn(slightlyEarly, c4, 90), grace = 0.0))
    }

    @Test
    fun `notes in the capture tail count and notes after it do not`() {
        val captureEnd = startedAt + 9 * SECOND_NANOS

        assertEquals(5.0, extract(MidiEvent.noteOn(captureEnd, c4, 90)).single().onsetBeat)
        assertEquals(emptyList(), extract(MidiEvent.noteOn(captureEnd + 1, c4, 90)))
    }

    @Test
    fun `events are ordered by time whatever order they arrived in`() {
        val notes = extract(
            MidiEvent.noteOn(performanceStart + SECOND_NANOS, d4, 90),
            MidiEvent.noteOn(performanceStart, c4, 90),
        )

        assertEquals(listOf(c4, d4), notes.map { it.pitch })
    }
}
