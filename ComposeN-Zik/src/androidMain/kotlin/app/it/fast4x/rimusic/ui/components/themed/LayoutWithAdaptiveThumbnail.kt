package app.it.fast4x.rimusic.ui.components.themed

import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import com.valentinilk.shimmer.shimmer
import app.n_zik.android.colorPalette
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.ui.styling.shimmer
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.core.coil.ImageCacheFactory
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale


@Composable
inline fun LayoutWithAdaptiveThumbnail(
    thumbnailContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val isLandscape = isLandscape

    if (isLandscape) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            thumbnailContent()
            content()
        }
    } else {
        content()
    }
}

@UnstableApi
fun adaptiveThumbnailContent(
    isLoading: Boolean,
    url: String?,
    shape: Shape? = null,
    showIcon: Boolean = false,
    isYoutubePlaylist: Boolean = false,
    onOtherVersionAvailable: (() -> Unit)? = {},
    onClick: (() -> Unit)? = {}
): @Composable () -> Unit = {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val thumbnailSizeDp = if (isLandscape) (maxHeight - 128.dp) else (maxWidth - 64.dp)
        val thumbnailPaddingDp by rememberPreference(thumbnailSizeDpKey, 85f)

        val modifier = Modifier
            //.padding(all = 16.dp)
            .padding(horizontal = if (shape == CircleShape) 0.dp else thumbnailPaddingDp.dp)
            .padding(top = 16.dp)
            .clip(shape ?: thumbnailShape())
            .clip(uiRoundnessShape()).clickable {
                if (onClick != null) {
                    onClick()
                }
            }
            //.size(thumbnailSizeDp)

        if (isLoading) {
            Spacer(
                modifier = modifier
                    .shimmer()
                    .background(colorPalette().shimmer)
            )
        } else {
            ImageCacheFactory.AsyncImage(
                thumbnailUrl = url,
                modifier = modifier
            )
            if (isYoutubePlaylist) {
                Image(
                    painter = painterResource(R.drawable.ytmusic),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        androidx.compose.ui.graphics.Color.Red
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = if (shape == CircleShape) 0.dp else thumbnailPaddingDp.dp)
                        .padding(top = 16.dp)
                        .padding(all = 5.dp)
                        .size(40.dp),
                    contentDescription = stringResource(R.string.cd_youtube_playlist),
                    contentScale = ContentScale.Fit
                )
            }
            if(showIcon)
                onOtherVersionAvailable?.let {
                    Box(
                        modifier = modifier
                            .align(Alignment.BottomEnd)
                            .fillMaxWidth(0.2f)
                    ) {
                        HeaderIconButton(
                            icon = R.drawable.alternative_version,
                            color = colorPalette().text,
                            onClick = {
                                onOtherVersionAvailable()
                            },
                            modifier = Modifier.size(35.dp)
                        )
                    }
                }
        }
    }
}





