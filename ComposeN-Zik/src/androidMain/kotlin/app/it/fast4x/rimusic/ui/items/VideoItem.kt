package app.it.fast4x.rimusic.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.fast4x.innertube.Innertube
import app.n_zik.android.uiRoundnessShape


import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.ui.styling.overlay
import app.n_zik.android.uiRoundnessShape

import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.parseArtists
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.colorPalette
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.utils.shimmerEffect
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.utils.getLikedIcon
import app.it.fast4x.rimusic.utils.getDislikedIcon
import app.n_zik.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun VideoItem(
    video: Innertube.VideoItem,
    thumbnailHeightDp: Dp,
    thumbnailWidthDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    disableScrollingText: Boolean,
    likeState: Boolean? = null
) {
    VideoItem(
        thumbnailUrl = video.thumbnail?.url,
        duration = video.durationText,
        title = video.info?.name,
        uploader = video.authors.parseArtists().joinToString(", "),
        views = video.viewsText,
        thumbnailHeightDp = thumbnailHeightDp,
        thumbnailWidthDp = thumbnailWidthDp,
        modifier = modifier,
        alternative = alternative,
        disableScrollingText = disableScrollingText,
        likeState = likeState
    )
}

@Composable
fun VideoItem(
    thumbnailUrl: String?,
    duration: String?,
    title: String?,
    uploader: String?,
    views: String?,
    thumbnailHeightDp: Dp,
    thumbnailWidthDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    disableScrollingText: Boolean,
    likeState: Boolean? = null
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailWidthDp,
        modifier = modifier
    ) {
        Box {
            ImageCacheFactory.AsyncImage(
                thumbnailUrl = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .clip(thumbnailShape())
                    .size(width = thumbnailWidthDp, height = thumbnailHeightDp)
            )

            duration?.let {
                BasicText(
                    text = duration,
                    style = typography().xxs.medium.color(colorPalette().onOverlay),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(all = 4.dp)
                        .background(
                            color = colorPalette().overlay,
                            shape = uiRoundnessShape()
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .align(Alignment.BottomEnd)
                )
            }

            if (likeState != null)
                HeaderIconButton(
                    onClick = {},
                    icon = if (likeState == true) getLikedIcon() else getDislikedIcon(),
                    color = when(likeState) {
                        false -> colorPalette().red
                        else -> colorPalette().favoritesIcon
                    },
                    iconSize = 12.dp,
                    modifier = Modifier.align( Alignment.BottomStart )
                                       .absoluteOffset( x = (-8).dp )
                )
        }

        ItemInfoContainer {
            BasicText(
                text = title ?: "",
                style = typography().xs.semiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
            )

            BasicText(
                text = uploader ?: "",
                style = typography().xs.semiBold.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
            )

            views?.let {
                BasicText(
                    text = views,
                    style = typography().xxs.medium.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                )
            }
        }
    }
}

@Composable
fun VideoItemPlaceholder(
    thumbnailHeightDp: Dp,
    thumbnailWidthDp: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = thumbnailWidthDp, height = thumbnailHeightDp)
                .clip(thumbnailShape())
                .shimmerEffect()
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(uiRoundnessShape())
                    .shimmerEffect()
            )
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .clip(uiRoundnessShape())
                    .shimmerEffect()
            )
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .clip(uiRoundnessShape())
                    .shimmerEffect()
            )
        }
    }
}




