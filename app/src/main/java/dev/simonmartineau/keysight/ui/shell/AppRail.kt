package dev.simonmartineau.keysight.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.simonmartineau.keysight.Screen
import dev.simonmartineau.keysight.ui.theme.KeySightTheme
import dev.simonmartineau.keysight.ui.theme.Metrics
import dev.simonmartineau.keysight.ui.theme.VerticalHairline
import dev.simonmartineau.keysight.ui.theme.palette
import dev.simonmartineau.keysight.ui.theme.type

/**
 * The frame of every screen but a run: the page on the ground, the rail down its left edge,
 * both inside the system bars. A run has no frame, so [content] is drawn alone and full-bleed
 * and the practice screen keeps its own insets.
 */
@Composable
fun AppFrame(screen: Screen, onSelect: (Screen.Tab) -> Unit, content: @Composable () -> Unit) {
    val palette = MaterialTheme.palette
    Box(Modifier.fillMaxSize().background(palette.ground)) {
        if (!screen.hasRail) {
            content()
        } else {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                AppRail(screen.tab, onSelect)
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        }
    }
}

/**
 * The global navigation: Home, Play and History from the top, Settings pinned to the
 * bottom, on paper with a hairline down its right edge. The active item is the one whose tab
 * the screen belongs to, so a run's detail page still lights History.
 */
@Composable
fun AppRail(active: Screen.Tab, onSelect: (Screen.Tab) -> Unit, modifier: Modifier = Modifier) {
    val palette = MaterialTheme.palette
    Row(modifier.fillMaxHeight()) {
        Column(
            Modifier
                .width(Metrics.RailWidth)
                .fillMaxHeight()
                .background(palette.paper)
                .padding(vertical = 12.dp),
        ) {
            Screen.Tab.entries.filter { it != Screen.Tab.SETTINGS }.forEach { tab ->
                RailItem(tab, selected = tab == active, onClick = { onSelect(tab) })
            }
            Spacer(Modifier.weight(1f))
            RailItem(Screen.Tab.SETTINGS, selected = active == Screen.Tab.SETTINGS, onClick = { onSelect(Screen.Tab.SETTINGS) })
        }
        VerticalHairline(Modifier.fillMaxHeight())
    }
}

/**
 * A 20dp mark over a label, the rail's full width. Active is a dim paper ground, the mark in
 * the accent and the label in ink at medium; inactive is a mark at the fill and a faint label.
 * The mark is a plain square until the app has an icon set.
 */
@Composable
private fun RailItem(tab: Screen.Tab, selected: Boolean, onClick: () -> Unit) {
    val palette = MaterialTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) palette.paperDim else palette.paper)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (selected) palette.inkAccent else palette.inkAt(0.24f)),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            tab.label.uppercase(),
            style = MaterialTheme.type.label.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.SemiBold),
            color = if (selected) palette.ink else palette.onSurfaceFaint,
            maxLines = 1,
        )
    }
}

@Preview(name = "AppRail", heightDp = 390)
@Composable
private fun AppRailPreview() {
    KeySightTheme {
        Box(Modifier.fillMaxHeight().background(MaterialTheme.palette.ground)) {
            AppRail(active = Screen.Tab.PLAY, onSelect = {})
        }
    }
}
