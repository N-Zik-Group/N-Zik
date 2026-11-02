package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.isGlassMotionAllowed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Retargets physical placement without carrying old coordinates across viewport changes. */
internal class PlayerReflowPlacement(private val scope: CoroutineScope) {
    private var viewport: IntSize? = null
    private var target: Offset? = null
    private var animation: Animatable<Offset, AnimationVector2D>? = null
    private var job: Job? = null

    fun position(viewport: IntSize, target: Offset, enabled: Boolean, damping: Float,
        scrollTranslation: Offset = Offset.Zero): Offset {
        // Scroll belongs to direct manipulation, outside the structural reflow spring.
        val structuralTarget = target - scrollTranslation
        val current = animation
        if (current == null || this.viewport != viewport || !enabled) {
            job?.cancel()
            animation = Animatable(structuralTarget, Offset.VectorConverter)
            this.viewport = viewport
            this.target = structuralTarget
            return target
        }
        if (this.target != structuralTarget) {
            this.target = structuralTarget
            job?.cancel()
            val retained = current
            job = scope.launch {
                retained.animateTo(structuralTarget, spring(dampingRatio = damping, stiffness = 1000f))
            }
        }
        return current.value + scrollTranslation
    }
}

@Composable
internal fun rememberPlayerReflowPlacement(): PlayerReflowPlacement {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerReflowPlacement(scope) }
}

internal fun GlassSettings.playerReflowDamping(): Float =
    if (jellyEnabled) (1f - .2f * jellyIntensity.coerceIn(0f, 1f)) else 1f

/** The outer slot stays measurable while its content and hit target slide together. */
@Composable
internal fun Modifier.playerReflowPlacement(motion: PlayerReflowPlacement, settings: GlassSettings): Modifier {
    val geometry = LocalPlayerTransportGeometry.current
    val window = LocalWindowInfo.current.containerSize
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val enabled = isGlassMotionAllowed()
    val damping = settings.playerReflowDamping()
    return onPlaced { coordinates[0] = it }.layout { measurable, constraints ->
        val child = measurable.measure(constraints)
        val lookahead = isLookingAhead
        layout(child.width, child.height) {
            val anchor = coordinates[0]?.takeIf { it.isAttached }
            if (lookahead || anchor == null) child.place(0, 0)
            else {
                val origin = geometry?.bounds(anchor)?.topLeft ?: anchor.positionInRoot()
                val target = origin + Offset(anchor.size.width / 2f, anchor.size.height / 2f)
                val viewport = geometry?.host?.takeIf { it.isAttached }?.size ?: window
                val actual = motion.position(viewport, target, enabled, damping,
                    geometry?.lowerScrollTranslation(anchor) ?: Offset.Zero)
                child.place((actual.x - target.x).roundToInt(), (actual.y - target.y).roundToInt())
            }
        }
    }
}
