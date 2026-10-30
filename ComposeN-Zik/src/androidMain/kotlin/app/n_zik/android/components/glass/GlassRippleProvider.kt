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

@Suppress("DEPRECATION") // Installed Material3 still reads these state-layer alpha overrides.
private val glassRippleConfiguration = RippleConfiguration(
    color = Color.White,
    rippleAlpha = RippleAlpha(draggedAlpha = .12f, focusedAlpha = .12f, hoveredAlpha = .08f, pressedAlpha = .12f),
)

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
    val glassIndication = remember(pointerTracker) { GlassHighlightIndication(pointerTracker) }
    CompositionLocalProvider(
        LocalGlassRippleActive provides settings.enabled,
        LocalRippleConfiguration provides if (settings.enabled) glassRippleConfiguration else nativeConfiguration,
        LocalIndication provides if (settings.enabled) glassIndication else nativeIndication,
    ) {
        Box(
            modifier = Modifier.trackGlassHighlightPointers(pointerTracker),
            propagateMinConstraints = true,
        ) { content() }
    }
}
