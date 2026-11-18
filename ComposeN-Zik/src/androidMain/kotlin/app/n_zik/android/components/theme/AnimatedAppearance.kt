package app.n_zik.android.components.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.n_zik.android.components.glass.GlassArtworkTint
import app.n_zik.android.components.glass.LocalGlassArtworkTint
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.ui.styling.lerpTo

/** Stable target that prevents scoped fades from restarting on global animation frames. */
val LocalPaletteFadeTarget = staticCompositionLocalOf<Appearance?> { null }

/** Continuous light-to-dark position of the visible app palette. */
val LocalPaletteDarkness = compositionLocalOf<Float?> { null }

@Composable
fun paletteDarkness(): Float = LocalPaletteDarkness.current ?: LocalAppearance.current.darkness

private val Appearance.darkness: Float get() = if (colorPalette.isDark) 1f else 0f

internal val LocalNativeSurfaceMix = compositionLocalOf<NativeSurfaceMix?> { null }

/** Tracks the coverage of the native black surface styles during a theme change. */
internal data class NativeSurfaceMix(val pitchBlack: Float, val pureBlack: Float, val miniPlayerBlack: Float = 0f) {
    fun at(target: NativeSurfaceMix, fraction: Float) = NativeSurfaceMix(
        pitchBlack + (target.pitchBlack - pitchBlack) * fraction,
        pureBlack + (target.pureBlack - pureBlack) * fraction,
        miniPlayerBlack + (target.miniPlayerBlack - miniPlayerBlack) * fraction,
    )

    fun color(base: Color, pitchBlack: Color, pureBlack: Color): Color {
        val black = this.pitchBlack + this.pureBlack
        if (black <= 0f) return base
        val tint = lerp(pitchBlack, pureBlack, (this.pureBlack / black).coerceIn(0f, 1f))
        return lerp(base, tint, black.coerceIn(0f, 1f))
    }
}

private data class AppearanceFrame(
    val appearance: Appearance,
    val darkness: Float,
    val nativeSurfaceMix: NativeSurfaceMix?,
    val artworkTint: GlassArtworkTint?,
)

/** Returns a copy of this typography with every text style recolored while preserving each style's font size and other attributes. */
fun Typography.withColor(color: Color): Typography = copy(
    xxxs = xxxs.copy(color = color),
    xxs = xxs.copy(color = color),
    xs = xs.copy(color = color),
    s = s.copy(color = color),
    m = m.copy(color = color),
    l = l.copy(color = color),
    xl = xl.copy(color = color),
    xxl = xxl.copy(color = color),
    xxxl = xxxl.copy(color = color),
    xlxl = xlxl.copy(color = color),
)

/** Interpolates palette and typography colors while taking shapes from [target]. */
fun fadeAppearance(from: Appearance, target: Appearance, fraction: Float): Appearance {
    val palette = from.colorPalette.lerpTo(target.colorPalette, fraction)
    return target.copy(
        colorPalette = palette,
        typography = target.typography.withColor(palette.text),
    )
}

data class AppearanceRetarget(
    val displayed: Appearance,
    val animateFrom: Appearance?,
)

fun appearanceRetarget(current: Appearance?, fadeFrom: Appearance?, target: Appearance): AppearanceRetarget = when {
    current == null -> AppearanceRetarget(displayed = target, animateFrom = null)
    fadeFrom == null -> AppearanceRetarget(displayed = target, animateFrom = null)
    current == target -> AppearanceRetarget(displayed = target, animateFrom = null)
    else -> AppearanceRetarget(displayed = target, animateFrom = current)
}

/** Compatibility entry point for content that explicitly consumes the animated appearance. */
@Composable
fun AnimatedAppearance(
    target: Appearance,
    fadeFrom: Appearance?,
    durationMillis: Int = 350,
    onFadeComplete: () -> Unit,
    content: @Composable (Appearance) -> Unit,
) {
    ProvideAnimatedAppearance(target, fadeFrom, durationMillis, onFadeComplete) {
        AnimatedAppearanceContent(content)
    }
}

@Composable
private fun AnimatedAppearanceContent(content: @Composable (Appearance) -> Unit) {
    content(LocalAppearance.current)
}

/** Provides animated colors to theme readers without passing frame values through [content]. */
@Composable
internal fun ProvideAnimatedAppearance(
    target: Appearance,
    fadeFrom: Appearance?,
    durationMillis: Int = 350,
    onFadeComplete: () -> Unit,
    targetNativeSurfaceMix: NativeSurfaceMix? = null,
    targetArtworkTint: GlassArtworkTint? = null,
    content: @Composable () -> Unit,
) {
    var displayed by remember { mutableStateOf<AppearanceFrame?>(null) }
    val fraction = remember { Animatable(1f) }

    LaunchedEffect(fadeFrom, target, targetNativeSurfaceMix, targetArtworkTint) {
        val previous = displayed
        val transition = appearanceRetarget(previous?.appearance, fadeFrom, target)
        val endpoint = AppearanceFrame(target, target.darkness, targetNativeSurfaceMix, targetArtworkTint)
        val fromMix = previous?.nativeSurfaceMix
        val changingSurface = fadeFrom != null && fromMix != null && targetNativeSurfaceMix != null && fromMix != targetNativeSurfaceMix
        val fromTint = previous?.artworkTint ?: targetArtworkTint ?: GlassArtworkTint()
        val changingTint = fadeFrom != null && targetArtworkTint != null && targetArtworkTint != fromTint
        if (transition.animateFrom == null && !changingSurface && !changingTint) {
            displayed = endpoint
            return@LaunchedEffect
        }
        val from = transition.animateFrom ?: transition.displayed
        val fromDarkness = previous?.darkness ?: from.darkness
        fraction.snapTo(0f)
        fraction.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        ) {
            displayed = AppearanceFrame(
                fadeAppearance(from, target, value),
                fromDarkness + (endpoint.darkness - fromDarkness) * value,
                targetNativeSurfaceMix?.let { fromMix?.at(it, value) ?: it },
                targetArtworkTint?.let { fromTint.lerpTo(it, value) },
            )
        }
        displayed = endpoint
        onFadeComplete()
    }

    val currentFrame = rememberUpdatedState(
        displayed.takeIf { fadeFrom != null } ?: AppearanceFrame(target, target.darkness, targetNativeSurfaceMix, targetArtworkTint),
    )
    val frame = currentFrame.value
    val artworkTint = remember { derivedStateOf { currentFrame.value.artworkTint ?: GlassArtworkTint() } }
    CompositionLocalProvider(
        LocalAppearance provides frame.appearance,
        LocalPaletteDarkness provides frame.darkness,
        LocalNativeSurfaceMix provides frame.nativeSurfaceMix,
        LocalGlassArtworkTint provides artworkTint.takeIf { targetArtworkTint != null },
        LocalPaletteFadeTarget provides target,
    ) {
        content()
    }
}
