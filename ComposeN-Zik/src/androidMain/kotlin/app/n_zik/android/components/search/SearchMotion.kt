package app.n_zik.android.components.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import app.n_zik.android.components.glass.isGlassMotionAllowed

internal const val SearchMotionDurationMillis = 250

@Composable
internal fun SearchHeaderVisibility(visible: Boolean, content: @Composable () -> Unit) {
    val motion = isGlassMotionAllowed()
    AnimatedVisibility(
        visible = visible,
        enter = if (motion) expandVertically(tween(SearchMotionDurationMillis), expandFrom = Alignment.Top) +
            fadeIn(tween(SearchMotionDurationMillis)) else EnterTransition.None,
        exit = if (motion) shrinkVertically(tween(SearchMotionDurationMillis), shrinkTowards = Alignment.Top) +
            fadeOut(tween(SearchMotionDurationMillis)) else ExitTransition.None,
    ) { content() }
}

@Composable
internal fun rememberSearchResultOpacity(visible: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(if (isGlassMotionAllowed()) SearchMotionDurationMillis else 0),
    label = "searchResultOpacity",
)

/** Retain lazy item dimensions so a hidden result never makes its whole list measure at once. */
internal fun Modifier.searchResultOpacity(opacity: State<Float>): Modifier =
    layout { measurable, constraints ->
        val result = measurable.measure(constraints)
        layout(result.width, result.height) {
            if (opacity.value > 0f) result.placeRelativeWithLayer(0, 0) { alpha = opacity.value }
        }
    }

@Composable
internal fun SearchResultContent(
    opacity: State<Float>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.searchResultOpacity(opacity)) { content() }
}
