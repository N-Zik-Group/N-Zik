package app.n_zik.android.components.ui.screens


import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

import app.n_zik.android.core.coil.imageLoadingViewport
import app.n_zik.android.core.coil.rememberImageLoadingViewportPainter
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.components.glass.glassImageRim
import app.n_zik.android.thumbnailShape
import app.n_zik.android.ui.components.ArtworkShimmerPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview

/** Places the artwork beside the content in landscape. */
@Composable
fun DynamicOrientationLayout(
    thumbnail: Painter,
    shape: androidx.compose.ui.graphics.Shape? = null,
    thumbnailContentPadding: PaddingValues = PaddingValues(0.dp),
    isLoading: Boolean = false,
    content: @Composable () -> Unit
) {

    if( isLandscape )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth( .5f ).padding(thumbnailContentPadding)
            ) {
                
                val artworkShape = shape ?: thumbnailShape()
                val artworkModifier = Modifier.fillMaxSize(.5f).aspectRatio(1f).clip(artworkShape)
                    .glassImageRim(artworkShape)
                if (isLoading || forceLoadingShimmerPreview()) {
                    ArtworkShimmerPlaceholder(artworkModifier)
                } else {
                    val viewportPainter = rememberImageLoadingViewportPainter(thumbnail)
                    Image(
                        painter = viewportPainter, contentDescription = null, contentScale = ContentScale.FillBounds,
                        modifier = artworkModifier.imageLoadingViewport(viewportPainter),
                    )
                }
            }
            content()
        }
    else
        content()
}

/** Reserves overlay space for the landscape artwork independently of the scrolling content. */
@Composable
internal fun LayoutWithAdaptiveThumbnail(
    thumbnailContent: @Composable () -> Unit,
    thumbnailContentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    if (isLandscape) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(thumbnailContentPadding)) { thumbnailContent() }
            content()
        }
    } else {
        content()
    }
}




