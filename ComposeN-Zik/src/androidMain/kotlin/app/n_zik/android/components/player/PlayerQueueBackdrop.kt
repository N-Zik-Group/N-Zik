package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import app.n_zik.android.components.glass.backdrop.Backdrop

/** Replays player content beneath the queue's native scrim, without capturing queue materials. */
@Composable
fun rememberPlayerQueueBackdrop(player: Backdrop, expansion: () -> Float): Backdrop {
    val latestExpansion = rememberUpdatedState(expansion)
    return remember(player) {
        object : Backdrop {
            override val isCoordinatesDependent: Boolean = player.isCoordinatesDependent
            private fun alpha(): Float = (latestExpansion.value() / .65f).coerceIn(0f, 1f) * .5f
            // Include this draw-time input even when the player recording itself is unchanged.
            // An unversioned player remains uncacheable rather than inventing a stable version.
            override val contentVersion: Int? get() {
                val scrimVersion = alpha().toBits()
                return player.contentVersion?.let { 31 * it + scrimVersion }
            }
            override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?) {
                with(player) { drawBackdrop(density, coordinates, layerBlock) }
                drawRect(Color.Black.copy(alpha = alpha()))
            }
        }
    }
}
