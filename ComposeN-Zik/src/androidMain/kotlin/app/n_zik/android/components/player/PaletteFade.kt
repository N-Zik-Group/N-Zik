package app.n_zik.android.components.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.withFrameNanos
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.lerpTo
import app.n_zik.android.components.theme.LocalPaletteDarkness
import app.n_zik.android.components.theme.LocalPaletteFadeTarget
import app.n_zik.android.components.theme.LaunchedAppearanceAnimationEffect
import app.n_zik.android.components.theme.ProvideAppearanceAnimationClock
import app.n_zik.android.components.theme.animateAppearanceFraction
import app.n_zik.android.components.glass.LocalPlayerForegroundFrame
import app.n_zik.android.components.glass.PlayerForegroundFrame
import kotlinx.coroutines.flow.first

/**
 * Pure transition model for the scoped palette fade (no Compose, unit-testable).
 *
 * The global [ColorPalette] is set once per track change; this model computes
 * the interpolated palette displayed inside the fade scope between the last
 * stable palette and the new target.
 */
class PaletteFadeModel {
    private var fromPalette: ColorPalette? = null
    private var fromDarkness = 0f
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
    fun retarget(newTarget: ColorPalette, displayedFraction: Float, resume: Boolean = false): Boolean {
        val target = targetPalette
        if (target == null) {
            targetPalette = newTarget
            return false
        }
        if (target == newTarget && (!resume || fromPalette == null)) return false
        val origin = fromPalette ?: target
        fromDarkness = darknessAt(displayedFraction)
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

    fun darknessAt(fraction: Float): Float {
        val target = if (requireNotNull(targetPalette).isDark) 1f else 0f
        return if (fromPalette == null) target else fromDarkness + (target - fromDarkness) * fraction
    }

    /** Marks the fade as finished; [paletteAt] then returns the target. */
    fun endFade() {
        fromPalette = null
    }
}

internal val LocalPlayerArtworkPaintTarget = staticCompositionLocalOf<MutableState<PlayerArtworkPaint?>?> { null }
internal val LocalPlayerPaletteTransitioning = compositionLocalOf { false }
internal val LocalPlayerArtworkPaint = staticCompositionLocalOf<State<PlayerArtworkPaint?>?> { null }
internal val LocalPlayerContrastTarget = staticCompositionLocalOf<MutableState<Pair<String?, Long>?>?> { null }

private data class PlayerPaletteFrame(
    val palette: ColorPalette,
    val darkness: Float,
    val paint: PlayerArtworkPaint?,
    val foreground: PlayerForegroundFrame,
    val contrastTarget: Pair<String?, Long>?,
)

/**
 * Fades the [LocalAppearance] color palette and typography within [content]
 * while the global palette switches in a single step.
 *
 * Only the wrapped subtree (player / mini-player) recomposes during the
 * transition — the rest of the app applies the new palette instantly, which
 * keeps track changes cheap. The subtree is always wrapped in a
 * [CompositionLocalProvider] (no structural if/else), so the player is never
 * remounted when the fade finishes. The palette currently shown is tracked in
 * the composable's state, so a new target never flashes for a frame before the
 * transition begins from the old palette.
 */
@Composable
fun PaletteFade(
    durationMillis: Int = 600,
    artworkInMotion: State<Boolean>? = null,
    content: @Composable () -> Unit,
) {
    ProvideAppearanceAnimationClock {
        PlayerPaletteFade(durationMillis, artworkInMotion, content)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PlayerPaletteFade(
    durationMillis: Int,
    artworkInMotion: State<Boolean>?,
    content: @Composable () -> Unit,
) {
    val targetAppearance = LocalPaletteFadeTarget.current ?: LocalAppearance.current
    val model = remember { PaletteFadeModel() }
    // Palette currently shown by this scope. Null before the first target is
    // known; afterwards it always holds the last displayed palette, so a new
    // target never flashes before the fade starts from the previous one.
    var displayed by remember { mutableStateOf<PlayerPaletteFrame?>(null) }
    val artworkTarget = remember { mutableStateOf<PlayerArtworkPaint?>(null) }
    val contrastTarget = remember { mutableStateOf<Pair<String?, Long>?>(null) }
    val displayedPaint = remember { derivedStateOf { displayed?.paint } }
    val targetPaint = artworkTarget.value
    val targetPalette = targetPaint?.themePalette ?: targetAppearance.colorPalette
    val targetContrast = contrastTarget.value

    val waitForAmbientPalette = targetPaint?.themePalette == null && artworkInMotion?.value == true
    LaunchedAppearanceAnimationEffect(targetPalette, targetPaint, targetContrast, waitForAmbientPalette) {
        val previous = displayed
        val fromPaint = previous?.paint ?: targetPaint
        val paintChanged = fromPaint != targetPaint
        val generation = (previous?.foreground?.generation ?: 0) + 1
        fun publish(progress: Float) {
            val paint = if (progress == 1f || targetPaint == null || !paintChanged) targetPaint
                else fromPaint?.at(targetPaint, progress)
            displayed = PlayerPaletteFrame(model.paletteAt(progress), model.darknessAt(progress), paint,
                PlayerForegroundFrame(generation, progress, changesPolarity = model.darknessAt(0f) != model.darknessAt(1f)),
                targetContrast)
        }
        if (previous == null) {
            model.retarget(targetPalette, 1f)
            publish(1f)
            return@LaunchedAppearanceAnimationEffect
        }
        if (waitForAmbientPalette) return@LaunchedAppearanceAnimationEffect
        if (artworkInMotion != null && targetPaint?.themePalette == null) {
            // Ambient palette updates can precede the player's pager request. Scoped artwork paint
            // is already committed by the visible player, after its cover and layout gates.
            withFrameNanos { }
            snapshotFlow { artworkInMotion.value }.first { moving -> !moving }
        }
        val paletteChanged = model.retarget(targetPalette, previous.foreground.progress, resume = artworkInMotion != null)
        val contrastChanged = previous.contrastTarget != null && previous.contrastTarget != targetContrast
        if (!paletteChanged && !paintChanged && !contrastChanged) {
            model.endFade()
            publish(1f)
            return@LaunchedAppearanceAnimationEffect
        }
        publish(0f)
        animateAppearanceFraction(durationMillis, ::publish)
        model.endFade()
        publish(1f)
    }

    val frame = displayed
    val palette = frame?.palette ?: targetPalette
    CompositionLocalProvider(
        LocalAppearance provides targetAppearance.copy(
            colorPalette = palette,
            typography = remember(targetAppearance.typography, palette.text) {
                targetAppearance.typography.withDeferredColor(palette.text)
            },
        ),
        LocalPaletteDarkness provides (frame?.darkness ?: if (palette.isDark) 1f else 0f),
        LocalRippleConfiguration provides remember(palette.text) { RippleConfiguration(color = palette.text) },
        LocalPaletteFadeTarget provides targetAppearance,
        LocalPlayerArtworkPaintTarget provides artworkTarget,
        LocalPlayerArtworkPaint provides displayedPaint,
        LocalPlayerContrastTarget provides contrastTarget,
        LocalPlayerForegroundFrame provides frame?.foreground,
        LocalPlayerPaletteTransitioning provides (frame?.foreground?.progress?.let { it < 1f } == true),
    ) {
        content()
    }
}
