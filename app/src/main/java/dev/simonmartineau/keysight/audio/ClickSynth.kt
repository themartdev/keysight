package dev.simonmartineau.keysight.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

/** Renders the metronome's click samples. Pure, so the shape of a click is a unit test. */
object ClickSynth {

    /** The sound of one click: a sine burst that decays to silence over [durationMs]. */
    data class Voice(val frequencyHz: Double, val durationMs: Double, val amplitude: Double) {
        init {
            require(frequencyHz > 0.0 && durationMs > 0.0) { "a click needs a frequency and a duration" }
            require(amplitude in 0.0..1.0) { "amplitude must be within 0..1" }
        }
    }

    /** The downbeat: higher and louder. */
    val ACCENT = Voice(frequencyHz = 1320.0, durationMs = 30.0, amplitude = 0.9)

    val BEAT = Voice(frequencyHz = 880.0, durationMs = 30.0, amplitude = 0.6)

    /** Length of the linear fade-in that keeps the attack from thumping. */
    const val FADE_IN_MS = 1.0

    /** Where the exponential decay has fallen to, relative to full amplitude, at [Voice.durationMs]. */
    private const val DECAY_FLOOR = 0.01

    fun render(sampleRate: Int, voice: Voice): ShortArray {
        require(sampleRate > 0) { "sampleRate must be positive" }
        val frames = (voice.durationMs * sampleRate / 1000.0).roundToInt()
        val fadeInFrames = (FADE_IN_MS * sampleRate / 1000.0).roundToInt().coerceAtLeast(1)
        val decayPerSecond = -ln(DECAY_FLOOR) / (voice.durationMs / 1000.0)
        return ShortArray(frames) { frame ->
            val t = frame.toDouble() / sampleRate
            val fadeIn = ((frame + 1).toDouble() / fadeInFrames).coerceAtMost(1.0)
            val envelope = exp(-decayPerSecond * t) * fadeIn
            val sample = voice.amplitude * envelope * sin(2.0 * PI * voice.frequencyHz * t)
            (sample * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
