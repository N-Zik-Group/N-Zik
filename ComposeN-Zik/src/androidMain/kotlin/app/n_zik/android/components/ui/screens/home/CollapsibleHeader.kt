package app.n_zik.android.components.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 200ms: the header travels 2-3x the distance of a 64dp bar, so the root bar's
// 150ms/Linear snap would look rushed; matches the app's other 200ms animations.
private const val SNAP_DURATION_MS = 200

/**
 * Snap target for a collapsible header after a fling: the nearest bound
 * (0f = expanded, -headerHeight = collapsed), threshold at the header midpoint.
 *
 * @param offset Current header offset, in [0f, -headerHeight]
 * @param headerHeight Measured header height in pixels (0 = not measured yet)
 * @return The offset to animate to
 */
internal fun collapsibleHeaderSnapTarget(offset: Float, headerHeight: Int): Float {
    if (headerHeight <= 0) return 0f
    val threshold = -headerHeight / 2f
    return if (offset < threshold) -headerHeight.toFloat() else 0f
}

// The title row (section title + count) fades out over the first ~30% of the
// header's collapse travel, and back over the last ~30% of the expansion.
private const val TITLE_FADE_FRACTION = 0.3f

/**
 * Alpha for the collapsible header's title row (section title + count): fades
 * from 1 to 0 over the first [TITLE_FADE_FRACTION] of the collapse travel and
 * back the other way over the last part of the expansion, so the title hides
 * in place instead of just scrolling off-screen with the rest of the header.
 *
 * @param offset Current header offset, in [0f, -headerHeight]
 * @param headerHeight Measured header height in pixels (0 = not measured yet)
 * @return Alpha in [0f, 1f] to apply to the title row
 */
internal fun collapsibleTitleAlpha(offset: Float, headerHeight: Int): Float {
    if (headerHeight <= 0) return 1f
    return (1f + offset / (headerHeight * TITLE_FADE_FRACTION)).coerceIn(0f, 1f)
}

/**
 * Alpha for a title row given its position relative to the status bar: fully
 * visible once the row sits below the status bar, fading to 0 as it slides up
 * under it. This covers the case where the app's root top bar has just hidden
 * and only the toolbar remains: the header is then still expanded (offset 0),
 * so the collapse-based alpha alone would leave the title drawn in the status bar.
 *
 * @param titleTop Top of the title row in root coordinates, in pixels
 * @param statusBarTop Height of the status bar inset, in pixels (0 = none)
 * @return Alpha in [0f, 1f] to apply to the title row
 */
internal fun titleStatusBarAlpha(titleTop: Float, statusBarTop: Int): Float {
    if (statusBarTop <= 0) return 1f
    return (titleTop / statusBarTop).coerceIn(0f, 1f)
}

/**
 * Wraps a collapsible header's title row (section title + count) so it fades
 * out as it goes under the status bar, whichever thing pushes it there: the
 * header collapsing (first part of its travel) or the root top bar hiding
 * (which moves the whole screen up while the header stays expanded). Both
 * states are read in the draw phase, so nothing recomposes while scrolling
 * and the rest of the header (toolbar, chips, search) stays untouched.
 *
 * @param titleOffset Header offset state, in [0f, -headerHeight]
 * @param titleHeight Measured header height state, in pixels
 * @param content The title row (TabHeader + count)
 */
@Composable
fun CollapsibleTitleRow(
    titleOffset: MutableFloatState,
    titleHeight: MutableIntState,
    content: @Composable () -> Unit
) {
    val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current)
    // Unmeasured yet: treated as far below the status bar, i.e. fully visible
    val titleTop = remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }

    Box(
        Modifier
            .onGloballyPositioned { titleTop.floatValue = it.positionInRoot().y }
            .graphicsLayer {
                alpha = minOf(
                    collapsibleTitleAlpha(titleOffset.floatValue, titleHeight.intValue),
                    titleStatusBarAlpha(titleTop.floatValue, statusBarTop)
                )
            }
    ) {
        content()
    }
}

/**
 * Nested scroll connection for a pinned collapsible header: the header follows
 * the finger during drags and fling frames, then snaps to the nearest bound when
 * the fling ends; any new non-zero scroll delta cancels a running snap. A collapse
 * only sticks when the scrollable actually consumed the delta (bottom edge, short
 * list): otherwise the pre-scroll movement is reverted so the header stays glued
 * to the content. An expansion always sticks, so a header left shut by a snap
 * reopens once the list is back at its top. While disabled, it neither follows
 * nor snaps.
 *
 * @param scope Structured scope hosting the snap animation
 * @param snapJob Holds the snap animation job so new deltas can cancel it
 * @param enabled Whether the header may follow the scroll (false when the list is empty)
 */
internal fun collapsibleHeaderConnection(
    headerHeight: Int,
    headerOffset: MutableFloatState,
    scope: CoroutineScope,
    snapJob: MutableState<Job?>,
    enabled: Boolean
): NestedScrollConnection = object : NestedScrollConnection {
    // Movement applied in onPreScroll, reverted in onPostScroll when the content
    // consumed none of the delta (the header must not leave the content behind).
    private var pendingDelta = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!enabled) return Offset.Zero
        val delta = available.y
        if (delta != 0f) {
            // A new finger delta takes the header back: stop any running snap
            snapJob.value?.cancel()
            snapJob.value = null
        }
        val before = headerOffset.floatValue
        headerOffset.floatValue = (before + delta).coerceIn(-headerHeight.toFloat(), 0f)
        pendingDelta = headerOffset.floatValue - before
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!enabled || pendingDelta == 0f) return Offset.Zero
        // Only a collapse is reverted: an expansion (pendingDelta > 0) with the content
        // stuck at its top edge must stay, otherwise a header snapped shut can never
        // reopen once the list is back at the top (it would leave an empty gap).
        if (consumed.y == 0f && pendingDelta < 0f) {
            headerOffset.floatValue = (headerOffset.floatValue - pendingDelta).coerceIn(-headerHeight.toFloat(), 0f)
        }
        pendingDelta = 0f
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (!enabled || headerHeight <= 0) return Velocity.Zero
        val target = collapsibleHeaderSnapTarget(headerOffset.floatValue, headerHeight)
        if (target == headerOffset.floatValue) return Velocity.Zero
        snapJob.value?.cancel()
        snapJob.value = scope.launch {
            Animatable(headerOffset.floatValue)
                .animateTo(target, tween(SNAP_DURATION_MS, easing = FastOutSlowInEasing)) {
                    headerOffset.floatValue = value
                }
        }
        return Velocity.Zero
    }
}

/**
 * Remembers the [collapsibleHeaderConnection] for a collapsible header: owns the
 * coroutine scope and the snap job, cancels a running snap when the measured
 * header height changes (its animation bounds would be stale), and resets the
 * header to the expanded position when it becomes disabled (nothing to display).
 *
 * @param enabled Whether the header may follow the scroll (false when the list is empty)
 * @return The [NestedScrollConnection] to attach via `Modifier.nestedScroll`
 */
@Composable
fun rememberCollapsibleHeaderConnection(
    headerHeight: Int,
    headerOffset: MutableFloatState,
    enabled: Boolean
): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    val snapJob = remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(headerHeight) {
        snapJob.value?.cancel()
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            snapJob.value?.cancel()
            headerOffset.floatValue = 0f
        }
    }
    return remember(headerHeight, scope, snapJob, enabled) {
        collapsibleHeaderConnection(headerHeight, headerOffset, scope, snapJob, enabled)
    }
}
