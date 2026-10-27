package app.n_zik.android.components.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.ui.styling.lerpTo

/** Stable target that prevents scoped fades from restarting on global animation frames. */
val LocalPaletteFadeTarget = staticCompositionLocalOf<Appearance?> { null }

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
fun ProvideAnimatedAppearance(
    target: Appearance,
    fadeFrom: Appearance?,
    durationMillis: Int = 350,
    onFadeComplete: () -> Unit,
    content: @Composable () -> Unit,
) {
    var displayed by remember { mutableStateOf<Appearance?>(null) }
    val fraction = remember { Animatable(1f) }

    LaunchedEffect(fadeFrom, target) {
        val transition = appearanceRetarget(displayed, fadeFrom, target)
        if (transition.animateFrom == null) {
            displayed = transition.displayed
            return@LaunchedEffect
        }
        fraction.snapTo(0f)
        fraction.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        ) {
            displayed = fadeAppearance(transition.animateFrom, target, value)
        }
        displayed = target
        onFadeComplete()
    }

    val value = if (fadeFrom == null) target else displayed ?: target
    CompositionLocalProvider(
        LocalAppearance provides value,
        LocalPaletteFadeTarget provides target,
    ) {
        content()
    }
}
