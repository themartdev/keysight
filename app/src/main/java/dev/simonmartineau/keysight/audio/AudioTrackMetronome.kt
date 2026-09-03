package dev.simonmartineau.keysight.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import dev.simonmartineau.keysight.timing.RunTimeline
import dev.simonmartineau.keysight.timing.MonotonicClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The metronome on `AudioTrack`.
 *
 * The stream starts with [PRE_ROLL_MS] of silence, then the click track. Once the track is
 * playing, `AudioTrack.getTimestamp` says which frame was at the output at which
 * `System.nanoTime`, and from that the instant the first click frame reaches the output is
 * computed and returned as the run start. Output latency therefore never enters the
 * count-in: beat 0 is when the player hears it.
 *
 * If no timestamp arrives before the pre-roll runs out, the fallback anchor is the `play()`
 * instant plus the pre-roll plus whatever output latency the device reports, and the choice
 * is logged so device testing can see it.
 */
class AudioTrackMetronome(
    context: Context,
    private val clock: MonotonicClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Metronome {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private var playback: Playback? = null

    override suspend fun start(timeline: RunTimeline): MetronomeStart = withContext(ioDispatcher) {
        stop()
        val sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        val track = ClickTrack(sampleRate, timeline)
        val session = Playback(track)
        synchronized(this@AudioTrackMetronome) { playback = session }
        try {
            session.play()
            session.anchor()
        } catch (e: CancellationException) {
            stop()
            throw e
        }
    }

    override fun stop() {
        val session = synchronized(this) { playback.also { playback = null } } ?: return
        session.release()
    }

    private fun reportedLatencyNanos(): Long? =
        audioManager?.getProperty(PROPERTY_OUTPUT_LATENCY)?.toLongOrNull()?.let { it * 1_000_000L }

    private inner class Playback(private val clickTrack: ClickTrack) {

        private val sampleRate = clickTrack.sampleRate
        private val preRollFrames = (sampleRate.toLong() * PRE_ROLL_MS / 1000L).toInt()
        private val chunkFrames = (sampleRate / 100).coerceAtLeast(64)
        private val audioTrack: AudioTrack = buildTrack()

        @Volatile
        private var running = true
        private var writer: Thread? = null
        private var playCalledNanos = 0L

        /**
         * Fills as much of the buffer as a non-blocking write allows, starts playback, and hands
         * the stream to the writer thread. A [release] that lands during the prefill leaves the
         * track released, so nothing here may touch it once [running] is false.
         */
        fun play() {
            val prefill = audioTrack.bufferSizeInFrames.coerceAtLeast(chunkFrames)
            var frame = 0L
            val chunk = ShortArray(chunkFrames)
            while (running && frame < prefill) {
                val count = minOf(chunkFrames.toLong(), prefill - frame).toInt()
                clickTrack.render(frame - preRollFrames, chunk, count)
                val written = audioTrack.write(chunk, 0, count, AudioTrack.WRITE_NON_BLOCKING)
                if (written <= 0) break
                frame += written
                if (written < count) break
            }
            if (!running) return
            playCalledNanos = clock.nowNanos()
            audioTrack.play()
            writer = Thread({ writeLoop(frame) }, "metronome-writer").also { it.start() }
        }

        suspend fun anchor(): MetronomeStart {
            val timestamp = AudioTimestamp()
            val deadline = playCalledNanos + ANCHOR_WAIT_MS * 1_000_000L
            while (clock.nowNanos() < deadline) {
                if (running && audioTrack.getTimestamp(timestamp) && timestamp.nanoTime > 0L) {
                    val framesUntilFirstClick = preRollFrames - timestamp.framePosition
                    val start = timestamp.nanoTime + (framesUntilFirstClick * 1_000_000_000.0 / sampleRate).toLong()
                    Log.i(TAG, "anchored by timestamp: frame ${timestamp.framePosition} at ${timestamp.nanoTime}, beat 0 at $start")
                    return MetronomeStart(start, anchoredByTimestamp = true, reportedLatencyNanos = reportedLatencyNanos())
                }
                delay(ANCHOR_POLL_MS)
            }
            val latency = reportedLatencyNanos()
            val start = playCalledNanos + PRE_ROLL_MS * 1_000_000L + (latency ?: 0L)
            Log.w(TAG, "no audio timestamp within ${ANCHOR_WAIT_MS} ms; anchored by play() plus reported latency $latency ns")
            return MetronomeStart(start, anchoredByTimestamp = false, reportedLatencyNanos = latency)
        }

        fun release() {
            running = false
            writer?.join(500)
            runCatching {
                if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    Log.i(TAG, "stopping; underruns: ${audioTrack.underrunCount}")
                    audioTrack.pause()
                    audioTrack.flush()
                }
            }
            audioTrack.release()
        }

        private fun writeLoop(firstFrame: Long) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val chunk = ShortArray(chunkFrames)
            var frame = firstFrame
            while (running) {
                clickTrack.render(frame - preRollFrames, chunk)
                val written = audioTrack.write(chunk, 0, chunkFrames, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    Log.e(TAG, "AudioTrack.write failed: $written")
                    return
                }
                frame += written
            }
        }

        private fun buildTrack(): AudioTrack {
            val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufferBytes = maxOf(minBytes * 4, sampleRate / 10 * 2)
            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        }
    }

    private companion object {
        const val TAG = "Metronome"
        const val PRE_ROLL_MS = 300L
        const val ANCHOR_WAIT_MS = 250L
        const val ANCHOR_POLL_MS = 2L

        /** Not a public constant, but `AudioManager.getProperty` answers it on most devices. */
        const val PROPERTY_OUTPUT_LATENCY = "android.media.property.OUTPUT_LATENCY"
    }
}
