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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.n_zik.android.colorPalette

/**
 * Bottom Sheet with "Expand / Deploy" animation.
 *
 * When the user taps or drags the mini-player, a background card visually
 * deploys from the exact mini-player dimensions to full-screen, while the
 * mini-player content fades out and the full player content fades in.
 * The content itself does NOT scale — only the background card changes size.
 * Closing reverses the effect.
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
    val baseShape = app.n_zik.android.uiRoundnessShape()

    val miniPlayerColor = colorPalette().background2
    val playerColor = colorPalette().background0

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
            // ── Clip with progressive corners ──
            .graphicsLayer {
                val p = state.progress.coerceIn(0f, 1f)
                val baseCornerPx = (baseShape as? RoundedCornerShape)?.topStart?.toPx(size, this) ?: 16.dp.toPx()
                val corner28Px = 28.dp.toPx()
                val cornerPx = if (p < 0.5f) {
                    baseCornerPx + (corner28Px - baseCornerPx) * (p / 0.5f)
                } else {
                    corner28Px * (1f - (p - 0.5f) / 0.5f)
                }
                shape = RoundedCornerShape(cornerPx)
                clip = true
            }
            // ── Deploy animation: expanding card background ──
            // Draws a rounded rect that grows from the mini-player's exact size
            // to full screen. Only visible during the transition (when content is fading).
            .drawBehind {
                if (!isCollapsedOrDismissed) {
                    val p = state.progress.coerceIn(0f, 1f)
                    val miniHeightPx = collapsedContentHeight.toPx()

                    // Card height: grows from mini-player height to full screen height
                    // Anchored at top of box (which appears at bottom of screen due to translationY)
                    val cardHeight = miniHeightPx + (size.height - miniHeightPx) * p

                    // Card width starts at mini-player width (with 16dp padding on each side)
                    // and grows to full width.
                    val horizontalPaddingPx = 16.dp.toPx()
                    val startWidth = size.width - (horizontalPaddingPx * 2)
                    val cardWidth = startWidth + (size.width - startWidth) * p
                    
                    val cardLeft = (size.width - cardWidth) / 2f

                    // Corner radius: bell curve — grows then shrinks
                    val baseCornerPx = (baseShape as? RoundedCornerShape)?.topStart?.toPx(size, this) ?: 16.dp.toPx()
                    val corner28Px = 28.dp.toPx()
                    val cornerPx = if (p < 0.5f) {
                        baseCornerPx + (corner28Px - baseCornerPx) * (p / 0.5f)
                    } else {
                        corner28Px * (1f - (p - 0.5f) / 0.5f)
                    }

                    val currentColor = androidx.compose.ui.graphics.lerp(miniPlayerColor, playerColor, p)

                    drawRoundRect(
                        color = currentColor,
                        topLeft = Offset(cardLeft, 0f),
                        size = Size(cardWidth, cardHeight),
                        cornerRadius = CornerRadius(cornerPx, cornerPx)
                    )
                }
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
        // Fades in from 45% to 100% — slower, smoother transition
        if (!isCollapsedOrDismissed) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = ((state.progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    },
                content = content
            )
        }

        // Mini-player — visible when not fully expanded
        // Fades out/in over 0%–65% — smooth transition, visible as card shrinks
        if (state.progress < 0.65f && !state.isExpanded && (onDismiss == null || !state.isDismissed)) {
            Box(
                modifier =
                Modifier
                    .graphicsLayer {
                        alpha = (1f - state.progress / 0.65f).coerceIn(0f, 1f)
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
