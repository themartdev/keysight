package dev.simonmartineau.keysight.ui.notation

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import dev.simonmartineau.keysight.R

/**
 * The bundled SMuFL font, loaded once per context. Its metrics live in
 * [dev.simonmartineau.keysight.notation.BravuraMetrics]; this is only the typeface.
 */
@Composable
fun rememberBravura(): Typeface {
    val context = LocalContext.current
    return remember(context) {
        checkNotNull(ResourcesCompat.getFont(context, R.font.bravura)) { "the Bravura font resource failed to load" }
    }
}
