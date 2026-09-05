package app.it.fast4x.rimusic.ui.items

import app.n_zik.android.core.database.*
import app.n_zik.android.uiRoundnessShape

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.MONTHLY_PREFIX
import app.it.fast4x.rimusic.PINNED_PREFIX

import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.TextPlaceholder
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.ui.styling.overlay
import app.it.fast4x.rimusic.ui.styling.shimmer
import app.it.fast4x.rimusic.utils.checkFileExists
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.uiRoundnessShape
import androidx.compose.ui.res.stringResource

val HTTP_REGEX = Regex("^https?://.*")
val FOUR_CORNERS = listOf( Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd )

@Composable
fun RenderThumbnail(
    thumbnailUrl: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    if( thumbnailUrl.matches( HTTP_REGEX ) )
        ImageCacheFactory.Thumbnail( thumbnailUrl, contentDescription, contentScale, modifier )
    else
        ImageCacheFactory.AsyncImage(
            thumbnailUrl = thumbnailUrl,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
}

@Composable
private fun BoxWithConstraintsScope.ThumbnailRenderer(
    thumbnailUrls: List<String>,
    contentScale: ContentScale = ContentScale.Crop
) {
    if( thumbnailUrls.size >= 4 ) {
        val halfWidth = maxWidth / 2
        val halfHeight = maxHeight / 2

        FOUR_CORNERS.forEachIndexed { index, corner ->
            RenderThumbnail(
                thumbnailUrl = thumbnailUrls[index],
                contentDescription = corner.toString(),
                contentScale = contentScale,
                modifier = Modifier.size( halfWidth, halfHeight )
                                   .align( corner )
            )
        }
    }
    else if( thumbnailUrls.isNotEmpty() )
        RenderThumbnail( thumbnailUrls.first(), "fullSizeRender", contentScale )
}

@Composable
fun PlaylistItem(
    @DrawableRes icon: Int,
    colorTint: Color,
    name: String?,
    songCount: Int?,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    iconSize: Dp = 34.dp,
    disableScrollingText: Boolean,
    isYoutubePlaylist : Boolean = false,
    isEditable : Boolean = false
) {
    PlaylistItem(
        thumbnailContent = {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorTint),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(iconSize)
            )
        },
        songCount = songCount,
        name = name,
        channelName = null,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText,
        isYoutubePlaylist = isYoutubePlaylist,
        isEditable = isEditable
    )
}

@Composable
fun PlaylistItem(
    playlist: PlaylistPreview,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubePlaylist: Boolean,
    showInfo: Boolean = true,
    isEditable: Boolean,
    thumbnailOverlay: @Composable () -> Unit = {}
) {
    val context = LocalContext.current

    val thumbnails by remember {
        val customThumbnail = checkFileExists( context, "thumbnail/playlist_${playlist.playlist.id}" )

        if( customThumbnail != null )
            flowOf( listOf( customThumbnail ) )
        else
            Database.songPlaylistMapTable
                    .sortSongsByPlayTime( playlist.playlist.id )
                    .distinctUntilChanged()
                    .map { list ->
                        // Ensure it only takes not null thumbnailUrl
                        list.mapNotNull( Song::thumbnailUrl ).takeLast( 4 )
                    }
    }.collectAsState( emptyList(), Dispatchers.IO )

    PlaylistItem(
        browseId = playlist.playlist.browseId,
        thumbnailContent = { ThumbnailRenderer( thumbnails ) },
        songCount = playlist.songCount,
        name = playlist.playlist.name,
        channelName = null,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText,
        isYoutubePlaylist = isYoutubePlaylist,
        showInfo = showInfo,
        isEditable = isEditable,
        thumbnailOverlay = thumbnailOverlay
    )
}

@Composable
fun PlaylistItem(
    playlist: Innertube.PlaylistItem,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showSongsCount: Boolean = true,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubePlaylist : Boolean = false,
    showInfo: Boolean = true,
    isEditable : Boolean = false
) {
    PlaylistItem(
        thumbnailContent = thumb@ {
            val thumbnailUrl = playlist.thumbnail?.url ?: return@thumb

            RenderThumbnail(
                thumbnailUrl,
                "${playlist.title}s_thumbnail",
                ContentScale.FillHeight,
                Modifier.fillMaxSize()
            )
        },
        songCount = playlist.songCount,
        showSongsCount = showSongsCount,
        name = playlist.info?.name,
        channelName = playlist.channel?.name,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText,
        browseId = playlist.key,
        isYoutubePlaylist = isYoutubePlaylist,
        showInfo = showInfo,
        isEditable = isEditable
    )
}

@Composable
fun PlaylistItem(
    browseId: String? = null,
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    songCount: Int?,
    name: String?,
    channelName: String?,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    showSongsCount: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubePlaylist : Boolean = false,
    showInfo: Boolean = true,
    isEditable : Boolean = false,
    thumbnailOverlay: @Composable () -> Unit = {}
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .conditional(alternative) { fillMaxWidth().aspectRatio(1f) }
                .conditional(!alternative) { size(thumbnailSizeDp) }
                .clip(thumbnailShape())
                .background(colorPalette().background4)
        ) {
            thumbnailContent()
            thumbnailOverlay()

            name ?: return@BoxWithConstraints
            val (icon, color) = when {


                name.startsWith( PINNED_PREFIX, true ) ->
                    painterResource( R.drawable.pin_filled ) to colorPalette().accent

                name.startsWith( MONTHLY_PREFIX, true ) ->
                    painterResource( R.drawable.stat_month ) to colorPalette().accent

                browseId == "SPOTIFY_IMPORT" || browseId?.startsWith("SPOTIFY_IMPORT") == true ->
                    painterResource( R.drawable.spotify ) to Color.Unspecified

                browseId == "RIPLAY_IMPORT" || browseId?.startsWith("RIPLAY_IMPORT") == true ->
                    painterResource( R.drawable.riplay ) to Color.Unspecified

                isYoutubePlaylist || browseId?.startsWith("VL") == true ->
                    painterResource( R.drawable.ytmusic ) to Color.Red

                browseId.isNullOrEmpty() ->
                    painterResource( R.drawable.ic_launcher ) to Color.Unspecified

                else ->
                    painterResource( R.drawable.ic_launcher ) to Color.Unspecified
            }

            Icon(
                painter = icon,
                contentDescription = stringResource(R.string.cd_origin_indicator),
                tint = color,
                modifier = Modifier.size( 40.dp ).padding( all = 5.dp )
            )

            songCount?.let {
                if( !showSongsCount ) return@let

                BasicText(
                    text = songCount.toString(),
                    style = typography().xxs.medium.color( colorPalette().onOverlay ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding( all = 4.dp )
                                       .background(
                                           color = colorPalette().overlay,
                                           shape = uiRoundnessShape()
                                       )
                                       .padding( all = 6.dp)
                                       .align(Alignment.BottomEnd)
                )
            }
        }

        if (showInfo) {
            ItemInfoContainer(
                horizontalAlignment = if (alternative && channelName == null) Alignment.CenterHorizontally else Alignment.Start,
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (showName)
                    if (name != null) {
                        BasicText(
                            //text = name.substringAfter(PINNED_PREFIX) ?: "",
                            text = cleanPrefix(name),
                            style = typography().xs.semiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                        )
                    }

                channelName?.let {
                    BasicText(
                        text = channelName,
                        style = typography().xs.semiBold.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistItemPlaceholder(
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier
    ) {
        Spacer(
            modifier = Modifier
                .background(color = colorPalette().shimmer, shape = thumbnailShape())
                .size(thumbnailSizeDp)
        )

        ItemInfoContainer(
            horizontalAlignment = if (alternative) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            TextPlaceholder()
            TextPlaceholder()
        }
    }
}




