package app.n_zik.android.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState

/**
 * Bottom Sheet - Same as vivi-music/Metrolist
 * Always rendered when there's media content. When collapsed, only the mini-player
 * area is interactive — the rest of the screen remains clickable (nav bar, lists, etc.).
 *
 * @param bottomPadding Padding from the bottom to adjust the collapsed position
 * @param collapsedContentHeight The visual height of the mini-player content (without system bar insets).
 *        Used for the collapsed hit target. Defaults to [PlayerSheetState.collapsedBound].
 */
@Composable
fun CustomBottomSheet(
    state: PlayerSheetState,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    bottomPadding: Dp = 0.dp,
    collapsedContentHeight: Dp = state.collapsedBound,
    disableDismiss: Boolean = false,
    collapsedContent: @Composable BoxScope.() -> Unit,
    isExpandable: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val isCollapsedOrDismissed = state.isCollapsed || state.isDismissed

    // The difference between collapsedBound (includes system bar insets) and
    // the visual mini-player height. This is used to smoothly interpolate the
    // translationY so that the collapsed position matches the expanding position.
    val insetDiffPx = (state.collapsedBound - collapsedContentHeight).value

    Box(
        modifier = modifier
            .then(
                if (isCollapsedOrDismissed) {
                    // When collapsed: only take up the mini-player visual area + bottom padding
                    // so content behind remains clickable.
                    Modifier
                        .fillMaxWidth()
                        .height(collapsedContentHeight + bottomPadding)
                } else {
                    // When expanding/expanded: take up the full screen
                    Modifier.fillMaxSize()
                }
            )
            .graphicsLayer {
                if (isCollapsedOrDismissed) {
                    translationY = 0f
                } else {
                    val paddingPx = bottomPadding.toPx()
                    val y = (state.expandedBound - state.value)
                        .toPx()
                        .coerceAtLeast(0f)
                    val progressFactor = state.progress.coerceIn(0f, 1f)
                    // Compensate for the difference between collapsedBound and collapsedContentHeight
                    val insetCompensation = (state.collapsedBound - collapsedContentHeight)
                        .toPx() * (1f - progressFactor)
                    // Interpolate bottomPadding: full offset at collapsed, 0 at expanded
                    // so the player covers the full screen when expanded
                    translationY = y - paddingPx * (1f - progressFactor) + insetCompensation
                }
            }
            .then(
                if (!isCollapsedOrDismissed) {
                    Modifier.pointerInput(state, isExpandable) {
                        if (!isExpandable) return@pointerInput
                        val velocityTracker = VelocityTracker()

                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                velocityTracker.addPointerInputChange(change)
                                state.dispatchRawDelta(dragAmount)
                            },
                            onDragCancel = {
                                velocityTracker.resetTracking()
                                state.snapTo(state.collapsedBound)
                            },
                            onDragEnd = {
                                val velocity = -velocityTracker.calculateVelocity().y
                                velocityTracker.resetTracking()
                                state.performFling(velocity, onDismiss)
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                val cornerRadius = if (!state.isExpanded) 16.dp.toPx() else 0f
                shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                clip = true
            }
    ) {
        if (!state.isCollapsed && !state.isDismissed) {
            BackHandler(onBack = state::collapseSoft)
        }

        // Drag handle area at the top - only when expanded
        if (state.isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .pointerInput(state, isExpandable) {
                        if (!isExpandable) return@pointerInput
                        val velocityTracker = VelocityTracker()

                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                velocityTracker.addPointerInputChange(change)
                                state.dispatchRawDelta(dragAmount)
                            },
                            onDragCancel = {
                                velocityTracker.resetTracking()
                                state.snapTo(state.collapsedBound)
                            },
                            onDragEnd = {
                                val velocity = -velocityTracker.calculateVelocity().y
                                velocityTracker.resetTracking()
                                state.performFling(velocity, onDismiss)
                            }
                        )
                    }
            )
        }

        // Main player content — only rendered when not collapsed (performance + prevents touch blocking)
        if (!isCollapsedOrDismissed) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = ((state.progress - 0.5f) * 2).coerceIn(0f, 1f)
                    },
                content = content
            )
        }

        // Mini-player — visible when not fully expanded
        if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
            Box(
                modifier =
                Modifier
                    .graphicsLayer {
                        alpha = 1f - ((state.progress - 0.5f) * 2).coerceIn(0f, 1f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (isExpandable) state.expandSoft() },
                    )
                    .pointerInput(state, isExpandable) {
                        if (!isExpandable) return@pointerInput
                        val velocityTracker = VelocityTracker()

                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                velocityTracker.addPointerInputChange(change)
                                state.dispatchRawDelta(dragAmount)
                            },
                            onDragCancel = {
                                velocityTracker.resetTracking()
                                state.snapTo(state.collapsedBound)
                            },
                            onDragEnd = {
                                val velocity = -velocityTracker.calculateVelocity().y
                                velocityTracker.resetTracking()
                                state.performFling(velocity, if (!disableDismiss) onDismiss else null)
                            }
                        )
                    }
                    .fillMaxWidth()
                    .height(collapsedContentHeight),
                content = collapsedContent,
            )
        }
    }
}
