package app.n_zik.android.extensions.pip

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.core.coil.ImageCacheFactory

@Composable
@OptIn(UnstableApi::class)
fun PipModuleCover(
    url: String,
    modifier: Modifier = Modifier
){
    ImageCacheFactory.AsyncImage(
        thumbnailUrl = url,
        contentDescription = "",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxSize()
    )
}


