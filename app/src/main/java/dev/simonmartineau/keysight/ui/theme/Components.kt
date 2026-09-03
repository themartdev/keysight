package dev.simonmartineau.keysight.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The components of `docs/design.md`, drawn from the palette and the type scale alone: no
 * Material elevation, no blurred shadow, no radius but 4dp (2dp on the switch's knob), and
 * every grey is ink at a step of the ramp. Separation is a hairline or space.
 */

/** The one radius. */
val CornerShape = RoundedCornerShape(4.dp)

/** The metrics of `docs/design.md`, in one place. */
object Metrics {
    val PagePaddingTop = 26.dp
    val PagePaddingSides = 32.dp
    val PagePaddingBottom = 28.dp
    val PanePadding = 24.dp
    val RailWidth = 80.dp
    val PresetPaneWidth = 272.dp

    /** Within a control group. */
    val GapTight = 9.dp

    /** Between controls. */
    val GapControls = 14.dp

    /** Between blocks. */
    val GapBlocks = 22.dp

    /** Between panes. */
    val GapPanes = 30.dp

    val TouchTarget = 48.dp
}

private const val PRESS_MILLIS = 120

/**
 * The letterpress slab: the one filled button of a screen. Behind it a solid block of ink,
 * offset by 3dp, drawn rather than shadowed; on press the button moves 2dp into the block and
 * the block shrinks to 1dp, so it reads as a key being struck. Disabled, the slab is lifted
 * off the page: no block, a weak fill and a faint label.
 */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = MaterialTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel by animateDpAsState(if (pressed && enabled) 2.dp else 0.dp, tween(PRESS_MILLIS), label = "press")
    val block = 3.dp - travel
    Box(modifier.padding(end = 3.dp, bottom = 3.dp)) {
        if (enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset { IntOffset(block.roundToPx(), block.roundToPx()) }
                    .background(palette.inkAt(0.16f), CornerShape),
            )
        }
        Box(
            Modifier
                .offset { IntOffset(travel.roundToPx(), travel.roundToPx()) }
                .clip(CornerShape)
                .background(if (enabled) palette.inkAccent else palette.outlineWeak)
                .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 30.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text.uppercase(),
                style = MaterialTheme.type.button,
                color = if (enabled) palette.onAccent else palette.onSurfaceFaint,
                maxLines = 1,
            )
        }
    }
}

/** The keycap: transparent with a 1dp outline, filled [Palette.paperDim] while pressed. Stop, Try again, Change settings. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = MaterialTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .clip(CornerShape)
            .background(if (pressed && enabled) palette.paperDim else Color.Transparent)
            .border(1.dp, if (enabled) palette.outline else palette.outlineWeak, CornerShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 30.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.type.buttonQuiet,
            color = if (enabled) palette.ink else palette.onSurfaceFaint,
            maxLines = 1,
        )
    }
}

/**
 * A keycap at chip size. [selected] is a [Palette.paperDim] fill, the border unchanged. A
 * chip that is not [available] yet has a dashed weak border and a faint label and does
 * nothing when tapped; it names what is coming, so it is not clickable.
 */
@Composable
fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    available: Boolean = true,
) {
    val palette = MaterialTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val filled = available && (selected || pressed)
    val border = if (available) {
        Modifier.border(1.dp, palette.outline, CornerShape)
    } else {
        Modifier.dashedBorder(1.dp, palette.outlineWeak)
    }
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clip(CornerShape)
            .background(if (filled) palette.paperDim else Color.Transparent)
            .then(border)
            .clickable(interaction, indication = null, enabled = available, role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.type.buttonQuiet.copy(fontSize = 11.sp),
            color = if (available) palette.ink else palette.onSurfaceFaint,
            maxLines = 1,
        )
    }
}

private fun Modifier.dashedBorder(width: Dp, color: Color): Modifier = drawBehind {
    val stroke = width.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(4.dp.toPx() - inset),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/**
 * The app's signature: a label, then a 1dp weak rule filling the width, 12dp apart. Every
 * section on every screen opens with one. [trailing] is a small action at the rule's end,
 * "All history" beside "Recent sessions".
 */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    val palette = MaterialTheme.palette
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.type.label, color = palette.onSurfaceFaint, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(palette.outlineWeak),
        )
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A small text action that sits at a heading's end: the label style, ink, no border. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.type.label,
        color = MaterialTheme.palette.ink,
        maxLines = 1,
        modifier = modifier
            .clip(CornerShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

/** A final barline laid flat: 1dp of ink at 28%, 1dp of air, 1dp at 13%. Horizontal only. */
@Composable
fun DoubleRule(modifier: Modifier = Modifier) {
    val palette = MaterialTheme.palette
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        val line = 1.dp.toPx()
        drawRect(palette.inkAt(0.28f), size = Size(size.width, line))
        drawRect(palette.inkAt(0.13f), topLeft = Offset(0f, 2 * line), size = Size(size.width, line))
    }
}

/** A 1dp hairline across, the separator between rows. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.palette.hairline))
}

/** A 1dp hairline down, the separation between panes. */
@Composable
fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).background(MaterialTheme.palette.hairline))
}

/** A caption in micro over a numeral. The caption carries the unit; the number is bare. */
@Composable
fun StatNumber(caption: String, value: String, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.palette
    Column(modifier) {
        Text(caption, style = MaterialTheme.type.micro, color = palette.onSurfaceFaint, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.type.numeral, color = palette.ink, maxLines = 1)
    }
}

/** The column widths of [SessionRow], shared with its header. */
private object SessionColumns {
    val When = 160.dp
    val Runs = 90.dp
    val Accuracy = 120.dp
    val Chevron = 16.dp
}

/** The header over a list of [SessionRow]s, in the label style. */
@Composable
fun SessionRowHeader(modifier: Modifier = Modifier) {
    val palette = MaterialTheme.palette
    val style = MaterialTheme.type.label
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("WHEN", style = style, color = palette.onSurfaceFaint, modifier = Modifier.width(SessionColumns.When))
            Text("WHAT", style = style, color = palette.onSurfaceFaint, modifier = Modifier.weight(1f))
            Text("RUNS", style = style, color = palette.onSurfaceFaint, modifier = Modifier.width(SessionColumns.Runs))
            Text("PITCH · RHYTHM", style = style, color = palette.onSurfaceFaint, modifier = Modifier.width(SessionColumns.Accuracy))
            Spacer(Modifier.width(SessionColumns.Chevron))
        }
        Hairline()
    }
}

/**
 * One session in a table: when, what, how many runs, "pitch · rhythm", a chevron; fixed
 * columns so the rows line up, a hairline under each, [Palette.paperDim] while pressed. The
 * two values are muted with tabular figures, so the column reads as a column.
 */
@Composable
fun SessionRow(
    whenText: String,
    what: String,
    runs: String,
    accuracy: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val palette = MaterialTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val body = MaterialTheme.type.body
    val value = body.copy(fontFeatureSettings = TABULAR_FIGURES)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (pressed) palette.paperDim else Color.Transparent)
                .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(whenText, style = body, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(SessionColumns.When))
            Text(what, style = body, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(runs, style = value, color = palette.onSurfaceMuted, maxLines = 1, modifier = Modifier.width(SessionColumns.Runs))
            Text(accuracy, style = value, color = palette.onSurfaceMuted, maxLines = 1, modifier = Modifier.width(SessionColumns.Accuracy))
            Chevron(down = expanded, color = palette.onSurfaceFaint)
        }
        Hairline()
    }
}

/** A 16dp chevron pointing right, or down when [down]: the row opens, or is open. */
@Composable
private fun Chevron(down: Boolean, color: Color) {
    Canvas(Modifier.size(SessionColumns.Chevron)) {
        val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        val path = Path()
        if (down) {
            path.moveTo(w * 0.25f, h * 0.38f)
            path.lineTo(w * 0.5f, h * 0.62f)
            path.lineTo(w * 0.75f, h * 0.38f)
        } else {
            path.moveTo(w * 0.38f, h * 0.25f)
            path.lineTo(w * 0.62f, h * 0.5f)
            path.lineTo(w * 0.38f, h * 0.75f)
        }
        drawPath(path, color, style = stroke)
    }
}

/**
 * One bar per day, oldest first, the most recent in the accent and the rest at [Palette.fill];
 * the tallest bar fills [height]. A day with nothing is not a short bar: it is a dash on the
 * baseline, so a gap in practice reads as a gap. The width is the bars' own, 5dp each and 5dp
 * apart.
 */
@Composable
fun Sparkbars(counts: List<Int>, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    val palette = MaterialTheme.palette
    val barWidth = 5.dp
    val gap = 5.dp
    val width = barWidth * counts.size + gap * (counts.size - 1).coerceAtLeast(0)
    Canvas(modifier.width(width).height(height)) {
        val bar = barWidth.toPx()
        val step = bar + gap.toPx()
        val radius = CornerRadius(2.dp.toPx())
        val dash = 2.dp.toPx()
        val minBar = 4.dp.toPx()
        val max = counts.maxOrNull()?.takeIf { it > 0 } ?: 1
        counts.forEachIndexed { index, count ->
            val x = index * step
            if (count <= 0) {
                drawRoundRect(palette.inkAt(0.32f), Offset(x, size.height - dash), Size(bar, dash), CornerRadius(dash / 2))
            } else {
                val h = (size.height * count / max).coerceAtLeast(minBar)
                val color = if (index == counts.lastIndex) palette.inkAccent else palette.fill
                drawRoundRect(color, Offset(x, size.height - h), Size(bar, h), radius)
            }
        }
    }
}

/** One cell of a [ParamGrid]: what the parameter is and what it is set to; tapping opens its picker. */
@Immutable
data class Param(val label: String, val value: String, val onClick: () -> Unit)

/**
 * The parameters as a grid of paper cells with hairline gutters, the whole with the one
 * radius and a hairline around it so it stands on paper as well as on the ground. Each cell is
 * a button: a micro label over the value. Cells fill the last row's remaining columns with air.
 */
@Composable
fun ParamGrid(params: List<Param>, modifier: Modifier = Modifier, columns: Int = 3) {
    val palette = MaterialTheme.palette
    Column(
        modifier
            .clip(CornerShape)
            .background(palette.hairline)
            .padding(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        params.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { param -> ParamCell(param, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun ParamCell(param: Param, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier
            .background(if (pressed) palette.paperDim else palette.paper)
            .clickable(interaction, indication = null, role = Role.Button, onClick = param.onClick)
            .defaultMinSize(minHeight = Metrics.TouchTarget)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(param.label, style = MaterialTheme.type.micro, color = palette.onSurfaceFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(param.value, style = MaterialTheme.type.paramValue, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A square switch: a 44 by 26 track with the one radius, a 20dp knob with a 1dp ring so the
 * off state stays visible on the light track. The track is ink at 30% off and the accent on.
 */
@Composable
fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = MaterialTheme.palette
    val trackWidth = 44.dp
    val trackHeight = 26.dp
    val knob = 20.dp
    val inset = (trackHeight - knob) / 2
    val travel by animateDpAsState(if (checked) trackWidth - knob - inset else inset, tween(PRESS_MILLIS), label = "knob")
    val track by animateColorAsState(if (checked) palette.inkAccent else palette.inkAt(0.30f), tween(PRESS_MILLIS), label = "track")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .size(trackWidth, trackHeight)
            .background(track, CornerShape),
    ) {
        Box(
            Modifier
                .offset { IntOffset(travel.roundToPx(), inset.roundToPx()) }
                .size(knob)
                .background(palette.paper, RoundedCornerShape(2.dp))
                .border(1.dp, palette.inkAt(0.40f), RoundedCornerShape(2.dp)),
        )
    }
}
