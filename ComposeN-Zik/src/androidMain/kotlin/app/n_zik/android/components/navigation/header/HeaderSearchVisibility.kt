package app.n_zik.android.components.navigation.header

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Alignment
import app.n_zik.android.components.glass.isGlassMotionAllowed
import kotlin.math.roundToInt

/** Owned above NavHost so simultaneous page rails share one navigation transition. */
internal val LocalSideHeaderSearchProgress = staticCompositionLocalOf<State<Float>?> { null }

/** Full-size glyph slides beneath the following profile; only its layout slot retracts. */
@Composable
internal fun HorizontalHeaderSearchVisibility(progress: State<Float>, fade: Boolean = false, content: @Composable () -> Unit) =
    SharedHeaderSearchVisibility(progress, vertical = false, fade, content)

@Composable
internal fun VerticalHeaderSearchVisibility(progress: State<Float>, fade: Boolean = false, content: @Composable () -> Unit) =
    SharedHeaderSearchVisibility(progress, vertical = true, fade, content)

@Composable
private fun SharedHeaderSearchVisibility(progress: State<Float>, vertical: Boolean, fade: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.then(if (progress.value < 1f) Modifier.clipToBounds() else Modifier).layout { measurable, constraints ->
        val fraction = progress.value.coerceIn(0f, 1f)
        val cell = measurable.measure(if (vertical) constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            else constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val width = if (vertical) cell.width else (cell.width * fraction).roundToInt()
        val height = if (vertical) (cell.height * fraction).roundToInt() else cell.height
        layout(width, height) {
            // The shrinking slot moves the full child toward the following anchored profile.
            cell.placeRelativeWithLayer(0, 0) { alpha = if (fade) fraction else 1f }
        }
    }) { content() }
}

@Composable
internal fun rememberHeaderSearchVisibility(visible: Boolean): MutableTransitionState<Boolean> =
    remember { MutableTransitionState(visible) }.apply { targetState = visible }

@Composable
internal fun headerSearchJoinProgress(visible: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(if (isGlassMotionAllowed()) 200 else 0),
    label = "headerSearchJoin",
)

/** Changes only the search slot; the adjacent profile retains its composition and anchor. */
@Composable
internal fun HeaderSearchVisibility(
    visibility: MutableTransitionState<Boolean>,
    motionAllowed: Boolean = isGlassMotionAllowed(),
    slideFromProfile: Boolean = false,
    vertical: Boolean = false,
    content: @Composable () -> Unit,
) {
    val duration = if (motionAllowed) 200 else 0
    AnimatedVisibility(
        visibleState = visibility,
        enter = fadeIn(tween(duration)) + if (vertical) {
            expandVertically(tween(duration), expandFrom = Alignment.Bottom) +
                slideInVertically(tween(duration)) { it }
        } else expandHorizontally(tween(duration), expandFrom = Alignment.End) +
            if (slideFromProfile) slideInHorizontally(tween(duration)) { it } else EnterTransition.None,
        exit = fadeOut(tween(duration)) + if (vertical) {
            shrinkVertically(tween(duration), shrinkTowards = Alignment.Bottom) +
                slideOutVertically(tween(duration)) { it }
        } else shrinkHorizontally(tween(duration), shrinkTowards = Alignment.End) +
            if (slideFromProfile) slideOutHorizontally(tween(duration)) { it } else ExitTransition.None,
    ) { content() }
}
