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

import app.it.fast4x.rimusic.utils.isLandscape
import app.n_zik.android.components.glass.glassImageRim
import app.n_zik.android.thumbnailShape

/** Places the artwork beside the content in landscape. */
@Composable
fun DynamicOrientationLayout(
    thumbnail: Painter,
    shape: androidx.compose.ui.graphics.Shape? = null,
    thumbnailContentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {

    if( isLandscape )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth( .5f ).padding(thumbnailContentPadding)
            ) {
                
                Image(
                    painter = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize( .5f )
                        .aspectRatio( 1f )
                        .clip( shape ?: thumbnailShape() )
                        .glassImageRim(shape ?: thumbnailShape())
                )
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




