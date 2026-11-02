package app.n_zik.android.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassExplicitBadge
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.usesGlassContour
import app.n_zik.android.components.glass.usesGlassMaterial

/** Common explicit indicator. The caller owns placement, size and native tint. */
@Composable
fun ExplicitBadge(color: Color, modifier: Modifier = Modifier, settings: GlassSettings = rememberGlassSettings()) {
    if (settings.usesGlassMaterial(GlassArea.ActionButtons) || settings.usesGlassContour(GlassArea.ActionButtons)) {
        GlassExplicitBadge(modifier, settings)
    } else {
        app.it.fast4x.rimusic.ui.components.themed.IconButton(
            icon = R.drawable.explicit, color = color, onClick = {}, modifier = modifier,
        )
    }
}
