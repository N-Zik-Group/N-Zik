package app.n_zik.android.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.n_zik.android.components.glass.GLASS_OPTICS_FADE_MILLIS
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.glassOpticsGateProgress
import app.n_zik.android.components.glass.backdrop.Backdrop

/** One clock owns the deploy plate, source handoff and all player optical consumers. */
val LocalPlayerSheetGlassTransition = staticCompositionLocalOf<State<Float>?> { null }

@Composable
fun rememberPlayerSheetGlassTransition(state: PlayerSheetState): State<Float> =
    animateFloatAsState(if (state.isExpanded) 1f else 0f,
        tween(GLASS_OPTICS_FADE_MILLIS), label = "playerSheetGlass")

/** Capture stays alive until the last consumer's deployment fade has ended. */
@Composable
fun playerGlassSourceCaptureActive(settings: GlassSettings, transition: State<Float>,
    inheritedOpticsEnabled: Boolean): Boolean {
    val progress = transition.value * glassOpticsGateProgress(inheritedOpticsEnabled)
    return settings.enabled && glassRenderer(settings) != GlassRenderer.Tint && progress > 0f
}

/** Keep the same source identity while live sources crossfade at the expanded endpoint. */
@Composable
fun rememberPlayerControlsGlassSource(deploy: Backdrop?, player: Backdrop,
    transition: State<Float>): Backdrop = remember(deploy, player, transition) {
    object : Backdrop {
        override val isCoordinatesDependent = true
        override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?) {
            fun draw(source: Backdrop?, alpha: Float) {
                if (source == null || alpha <= 0f) return
                val canvas = drawContext.canvas
                canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
                try { with(source) { drawBackdrop(density, coordinates, layerBlock) } }
                finally { canvas.restore() }
            }
            val progress = transition.value.coerceIn(0f, 1f)
            draw(deploy, 1f - progress)
            draw(player, progress)
        }
    }
}
