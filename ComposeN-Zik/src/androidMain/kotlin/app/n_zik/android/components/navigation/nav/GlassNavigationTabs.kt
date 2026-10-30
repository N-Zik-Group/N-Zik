// Backdrop catalog, Apache-2.0: https://github.com/Kyant0/AndroidLiquidGlass
// Source commit b18eb0ff12c616546a68c72e7d0097f1ab286c87; see LICENSE-backdrop.txt.
// Adapted from Square LiquidBottomTabs at 4cde83d, GPL-3.0; see NOTICE-square.txt.
package app.n_zik.android.components.navigation.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.surfaceColor
import app.n_zik.android.components.glass.InteractiveHighlight
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.BackdropEffectScope
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.drawBackdrop
import app.n_zik.android.components.glass.backdrop.effects.blur
import app.n_zik.android.components.glass.backdrop.effects.lens
import app.n_zik.android.components.glass.backdrop.effects.vibrancy
import app.n_zik.android.components.glass.backdrop.highlight.Highlight
import app.n_zik.android.components.glass.backdrop.shadow.InnerShadow
import app.n_zik.android.components.glass.backdrop.shadow.Shadow
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.glassNativeSurfaceTint
import app.n_zik.android.components.glass.isGlassMotionAllowed
import app.n_zik.android.uiRoundnessShape
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal val LocalGlassTabReplica = staticCompositionLocalOf { false }
internal val LocalGlassTabScale = staticCompositionLocalOf { { 1f } }

internal val GlassNavigationBarHeight = 64.dp
internal val GlassNavigationPlayerGap = 18.dp

/** Converts a physical drag into a bounded tab position, including RTL and empty layouts. */
internal fun glassDraggedTab(position: Float, delta: Float, extent: Float, count: Int, reverse: Boolean): Float {
    if (count < 1 || extent <= 0f || !delta.isFinite()) return position
    val step = extent / count
    return (position + delta / step * if (reverse) -1f else 1f).coerceIn(0f, (count - 1).toFloat())
}

internal fun glassSettledTab(position: Float, count: Int): Int =
    if (count < 1 || !position.isFinite()) 0 else position.roundToInt().coerceIn(0, count - 1)

/** The source selector grows along travel and compresses across it as velocity increases. */
internal fun glassTabStretch(scale: Float, velocity: Float, alongTravel: Boolean): Float =
    if (alongTravel) scale / (1f - (velocity / 10f * 0.75f).coerceIn(-0.2f, 0.2f))
    else scale * (1f - (velocity / 10f * 0.25f).coerceIn(-0.2f, 0.2f))

/** Active Square tab control, retaining NZik's actions, vertical rail and reduced-motion policy. */
@Composable
internal fun GlassNavigationTabs(
    selectedIndex: Int,
    indices: List<Int>,
    onTabChanged: (Int) -> Unit,
    settings: GlassSettings,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    drawContainer: Boolean = true,
    externalSurfaceBackdrop: Backdrop? = null,
    containerShape: Shape? = null,
    content: @Composable () -> Unit,
) {
    if (indices.isEmpty()) return
    val count = indices.size
    val selected = indices.indexOf(selectedIndex).coerceAtLeast(0)
    val currentSelected by rememberUpdatedState(selected)
    val currentIndices by rememberUpdatedState(indices)
    val currentCallback by rememberUpdatedState(onTabChanged)
    val density = LocalDensity.current
    val inset = with(density) { 4.dp.roundToPx() }
    if (!settings.usesGlassMaterial(GlassArea.Nav)) {
        val nativeShape = containerShape ?: uiRoundnessShape()
        Box(modifier.then(if (drawContainer) Modifier.background(surfaceColor(colorPalette().background1), nativeShape)
            .glassAreaContour(settings, GlassArea.Nav, nativeShape) else Modifier)) {
            GlassTabLayout(vertical, inset, Modifier.fillMaxSize(), content)
        }
        return
    }
    val reverse = !vertical && LocalLayoutDirection.current == LayoutDirection.Rtl
    val motion = isGlassMotionAllowed()
    val scope = rememberCoroutineScope()
    val extent = remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { Animatable(0f) }
    val panelOffset by remember(density, motion) {
        derivedStateOf {
            if (!motion || extent.floatValue <= 0f) 0f else {
                val fraction = (offsetAnimation.value / extent.floatValue).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
    }
    val animation = remember(scope, count, vertical, reverse, motion, inset) {
        GlassTabDragAnimation(
            animationScope = scope,
            motionAllowed = motion,
            initialValue = selected.toFloat(),
            valueRange = 0f..(count - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            onDragStarted = {},
            onDragStopped = {
                val target = glassSettledTab(targetValue, count)
                animateToValue(target.toFloat())
                if (target != currentSelected) currentCallback(currentIndices[target])
                scope.launch {
                    if (motion) offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    else offsetAnimation.snapTo(0f)
                }
            },
            onDrag = { _, amount ->
                val delta = if (vertical) amount.y else amount.x
                updateValue(glassDraggedTab(targetValue, delta, extent.floatValue - 2 * inset, count, reverse))
                if (motion) scope.launch { offsetAnimation.snapTo(offsetAnimation.value + delta) }
            },
        )
    }
    LaunchedEffect(selected, animation) {
        if (animation.targetValue != selected.toFloat()) animation.animateToValue(selected.toFloat())
    }
    val interactiveHighlight = remember(scope, animation, vertical, reverse, inset) {
        InteractiveHighlight(scope) { size, _ ->
            val step = (extent.floatValue - 2 * inset) / count
            val center = (animation.value + 0.5f) * step + panelOffset
            if (vertical) Offset(size.width / 2f, center)
            else Offset(if (reverse) size.width - center else center, size.height / 2f)
        }
    }
    val backdrop = LocalGlassBackdrop.current
    val renderer = glassRenderer(settings)
    val optical = backdrop != null && renderer != GlassRenderer.Tint
    val tabsBackdrop = rememberLayerBackdrop()
    val ownSurfaceBackdrop = rememberLayerBackdrop()
    val surfaceBackdrop = externalSurfaceBackdrop ?: ownSurfaceBackdrop
    val shape = uiRoundnessShape()
    val panelShape = containerShape ?: shape
    val originalFilm = surfaceColor(colorPalette().background1)
    val tintedFilm = glassNativeSurfaceTint(settings, originalFilm)
    val film = tintedFilm.copy(alpha = tintedFilm.alpha * settings.film.coerceIn(0f, 1f))
    val wash = (if (colorPalette().isDark) Color.Black else Color.White)
        .copy(alpha = settings.indicatorOpacity.coerceIn(0f, 1f))
    val panelTranslation = Modifier.graphicsLayer {
        if (vertical) translationY = panelOffset else translationX = panelOffset
    }
    val slotLens: BackdropEffectScope.() -> Unit = {
        if (renderer == GlassRenderer.Refraction) {
            val progress = animation.pressProgress
            lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, dispersion = 1f)
        }
    }
    Box(modifier.onSizeChanged {
        extent.floatValue = (if (vertical) it.height else it.width).toFloat()
    }) {
        // A sibling capture avoids sampling the selector into its own glass.
        if (drawContainer) Box(
            panelTranslation.fillMaxSize().layerBackdrop(ownSurfaceBackdrop)
                .glassMaterial(
                    settings, panelShape, highlightAlpha = .3f, filmColor = originalFilm,
                    layerBlock = {
                        val axisExtent = if (vertical) size.height else size.width
                        val scale = lerp(1f, 1f + 16.dp.toPx() / axisExtent.coerceAtLeast(1f), animation.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                ).clip(panelShape)
                .then(if (motion) interactiveHighlight.modifier else Modifier),
        )
        GlassTabLayout(
            vertical, inset,
            // Departing glyphs may outlive a shrinking tab strip; the material keeps its own clip.
            panelTranslation.fillMaxSize(),
            content,
        )
        if (optical) {
            CompositionLocalProvider(
                LocalGlassTabReplica provides true,
                LocalGlassTabScale provides { lerp(1f, 1.2f, animation.pressProgress) },
            ) {
                GlassTabLayout(
                    vertical, inset,
                    Modifier.fillMaxSize().clearAndSetSemantics {}.alpha(0f)
                        .layerBackdrop(tabsBackdrop).then(panelTranslation),
                    content,
                )
            }
        }
        val slotPlacement = Modifier.layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val inner = ((if (vertical) height else width) - 2 * inset).coerceAtLeast(count)
            val step = inner.toFloat() / count
            val child = measurable.measure(Constraints.fixed(
                if (vertical) (width - 2 * inset).coerceAtLeast(1) else step.roundToInt().coerceAtLeast(1),
                if (vertical) step.roundToInt().coerceAtLeast(1) else (height - 2 * inset).coerceAtLeast(1),
            ))
            layout(width, height) {
                val physical = if (reverse) count - 1 - animation.value else animation.value
                val position = inset + (physical * step + panelOffset).roundToInt()
                child.place(if (vertical) inset else position, if (vertical) position else inset)
            }
        }
        val slotGrowth = Modifier.layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val scaleX = glassTabStretch(animation.scaleX, animation.velocity, !vertical)
            val scaleY = glassTabStretch(animation.scaleY, animation.velocity, vertical)
            val expandedWidth = (width * scaleX).roundToInt().coerceAtLeast(1)
            val expandedHeight = (height * scaleY).roundToInt().coerceAtLeast(1)
            val child = measurable.measure(Constraints.fixed(expandedWidth, expandedHeight))
            layout(width, height) {
                child.place((width - expandedWidth) / 2, (height - expandedHeight) / 2)
            }
        }
        val pressedBackdrop = remember(backdrop, surfaceBackdrop, animation) {
            object : Backdrop {
                private val surfacePaint = Paint()
                override val isCoordinatesDependent = true
                override fun DrawScope.drawBackdrop(
                    density: Density,
                    coordinates: LayoutCoordinates?,
                    layerBlock: (GraphicsLayerScope.() -> Unit)?,
                ) {
                    backdrop?.let { with(it) { drawBackdrop(density, coordinates, layerBlock) } }
                    val progress = animation.pressProgress.coerceIn(0f, 1f)
                    if (progress > 0f) {
                        surfacePaint.alpha = progress
                        val canvas = drawContext.canvas
                        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), surfacePaint)
                        try {
                            with(surfaceBackdrop) { drawBackdrop(density, coordinates, layerBlock) }
                        } finally {
                            canvas.restore()
                        }
                    }
                }
            }
        }
        Box(
            slotPlacement
                .then(if (motion) interactiveHighlight.gestureModifier else Modifier)
                .then(animation.modifier)
                .then(slotGrowth)
                .then(if (optical) Modifier.drawBackdrop(
                    backdrop = pressedBackdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx() * (1f - animation.pressProgress.coerceIn(0f, 1f)))
                        slotLens()
                    },
                    highlight = if (settings.rim > 0f) ({ Highlight.Default.copy(alpha = animation.pressProgress * (settings.rim / .55f).coerceIn(0f, 1f)) }) else null,
                    shadow = { Shadow(alpha = animation.pressProgress) },
                    innerShadow = {
                        InnerShadow(radius = 8.dp * animation.pressProgress, alpha = animation.pressProgress)
                    },
                    onDrawSurface = {
                        val progress = animation.pressProgress
                        drawRect(film, alpha = 1f - progress)
                        drawRect(wash, alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                ) else Modifier.clip(shape).background(wash)),
        )
        if (optical) {
            // The lit glyphs are refracted above the wash, as in Square's active component.
            Box(slotPlacement.then(slotGrowth).drawBackdrop(
                backdrop = tabsBackdrop,
                shape = { shape },
                effects = slotLens,
                highlight = null,
                shadow = null,
            ))
        }
    }
}

@Composable
private fun GlassTabLayout(
    vertical: Boolean,
    inset: Int,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val count = measurables.size.coerceAtLeast(1)
        val inner = ((if (vertical) height else width) - 2 * inset).coerceAtLeast(count)
        val step = inner.toFloat() / count
        val children = measurables.map { measurable ->
            measurable.measure(Constraints.fixed(
                if (vertical) (width - 2 * inset).coerceAtLeast(1) else step.roundToInt().coerceAtLeast(1),
                if (vertical) step.roundToInt().coerceAtLeast(1) else (height - 2 * inset).coerceAtLeast(1),
            ))
        }
        layout(width, height) {
            children.forEachIndexed { index, child ->
                if (vertical) child.place(inset, inset + (index * step).roundToInt())
                else child.placeRelative(inset + (index * step).roundToInt(), inset)
            }
        }
    }
}
