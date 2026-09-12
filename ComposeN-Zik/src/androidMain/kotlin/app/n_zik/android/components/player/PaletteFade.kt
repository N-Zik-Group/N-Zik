package app.n_zik.android.components.player

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
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.lerpTo
import app.n_zik.android.components.theme.LocalPaletteFadeTarget
import app.n_zik.android.components.theme.withColor

/**
 * Pure transition model for the scoped palette fade (no Compose, unit-testable).
 *
 * The global [ColorPalette] is set once per track change; this model computes
 * the interpolated palette displayed inside the fade scope between the last
 * stable palette and the new target.
 */
class PaletteFadeModel {
    private var fromPalette: ColorPalette? = null
    private var targetPalette: ColorPalette? = null

    /** True while a fade is in progress. */
    val isFading: Boolean
        get() = fromPalette != null

    /**
     * Updates the target palette. When a previous target exists, the currently
     * displayed palette (origin lerped by [displayedFraction]) becomes the new
     * fade origin, so rapid track changes restart smoothly from where the UI
     * actually is instead of jumping.
     *
     * @return true when a fade should be started, false for the first target
     * or when the target is unchanged
     */
    fun retarget(newTarget: ColorPalette, displayedFraction: Float): Boolean {
        val target = targetPalette
        if (target == null) {
            targetPalette = newTarget
            return false
        }
        if (target == newTarget) return false
        val origin = fromPalette ?: target
        fromPalette = origin.lerpTo(target, displayedFraction)
        targetPalette = newTarget
        return true
    }

    /**
     * The palette to display at [fraction] (0 = fade origin, 1 = target).
     * Returns the plain target while no fade is in progress.
     */
    fun paletteAt(fraction: Float): ColorPalette {
        val from = fromPalette ?: return requireNotNull(targetPalette)
        return from.lerpTo(requireNotNull(targetPalette), fraction)
    }

    /** Marks the fade as finished; [paletteAt] then returns the target. */
    fun endFade() {
        fromPalette = null
    }
}

/**
 * Fades the [LocalAppearance] color palette and typography within [content]
 * while the global palette switches in a single step.
 *
 * Only the wrapped subtree (player / mini-player) recomposes during the
 * transition — the rest of the app applies the new palette instantly, which
 * keeps track changes cheap.
 *
 * The subtree is always wrapped in a [CompositionLocalProvider] (no structural
 * if/else), so the player is never remounted when the fade finishes. The
 * palette currently shown is tracked in a state that keeps the previous palette
 * until the fade starts, so a new target never flashes for a frame before the
 * transition begins from the old palette.
 */
@Composable
fun PaletteFade(
    durationMillis: Int = 350,
    content: @Composable () -> Unit,
) {
    val targetAppearance = LocalPaletteFadeTarget.current ?: LocalAppearance.current
    val targetPalette = targetAppearance.colorPalette

    val model = remember { PaletteFadeModel() }
    val fraction = remember { Animatable(1f) }
    // Palette currently shown by this scope. Null before the first target is
    // known; afterwards it always holds the last displayed palette, so a new
    // target never flashes before the fade starts from the previous one.
    var displayed by remember { mutableStateOf<ColorPalette?>(null) }

    LaunchedEffect(targetPalette) {
        if (displayed == null) {
            model.retarget(targetPalette, 1f)
            displayed = targetPalette
            return@LaunchedEffect
        }
        if (!model.retarget(targetPalette, fraction.value)) {
            model.endFade()
            displayed = targetPalette
            return@LaunchedEffect
        }
        fraction.snapTo(0f)
        fraction.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
        ) {
            displayed = model.paletteAt(value)
        }
        model.endFade()
        displayed = targetPalette
    }

    val palette = displayed ?: targetPalette
    CompositionLocalProvider(
        LocalAppearance provides targetAppearance.copy(
            colorPalette = palette,
            typography = targetAppearance.typography.withColor(palette.text),
        ),
        LocalPaletteFadeTarget provides targetAppearance,
    ) {
        content()
    }
}
