package dev.simonmartineau.keysight.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.timing.AttemptTimeline
import kotlin.math.floor

/**
 * One dot per beat of the measure, lit on the current count-in beat.
 *
 * It does not tick. Every frame it reads the frame time, which is on the same
 * `System.nanoTime` base as the attempt clock, and derives the beat from the timeline, so
 * it can never drift from the metronome.
 */
@Composable
fun BeatIndicator(timeline: AttemptTimeline, startedAtNanos: Long, modifier: Modifier = Modifier) {
    var beat by remember(startedAtNanos) { mutableIntStateOf(-1) }
    LaunchedEffect(startedAtNanos) {
        while (true) {
            withFrameNanos { frameNanos ->
                beat = floor(timeline.beatAtNanos(frameNanos - startedAtNanos)).toInt()
            }
        }
    }
    val beatsPerMeasure = timeline.timeSignature.beatsPerMeasure
    val lit = if (beat >= 0 && beat < timeline.countInBeats) beat % beatsPerMeasure else -1
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(beatsPerMeasure) { index ->
            val active = index == lit
            Box(
                Modifier
                    .size(if (active) 18.dp else 14.dp)
                    .background(
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}
