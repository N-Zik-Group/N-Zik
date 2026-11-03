package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.LocalQueuePlayerBackdrop
import app.n_zik.android.components.glass.glassContentCaptureEnabled
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop

internal val LocalMiniQueueCoverage = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/** Keeps the activity content and its mini-open queue as separate drawing siblings. */
@Composable
fun MiniPlayerQueueScene(
    queueVisible: Boolean,
    scene: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    var covered by remember { mutableStateOf(false) }
    val inheritedCovered = app.n_zik.android.components.glass.LocalGlassUnderlayCovered.current
    val inheritedOptics = app.n_zik.android.components.glass.LocalGlassOpticsEnabled.current
    val reportCoverage = remember { { value: Boolean -> covered = value } }
    val backdrop = rememberLayerBackdrop()
    val settings = rememberGlassSettings(GlassScope.Queue)
    val capture = glassContentCaptureEnabled(settings)
    val frozen = remember(queueVisible) { { !queueVisible } }
    // A fully expanded player owns its complete foreground, without the Home beneath it.
    val foreground = LocalGlassMenuHost.current?.foregroundBackdrop
    val source = foreground ?: backdrop
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            app.n_zik.android.components.glass.LocalGlassUnderlayCovered provides (inheritedCovered || (queueVisible && covered)),
            app.n_zik.android.components.glass.LocalGlassOpticsEnabled provides playerQueueOpticsEnabled(inheritedOptics, queueVisible),
        ) {
        Box(Modifier.matchParentSize()
            .then(if (capture && foreground == null) Modifier.layerBackdrop(backdrop, frozen = frozen) else Modifier), content = scene)
        }
        CompositionLocalProvider(LocalQueuePlayerBackdrop provides if (capture) source else null,
            LocalMiniQueueCoverage provides reportCoverage) {
            overlay()
        }
    }
}
