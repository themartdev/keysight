package dev.simonmartineau.keysight.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.beatsLabel
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.settings.RunChoices

/**
 * The run's setup, a sheet over the stage: the score behind it is the run waiting to start
 * and regenerates as the choices change. Every row is a list, so a new mode or a new
 * choice is one more entry. [level] is the controller's, shown and not chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupSheet(config: RunConfig, content: ContentConfig, level: String?, actions: PracticeActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SetupContent(config, content, level, actions, Modifier.verticalScroll(rememberScrollState()))
    }
}

@Composable
fun SetupContent(config: RunConfig, content: ContentConfig, level: String?, actions: PracticeActions, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
        SectionLabel("Mode")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            VisibilityMode.entries.forEach { mode ->
                FilterChip(selected = mode == config.mode, onClick = { actions.setMode(mode) }, label = { Text(mode.label) })
            }
        }
        SectionLabel("Lookahead, beats")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            RunChoices.LOOKAHEAD_BEATS.forEach { beats ->
                FilterChip(
                    selected = beats == config.lookaheadBeats,
                    onClick = { actions.setLookaheadBeats(beats) },
                    enabled = config.mode == VisibilityMode.FLASH,
                    label = { Text(beats.beatsLabel()) },
                )
            }
        }
        SectionLabel("Length")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            RunChoices.SEGMENT_COUNTS.forEach { count ->
                FilterChip(
                    selected = count == config.segmentCount,
                    onClick = { actions.setSegmentCount(count) },
                    label = { Text(count?.let(::barsLabel) ?: "Open") },
                )
            }
        }
        SectionLabel("Music")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ChoiceMenu(content.keySignature.majorName, KeySignature.ALL, { it.majorName }, actions.setKey)
            ChoiceMenu(content.hands.label, Hands.entries, { it.label }, actions.setHands)
        }
        if (content.hands == Hands.BOTH) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Accompaniment.entries.forEach { accompaniment ->
                    FilterChip(
                        selected = accompaniment == content.accompaniment,
                        onClick = { actions.setAccompaniment(accompaniment) },
                        label = { Text(accompaniment.label) },
                    )
                }
            }
        }
        level?.let { line ->
            Spacer(Modifier.height(6.dp))
            Text("Level: $line", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel("Tempo")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ChoiceMenu(tempoLabel(config.tempoBpm), RunChoices.TEMPOS_BPM, ::tempoLabel, actions.setTempo)
            Spacer(Modifier.weight(1f))
            Text("Click while playing", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = config.metronome == MetronomeMode.THROUGHOUT,
                onCheckedChange = { actions.setMetronome(if (it) MetronomeMode.THROUGHOUT else MetronomeMode.COUNT_IN_ONLY) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

/** An outlined button showing [current] that opens a menu of [choices]. */
@Composable
private fun <T> ChoiceMenu(current: String, choices: List<T>, label: (T) -> String, onChoice: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(current)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(label(choice)) },
                    onClick = {
                        open = false
                        onChoice(choice)
                    },
                )
            }
        }
    }
}
