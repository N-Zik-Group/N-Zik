package app.n_zik.android.components.glass

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier

internal val LocalGlassRippleActive = staticCompositionLocalOf { false }

/** Changes only indication styling, leaving native click, long-click and scroll handling intact. */
@Composable
fun GlassRippleProvider(
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable () -> Unit,
) {
    // Keep the content at the same composition position when the master changes: the
    // root content owns navigation, search and scroll state. OFF forwards native values.
    val nativeConfiguration = LocalRippleConfiguration.current
    val nativeIndication = LocalIndication.current
    val pointerTracker = remember { GlassHighlightPointerTracker() }
    val policy = settings.interactionPolicy()
    val rippleActive = settings.enabled && policy.rippleIntensity > 0f
    val glassIndication = remember(pointerTracker, policy) { GlassHighlightIndication(pointerTracker, policy = policy) }
    val configuration = remember(policy) {
        RippleConfiguration(color = Color.White, rippleAlpha = RippleAlpha(
            draggedAlpha = .12f * policy.rippleIntensity, focusedAlpha = .12f * policy.rippleIntensity,
            hoveredAlpha = .08f * policy.rippleIntensity, pressedAlpha = .12f * policy.rippleIntensity,
        ))
    }
    CompositionLocalProvider(
        LocalGlassRippleActive provides rippleActive,
        LocalRippleConfiguration provides if (rippleActive) configuration else nativeConfiguration,
        LocalIndication provides if (rippleActive) glassIndication else nativeIndication,
    ) {
        Box(
            modifier = Modifier.trackGlassHighlightPointers(pointerTracker),
            propagateMinConstraints = true,
        ) { content() }
    }
}
