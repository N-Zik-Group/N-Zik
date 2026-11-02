package app.n_zik.android.components.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** The complete visual envelope ends below the window, including any inset or saved drag position. */
internal fun floatingActionScreenExitDistance(windowBottom: Float, restingTop: Float?, visualOutset: Float): Float =
    (windowBottom - (restingTop ?: 0f)).coerceAtLeast(0f) + visualOutset

/** Slides the live action across the window edge without clipping or fading its material. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScreenEdgeFloatingAction(
    visible: Boolean,
    offsetX: MutableState<Float>,
    offsetY: MutableState<Float>,
    enterDelayMillis: Int = 0,
    content: @Composable (Modifier) -> Unit,
) {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val view = LocalView.current
    val windowBottom = maxOf(windowHeight, view.rootView.height, view.height).toFloat()
    val visualOutset = with(LocalDensity.current) { 32.dp.toPx() }
    var restingBounds by remember { mutableStateOf<Rect?>(null) }
    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
        val offscreenFraction by transition.animateFloat(
            transitionSpec = { tween(500, if (targetState == EnterExitState.Visible) enterDelayMillis else 0) },
            label = "floatingActionScreenEdge",
        ) { if (it == EnterExitState.Visible) 0f else 1f }
        content(
            Modifier
                .movableFloatingAction(offsetX, offsetY, onRestingBounds = { restingBounds = it })
                .graphicsLayer {
                    // Transform after drag placement: the reachability clamp sees the resting anchor.
                    translationY = offscreenFraction * floatingActionScreenExitDistance(
                        windowBottom, restingBounds?.top, visualOutset,
                    )
                    clip = false
                },
        )
    }
}
