package app.n_zik.android.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.Box
import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.innertube.Innertube


import app.n_zik.android.ui.styling.onOverlay
import app.n_zik.android.ui.styling.overlay

import app.n_zik.android.utils.color
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.medium
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
import app.n_zik.android.colorPalette
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.utils.shimmerEffect
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.ui.components.themed.HeaderIconButton
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.utils.getLikedIcon
import app.n_zik.android.utils.getDislikedIcon
import app.n_zik.android.R

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
        uploader = video.authors.artistEntryNames().joinToString(", "),
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
                shape = thumbnailShape(),
                thumbnailUrl = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .clip(thumbnailShape())
                    .size(width = thumbnailWidthDp, height = thumbnailHeightDp)
            )

            duration?.let {
                VideoDurationBadge(it, Modifier.align(Alignment.BottomEnd))
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
                    placementModifier = Modifier.align( Alignment.BottomStart )
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
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    metadataLines: Int = 1,
    title: String? = null,
    uploader: String? = null,
    views: String? = null,
    video: Innertube.VideoItem? = null,
) {
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    ItemContainer(alternative, thumbnailWidthDp, modifier) {
        Box {
            Box(
                Modifier.size(width = thumbnailWidthDp, height = thumbnailHeightDp)
                    .clip(thumbnailShape())
                    .shimmerEffect()
            )
            if (video == null || video.durationText != null) {
                VideoDurationBadge("--:--", Modifier.align(Alignment.BottomEnd))
            }
        }
        ItemInfoContainer {
            ItemTextPlaceholder(
                video?.title ?: title,
                typography().xs.semiBold,
                maxLines = if (disableScrollingText) 2 else 1,
            )
            if (video != null || metadataLines > 0) {
                val author = video?.authors?.artistEntryNames()?.joinToString(", ") ?: uploader
                ItemTextPlaceholder(author, typography().xs.semiBold.secondary, width = 0.55f)
            }
            val viewCount = video?.viewsText ?: views
            if (viewCount != null || (video == null && metadataLines > 1)) ItemTextPlaceholder(
                viewCount,
                typography().xxs.medium.secondary,
                Modifier.padding(top = 4.dp),
                width = 0.4f,
            )
        }
    }
}

@Composable
private fun VideoDurationBadge(duration: String, modifier: Modifier) {
    BasicText(
        text = duration,
        style = typography().xxs.medium.color(colorPalette().onOverlay),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(4.dp)
            .background(colorPalette().overlay, uiRoundnessShape())
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}




