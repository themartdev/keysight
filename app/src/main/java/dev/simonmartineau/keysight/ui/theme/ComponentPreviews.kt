package dev.simonmartineau.keysight.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Every component once, on the ground of its theme, so the design system can be checked by eye. */
@Composable
private fun OnGround(dark: Boolean = false, content: @Composable () -> Unit) {
    KeySightTheme(darkTheme = dark) {
        Column(
            Modifier
                .background(MaterialTheme.palette.ground)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(Metrics.GapControls),
        ) { content() }
    }
}

@Preview(name = "PrimaryButton")
@Composable
private fun PrimaryButtonPreview() = OnGround {
    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.GapControls)) {
        PrimaryButton("Start", onClick = {})
        PrimaryButton("Start", onClick = {}, enabled = false)
    }
}

@Preview(name = "PrimaryButton dark")
@Composable
private fun PrimaryButtonDarkPreview() = OnGround(dark = true) {
    PrimaryButton("Start", onClick = {})
}

@Preview(name = "QuietButton")
@Composable
private fun QuietButtonPreview() = OnGround {
    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.GapControls)) {
        QuietButton("Change settings", onClick = {})
        QuietButton("Stop", onClick = {}, enabled = false)
    }
}

@Preview(name = "Chip")
@Composable
private fun ChipPreview() = OnGround {
    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.GapTight)) {
        Chip("Flash", onClick = {})
        Chip("Open score", onClick = {}, selected = true)
        Chip("Hanon, scales, chords", onClick = {}, available = false)
    }
}

@Preview(name = "SectionHeading", widthDp = 420)
@Composable
private fun SectionHeadingPreview() = OnGround {
    SectionHeading("Pick up where you left off")
    SectionHeading("Recent sessions", trailing = { TextAction("All history", onClick = {}) })
}

@Preview(name = "DoubleRule", widthDp = 420)
@Composable
private fun DoubleRulePreview() = OnGround {
    Text("Above", style = MaterialTheme.type.body)
    DoubleRule()
    Text("Below", style = MaterialTheme.type.body)
}

@Preview(name = "StatNumber")
@Composable
private fun StatNumberPreview() = OnGround {
    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.GapPanes)) {
        StatNumber("Pitch %", "91")
        StatNumber("Rhythm %", "84")
        StatNumber("Bars, 7 days", "112")
    }
}

@Preview(name = "SessionRow", widthDp = 760)
@Composable
private fun SessionRowPreview() = OnGround {
    Column {
        SessionRowHeader()
        SessionRow("Today, 18:42", "Read ahead · C major · right hand", "3 runs", "91 · 84", onClick = {})
        SessionRow("Yesterday", "Flash 2 beats · G major · both hands", "1 run", "78 · 90", onClick = {}, expanded = true)
    }
}

@Preview(name = "Sparkbars")
@Composable
private fun SparkbarsPreview() = OnGround {
    Sparkbars(listOf(12, 0, 24, 40, 0, 8, 32))
    Sparkbars(listOf(3, 9, 0, 0, 14, 22, 18, 30, 0, 12, 26, 20, 0, 35), height = 56.dp)
}

@Preview(name = "ParamGrid", widthDp = 480)
@Composable
private fun ParamGridPreview() = OnGround {
    ParamGrid(
        listOf(
            Param("Key", "C major") {},
            Param("Hands", "Right hand") {},
            Param("Length", "8 bars") {},
            Param("Tempo", "72 bpm") {},
            Param("Click", "Count-in only") {},
            Param("Lookahead", "4 beats") {},
        ),
    )
}

@Preview(name = "Switch")
@Composable
private fun SwitchPreview() = OnGround {
    var on by remember { mutableStateOf(true) }
    Row(horizontalArrangement = Arrangement.spacedBy(Metrics.GapControls)) {
        Switch(checked = on, onCheckedChange = { on = it })
        Switch(checked = false, onCheckedChange = {})
    }
}

@Preview(name = "Type scale", widthDp = 420)
@Composable
private fun TypeScalePreview() = OnGround {
    val type = MaterialTheme.type
    Text("Good evening", style = type.screenTitle)
    Text("Read ahead", style = type.paneTitle)
    Text("Read ahead · C major · right hand", style = type.lead)
    Text("91", style = type.numeral)
    Text("8 bars at 72 bpm, count-in only. Up to thirds, quarter notes.", style = type.body, modifier = Modifier.width(360.dp))
    Text("Last run: yesterday, 91% pitch", style = type.meta, color = MaterialTheme.palette.onSurfaceMuted)
    Text("bars per day", style = type.micro, color = MaterialTheme.palette.onSurfaceFaint)
    Text("SECTION LABEL", style = type.label, color = MaterialTheme.palette.onSurfaceFaint)
}
