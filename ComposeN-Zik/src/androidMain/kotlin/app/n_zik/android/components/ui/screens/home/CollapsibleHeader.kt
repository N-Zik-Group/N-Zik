package app.n_zik.android.components.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import app.n_zik.android.LocalTopBarOffset
import app.n_zik.android.components.player.APP_HEADER_HEIGHT
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
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

/** Publishes the root scroll position to every mounted Home toolbar in the same snapshot. */
internal class HomeHeaderMotionRegistry(private val appOffset: MutableFloatState) {
    private val listeners = mutableMapOf<Any, () -> Unit>()

    fun attach(owner: Any, update: () -> Unit) {
        listeners[owner] = update
        update()
    }

    fun detach(owner: Any) {
        listeners.remove(owner)
    }

    fun sync() {
        listeners.values.toList().forEach { it() }
    }

    fun offset(): Float = appOffset.floatValue

    /** The toolbar still owns page scroll when the landscape toggle hides the app header. */
    fun scrollBy(delta: Float, appHeaderHeight: Float) {
        appOffset.floatValue = (appOffset.floatValue + delta).coerceIn(-appHeaderHeight, 0f)
        sync()
    }
}

internal val LocalHomeHeaderMotionRegistry = staticCompositionLocalOf<HomeHeaderMotionRegistry?> { null }

/** Snaps to the nearest header bound, choosing expanded at the midpoint. */
internal fun collapsibleHeaderSnapTarget(offset: Float, headerHeight: Int): Float {
    if (headerHeight <= 0) return 0f
    val threshold = -headerHeight / 2f
    return if (offset < threshold) -headerHeight.toFloat() else 0f
}

internal fun titleStatusClipPx(titleTop: Float, statusBarTop: Int, titleHeight: Float): Float =
    (statusBarTop - titleTop).coerceIn(0f, titleHeight.coerceAtLeast(0f))

/** Keeps Home titles opaque while their parent toolbar moves, clipping only the status area. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun CollapsibleTitleRow(
    // TODO(Codex): Remove these compatibility parameters together with the four Home callers.
    titleOffset: MutableFloatState,
    titleHeight: MutableIntState,
    content: @Composable () -> Unit
) {
    val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current)
    val titleTop = remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }

    Box(
        Modifier
            .onGloballyPositioned { titleTop.floatValue = it.positionInRoot().y }
            .drawWithContent {
                clipRect(top = titleStatusClipPx(titleTop.floatValue, statusBarTop, size.height)) {
                    this@drawWithContent.drawContent()
                }
            }
    ) {
        content()
    }
}

/** Follows consumed drags, allows expansion at the list start, then snaps after a fling. */
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

/** Owns snap cancellation and resets disabled headers to their expanded position. */
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

/** Horizontal Home chrome follows the app header's scroll and snap clock in both orientations. */
@Composable
fun rememberHomeHeaderConnection(
    headerHeight: Int,
    headerOffset: MutableFloatState,
    enabled: Boolean,
): NestedScrollConnection {
    val navigationPosition = NavigationBarPosition.current()
    val appHeaderOffset = LocalTopBarOffset.current
    val motionRegistry = LocalHomeHeaderMotionRegistry.current
    val appHeaderHeight = with(LocalDensity.current) { APP_HEADER_HEIGHT.toPx() }
    val shared = homeHeaderSharesAppScroll(navigationPosition.isHorizontal, enabled)
    val owner = remember { Any() }
    DisposableEffect(shared, motionRegistry, owner, headerOffset, appHeaderHeight, headerHeight) {
        if (shared) motionRegistry?.attach(owner) {
            headerOffset.floatValue = homeHeaderOffsetForAppHeader(
                motionRegistry.offset(), appHeaderHeight, headerHeight,
            )
        }
        onDispose { motionRegistry?.detach(owner) }
    }
    LaunchedEffect(shared, appHeaderOffset, appHeaderHeight, headerHeight) {
        if (shared) snapshotFlow { appHeaderOffset.value }.collect { offset ->
            headerOffset.floatValue = homeHeaderOffsetForAppHeader(offset, appHeaderHeight, headerHeight)
        }
    }
    return if (shared) remember { object : NestedScrollConnection {} }
        else rememberCollapsibleHeaderConnection(headerHeight, headerOffset, enabled)
}

internal fun homeHeaderSharesAppScroll(horizontalNavigation: Boolean, enabled: Boolean): Boolean =
    horizontalNavigation && enabled

internal fun homeHeaderOffsetForAppHeader(appOffset: Float, appHeight: Float, homeHeight: Int): Float {
    if (appHeight <= 0f || homeHeight <= 0) return 0f
    return (appOffset / appHeight).coerceIn(-1f, 0f) * homeHeight
}

internal fun homeHeaderVisibleFraction(height: Int, offset: Float): Float =
    if (height <= 0) 1f else visibleHomeHeaderHeightPx(height, offset) / height
