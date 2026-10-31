package app.n_zik.android.components.player

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial

/** Minimal clearance below Android's safe top, including the information pane's actual overflow. */
@Composable
fun Modifier.playerLandscapeControlsMargin(
    settings: GlassSettings = rememberGlassSettings(GlassScope.PlayerHeader),
): Modifier {
    val geometry = LocalPlayerTransportGeometry.current
    val scoped = settings.inScope(GlassScope.PlayerHeader)
    val fallbackOutset = if (scoped.usesGlassMaterial(GlassArea.ActionButtons) ||
        scoped.usesGlassContour(GlassArea.ActionButtons)) PlayerInformationVerticalPadding else 0.dp
    val surfaceOutset = geometry?.landscapeInformationTopOutset?.let {
        with(LocalDensity.current) { it.toDp() }
    } ?: fallbackOutset
    return padding(top = 4.dp + surfaceOutset, bottom = 8.dp)
}
