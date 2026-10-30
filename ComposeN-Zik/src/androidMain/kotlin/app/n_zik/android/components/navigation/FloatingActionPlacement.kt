package app.n_zik.android.components.navigation

import kotlin.math.roundToInt

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

import app.n_zik.android.LocalPlayerAwareWindowInsets

val FloatingMultiActionSize = 64.dp
val FloatingActionGap = 16.dp
val FloatingActionSiblingSpace = FloatingMultiActionSize + FloatingActionGap

/** Default anchors respect the page's rail reserve and Android's safe edges. */
@Composable
fun floatingActionsHorizontalPadding(
    windowInsets: WindowInsets = LocalPlayerAwareWindowInsets.current,
): PaddingValues {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = windowInsets.union(WindowInsets.safeDrawing)
    return with(density) {
        PaddingValues.Absolute(
            left = insets.getLeft(density, direction).toDp() + FloatingActionGap,
            right = insets.getRight(density, direction).toDp() + FloatingActionGap,
        )
    }
}

internal fun reachableFloatingActionOffset(requested: Offset, anchor: Rect, safe: Rect): Offset {
    fun axis(value: Float, start: Float, end: Float, safeStart: Float, safeEnd: Float): Float {
        val minimum = safeStart - start
        val maximum = (safeEnd - end).coerceAtLeast(minimum)
        return value.takeIf { it.isFinite() }?.coerceIn(minimum, maximum) ?: 0f.coerceIn(minimum, maximum)
    }
    return Offset(
        axis(requested.x, anchor.left, anchor.right, safe.left, safe.right),
        axis(requested.y, anchor.top, anchor.bottom, safe.top, safe.bottom),
    )
}

private class FloatingActionDragPosition(var offset: Offset = Offset.Zero)

/** Clamp rendering after resize without changing the user's stored position. */
@Composable
fun Modifier.movableFloatingAction(offsetX: MutableState<Float>, offsetY: MutableState<Float>): Modifier {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val view = LocalView.current
    val safeInsets = WindowInsets.safeDrawing
    var geometry by remember { mutableStateOf<Pair<Rect, Rect>?>(null) }
    val dragPosition = remember { FloatingActionDragPosition() }
    return this
        // Measure the anchor before the child receives its translated placement.
        .onPlaced { coordinates ->
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val origin = coordinates.positionInWindow()
            geometry = Rect(origin, Size(
                coordinates.size.width.toFloat(), coordinates.size.height.toFloat(),
            )) to Rect(
                (location[0] + safeInsets.getLeft(density, direction)).toFloat(),
                (location[1] + safeInsets.getTop(density)).toFloat(),
                (location[0] + view.width - safeInsets.getRight(density, direction)).toFloat(),
                (location[1] + view.height - safeInsets.getBottom(density)).toFloat(),
            )
        }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                val requested = Offset(offsetX.value, offsetY.value)
                val displayed = geometry?.let { (anchor, safe) ->
                    reachableFloatingActionOffset(requested, anchor, safe)
                } ?: requested
                dragPosition.offset = displayed
                placeable.place(displayed.x.roundToInt(), displayed.y.roundToInt())
            }
        }
        .pointerInput(offsetX, offsetY) {
            var dragged = Offset.Zero
            detectDragGesturesAfterLongPress(
                onDragStart = { dragged = dragPosition.offset },
                onDrag = { change, amount ->
                    change.consume()
                    dragged += amount
                    offsetX.value = dragged.x
                    offsetY.value = dragged.y
                },
            )
        }
}
