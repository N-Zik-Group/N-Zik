package app.n_zik.android.components.player.lyrics

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.glass.GlassOverlayScene
import app.n_zik.android.components.glass.GlassScope

/** Owns the lyrics scene separately from foreground utility controls. */
@Composable
internal fun LyricsGlassScene(
    background: Color,
    modifier: Modifier = Modifier,
    transportHeight: Dp = 0.dp,
    transportShade: Color = Color.Transparent,
    scene: @Composable BoxScope.() -> Unit,
    controls: @Composable BoxScope.() -> Unit,
) {
    GlassOverlayScene(background, modifier, contentAlignment = Alignment.Center,
        scope = GlassScope.Lyrics, scene = {
            scene()
            if (transportHeight > 0.dp && transportShade.alpha > 0f) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(transportHeight)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, transportShade))))
            }
        }, content = controls)
}
