package app.n_zik.android.components.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.usesGlassMaterial

/** Material panes own their corners; a shared host mask would cut their outer corners again. */
fun Modifier.playerActionHostClip(settings: GlassSettings, nativeShape: Shape): Modifier =
    if (settings.inScope(GlassScope.PlayerActions).usesGlassMaterial(GlassArea.ActionButtons)) this
    else clip(nativeShape)
