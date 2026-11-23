package app.n_zik.android.core.coil

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay

@Composable
internal fun MissingArtworkThumbnail(modifier: Modifier, contentScale: ContentScale, loadingPainter: Painter) {
    var showFallback by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(10_000L)
        showFallback = true
    }
    Image(
        painter = if (showFallback) ImageCacheFactory.rememberArtworkResourcePainter() else loadingPainter,
        contentDescription = null,
        modifier = modifier.fillMaxSize().imageLoadingBackground(loadingPainter) { !showFallback },
        contentScale = contentScale,
    )
}
