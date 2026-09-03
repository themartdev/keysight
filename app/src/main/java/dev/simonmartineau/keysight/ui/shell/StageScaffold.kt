package dev.simonmartineau.keysight.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The strip's height: one touch target, two lines of small text. */
val STRIP_HEIGHT = 48.dp

/**
 * The frame of every destination that is a music stand: the window is the [stage], and the
 * one [strip] above it is all the chrome there is. The system bars are hidden here, so the
 * stage's box is the same whatever the strip shows and whatever the state, and a score fitted
 * to it never moves; only the display cutout is kept clear. Anything with many controls
 * comes as a sheet over the stage, never as a sibling that would shrink it.
 */
@Composable
fun StageScaffold(
    modifier: Modifier = Modifier,
    strip: @Composable RowScope.() -> Unit,
    stage: @Composable BoxScope.() -> Unit,
) {
    ImmersiveEffect()
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(STRIP_HEIGHT)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                content = strip,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), content = stage)
        }
    }
}
