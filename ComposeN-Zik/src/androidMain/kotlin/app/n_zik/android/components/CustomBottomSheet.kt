package app.n_zik.android.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.n_zik.android.colorPalette
import app.n_zik.android.surfaceColor
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.player.rememberMiniPlayerPlacementMotion

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
    startInsetPx: Float = 0f,
    endInsetPx: Float = 0f,
): CardGeometry {
    val cardHeight = collapsedHeightPx + (size.height - collapsedHeightPx) * p
    // The collapsed card can be narrowed on either side (a navigation rail there); it still
    // grows to the full width, so its left edge goes from the collapsed edge to 0
    val collapsedLeft = horizontalPaddingPx + startInsetPx
    val startWidth = size.width - collapsedLeft - (horizontalPaddingPx + endInsetPx)
    val cardWidth = startWidth + (size.width - startWidth) * p
    val cardLeft = collapsedLeft * (1f - p)
    val cornerPx = if (p < 0.5f) {
        baseCornerPx + (corner28Px - baseCornerPx) * (p / 0.5f)
    } else {
        corner28Px * (1f - (p - 0.5f) / 0.5f)
    }
    return CardGeometry(cardLeft, cardWidth, cardHeight, cornerPx)
}

/** Resolve bounded user roundness against the actual mini, never the fullscreen sheet. */
internal fun collapsedCardCornerPx(
    shape: Shape,
    size: Size,
    collapsedHeightPx: Float,
    horizontalPaddingPx: Float,
    startInsetPx: Float = 0f,
    endInsetPx: Float = 0f,
    density: Density,
): Float {
    val collapsedSize = Size(
        (size.width - 2f * horizontalPaddingPx - startInsetPx - endInsetPx).coerceAtLeast(0f),
        collapsedHeightPx.coerceAtLeast(0f),
    )
    return (shape as? RoundedCornerShape)?.topStart?.toPx(collapsedSize, density)
        ?: with(density) { 16.dp.toPx() }
}

/**
 * Vertical translation of the sheet box, which spans the whole parent and holds the
 * mini-player at its top edge.
 *
 * At progress 1 the sheet is full screen (no translation). At progress 0 the box is pushed
 * down so the mini-player sits at the bottom, or, when [topPaddingPx] is set, only down to
 * that distance from the top of the parent.
 *
 * @param topPaddingPx Distance from the parent's top edge when the mini-player is anchored
 *        at the top; null anchors it at the bottom above [bottomPaddingPx].
 */
internal fun sheetTranslationY(
    progress: Float,
    parentHeightPx: Float,
    collapsedHeightPx: Float,
    bottomPaddingPx: Float,
    topPaddingPx: Float?,
): Float {
    val collapsedY = topPaddingPx ?: (parentHeightPx - collapsedHeightPx - bottomPaddingPx)
    return collapsedY * (1f - progress.coerceIn(0f, 1f))
}

/**
 * Drag delta handed to the sheet state, which opens the sheet on a negative (upward) delta.
 * Anchored at the top the sheet deploys downwards, so the gesture is mirrored: dragging
 * down opens it and dragging up closes it.
 */
internal fun sheetDragDelta(dragAmountPx: Float, anchoredAtTop: Boolean): Float =
    if (anchoredAtTop) -dragAmountPx else dragAmountPx

/**
 * Fling velocity handed to [PlayerSheetState.performFling], where a positive value expands the
 * sheet and a negative one collapses or dismisses it. Mirrored when anchored at the top.
 *
 * @param velocityYPx Vertical velocity of the finger: negative when moving up.
 */
internal fun sheetFlingVelocity(velocityYPx: Float, anchoredAtTop: Boolean): Float =
    if (anchoredAtTop) velocityYPx else -velocityYPx

/**
 * Mirrors [PlayerSheetState.performFling]'s dismiss decision (velocity thresholds + the
 * pulled-position band) so a disabled dismiss can be reported as a blocked attempt instead of a
 * silent spring-back. Keep in sync with [PlayerSheetState.performFling].
 */
internal fun flingWouldDismiss(state: PlayerSheetState, velocity: Float): Boolean {
    if (velocity > 250f) return false
    if (velocity < -100f) return state.value <= state.collapsedBound
    val l0 = state.dismissedBound
    val l1 = state.dismissedBound + (state.collapsedBound - state.dismissedBound) * 0.80f
    return state.value in l0..l1
}

/**
 * Fade-in progress of the full player content (0 = hidden, 1 = visible).
 * Starts at 0.45f, exactly when the mini-player is fully faded
 * (miniPlayerFade), so the hand-over has no pop, no overlap and no
 * empty gap.
 */
const val PLAYER_SHEET_HANDOVER_PROGRESS = 0.45f

fun playerContentFade(progress: Float): Float =
    ((progress - PLAYER_SHEET_HANDOVER_PROGRESS) / (1f - PLAYER_SHEET_HANDOVER_PROGRESS)).coerceIn(0f, 1f)

/**
 * Fade-out progress of the mini-player (1 = visible, 0 = hidden).
 * Starts fading as soon as the drag begins and is fully transparent at
 * 0.45f — right when the player fade-in starts — so the mini-player is
 * already gone before the player appears (no overlap, no empty gap).
 */
fun miniPlayerFade(progress: Float): Float =
    1f - (progress / PLAYER_SHEET_HANDOVER_PROGRESS).coerceIn(0f, 1f)

/** Glass fades its body first, retaining the cover and controls until the full player takes over. */
internal fun miniPlayerContentFade(progress: Float, glassMaterial: Boolean): Float {
    val position = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    return if (glassMaterial) 1f - playerContentFade(position) else miniPlayerFade(position)
}

/** Progress at or below which the deploy card background is skipped: the mini-player still renders its own surface there, so drawing the card would only paint a sliver behind it. */
internal const val CARD_BG_COLLAPSED_EPSILON = 0.01f

/**
 * Whether the deploy card's background fill is drawn at the given progress.
 *
 * The card fill is the sheet's opaque base, so it must persist all the way to
 * fully expanded (progress = 1): otherwise the player background's legitimate
 * transparent regions (the rotating DstOut band of the fluid gradient, the
 * letterboxed cover, 0% blurred backdrop) expose the screen behind the sheet
 * (issue #855). Skipped for progress <= [CARD_BG_COLLAPSED_EPSILON] (collapsed
 * state and the first instants of the deploy), where the mini-player renders
 * its own surface.
 */
internal fun shouldDrawCardBackground(progress: Float): Boolean =
    progress.coerceIn(0f, 1f) > CARD_BG_COLLAPSED_EPSILON

/** Shape qui suit exactement le rectangle animé de la carte "deploy" (au lieu d'un RoundedCornerShape plein écran qui ne matchait pas la taille réelle de la carte dessinée dans drawBehind). */
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
 * Bottom sheet with an "expand / deploy" animation: the background card deploys from the
 * mini-player's dimensions to full screen while the mini-player content fades out and the
 * player content fades in (the content itself does not scale; closing reverses the effect).
 *
 * @param bottomPadding Bottom padding for the collapsed position; a draw-time provider, so an
 *        animated value moves the sheet without recomposing it.
 * @param topPadding When non-null, anchors the mini-player at the top edge (draw-time provider).
 * @param collapsedContentHeight Visual height of the mini-player content; the collapsed hit
 *        target. Defaults to [PlayerSheetState.collapsedBound].
 * @param collapsedHeightProvider Optional animated height, read in layout and drawing only.
 * @param collapsedStartInsetProvider Optional animated start inset shared by clipping and hit bounds.
 * @param collapsedEndInsetProvider Optional animated end inset shared by clipping and hit bounds.
 * @param collapsedHorizontalPaddingProvider Mini-player internal horizontal margin, shared with its clip.
 */
@Composable
fun CustomBottomSheet(
    state: PlayerSheetState,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    bottomPadding: () -> Dp = { 0.dp },
    topPadding: (() -> Dp)? = null,
    placementKey: Any? = null,
    verticalOffset: () -> Dp = { 0.dp },
    collapsedStartInset: Dp = 0.dp,
    collapsedEndInset: Dp = 0.dp,
    collapsedContentHeight: Dp = state.collapsedBound,
    collapsedHeightProvider: (() -> Dp)? = null,
    collapsedStartInsetProvider: (() -> Dp)? = null,
    collapsedEndInsetProvider: (() -> Dp)? = null,
    collapsedHorizontalPaddingProvider: (() -> Dp)? = null,
    disableDismiss: Boolean = false,
    /** Called when a dismiss gesture is attempted while [disableDismiss] is on (blocked attempt). */
    onDismissBlocked: (() -> Unit)? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    isExpandable: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val collapsedHeight = { (collapsedHeightProvider?.invoke() ?: collapsedContentHeight).coerceAtLeast(0.dp) }
    val startInset = { (collapsedStartInsetProvider?.invoke() ?: collapsedStartInset).coerceAtLeast(0.dp) }
    val endInset = { (collapsedEndInsetProvider?.invoke() ?: collapsedEndInset).coerceAtLeast(0.dp) }
    val horizontalPadding = { (collapsedHorizontalPaddingProvider?.invoke() ?: 16.dp).coerceAtLeast(0.dp) }
    val baseShape = app.n_zik.android.uiRoundnessShape()
    val scope = rememberCoroutineScope()
    val glass = rememberGlassSettings()
    val miniPlayerGlass = glass.usesGlassMaterial(GlassArea.MiniPlayer)
    val deployGlassActive by remember(state, miniPlayerGlass) {
        derivedStateOf { miniPlayerGlass && isPlayerDeployGlassActive(state.progress) }
    }
    val deployBackdrop = if (deployGlassActive) rememberLayerBackdrop() else null
    val pageBackdrop = LocalGlassMenuHost.current?.backdrop

    // Elevated surface: the deployed card stays on the mini-player's surface
    // color (dark gray in pitch black) instead of pure black
    val miniPlayerColor = surfaceColor(colorPalette().background2)
    val anchoredAtTop = topPadding != null
    val animatedPlacement = rememberMiniPlayerPlacementMotion(placementKey)

    CompositionLocalProvider(LocalGlassContainerBackdrop provides
        if (deployGlassActive) deployBackdrop else LocalGlassContainerBackdrop.current) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val offset = verticalOffset().toPx()
                val collapsedY = sheetTranslationY(
                    progress = 0f,
                    parentHeightPx = size.height,
                    collapsedHeightPx = collapsedHeight().toPx(),
                    bottomPaddingPx = bottomPadding().toPx(),
                    topPaddingPx = topPadding?.invoke()?.toPx(),
                )
                // Deployment stays finger-driven; only the collapsed anchor changes with navigation.
                translationY = (animatedPlacement(collapsedY + offset) - offset) *
                    (1f - state.progress.coerceIn(0f, 1f)) + offset
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
                val baseCornerPx = collapsedCardCornerPx(baseShape, size, collapsedHeight().toPx(),
                    horizontalPadding().toPx(), startInset().toPx(), endInset().toPx(), this)
                val geometry = computeCardGeometry(
                    p = p,
                    size = size,
                    collapsedHeightPx = collapsedHeight().toPx(),
                    horizontalPaddingPx = horizontalPadding().toPx(),
                    baseCornerPx = baseCornerPx,
                    corner28Px = 28.dp.toPx(),
                    startInsetPx = startInset().toPx(),
                    endInsetPx = endInset().toPx(),
                )
                shape = CardClipShape(geometry)
                clip = true
            }
            .pointerInput(state, isExpandable, disableDismiss, anchoredAtTop) {
                if (!isExpandable) return@pointerInput
                val velocityTracker = VelocityTracker()

                detectMostlyVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        velocityTracker.addPointerInputChange(change)
                        state.dispatchRawDelta(sheetDragDelta(dragAmount, anchoredAtTop))
                    },
                    onDragAbort = {
                        velocityTracker.resetTracking()
                    },
                    onDragCancel = {
                        velocityTracker.resetTracking()
                        state.snapTo(state.collapsedBound)
                    },
                    onDragEnd = { aborted ->
                        // A gesture that ended sideways must not fling with the velocity of the
                        // vertical phase that preceded it: settle with a zero velocity instead.
                        val velocity = if (aborted) 0f else sheetFlingVelocity(velocityTracker.calculateVelocity().y, anchoredAtTop)
                        velocityTracker.resetTracking()
                        // A dismiss attempt that is disabled (e.g. the Listen Together guest lock)
                        // is a blocked attempt, not a silent spring-back: report it.
                        if (disableDismiss && onDismiss != null && flingWouldDismiss(state, velocity)) {
                            onDismissBlocked?.invoke()
                        }
                        state.performFling(velocity, if (!disableDismiss) onDismiss else null)
                    }
                )
            }
            // ── Card background: expands during the deploy, then stays as the sheet's opaque base (#855) ──
            .drawBehind {
                val p = state.progress.coerceIn(0f, 1f)
                if (!miniPlayerGlass && shouldDrawCardBackground(p)) {
                    val baseCornerPx = collapsedCardCornerPx(baseShape, size, collapsedHeight().toPx(),
                    horizontalPadding().toPx(), startInset().toPx(), endInset().toPx(), this)
                    val geometry = computeCardGeometry(
                        p = p,
                        size = size,
                        collapsedHeightPx = collapsedHeight().toPx(),
                        horizontalPaddingPx = horizontalPadding().toPx(),
                        baseCornerPx = baseCornerPx,
                        corner28Px = 28.dp.toPx(),
                        startInsetPx = startInset().toPx(),
                        endInsetPx = endInset().toPx(),
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
        if (deployBackdrop != null) PlayerDeployGlassSurface(
            progress = { state.progress }, collapsedHeight = collapsedHeight(),
            startInset = startInset(), endInset = endInset(),
            horizontalPadding = horizontalPadding(),
            baseShape = baseShape, settings = glass, backdrop = pageBackdrop,
            filmColor = miniPlayerColor, exportedBackdrop = deployBackdrop,
        )
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
                // Player content fade-in (see playerContentFade); the mini-player
                // fades out on its own, earlier curve (see miniPlayerFade).

                // progress is read only inside these draw-phase lambdas, never in
                // composition: a composition read would recompose this whole sheet

                // on every frame of a drag or an animated pop/dismiss.
                .drawWithContent {
                    // Skip the (potentially heavy) draw entirely below the alpha
                    // threshold instead of just setting alpha = 0, which still

                    // records the full display list every frame.
                    val progress = state.progress
                    if (progress >= 0.3f && playerContentFade(progress) > 0.01f) {
                        drawContent()
                    }
                }
                .graphicsLayer {
                    alpha = playerContentFade(state.progress)
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
                    // Before the click target, so a navigation rail beside the mini-player keeps its taps
                    .layout { measurable, constraints ->
                        val left = startInset().roundToPx().coerceIn(0, constraints.maxWidth)
                        val right = endInset().roundToPx().coerceIn(0, constraints.maxWidth - left)
                        val height = collapsedHeight().roundToPx().coerceIn(0, constraints.maxHeight)
                        val width = (constraints.maxWidth - left - right).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width, minHeight = height, maxHeight = height)
                        )
                        layout(constraints.maxWidth, height) { placeable.placeRelative(left, 0) }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (isExpandable) state.expandSoft() },
                    )
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = miniPlayerContentFade(state.progress, miniPlayerGlass)
                        if (state.progress > 0.01f) {
                            val baseCornerPx = collapsedCardCornerPx(baseShape, size, collapsedHeight().toPx(),
                                horizontalPadding().toPx(), density = this)
                            shape = CardClipShape(
                                computeCardGeometry(
                                    p = 0f,
                                    size = size,
                                    collapsedHeightPx = collapsedHeight().toPx(),
                                    horizontalPaddingPx = horizontalPadding().toPx(),
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
}
