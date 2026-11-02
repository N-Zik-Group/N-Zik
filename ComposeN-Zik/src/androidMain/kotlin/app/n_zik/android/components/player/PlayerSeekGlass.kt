// Adapted from Square / Convx Project (C) 2026, GPL-3.0; see ../glass/NOTICE-square.txt.
// Square's LiquidSlider optics originate in Kyant0's Apache-2.0 Backdrop catalog;
// see ../glass/LICENSE-backdrop.txt. Native tracks and cursors remain underneath the additive Glass optics.
package app.n_zik.android.components.player

import app.n_zik.android.components.glass.dispersionAmount

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.glassOpticsProgress
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.backdrop.drawBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.effects.blur
import app.n_zik.android.components.glass.backdrop.effects.colorControls
import app.n_zik.android.components.glass.backdrop.effects.lens
import app.n_zik.android.components.glass.backdrop.highlight.Highlight
import app.n_zik.android.components.glass.backdrop.shadow.InnerShadow
import app.n_zik.android.components.glass.backdrop.shadow.Shadow
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.components.glass.forArea
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.glassAreaContour
import app.n_zik.android.components.glass.usesGlassMaterial
import app.n_zik.android.components.glass.glassSaturation
import kotlin.math.roundToInt
import kotlin.time.Clock

internal fun playerSeekLensX(width: Float, inset: Float, fraction: Float): Float =
    inset.coerceAtMost(width / 2f) + (width - 2f * inset).coerceAtLeast(0f) *
        (if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f)

internal data class PlayerSeekOptics(val blurDp: Float, val saturation: Float, val depthDp: Float, val amountDp: Float)

/** Press motion modulates the saved optics without introducing a nonzero minimum. */
internal fun playerSeekOptics(settings: GlassSettings, pressProgress: Float, opticsProgress: Float): PlayerSeekOptics {
    val press = pressProgress.coerceIn(0f, 1f)
    val optics = opticsProgress.coerceIn(0f, 1f)
    return PlayerSeekOptics(settings.blur * (1f - press) * optics,
        1f + (glassSaturation(settings.vibrancy) - 1f) * optics,
        settings.depth * 48f * press * optics, settings.amount * 48f * press * optics)
}

@Stable
class PlayerSeekGlassThumb(val motion: PlayerSeekGlassMotion,
    val trackBackdrop: LayerBackdrop? = null,
    val settings: GlassSettings = GlassSettings(),
    val opticsEnabled: Boolean = true,
    val opticsProgress: Float = 1f,
    val renderer: GlassRenderer = GlassRenderer.Refraction)

/** Optional native-only recording; the optical pane never samples this foreground layer. */
fun Modifier.playerSeekTrack(thumb: PlayerSeekGlassThumb?): Modifier =
    thumb?.takeIf { it.opticsEnabled && it.renderer != GlassRenderer.Tint && it.motion.pressProgress > .001f }
        ?.trackBackdrop?.let { layerBackdrop(it) } ?: this

/** Center Square optics on the unchanged native track and cursor. */
@Composable
fun BoxScope.PlayerSeekOpticalThumb(
    thumb: PlayerSeekGlassThumb?,
    fraction: Float,
) {
    if (thumb == null) return
    val animation = thumb.motion
    LaunchedEffect(animation, fraction) {
        if (animation.targetValue != fraction) animation.updateValue(fraction)
    }
    if (!thumb.opticsEnabled) return
    val density = LocalDensity.current
    // The native track already paints above this pane. Replaying it inside the lens
    // creates a second, independently distorted stroke beside the real cursor.
    val backdrop = LocalGlassContainerBackdrop.current
    var trackWidth by remember { mutableFloatStateOf(0f) }
    val progress = animation.pressProgress.coerceIn(0f, 1f)
    val visibility = progress * thumb.opticsProgress
    val samplesBackdrop = thumb.settings.usesGlassMaterial(GlassArea.ActionButtons) &&
        backdrop != null && thumb.renderer != GlassRenderer.Tint
    Box(Modifier.matchParentSize().zIndex(-1f).onSizeChanged {
        trackWidth = it.width.toFloat()
    }) {
        val thumbWidth = with(density) { PLAYER_SEEK_OPTICAL_PANE_SIZE.toPx() }
        val x = playerSeekLensX(trackWidth, 0f, animation.value) - thumbWidth / 2f
        val offset = x.coerceIn(-thumbWidth / 4f,
            (trackWidth - thumbWidth * 3f / 4f).coerceAtLeast(-thumbWidth / 4f))
        val motion: GraphicsLayerScope.() -> Unit = {
            scaleX = animation.opticalScaleX
            scaleY = animation.opticalScaleY
            // CenterLeft already anchors the pane to the native cursor's vertical center.
            // Scaling must expand around that center, without a floating bubble offset.
            // The pane overflows the native track's thin slot. Alpha on this outer
            // layer creates an implicit offscreen clip during entry/exit. Composite
            // the sampled pixels inside their actual optical bounds instead.
            alpha = if (samplesBackdrop) 1f else visibility
        }
        // At rest there is no visible optical layer; the native track remains unchanged.
        val optics = if (progress <= .001f) Modifier.graphicsLayer(motion)
            else if (!thumb.settings.usesGlassMaterial(GlassArea.ActionButtons))
                Modifier.graphicsLayer(motion).glassAreaContour(thumb.settings, GlassArea.ActionButtons, CircleShape)
            else if (backdrop == null || thumb.renderer == GlassRenderer.Tint) Modifier.graphicsLayer(motion)
            .glassMaterial(thumb.settings.forArea(GlassArea.ActionButtons), CircleShape,
                separateWindow = true, drawFilm = false, drawShadow = false) else
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    val configured = playerSeekOptics(thumb.settings, progress, thumb.opticsProgress)
                    if (configured.saturation != 1f) colorControls(saturation = configured.saturation)
                    if (configured.blurDp > 0f) blur(configured.blurDp.dp.toPx())
                    // Keep the configured reaches inside the actual optical pane.
                    if (thumb.renderer == GlassRenderer.Refraction) {
                        val reach = size.minDimension * .45f
                        lens(configured.depthDp.dp.toPx().coerceAtMost(reach),
                            configured.amountDp.dp.toPx().coerceAtMost(reach),
                            depthEffect = thumb.settings.depthEffect,
                            dispersion = thumb.settings.dispersionAmount())
                    }
                },
                highlight = if (thumb.settings.forArea(GlassArea.ActionButtons).rim > 0f) {
                    { Highlight.Ambient.copy(width = Highlight.Ambient.width / 1.5f,
                        blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                        alpha = visibility * progress * (thumb.settings.forArea(GlassArea.ActionButtons).rim / .55f).coerceIn(0f, 1f)) }
                } else null,
                shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = .05f), alpha = visibility) },
                innerShadow = { InnerShadow(radius = 4.dp * progress, alpha = visibility * progress) },
                layerBlock = motion,
                opticsAlpha = visibility,
            )
        Box(Modifier.align(AbsoluteAlignment.CenterLeft)
            .absoluteOffset { IntOffset(offset.roundToInt(), 0) }
            .requiredSize(PLAYER_SEEK_OPTICAL_PANE_SIZE)
            .then(optics)
            .testTag(if (thumb.settings.usesGlassMaterial(GlassArea.ActionButtons)) "player.seek.glassThumb" else "player.seek.contourThumb"))
    }
}

/** Solely observes presses. The original timeline still owns every pointer and seek. */
@Composable
fun PlayerSeekGlass(
    type: PlayerTimelineType,
    enabled: Boolean,
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerSeek),
    fraction: () -> Float = { 0f },
    timeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    content: @Composable (PlayerSeekGlassThumb?) -> Unit,
) {
    val settings = settings.inScope(GlassScope.PlayerSeek)
    val material = settings.usesGlassMaterial(GlassArea.ActionButtons)
    val contour = settings.usesGlassContour(GlassArea.ActionButtons)
    val opticsProgress = glassOpticsProgress()
    val opticsEnabled = opticsProgress > 0f
    val renderer = glassRenderer(settings)
    val scope = rememberCoroutineScope()
    val latestFraction by rememberUpdatedState(fraction)
    val animation = remember(scope, type) { PlayerSeekGlassMotion(scope, fraction(), timeMillis) }
    LaunchedEffect(animation, material, enabled, opticsEnabled) { animation.reset(latestFraction()) }
    val thumb = remember(animation, settings, opticsEnabled, opticsProgress, renderer) {
        PlayerSeekGlassThumb(animation, settings = settings, opticsEnabled = opticsEnabled,
            opticsProgress = opticsProgress, renderer = renderer)
    }
    Box(Modifier.fillMaxWidth().pointerInput(material, contour, enabled, opticsEnabled, animation) {
        if (!(material || contour) || !enabled || !opticsEnabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            animation.press()
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes
                        .firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                }
            } finally {
                animation.release()
            }
        }
    }) {
        content(if (material || contour) thumb else null)
    }
}
