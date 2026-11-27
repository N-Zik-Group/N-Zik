package app.n_zik.android.ui.items

import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo
import app.n_zik.android.uiRoundnessShape

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import app.n_zik.innertube.Innertube
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.canEditPlaylistMembership
import app.n_zik.android.core.database.isPlaylistBookmarked
import app.n_zik.android.core.database.isYouTubePlaylistSource
import app.n_zik.android.MONTHLY_PREFIX
import app.n_zik.android.PINNED_PREFIX
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.core.rewind.RewindPlaylists.rewindDisplayName

import app.n_zik.android.cleanPrefix
import app.n_zik.android.colorPalette
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.ui.styling.onOverlay
import app.n_zik.android.ui.styling.overlay
import app.n_zik.android.utils.checkFileExists
import app.n_zik.android.utils.color
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.medium
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import app.n_zik.android.components.glass.glassImageRim
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.thumbnail
import androidx.compose.ui.res.stringResource
import app.n_zik.android.ui.components.themed.HeaderIconButton
import app.n_zik.android.ui.styling.favoritesIcon

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
        ImageCacheFactory.Thumbnail( thumbnailUrl, contentDescription, contentScale, modifier, shape = null )
    else
        ImageCacheFactory.AsyncImage(
            shape = null,
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

/** Null while Room is loading. An empty emission means the playlist has no covers. */
@Composable
internal fun rememberPlaylistSongThumbnails(
    playlistId: Long,
    thumbnailSizePx: Int? = null,
): List<String>? = key(playlistId, thumbnailSizePx) {
    val thumbnails by remember(playlistId, thumbnailSizePx) {
        Database.songPlaylistMapTable.coverThumbnailUrls(playlistId)
            .map { covers ->
                if (thumbnailSizePx == null) covers else covers.mapNotNull { it.thumbnail(thumbnailSizePx) }
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    thumbnails
}

@Composable
internal fun PlaylistReadOnlyBadge(playlist: Playlist) {
    if (canEditPlaylistMembership(playlist)) return

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Image(
            painter = painterResource(R.drawable.locked),
            contentDescription = stringResource(R.string.playlist_add_destination_locked),
            colorFilter = ColorFilter.tint(Color.Red),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(5.dp)
                .background(colorPalette().text, CircleShape)
                .padding(5.dp)
                .size(18.dp),
        )
    }
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

    val preview = LocalThemePreview.current
    val playlistId = playlist.playlist.id
    val knownCovers = remember(context, playlistId, preview) {
        if (preview != null) listOfNotNull(ThemePreviewDemo.playlistCover(playlistId))
        else checkFileExists(context, "thumbnail/playlist_$playlistId")?.let { listOf(it) }
    }
    val thumbnails = knownCovers ?: rememberPlaylistSongThumbnails(playlistId)

    PlaylistItem(
        browseId = playlist.playlist.browseId,
        thumbnailContent = {
            if (thumbnails == null) ItemArtworkPlaceholder(thumbnailSizeDp, alternative, thumbnailShape())
            else ThumbnailRenderer(thumbnails)
        },
        songCount = playlist.songCount,
        name = playlist.playlist.name,
        channelName = null,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText,
        isYoutubePlaylist = isPlaylistBookmarked(playlist.playlist, isYoutubePlaylist),
        showInfo = showInfo,
        isEditable = isEditable,
        thumbnailOverlay = {
            thumbnailOverlay()
            PlaylistReadOnlyBadge(playlist.playlist)
        },
    )
}

@Composable
fun PlaylistItem(
    playlist: Innertube.PlaylistItem,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    // Remote YT cards: no song-count badge and no dark scrim.
    showSongsCount: Boolean = false,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubePlaylist : Boolean = false,
    showInfo: Boolean = true,
    isEditable : Boolean = false,
    isBookmarked: Boolean? = null
) {
    val localPlaylist by if (isBookmarked != null) {
        remember(isBookmarked) { mutableStateOf<Playlist?>(null) }
    } else {
        remember(playlist.key) {
            Database.playlistTable.findByBrowseId(playlist.key)
        }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)
    }
    val isBookmarkedResolved = isBookmarked ?: isPlaylistBookmarked(localPlaylist)

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
        remoteSource = true,
        isYoutubePlaylist = isBookmarkedResolved,
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
    remoteSource: Boolean = false,
    thumbnailOverlay: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier
    ) {
        Box {
            BoxWithConstraints(
                modifier = Modifier
                    .conditional(alternative) { fillMaxWidth().aspectRatio(1f) }
                    .conditional(!alternative) { size(thumbnailSizeDp) }
                    .clip(thumbnailShape())
                    .glassImageRim(thumbnailShape())
                    .background(colorPalette().background4)
            ) {
                // Clipped thumbnail area with overlay, icon and song count
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(thumbnailShape())
                ) {
                    this@BoxWithConstraints.run {
                        thumbnailContent()
                        // Full-cover dark scrim on playlist cards, paired with the song
                        // count badge: both are excluded for online (remote) cards
                        // (showSongsCount = false) and for icon-tile callers
                        // (songCount = null). Caller overlays render on top of the scrim.
                        if( name != null && songCount != null && showSongsCount ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(colorPalette().overlay)
                            )
                        }
                        thumbnailOverlay()
                    }

                    if (name != null) {
                        val rewindIcon = RewindPlaylists.iconForPlaylist(name)
                        val (icon, color) = when {
                            name.startsWith( PINNED_PREFIX, true ) ->
                                painterResource( R.drawable.pin_filled ) to colorPalette().accent

                            name.startsWith( MONTHLY_PREFIX, true ) ->
                                painterResource( R.drawable.stat_month ) to colorPalette().accent

                            rewindIcon != null ->
                                painterResource(rewindIcon) to colorPalette().accent

                            browseId == "SPOTIFY_IMPORT" || browseId?.startsWith("SPOTIFY_IMPORT") == true ->
                                painterResource( R.drawable.spotify ) to Color.Unspecified

                            browseId == "RIPLAY_IMPORT" || browseId?.startsWith("RIPLAY_IMPORT") == true ->
                                painterResource( R.drawable.riplay ) to Color.Unspecified

                            isYouTubePlaylistSource(browseId, remoteSource || isYoutubePlaylist) ->
                                painterResource( R.drawable.ytmusic ) to Color.Red

                            browseId.isNullOrEmpty() ->
                                painterResource( R.drawable.ic_launcher ) to Color.Unspecified

                            else ->
                                painterResource( R.drawable.ic_launcher ) to Color.Unspecified
                        }

                        Icon(
                            painter = icon,
                            contentDescription = stringResource(R.string.accessibility_origin_indicator),
                            tint = color,
                            // Dark badge background: the origin icon stays legible over
                            // any cover (same overlay color as the song-count badge).
                            modifier = Modifier.size( 40.dp ).padding( all = 5.dp )
                                                   .background( colorPalette().overlay, CircleShape )
                        )

                        songCount?.let {
                            if( showSongsCount )
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
                }
            }

            // Bookmark icon outside clip so it's not clipped
            if (isYoutubePlaylist)
                HeaderIconButton(
                    onClick = {},
                    icon = R.drawable.bookmark,
                    color = colorPalette().favoritesIcon,
                    iconSize = 12.dp,
                    placementModifier = Modifier.align( Alignment.BottomStart )
                                       .absoluteOffset( x = (-8).dp )
                )
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
                            // Spec 2: localized display name for generated rewind-* playlists (no-op for other names)
                            text = context.rewindDisplayName(cleanPrefix(name)),
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
    showChannel: Boolean = true,
    showTitle: Boolean = true,
    title: String? = null,
    channel: String? = null,
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier
    ) {
        ItemArtworkPlaceholder(thumbnailSizeDp, alternative, thumbnailShape())

        ItemInfoContainer(
            modifier = if (alternative) Modifier.fillMaxWidth() else Modifier,
            horizontalAlignment = if (alternative && !showChannel) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            if (showTitle) ItemTextPlaceholder(title, typography().xs.semiBold)
            if (showChannel) ItemTextPlaceholder(channel, typography().xs.semiBold.secondary, width = 0.45f)
        }
    }
}




