package app.n_zik.android.components.glass

import androidx.compose.runtime.staticCompositionLocalOf
import app.n_zik.android.components.glass.backdrop.Backdrop

/**
 * The nearest rendered container material, exported before drawing its children.
 * Never provide a capture containing the consuming controls: that creates draw feedback.
 */
val LocalGlassContainerBackdrop = staticCompositionLocalOf<Backdrop?> { null }
