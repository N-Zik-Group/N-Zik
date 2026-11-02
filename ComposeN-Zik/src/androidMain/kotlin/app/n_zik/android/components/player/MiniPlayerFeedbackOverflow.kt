package app.n_zik.android.components.player

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil

/** Only mini controls register here; ordinary sheet content keeps its exact card mask. */
internal val LocalMiniPlayerFeedbackOverflow = staticCompositionLocalOf<MiniPlayerFeedbackOverflow?> { null }

/** Bounds are sampled in the clipping layer's phase, without recomposing the player. */
internal class MiniPlayerFeedbackOverflow {
    private val controls = mutableStateMapOf<Any, (LayoutCoordinates) -> Rect?>()

    fun register(owner: Any, bounds: (LayoutCoordinates) -> Rect?) { controls[owner] = bounds }
    fun unregister(owner: Any) { controls.remove(owner) }

    fun clipFrame(
        base: Shape,
        coordinates: LayoutCoordinates?,
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): MiniPlayerFeedbackClip {
        if (coordinates == null || !coordinates.isAttached) return MiniPlayerFeedbackClip(base)
        val bounds = controls.values.mapNotNull { it(coordinates) }
        if (bounds.isEmpty()) return MiniPlayerFeedbackClip(base)
        val outline = miniPlayerFeedbackOutline(base.createOutline(size, layoutDirection, density), bounds)
        val shape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) = outline
        }
        return MiniPlayerFeedbackClip(shape, miniPlayerFeedbackOutsets(size, bounds, density))
    }
}

internal data class MiniPlayerFeedbackClip(val shape: Shape, val outsets: LayerOutsets = LayerOutsets.Zero)

/** Keep Auto's grouped opacity while its offscreen buffer includes the moving control. */
internal fun miniPlayerFeedbackOutsets(size: Size, bounds: List<Rect>, density: Density): LayerOutsets {
    if (bounds.isEmpty()) return LayerOutsets.Zero
    return with(density) {
        LayerOutsets(
            left = ceil((-bounds.minOf { it.left }).coerceAtLeast(0f)).toDp(),
            top = ceil((-bounds.minOf { it.top }).coerceAtLeast(0f)).toDp(),
            right = ceil((bounds.maxOf { it.right } - size.width).coerceAtLeast(0f)).toDp(),
            bottom = ceil((bounds.maxOf { it.bottom } - size.height).coerceAtLeast(0f)).toDp(),
        )
    }
}

/** Union rather than an inflated card: only the currently moving controls gain overdraw. */
internal fun miniPlayerFeedbackOutline(base: Outline, controls: List<Rect>): Outline {
    if (controls.isEmpty()) return base
    val path = Path().apply {
        when (base) {
            is Outline.Generic -> addPath(base.path)
            is Outline.Rectangle -> addRect(base.rect)
            is Outline.Rounded -> addRoundRect(base.roundRect)
        }
        controls.forEach { bounds ->
            if (bounds.left.isFinite() && bounds.top.isFinite() && bounds.right.isFinite() &&
                bounds.bottom.isFinite() && bounds.width > 0f && bounds.height > 0f) addRect(bounds)
        }
    }
    return Outline.Generic(path)
}
