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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.n_zik.android.colorPalette

/**
 * Géométrie de la carte "deploy" à un instant t (progress p).
 * Calculée une seule fois par frame et réutilisée pour le fond (drawBehind)
 * ET pour le clip (graphicsLayer), afin qu'ils restent toujours synchronisés
 * — c'est ce qui évite le débordement de contenu à l'ouverture/fermeture.
 */
internal data class CardGeometry(
    val left: Float,
    val width: Float,
    val height: Float,
    val cornerPx: Float,
)

internal fun computeCardGeometry(
    p: Float,
    size: Size,
    collapsedHeightPx: Float,
    horizontalPaddingPx: Float,
    baseCornerPx: Float,
    corner28Px: Float,
): CardGeometry {
    val cardHeight = collapsedHeightPx + (size.height - collapsedHeightPx) * p
    val startWidth = size.width - (horizontalPaddingPx * 2)
    val cardWidth = startWidth + (size.width - startWidth) * p
    val cardLeft = (size.width - cardWidth) / 2f
    val cornerPx = if (p < 0.5f) {
        baseCornerPx + (corner28Px - baseCornerPx) * (p / 0.5f)
    } else {
        corner28Px * (1f - (p - 0.5f) / 0.5f)
    }
    return CardGeometry(cardLeft, cardWidth, cardHeight, cornerPx)
}

/**
 * Fade-in progress of the full player content (0 = hidden, 1 = visible).
 * Starts at 0.45f, exactly when the mini-player is fully faded
 * (miniPlayerFade), so the hand-over has no pop, no overlap and no
 * empty gap.
 */
internal fun playerContentFade(progress: Float): Float =
    ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)

/**
 * Fade-out progress of the mini-player (1 = visible, 0 = hidden).
 * Starts fading as soon as the drag begins and is fully transparent at
 * 0.45f — right when the player fade-in starts — so the mini-player is
 * already gone before the player appears (no overlap, no empty gap).
 */
internal fun miniPlayerFade(progress: Float): Float =
    1f - (progress / 0.45f).coerceIn(0f, 1f)

/**
 * Shape qui suit exactement le rectangle animé de la carte "deploy"
 * (au lieu d'un RoundedCornerShape plein écran qui ne matchait pas
 * la taille réelle de la carte dessinée dans drawBehind).
 */
private class CardClipShape(private val geometry: CardGeometry) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rounded(
        RoundRect(
            left = geometry.left,
            top = 0f,
            right = geometry.left + geometry.width,
            bottom = geometry.height,
            radiusX = geometry.cornerPx,
            radiusY = geometry.cornerPx,
        )
    )
}

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
    val baseShape = app.n_zik.android.uiRoundnessShape()
    val scope = rememberCoroutineScope()

    val miniPlayerColor = colorPalette().background2

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Single smooth formula — no branching on isCollapsedOrDismissed.
                // progress: 0 = collapsed, 1 = expanded.
                // targetY: distance to push the Box down so the miniplayer (at Box top)
                // sits at the bottom of the parent.
                val targetY = (size.height - collapsedContentHeight.toPx() - bottomPadding.toPx())
                val p = state.progress.coerceIn(0f, 1f)
                translationY = targetY * (1f - p)
            }
            // ── Clip to the exact animated card rect (same geometry as drawBehind) ──
            // MUST precede the pointerInput below: the hit-test walks this chain
            // outside-in, and a clip stops it for any point outside the card. If the
            // drag input were registered first, its hit area would be the whole
            // translated box (full screen when collapsed), covering the navbar strip
            // behind the miniplayer and stealing its taps. clip→input restricts the
            // input area to the animated card rect (miniplayer band when collapsed,
            // full screen when expanded).
            .graphicsLayer {
                val p = state.progress.coerceIn(0f, 1f)
                val baseCornerPx = (baseShape as? RoundedCornerShape)?.topStart?.toPx(size, this) ?: 16.dp.toPx()
                val geometry = computeCardGeometry(
                    p = p,
                    size = size,
                    collapsedHeightPx = collapsedContentHeight.toPx(),
                    horizontalPaddingPx = 16.dp.toPx(),
                    baseCornerPx = baseCornerPx,
                    corner28Px = 28.dp.toPx(),
                )
                shape = CardClipShape(geometry)
                clip = true
            }
            .pointerInput(state, isExpandable, disableDismiss) {
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
            // ── Deploy animation: expanding card background ──
            .drawBehind {
                val p = state.progress.coerceIn(0f, 1f)
                if (p > 0.01f && p < 0.99f) {
                    val baseCornerPx = (baseShape as? RoundedCornerShape)?.topStart?.toPx(size, this) ?: 16.dp.toPx()
                    val geometry = computeCardGeometry(
                        p = p,
                        size = size,
                        collapsedHeightPx = collapsedContentHeight.toPx(),
                        horizontalPaddingPx = 16.dp.toPx(),
                        baseCornerPx = baseCornerPx,
                        corner28Px = 28.dp.toPx(),
                    )
                    // The card keeps the mini-player background color throughout
                    // the deploy animation instead of lerping toward the player color.
                    val currentColor = miniPlayerColor
                    drawRoundRect(
                        color = currentColor,
                        topLeft = Offset(geometry.left, 0f),
                        size = Size(geometry.width, geometry.height),
                        cornerRadius = CornerRadius(geometry.cornerPx, geometry.cornerPx)
                    )
                }
            }
    ) {
        // Player content fade-in (see playerContentFade); the mini-player
        // fades out on its own, earlier curve (see miniPlayerFade).
        val playerAlpha = playerContentFade(state.progress)

        if (!state.isCollapsed && !state.isDismissed) {
            BackHandler(onBack = state::collapseSoft)
        }

        // Drag handle area at the top - only when expanded (visual only, drag handled by main Box)
        if (state.isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            )
        }

        // Main player content — always in composition, GPU-skipped when dragging down.
        // NOT clipped here: the parent Box's graphicsLayer already clips to the
        // animated card shape, so clipping again here would just rebuild the same
        // Outline path a second time per frame for nothing.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Skip the (potentially heavy) draw entirely below the alpha
                    // threshold instead of just setting alpha = 0, which still
                    // records the full display list every frame.
                    if (state.progress >= 0.3f && playerAlpha > 0.01f) {
                        drawContent()
                    }
                }
                .graphicsLayer {
                    alpha = playerAlpha
                },
            content = content
        )

        // Mini-player — visible while the card is not fully deployed. Fades
        // out from 0 to 0.45f (miniPlayerFade), so it is fully transparent
        // right when the player fade-in starts — the hand-over is smooth,
        // without pop, overlap or empty gap. Excluded when dismissed: at
        // the dismissed bound the sheet is closed and keeping the box would
        // leave an invisible tap/drag target at the screen edge.
        // Clipped to the collapsed card shape (geometry at progress 0) while
        // deploying so the mini-player's shadow (drawn inside the legacy
        // MiniPlayer) cannot leak outside its card as the card grows around it.
        if (!state.isExpanded && !state.isDismissed) {
            Box(
                modifier =
                Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (isExpandable) state.expandSoft() },
                    )
                    .fillMaxWidth()
                    .height(collapsedContentHeight)
                    .graphicsLayer {
                        alpha = miniPlayerFade(state.progress)
                        if (state.progress > 0.01f) {
                            val baseCornerPx = (baseShape as? RoundedCornerShape)?.topStart?.toPx(size, this) ?: 16.dp.toPx()
                            shape = CardClipShape(
                                computeCardGeometry(
                                    p = 0f,
                                    size = size,
                                    collapsedHeightPx = collapsedContentHeight.toPx(),
                                    horizontalPaddingPx = 16.dp.toPx(),
                                    baseCornerPx = baseCornerPx,
                                    corner28Px = 28.dp.toPx(),
                                )
                            )
                            clip = true
                        }
                    },
                content = collapsedContent,
            )
        }
    }
}