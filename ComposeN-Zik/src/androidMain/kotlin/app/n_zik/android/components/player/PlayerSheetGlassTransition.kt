package app.n_zik.android.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.n_zik.android.ui.screens.player.PlayerSheetState
import app.n_zik.android.components.glass.GLASS_OPTICS_FADE_MILLIS
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.glassOpticsGateProgressState
import app.n_zik.android.components.glass.backdrop.Backdrop

/** One clock owns the deploy plate and all player optical consumers. */
val LocalPlayerSheetGlassTransition = staticCompositionLocalOf<State<Float>?> { null }

@Composable
fun rememberPlayerSheetGlassTransition(state: PlayerSheetState): State<Float> =
    animateFloatAsState(if (state.isExpanded) 1f else 0f,
        tween(GLASS_OPTICS_FADE_MILLIS), label = "playerSheetGlass")

/** Capture stays alive until the last consumer's deployment fade has ended. */
@Composable
fun playerGlassSourceCaptureActive(settings: GlassSettings, transition: State<Float>,
    inheritedOpticsEnabled: Boolean): Boolean {
    if (!settings.enabled || glassRenderer(settings) == GlassRenderer.Tint) return false
    val gate = glassOpticsGateProgressState(inheritedOpticsEnabled)
    return remember(transition, gate) {
        derivedStateOf { transition.value > 0f && gate.value > 0f }
    }.value
}

/** Modal owners retain the completed expanded player independently of its control optics budget. */
fun playerGlassSceneCaptureActive(settings: GlassSettings, expanded: Boolean,
    backgroundCapture: Boolean): Boolean = backgroundCapture || (settings.enabled && expanded)

/** Quality fades must never introduce the deploy card's underlying application scene. */
fun rememberPlayerControlsGlassSource(player: Backdrop): Backdrop = player
