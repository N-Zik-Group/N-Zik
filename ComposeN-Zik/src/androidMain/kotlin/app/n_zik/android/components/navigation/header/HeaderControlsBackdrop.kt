package app.n_zik.android.components.navigation.header

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop

/** Controls sample the surface actually visible beneath them, before the row's group fade. */
@Composable
internal fun rememberHeaderControlsBackdrop(
    hasTopGlassBackdrop: Boolean,
    headerColor: Color,
): Backdrop? = if (hasTopGlassBackdrop) {
    LocalUnifiedTopGlassSurface.current ?: LocalGlassBackdrop.current
} else {
    // The native header is opaque: sampling the page would reveal covered artwork.
    rememberCanvasBackdrop { drawRect(headerColor) }
}
