package dev.simonmartineau.keysight.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.midi.MidiConnection

/**
 * The keyboard: a dot in the connection's colour and a word beside it. [compact] is the
 * strip's version, the device's name cut to one short line; the settings page has the room
 * for the whole message.
 */
@Composable
fun MidiStatus(connection: MidiConnection, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            Modifier
                .size(10.dp)
                .background(connectionColor(connection), CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (compact) connectionWord(connection) else connectionMessage(connection),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (compact) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            modifier = if (compact) Modifier.widthIn(max = 160.dp) else Modifier,
        )
    }
}

@Composable
private fun connectionColor(connection: MidiConnection): Color = when (connection) {
    MidiConnection.NoDevice -> MaterialTheme.colorScheme.outline
    is MidiConnection.Connecting -> MaterialTheme.colorScheme.secondary
    is MidiConnection.Connected -> MaterialTheme.colorScheme.primary
    is MidiConnection.Failed -> MaterialTheme.colorScheme.error
}

/** The connection in a word or the device's name, for the strip. */
fun connectionWord(connection: MidiConnection): String = when (connection) {
    MidiConnection.NoDevice -> "No keyboard"
    is MidiConnection.Connecting -> "Connecting"
    is MidiConnection.Connected -> connection.deviceName
    is MidiConnection.Failed -> "Keyboard failed"
}

/** The connection in full, for the settings page. */
fun connectionMessage(connection: MidiConnection): String = when (connection) {
    MidiConnection.NoDevice -> "Connect a MIDI keyboard"
    is MidiConnection.Connecting -> "Connecting to ${connection.deviceName}"
    is MidiConnection.Connected -> connection.deviceName
    is MidiConnection.Failed -> "${connection.deviceName}: ${connection.message}"
}
