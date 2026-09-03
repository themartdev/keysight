package dev.simonmartineau.keysight.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.simonmartineau.keysight.ui.theme.CornerShape
import dev.simonmartineau.keysight.ui.theme.Metrics
import dev.simonmartineau.keysight.ui.theme.SectionHeading
import dev.simonmartineau.keysight.ui.theme.palette
import dev.simonmartineau.keysight.ui.theme.type

/**
 * A parameter's choices, one row each, on a paper panel over the page: the section heading
 * names the parameter, the selected row is marked the way a preset is, and a tap picks and
 * closes. No Material sheet: a panel with a hairline is all the separation it needs.
 */
@Composable
fun <T> PickerDialog(
    title: String,
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    description: ((T) -> String?)? = null,
) {
    val palette = MaterialTheme.palette
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(360.dp)
                .heightIn(max = 340.dp)
                .clip(CornerShape)
                .background(palette.paper)
                .border(1.dp, palette.outlineWeak, CornerShape)
                .padding(Metrics.PanePadding),
        ) {
            SectionHeading(title)
            Spacer(Modifier.height(Metrics.GapTight))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    PresetRow(
                        title = label(choice),
                        description = description?.invoke(choice),
                        selected = choice == selected,
                        onClick = { onPick(choice); onDismiss() },
                    )
                }
            }
        }
    }
}

/**
 * A row of a preset list: the title, a one-line description under it when there is one, a
 * dim paper fill and a 2dp accent edge on the left when selected. A row that is not
 * [enabled] is faint and does nothing; it names what is coming.
 */
@Composable
fun PresetRow(title: String, description: String?, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val palette = MaterialTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(CornerShape)
            .background(if (selected) palette.paperDim else palette.paper)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .height(IntrinsicHeight),
    ) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(if (selected) palette.inkAccent else palette.paper),
        )
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Text(
                title,
                style = MaterialTheme.type.body,
                color = if (enabled) palette.ink else palette.onSurfaceFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.type.meta,
                    color = if (enabled) palette.onSurfaceMuted else palette.onSurfaceFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val IntrinsicHeight = androidx.compose.foundation.layout.IntrinsicSize.Min
