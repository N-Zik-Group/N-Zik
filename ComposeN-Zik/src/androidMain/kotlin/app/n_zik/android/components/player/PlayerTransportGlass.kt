package app.n_zik.android.components.player

import androidx.compose.foundation.Indication
import app.n_zik.android.components.ui.nativeControlIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.screens.player.bounceClick
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.n_zik.android.components.glass.glassButtonFeedback
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.glassOpticsProgress
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.retainGlassButtonGesture
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.uiRoundnessShape
import androidx.compose.ui.zIndex

/** Native and material branches share the ribbed silhouette without changing ordinary styles. */
@Composable
fun playerTransportPlayShape(type: PlayerPlayButtonType, rotation: Float = 0f): Shape {
    if (type != PlayerPlayButtonType.CircularRibbed) return uiRoundnessShape()
    return remember(rotation) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val outline = PlayerTransportRibbedShape.createOutline(size, layoutDirection, density) as Outline.Generic
                val matrix = Matrix().apply {
                    translate(size.width / 2f, size.height / 2f)
                    rotateZ(rotation)
                    translate(-size.width / 2f, -size.height / 2f)
                }
                return Outline.Generic(Path().apply { addPath(outline.path); transform(matrix) })
            }
        }
    }
}

/** Passive transport material; the original control retains its sole gesture owner. */
@Composable
fun Modifier.playerTransportSurface(
    filmColor: Color? = null,
    shape: Shape = uiRoundnessShape(),
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTransport),
): Modifier {
    val settings = settings.inScope(GlassScope.PlayerTransport)
    return if (settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        glassMaterial(settings.forArea(GlassArea.ActionButtons), shape,
            ownBackdrop = LocalGlassContainerBackdrop.current, filmColor = filmColor,
            separateWindow = LocalGlassContainerBackdrop.current == null)
    } else glassAreaContour(settings, GlassArea.ActionButtons, shape)
}

/** Existing filled controls keep their native fill unless the transport material replaces it. */
@Composable
internal fun Modifier.playerTransportFilledBackground(
    original: Color,
    shape: Shape = uiRoundnessShape(),
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTransport),
): Modifier = background(playerTransportNativeFill(original, settings), shape)
    .playerTransportSurface(original, shape, settings)

/** Preserve the optional legacy shrink only when Glass feedback is disabled. */
@Composable
fun Modifier.playerTransportNativeBounce(settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTransport)): Modifier =
    if (settings.enabled) this else bounceClick()

/** Adds feedback to the existing gesture source without creating another clickable. */
@Composable
fun Modifier.playerTransportFeedback(
    interactionSource: InteractionSource,
    shape: Shape = uiRoundnessShape(),
    enabled: Boolean = true,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTransport),
): Modifier = retainGlassButtonGesture(enabled && settings.enabled)
    .glassButtonFeedback(interactionSource, shape,
        enabled && settings.enabled && !LocalTransportFeedbackOwned.current, retainPressUntilUp = true,
        animateMotion = settings.inScope(GlassScope.PlayerTransport).usesGlassMaterial(GlassArea.ActionButtons),
        settings = settings)

@Composable
fun playerTransportIndication(native: androidx.compose.foundation.Indication?, settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTransport)): androidx.compose.foundation.Indication? =
    if (settings.enabled) null else native

private val LocalTransportFeedbackOwned = staticCompositionLocalOf { false }
private val LocalTransportCanvasSize = staticCompositionLocalOf<Dp?> { null }

/** Same native icon geometry, with its source exposed to the passive overdraw wrapper. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlayerTransportIconButton(
    onClick: () -> Unit,
    icon: Int,
    color: Color,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val canvasSize = LocalTransportCanvasSize.current
    if (canvasSize != null) {
        BoxWithConstraints(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            val glyphWidth = maxWidth
            val glyphHeight = maxHeight
            Box(Modifier.matchParentSize().layout { measurable, constraints ->
                val canvasPixels = canvasSize.roundToPx()
                val canvas = measurable.measure(Constraints.fixed(canvasPixels, canvasPixels))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    canvas.place((constraints.maxWidth - canvasPixels) / 2,
                        (constraints.maxHeight - canvasPixels) / 2)
                }
            }.playerTransportFeedback(interactionSource, enabled = enabled)
                .clip(uiRoundnessShape()).combinedClickable(
                    interactionSource = interactionSource,
                    indication = playerTransportIndication(nativeControlIndication()),
                    enabled = enabled, onClick = onClick), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(icon), contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
                    modifier = Modifier.requiredSize(glyphWidth, glyphHeight)
                        .testTag("player.transport.smallGlyph"))
            }
        }
        return
    }
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(icon),
        contentDescription = null,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
        modifier = modifier.playerTransportFeedback(interactionSource, enabled = enabled)
            .clip(uiRoundnessShape()).combinedClickable(
            interactionSource = interactionSource,
            indication = playerTransportIndication(nativeControlIndication()),
            enabled = enabled,
            onClick = onClick,
        ),
    )
}

/** Report the actionable overdraw to the transport fitter while retaining its native row slot. */
private object SmallTransportCanvasEnvelope : LayoutModifier {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val child = measurable.measure(constraints)
        return layout(child.width, child.height) { child.placeRelative(0, 0) }
    }
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
        maxOf(46.dp.roundToPx(), measurable.minIntrinsicWidth(height))
}

/** Overdraws the skip-button envelope without changing a small control's slot or glyph. */
@Composable
fun PlayerTransportSmallControl(
    id: String,
    surfaceAlpha: Float = 1f,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTransport),
    interactionSource: InteractionSource? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val settings = settings.inScope(GlassScope.PlayerTransport)
    val paneVisible = settings.usesGlassMaterial(GlassArea.ActionButtons) || settings.usesGlassContour(GlassArea.ActionButtons)
    val ownsFeedback = settings.enabled && paneVisible && interactionSource != null
    Box((if (ownsFeedback && interactionSource != null) Modifier.glassButtonFeedback(interactionSource, uiRoundnessShape(),
        enabled = enabled && settings.enabled, visualOutset = 10.dp, retainPressUntilUp = true,
        animateMotion = settings.usesGlassMaterial(GlassArea.ActionButtons), settings = settings) else Modifier)
        .then(SmallTransportCanvasEnvelope)) {
        if (paneVisible) {
            Box(Modifier.matchParentSize().requiredSize(46.dp).alpha(surfaceAlpha)
                .testTag("player.transport.$id.material")
                .playerTransportSurface(settings = settings))
        }
        CompositionLocalProvider(LocalTransportFeedbackOwned provides ownsFeedback,
            LocalTransportCanvasSize provides 46.dp) { content() }
    }
}

/** Keeps a background-free control's blurred artwork and glyph under one spring. */
@Composable
fun PlayerTransportControlMotion(
    interactionSource: InteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTransport),
    content: @Composable BoxScope.() -> Unit,
) {
    val settings = settings.inScope(GlassScope.PlayerTransport)
    Box(modifier.glassButtonFeedback(interactionSource, uiRoundnessShape(),
        enabled && settings.enabled, retainPressUntilUp = true,
        animateMotion = settings.usesGlassMaterial(GlassArea.ActionButtons), settings = settings)) {
        CompositionLocalProvider(LocalTransportFeedbackOwned provides settings.enabled) { content() }
    }
}

/** Replaces only an existing fill, leaving native rendering intact when material is off. */
@Composable
fun playerTransportNativeFill(original: Color, settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTransport)): Color =
    if (settings.inScope(GlassScope.PlayerTransport).usesGlassMaterial(GlassArea.ActionButtons)) Color.Transparent else original

/** Essential's background-free styles never acquire a surrounding material. */
@Composable
fun Modifier.playerTransportPlayBackground(
    type: PlayerPlayButtonType,
    original: Color,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTransport),
): Modifier {
    val settings = settings.inScope(GlassScope.PlayerTransport)
    return when (type) {
        PlayerPlayButtonType.CircularRibbed, PlayerPlayButtonType.Disabled -> background(original)
        else -> if (settings.usesGlassMaterial(GlassArea.ActionButtons))
            playerTransportSurface(original, settings = settings) else background(original)
                .glassAreaContour(settings, GlassArea.ActionButtons, uiRoundnessShape())
    }
}

/** Uses the existing scene through the artwork's alpha-derived outline, without capturing a control. */
@Composable
fun PlayerTransportRibbedSurface(
    filmColor: Color,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerTransport),
): Boolean {
    val settings = settings.inScope(GlassScope.PlayerTransport)
    if (!settings.usesGlassMaterial(GlassArea.ActionButtons)) {
        if (settings.usesGlassContour(GlassArea.ActionButtons)) {
            // The native image is emitted by the caller afterwards. Keep only this
            // shared contour above it, preserving that image's exact native fill.
            Box(modifier.size(100.dp).zIndex(1f).clip(PlayerTransportRibbedShape)
                .glassAreaContour(settings, GlassArea.ActionButtons, PlayerTransportRibbedShape))
        }
        return false
    }
    val opticsProgress = glassOpticsProgress()
    val opticsEnabled = opticsProgress > 0f
    val field = rememberPlayerRibbedField(opticsEnabled && LocalGlassContainerBackdrop.current != null &&
        glassRenderer(settings) == GlassRenderer.Refraction)
    val customLens: app.n_zik.android.components.glass.backdrop.BackdropEffectScope.(Float, Float) -> Unit =
        remember(field, settings.depthEffect, settings.dispersion) {
            { height, amount -> playerRibbedLens(field, height, amount, settings.depthEffect, settings.dispersion) }
        }
    // The exact clip, SDF lens and normal highlight share the same rotating local coordinates.
    Box(modifier.size(100.dp).clip(PlayerTransportRibbedShape)
        .glassMaterial(settings.forArea(GlassArea.ActionButtons).copy(rim = 0f), PlayerTransportRibbedShape,
            ownBackdrop = LocalGlassContainerBackdrop.current, filmColor = filmColor,
            separateWindow = LocalGlassContainerBackdrop.current == null,
            applyEdgeEffects = false, drawShadow = false, customLens = customLens)
        .playerRibbedHighlight(settings, field, opticsProgress)
        .then(if (opticsProgress < 1f) Modifier.glassAreaContour(
            settings.copy(rim = settings.rim * (1f - opticsProgress)),
            GlassArea.ActionButtons, PlayerTransportRibbedShape) else Modifier))
    return true
}
