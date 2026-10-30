package app.n_zik.android.components.ui.screens.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassEdgeRim
import app.n_zik.android.components.glass.glassTint
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial

/** Follows the image edge at its exact bounds, without applying material effects to the photo. */
@Composable
internal fun ProfileAvatarFrame(
    size: Dp,
    area: GlassArea,
    modifier: Modifier = Modifier,
    settings: GlassSettings = rememberGlassSettings(),
    content: @Composable () -> Unit,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        content()
        if (settings.usesGlassMaterial(area) || settings.usesGlassContour(area)) {
            Box(Modifier.matchParentSize().glassEdgeRim(settings, artistThumbnailShape(), glassTint(settings)))
        }
    }
}
