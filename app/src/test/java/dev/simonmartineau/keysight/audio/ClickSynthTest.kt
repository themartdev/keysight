package dev.simonmartineau.keysight.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClickSynthTest {

    private val sampleRate = 48_000

    @Test
    fun `a click lasts its duration`() {
        assertEquals(1440, ClickSynth.render(sampleRate, ClickSynth.BEAT).size)
        assertEquals(1323, ClickSynth.render(44_100, ClickSynth.ACCENT).size)
    }

    @Test
    fun `the accent is louder than the beat and neither clips`() {
        val accentPeak = ClickSynth.render(sampleRate, ClickSynth.ACCENT).maxOf { abs(it.toInt()) }
        val beatPeak = ClickSynth.render(sampleRate, ClickSynth.BEAT).maxOf { abs(it.toInt()) }

        assertTrue(accentPeak > beatPeak)
        assertTrue(accentPeak <= Short.MAX_VALUE)
        assertTrue(accentPeak > Short.MAX_VALUE * 0.7, "peak $accentPeak is far below the requested amplitude")
    }

    @Test
    fun `the click fades in and decays to silence`() {
        val click = ClickSynth.render(sampleRate, ClickSynth.BEAT)

        assertEquals(0, click[0].toInt(), "starts at zero")
        val firstMs = click.take(48).maxOf { abs(it.toInt()) }
        val loudest = click.maxOf { abs(it.toInt()) }
        assertTrue(firstMs < loudest, "the attack ramps rather than starting at full level")
        val lastMs = click.takeLast(48).maxOf { abs(it.toInt()) }
        assertTrue(lastMs < loudest * 0.03, "tail $lastMs is not quiet next to peak $loudest")
    }
}
