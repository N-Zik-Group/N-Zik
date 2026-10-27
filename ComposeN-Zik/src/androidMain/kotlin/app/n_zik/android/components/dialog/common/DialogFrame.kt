package app.n_zik.android.components.dialog.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.colorPalette
import app.n_zik.android.recessedSurfaceColor
import app.n_zik.android.surfaceColor
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

/** Shared dialog surface with bounded width and height. */
@Composable
fun DialogFrame(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    bodyPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    if (LocalDialogPresentation.current == null) {
        DialogPresence(visible = true) {
            DialogFrame(onDismiss, modifier, title, horizontalAlignment, footer, bodyPadding, content)
        }
        return
    }
    val presentation = requireNotNull(LocalDialogPresentation.current)
    val interactive = presentation.interactive
    val folding = title != null && footer != null
    SideEffect { presentation.durationMillis = dialogDuration(folding) }
    val bodyHeight = remember { mutableFloatStateOf(0f) }
    val cardBounds = remember { mutableStateOf(Rect.Zero) }
    val shape = uiRoundnessShape()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val maxWidth =
        if (isLandscape) Dialog.MAX_WIDTH_LANDSCAPE else Dialog.MAX_WIDTH_PORTRAIT
    val maxHeight =
        if (isLandscape) Dialog.MAX_HEIGHT_LANDSCAPE else Dialog.MAX_HEIGHT_PORTRAIT

    Dialog(
        onDismissRequest = { if (presentation.interactive) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = interactive,
            dismissOnClickOutside = interactive
        )
    ) {
        val focusManager = LocalFocusManager.current
        LaunchedEffect(interactive) { if (!interactive) focusManager.clearFocus(force = true) }
        CompositionLocalProvider(
            LocalDialogPresentation provides null,
            LocalDialogInput provides presentation
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().testTag("dialogFrameViewport")
                    .onPreviewKeyEvent { !presentation.interactive }
                    .pointerInput(onDismiss) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (!cardBounds.value.contains(down.position)) {
                                down.consume()
                                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                up?.consume()
                                if (up != null && presentation.interactive) onDismiss()
                            }
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        val motion = presentation.motion.value
                        val flash = dialogFlashAlpha(motion, folding)
                        if (flash > 0f) {
                            val width = size.width * .98f * dialogLineFraction(motion, folding)
                            val left = (size.width - width) / 2f
                            val center = size.height / 2f
                            // Two soft light lobes spread through the surface without a blur pass.
                            val outerRadius = 48.dp.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    .25f to Color.White.copy(alpha = flash * .08f),
                                    .5f to Color.White.copy(alpha = flash * .28f),
                                    .75f to Color.White.copy(alpha = flash * .08f),
                                    1f to Color.Transparent,
                                    startY = center - outerRadius, endY = center + outerRadius
                                ),
                                topLeft = Offset(left, center - outerRadius),
                                size = Size(width, outerRadius * 2f)
                            )
                            val radius = 24.dp.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = flash * .7f), Color.Transparent),
                                    startY = center - radius, endY = center + radius
                                ),
                                topLeft = Offset(left, center - radius),
                                size = Size(width, radius * 2f)
                            )
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent, .06f to Color.White,
                                    .94f to Color.White, 1f to Color.Transparent,
                                    startX = left, endX = left + width.coerceAtLeast(1f)
                                ),
                                topLeft = Offset(left, center - 3.dp.toPx()),
                                size = Size(width, 6.dp.toPx()), alpha = flash
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = modifier
                        .testTag("dialogFrameCard")
                        .onGloballyPositioned { cardBounds.value = it.boundsInParent() }
                        .then(if (interactive) Modifier else Modifier
                            .clearAndSetSemantics { }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                    }
                                }
                            })
                        .wrapContentSize()
                        .sizeIn(
                            maxWidth = screenWidthDp * maxWidth,
                            maxHeight = screenHeightDp * maxHeight
                        )
                        // Tight width prevents marquee children from repeatedly changing the window size.
                        .width(screenWidthDp * maxWidth)
                        .padding(16.dp)
                        .graphicsLayer {
                            val motion = presentation.motion.value
                            val bodyFraction = if (folding) dialogBodyFraction(motion) else 1f
                            val foldedHeight = (size.height - (bodyHeight.floatValue + 1.dp.toPx()) * (1f - bodyFraction))
                                .coerceAtLeast(1f)
                            val height = 1f + ((if (folding) foldedHeight else size.height) - 1f) * dialogTvFraction(motion, folding)
                            // An extra clip at rest would cut off the existing elevated Card shadow.
                            clip = motion < 1f
                            alpha = dialogTvAlpha(motion, folding)
                            this.shape = DialogMotionShape(shape, dialogLineFraction(motion, folding), height / size.height.coerceAtLeast(1f))
                        },
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        // Elevated surface: dark gray in pitch black instead of pure black
                        containerColor = surfaceColor(colorPalette().background1)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        title?.let {
                            Column(
                                modifier = Modifier
                                    .testTag("dialogFrameHeader")
                                    .graphicsLayer {
                                        if (folding) translationY = (bodyHeight.floatValue + 1.dp.toPx()) * (1f - dialogBodyFraction(presentation.motion.value)) / 2f
                                    }
                                    .fillMaxWidth()
                                    .background(surfaceColor(colorPalette().background1))
                                    .padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                BasicText(
                                    text = it,
                                    modifier = Modifier.fillMaxWidth().testTag("dialogFrameTitle"),
                                    style = typography().l.semiBold.copy(
                                        color = colorPalette().text,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.height(1.dp).testTag("dialogFrameHeaderDivider")
                                    .graphicsLayer {
                                        if (folding) translationY = (bodyHeight.floatValue + 1.dp.toPx()) * (1f - dialogBodyFraction(presentation.motion.value)) / 2f
                                    }
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("dialogFrameBody")
                                .onSizeChanged { bodyHeight.floatValue = it.height.toFloat() }
                                .clipToBounds()
                                .graphicsLayer {
                                    val fraction = if (folding) dialogBodyFraction(presentation.motion.value) else 1f
                                    // Empty outlines are not reliably empty on every Android renderer.
                                    alpha = if (fraction == 0f) 0f else 1f
                                    clip = true
                                    this.shape = DialogMotionShape(RectangleShape, 1f, fraction)
                                }
                                .fillMaxWidth()
                                .background(recessedSurfaceColor(colorPalette().background0))
                                .padding(bodyPadding),
                            horizontalAlignment = horizontalAlignment,
                            content = content
                        )
                        footer?.let {
                            HorizontalDivider(
                                modifier = Modifier
                                    .graphicsLayer {
                                        if (folding) {
                                            val fraction = dialogBodyFraction(presentation.motion.value)
                                            translationY = -(bodyHeight.floatValue + 1.dp.toPx()) * (1f - fraction) / 2f
                                            // Both strokes occupy the same pixel at the meeting point.
                                            alpha = if (fraction == 0f) 0f else 1f
                                        }
                                    }
                                    .height(1.dp).testTag("dialogFrameFooterDivider")
                            )
                            Column(
                                modifier = Modifier
                                    .testTag("dialogFrameFooter")
                                    .graphicsLayer {
                                        if (folding) translationY = -(bodyHeight.floatValue + 1.dp.toPx()) * (1f - dialogBodyFraction(presentation.motion.value)) / 2f
                                    }
                                    .fillMaxWidth()
                                    .background(surfaceColor(colorPalette().background1))
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                horizontalAlignment = horizontalAlignment,
                                content = it
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Centered clipping changes only the rendered outline, never child or window constraints. */
private class DialogMotionShape(
    private val base: Shape,
    private val widthFraction: Float,
    private val heightFraction: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val clipped = Size(size.width * widthFraction, size.height * heightFraction)
        return Outline.Generic(Path().apply {
            addOutline(base.createOutline(clipped, layoutDirection, density))
            translate(Offset((size.width - clipped.width) / 2f, (size.height - clipped.height) / 2f))
        })
    }
}
