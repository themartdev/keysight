package dev.simonmartineau.keysight.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One dot per beat of the measure, the [lit] one on the beat the metronome is clicking, or
 * none when [lit] is out of range. It does not tick: the stage that owns the frame loop tells
 * it which beat it is on, derived from the same timeline as the metronome. Every dot has the
 * lit dot's room, so the row does not shift as the beat moves.
 */
@Composable
fun BeatIndicator(beatsPerMeasure: Int, lit: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(beatsPerMeasure) { index ->
            val active = index == lit
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
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
}
