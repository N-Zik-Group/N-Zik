// Backdrop catalog, Apache-2.0: https://github.com/Kyant0/AndroidLiquidGlass
// Source commit b18eb0ff12c616546a68c72e7d0097f1ab286c87; see LICENSE-backdrop.txt.
// Square adaptations: GPL-3.0, see NOTICE-square.txt.

package app.n_zik.android.components.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.searchBubbleShape
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** [retainGesture] keeps fixed controls from scrolling ancestors; movable overlays opt out. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    settings: GlassSettings = rememberGlassSettings(),
    isInteractive: Boolean = true,
    retainGesture: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    filmColor: Color? = null,
    wash: Color = Color.Unspecified,
    contentHeight: Dp = 48f.dp,
    contentPadding: Dp = 16f.dp,
    blurScale: Float = 1f,
    flat: Boolean = false,
    menuTrigger: GlassMenuTriggerState? = null,
    shape: Shape = searchBubbleShape(),
    backdropScale: Float = 0.4f,
    content: @Composable RowScope.() -> Unit
) {
    val interactive = isInteractive && isGlassMotionAllowed()
    val animationScope = rememberCoroutineScope()
    if (menuTrigger != null) {
        val latestContent = rememberUpdatedState(content)
        val sourceLocals = currentCompositionLocalContext
        val sourceContent: @Composable () -> Unit = remember(sourceLocals) {
            {
                CompositionLocalProvider(sourceLocals) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { latestContent.value(this) }
                }
            }
        }
        SideEffect {
            menuTrigger.content = sourceContent
            menuTrigger.shape = shape
        }
    }

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    val configured = settings
    val config = if (flat) configured.copy(quality = GlassQuality.Performance) else configured
    Row(
        modifier
            .glassMenuTrigger(menuTrigger)
            .retainGlassButtonGesture(retainGesture)
            .glassMaterial(
                settings = config,
                shape = shape,
                filmColor = filmColor,
                blurRadiusDp = (config.blur * blurScale).coerceAtLeast(0f),
                backdropScale = backdropScale,
                highlightAlpha = 0.3f,
                ownBackdrop = backdrop,
                layerBlock = if (interactive) {
                    {
                        val width = size.width
                        val height = size.height

                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                    maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                    (width / height).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                    maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                    (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawTint = if (tint.isSpecified || surfaceColor.isSpecified || wash.isSpecified) {
                    {
                        if (tint.isSpecified) {
                            drawRect(tint, blendMode = BlendMode.Hue)
                            drawRect(tint.copy(alpha = 0.75f))
                        }
                        if (surfaceColor.isSpecified) {
                            drawRect(surfaceColor)
                        }
                        if (wash.isSpecified) {
                            drawRect(
                                Brush.linearGradient(
                                    0f to wash,
                                    0.55f to wash.copy(alpha = wash.alpha * 0.34f),
                                    1f to Color.Transparent,
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height),
                                ),
                            )
                            drawRect(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.14f),
                                        Color.Transparent,
                                    ),
                                    endY = size.height * 0.6f,
                                ),
                            )
                        }
                    }
                } else {
                    null
                },
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = null,
                        indication = if (interactive) null else LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = if (interactive) null else LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                    )
                }
            )
            .then(
                if (interactive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .height(contentHeight)
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
