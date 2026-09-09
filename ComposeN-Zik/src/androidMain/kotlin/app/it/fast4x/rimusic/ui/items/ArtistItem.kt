package app.it.fast4x.rimusic.ui.items

import androidx.compose.foundation.Image
import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import app.n_zik.android.uiRoundnessShape

import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.uiRoundnessShape

import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape

import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.shimmerEffect
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.uiRoundnessShape
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.n_zik.android.colorPalette
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ArtistItem(
    artist: Artist,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubeArtist : Boolean = false,
    bookmarkState: Boolean? = null,
    thumbnailOverlay: @Composable () -> Unit = {}
) {
    val likeState by if (bookmarkState != null) {
        remember(bookmarkState) { mutableStateOf(bookmarkState) }
    } else {
        remember( artist.id ) {
            Database.artistTable
                .likeState( artist.id )
                .distinctUntilChanged()
        }.collectAsState( null, Dispatchers.IO )
    }

    ArtistItem(
        thumbnailUrl = artist.thumbnailUrl,
        name = artist.name ?: "",
        subscribersCount = null,
        thumbnailSizePx = thumbnailSizePx,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText,
        isYoutubeArtist = isYoutubeArtist,
        likeState = likeState,
        thumbnailOverlay = thumbnailOverlay
    )
}

@Composable
fun ArtistItem(
    artist: Innertube.ArtistItem,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubeArtist : Boolean = false,
    smallThumbnail: Boolean = false,
    bookmarkState: Boolean? = null,
    thumbnailOverlay: @Composable () -> Unit = {}
) {
    val likeState by if (bookmarkState != null) {
        remember(bookmarkState) { mutableStateOf(bookmarkState) }
    } else {
        remember( artist.key ) {
            Database.artistTable
                .likeState( artist.key )
                .distinctUntilChanged()
        }.collectAsState( null, Dispatchers.IO )
    }

    ArtistItem(
        thumbnailUrl = artist.thumbnail?.url,
        name = artist.info?.name,
        subscribersCount = artist.subscribersCountText,
        thumbnailSizePx = thumbnailSizePx,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText,
        isYoutubeArtist = isYoutubeArtist,
        smallThumbnail = smallThumbnail,
        likeState = likeState,
        thumbnailOverlay = thumbnailOverlay
    )
}

@Composable
fun ArtistItem(
    thumbnailUrl: String?,
    name: String?,
    subscribersCount: String?,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubeArtist : Boolean = false,
    smallThumbnail: Boolean = false,
    likeState: Boolean? = null,
    thumbnailOverlay: @Composable () -> Unit = {}
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .conditional(alternative) { fillMaxWidth().aspectRatio(1f) }
                .conditional(!alternative) { size(thumbnailSizeDp) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(artistThumbnailShape())
            ) {
                ImageCacheFactory.Thumbnail(
                    thumbnailUrl = thumbnailUrl,
                    contentScale = if (alternative) ContentScale.FillWidth else ContentScale.Crop
                )
                thumbnailOverlay()
                if (isYoutubeArtist) {
                    Image(
                        painter = painterResource(R.drawable.ytmusic),
                        colorFilter = ColorFilter.tint(
                            Color.Red.copy(0.75f).compositeOver(Color.White)
                        ),
                        modifier = Modifier
                            .size(if (smallThumbnail) 30.dp else 40.dp)
                            .padding(all = 5.dp),
                        contentDescription = stringResource(R.string.cd_background_image),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            if (likeState != null)
                HeaderIconButton(
                    onClick = {},
                    icon = when(likeState) {
                        false -> R.drawable.bookmark_slash
                        else -> R.drawable.bookmark
                    },
                    color = when(likeState) {
                        false -> colorPalette().red
                        else -> colorPalette().favoritesIcon
                    },
                    iconSize = 12.dp,
                    modifier = Modifier.align( Alignment.BottomStart )
                                       .absoluteOffset( x = (-8).dp )
                )
        }

    if (showName)
        ItemInfoContainer(
            horizontalAlignment = if (alternative) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            BasicText(
                text = cleanPrefix(name ?: ""),
                style = typography().xs.semiBold,
                maxLines = 1, //if (alternative) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
            )

            subscribersCount?.let {
                BasicText(
                    text = subscribersCount,
                    style = typography().xxs.semiBold.secondary,
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
fun ArtistItemPlaceholder(
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(thumbnailSizeDp)
                .clip(artistThumbnailShape())
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
        }
    }
}




