package dev.simonmartineau.keysight.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.ui.theme.palette
import dev.simonmartineau.keysight.ui.theme.type

/**
 * The keyboard: a dot and a line of meta. The dot is the accent while a keyboard is talking
 * and a grey otherwise; it never turns a judgement colour, since a keyboard is not judged.
 */
@Composable
fun MidiStatus(connection: MidiConnection, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (connection is MidiConnection.Connected) palette.inkAccent else palette.outline, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            connectionLabel(connection),
            style = MaterialTheme.type.meta,
            color = palette.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The connection in a line: the device's name, or what to do about there being none. */
fun connectionLabel(connection: MidiConnection): String = when (connection) {
    MidiConnection.NoDevice -> "Connect a MIDI keyboard"
    is MidiConnection.Connecting -> "Connecting to ${connection.deviceName}"
    is MidiConnection.Connected -> connection.deviceName
    is MidiConnection.Failed -> "${connection.deviceName}: ${connection.message}"
}
