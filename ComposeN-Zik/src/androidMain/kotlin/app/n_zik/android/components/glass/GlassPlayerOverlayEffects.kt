package app.n_zik.android.components.glass

import androidx.compose.runtime.staticCompositionLocalOf

/** The visible full player uses lighter live blur for its menus and queue, without changing preferences. */
val LocalGlassPlayerOverlayActive = staticCompositionLocalOf { false }

internal data class GlassOverlayBlurBudget(val radiusDp: Float, val resolution: Float)

/** Keeps clear panes and ordinary controls at their configured quality. */
internal fun playerOverlayBlurBudget(
    radiusDp: Float,
    resolution: Float,
    playerActive: Boolean,
    menu: Boolean,
    queue: Boolean,
): GlassOverlayBlurBudget = if (playerActive && (menu || queue) && radiusDp > 0f) {
    GlassOverlayBlurBudget(radiusDp * .75f, resolution.coerceAtMost(.25f))
} else {
    GlassOverlayBlurBudget(radiusDp, resolution)
}
