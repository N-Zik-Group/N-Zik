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

/**
 * Stable appearance target for scoped fades.
 *
 * When [AnimatedAppearance] animates the global appearance, the value provided
 * through [LocalAppearance] changes every frame. Scoped fades must read this
 * stable target instead, otherwise their animation is restarted on every frame.
 */
val LocalPaletteFadeTarget = staticCompositionLocalOf<Appearance?> { null }

/**
 * Returns a copy of this typography with every text style recolored while
 * preserving each style's font size and other attributes.
 */
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

/**
 * Interpolates between two appearances at [fraction].
 *
 * Shapes are taken from [target]; only the palette and typography color are
 * animated.
 */
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

/**
 * Displays [target] globally.
 *
 * When [fadeFrom] is non-null, the transition from the currently displayed
 * appearance to [target] is animated across the whole app. When [fadeFrom] is
 * null, [target] is applied instantly. [onFadeComplete] is invoked after an
 * animated transition finishes so the caller can clear [fadeFrom].
 */
@Composable
fun AnimatedAppearance(
    target: Appearance,
    fadeFrom: Appearance?,
    durationMillis: Int = 350,
    onFadeComplete: () -> Unit,
    content: @Composable (Appearance) -> Unit,
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
        content(value)
    }
}
