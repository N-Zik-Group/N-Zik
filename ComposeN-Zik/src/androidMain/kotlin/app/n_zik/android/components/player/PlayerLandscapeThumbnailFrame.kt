package app.n_zik.android.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Fit the square before applying the saved inset, using the toolbar-bounded left viewport. */
@Composable
fun PlayerLandscapeThumbnailFrame(
    contentPadding: Dp,
    artworkVisible: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Standalone overlays own the entire column; an empty square would consume their height.
    if (!artworkVisible) return
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val edge = minOf(maxWidth, maxHeight).coerceAtLeast(0.dp)
        // A fully consumed slot cannot safely host legacy aspectRatio's fallback square.
        if (edge > contentPadding * 2) {
            Box(Modifier.size(edge), contentAlignment = Alignment.Center) { content() }
        }
    }
}
