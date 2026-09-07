@file:kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
package app.it.fast4x.rimusic.ui.items

import app.n_zik.android.core.database.*
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.artistTextWithFallback
import app.n_zik.android.utils.titleWithFallback
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.graphics.drawscope.Stroke
import app.it.fast4x.rimusic.utils.getDownloadProgress
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_SIZE_NORMAL
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_STROKE_WIDTH
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.ColorPaletteName
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.download.services.MyDownloadService
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.n_zik.android.components.menu.player.AddToPlaylistPlayerMenu
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.components.themed.NowPlayingSongIndicator
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.ui.styling.favoritesOverlay
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.colorPaletteNameKey
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.downloadedStateMedia
import app.it.fast4x.rimusic.utils.getLikeState
import app.it.fast4x.rimusic.utils.isExplicit
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.playlistindicatorKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.secondary
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.shimmerEffect
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.thumbnail
import app.n_zik.android.core.coil.size
import app.kreate.android.me.knighthat.utils.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import app.n_zik.android.uiRoundnessShape
import timber.log.Timber
import androidx.compose.ui.platform.LocalDensity


@UnstableApi
@Composable
fun SongItem(
    song: Innertube.SongItem,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    onDownloadClick: () -> Unit,
    downloadState: Int,
    thumbnailContent: (@Composable BoxScope.() -> Unit)? = null,
    disableScrollingText: Boolean,
    isNowPlaying: Boolean = false,
    forceRecompose: Boolean = false
) {
    SongItem(
        thumbnailUrl = song.thumbnail?.size(thumbnailSizePx),
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        onDownloadClick = {
            CoroutineScope(Dispatchers.IO).launch {
                Database.upsert( song )
            }
            onDownloadClick()
        },
        downloadState = downloadState,
        mediaItem = song.asMediaItem,
        onThumbnailContent = thumbnailContent,
        disableScrollingText = disableScrollingText,
        isNowPlaying = isNowPlaying,
        forceRecompose = forceRecompose
    )
}

@UnstableApi
@Composable
fun SongItem(
    song: MediaItem,
    thumbnailSizeDp: Dp,
    thumbnailSizePx: Int,
    modifier: Modifier = Modifier,
    onThumbnailContent: (@Composable BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onDownloadClick: () -> Unit,
    downloadState: Int,
    isRecommended: Boolean = false,
    disableScrollingText: Boolean,
    isNowPlaying: Boolean = false,
    forceRecompose: Boolean = false
) {
    SongItem(
        thumbnailUrl = song.mediaMetadata.artworkUri.thumbnail(thumbnailSizePx)?.toString(),
        thumbnailSizeDp = thumbnailSizeDp,
        onThumbnailContent = onThumbnailContent,
        trailingContent = trailingContent,
        modifier = modifier,
        onDownloadClick = {
            Database.asyncTransaction {
                insertIgnore( song )
            }
            onDownloadClick()
        },
        downloadState = downloadState,
        isRecommended = isRecommended,
        mediaItem = song,
        disableScrollingText = disableScrollingText,
        isNowPlaying = isNowPlaying,
        forceRecompose = forceRecompose
    )
}

@UnstableApi
@Composable
fun SongItem(
    song: Song,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    onThumbnailContent: (@Composable BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onDownloadClick: () -> Unit,
    downloadState: Int,
    disableScrollingText: Boolean,
    isNowPlaying: Boolean = false,
    forceRecompose: Boolean = false
) {
    SongItem(
        thumbnailUrl = song.thumbnailUrl,
        thumbnailSizeDp = thumbnailSizeDp,
        onThumbnailContent = onThumbnailContent,
        trailingContent = trailingContent,
        modifier = modifier,
        onDownloadClick = {
            Database.asyncTransaction {
                songTable.upsert( song )
            }
            onDownloadClick()
        },
        downloadState = downloadState,
        mediaItem = song.asMediaItem,
        disableScrollingText = disableScrollingText,
        isNowPlaying = isNowPlaying,
        forceRecompose = forceRecompose
    )
}

@UnstableApi
@Composable
fun SongItem(
    thumbnailUrl: String?,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    onThumbnailContent: (@Composable BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onDownloadClick: () -> Unit,
    downloadState: Int,
    isRecommended: Boolean = false,
    mediaItem: MediaItem,
    disableScrollingText: Boolean,
    isNowPlaying: Boolean = false,
    forceRecompose: Boolean = false
) {
    val binder = LocalPlayerServiceBinder.current

    SongItem(
        thumbnailSizeDp = thumbnailSizeDp,
        thumbnailContent = {
            ImageCacheFactory.Thumbnail( thumbnailUrl )

            onThumbnailContent?.invoke(this)

            NowPlayingSongIndicator(
                mediaId = mediaItem.mediaId,
                player = binder?.player,
                containerSize = thumbnailSizeDp
            )
        },
        modifier = modifier,
        trailingContent = trailingContent,
        onDownloadClick = onDownloadClick,
        downloadState = downloadState,
        isRecommended = isRecommended,
        mediaItem = mediaItem,
        disableScrollingText = disableScrollingText,
        isNowPlaying = isNowPlaying,
        forceRecompose = forceRecompose
    )
}

/*
@Composable
fun SongItem(
    thumbnailContent: @Composable BoxScope.() -> Unit,
    title: String?,
    authors: String?,
    duration: String?,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    isDownloaded: Boolean,
    onDownloadClick: () -> Unit,
    disableScrollingText: Boolean
) {
    ItemContainer(
        alternative = false,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(thumbnailSizeDp)
        ) {
            thumbnailContent()
        }

        ItemInfoContainer {
            trailingContent?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = title ?: "",
                        style = typography().xs.semiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )

                    it()
                }
            } ?: BasicText(
                text = title ?: "",
                style = typography().xs.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
            )


            Row(verticalAlignment = Alignment.CenterVertically) {

                IconButton(
                    onClick = onDownloadClick,
                    icon = if (isDownloaded) R.drawable.downloaded else R.drawable.download,
                    color = if (isDownloaded) colorPalette().text else colorPalette().textDisabled,
                    modifier = Modifier
                        .size(16.dp)
                )

                Spacer(modifier = Modifier.padding(horizontal = 2.dp))

                BasicText(
                    text = authors ?: "",
                    style = typography().xs.semiBold.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .weight(1f)
                        .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                )

                duration?.let {
                    BasicText(
                        text = duration,
                        style = typography().xxs.secondary.medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
*/

@OptIn(ExperimentalFoundationApi::class, ExperimentalTextApi::class,
    ExperimentalAnimationApi::class
)
@UnstableApi
@Composable
fun SongItem(
    thumbnailContent: @Composable BoxScope.() -> Unit,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    onDownloadClick: () -> Unit,
    downloadState: Int,
    isRecommended: Boolean = false,
    mediaItem: MediaItem,
    disableScrollingText: Boolean,
    isNowPlaying: Boolean = false,
    forceRecompose: Boolean = false
) {

    var downloadedStateMedia by remember { mutableStateOf(DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED) }
    downloadedStateMedia = if (!mediaItem.isLocal) downloadedStateMedia(mediaItem.mediaId)
    else DownloadedStateMedia.DOWNLOADED

    val title = mediaItem.titleWithFallback()
    val authors = mediaItem.artistTextWithFallback()
    val duration = mediaItem.mediaMetadata.extras?.getString("durationText")

    val playlistindicator by rememberPreference(playlistindicatorKey,false)
    val isSongMappedToPlaylist by remember {
        Database.songPlaylistMapTable.isMapped( mediaItem.mediaId )
    }.collectAsState( false, Dispatchers.IO )
    val colorPaletteName by rememberPreference(colorPaletteNameKey, ColorPaletteName.Dynamic)

    val context = LocalContext.current
    val colorPalette = LocalAppearance.current.colorPalette

    ItemContainer(
        alternative = false,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier
            .padding(end = 8.dp)
            .clip(uiRoundnessShape())
            .conditional(isNowPlaying){
                background(colorPalette.favoritesOverlay)
            }

    ) {
        Box(
            modifier = Modifier
                .size(thumbnailSizeDp)
        ) {
            thumbnailContent()

            val isSongLiked by remember( mediaItem.mediaId ) {
                Database.songTable
                    .isLiked( mediaItem.mediaId )
                    .distinctUntilChanged()
            }.collectAsState( false, Dispatchers.IO )

            if ( isSongLiked )
                HeaderIconButton(
                    onClick = {},
                    icon = getLikeState(mediaItem.mediaId),
                    color = colorPalette().favoritesIcon,
                    iconSize = 12.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)

                )
            /*
            if (totalPlayTimeMs != null) {
                if (totalPlayTimeMs <= 0 ) {
                    HeaderIconButton(
                        onClick = {},
                        icon = R.drawable.noteslashed,
                        color = colorPalette().favoritesIcon,
                        iconSize = 12.dp,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .align(Alignment.BottomStart)
                    )
                }
            }
             */

            /*
            BasicText(
                text = totalPlayTimeMs.toString() ?: "",
                style = typography().xs.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(all = 16.dp)
            )
             */
        }

        ItemInfoContainer {
            trailingContent?.let {
                val menuState = LocalMenuState.current
                val navController = rememberNavController()
                val binder = LocalPlayerServiceBinder.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRecommended)
                        IconButton(
                            icon = R.drawable.smart_shuffle,
                            color = colorPalette().accent,
                            enabled = true,
                            onClick = {},
                            modifier = Modifier
                                .size(18.dp)
                        )

                    if ( playlistindicator && isSongMappedToPlaylist ) {
                        IconButton(
                            icon = R.drawable.add_in_playlist,
                            color = if (colorPaletteName == ColorPaletteName.PureBlack) Color.Black else colorPalette().text,
                            enabled = true,
                            onClick = {
                                menuState.display {
                                    if (binder != null) {
                                        AddToPlaylistPlayerMenu(
                                            navController = navController,
                                            onDismiss = {
                                                menuState.hide()
                                            },
                                            mediaItem = mediaItem,
                                            binder = binder,
                                            onClosePlayer = {},
                                        )
                                    }
                                }
                            },
                            onLongClick = {
                                Toaster.i( R.string.playlistindicatorinfo2 )
                            },
                            modifier = Modifier
                                .size(14.dp)
                                .background(colorPalette().accent, CircleShape)
                                .padding(all = 3.dp)
                                .clip(uiRoundnessShape())
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                    }

                    if ( mediaItem.isExplicit )
                        IconButton(
                            icon = R.drawable.explicit,
                            color = colorPalette().text,
                            enabled = true,
                            onClick = {},
                            modifier = Modifier
                                .size(18.dp)
                        )

                    BasicText(
                        text = cleanPrefix(title),
                        style = typography().xs.semiBold,
                        /*
                        style = TextStyle(
                            color = if (isRecommended) colorPalette().accent else colorPalette().text,
                            fontStyle = typography().xs.semiBold.fontStyle,
                            fontSize = typography().xs.semiBold.fontSize
                        ),
                         */
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )

                    /*
                    if (playlistindicator && (songPlaylist > 0)) {
                        IconButton(
                            icon = R.drawable.add_in_playlist,
                            color = colorPalette().text,
                            enabled = true,
                            onClick = {},
                            modifier = Modifier
                                .size(18.dp)
                                .background(colorPalette().accent, CircleShape)
                                .padding(all = 3.dp)
                                .clip(uiRoundnessShape()).combinedClickable(onClick = {}, onLongClick = {
                                    SmartMessage(context.resources.getString(R.string.playlistindicatorinfo2), context = context)
                                })
                        )
                    }
                     */

                    it()
                }
            } ?: Row(verticalAlignment = Alignment.CenterVertically) {
                val menuState = LocalMenuState.current
                val navController = rememberNavController()
                val binder = LocalPlayerServiceBinder.current
                    if (isRecommended)
                        IconButton(
                            icon = R.drawable.smart_shuffle,
                            color = colorPalette().accent,
                            enabled = true,
                            onClick = {},
                            modifier = Modifier
                                .size(18.dp)
                        )

                    if ( mediaItem.isExplicit )
                        IconButton(
                            icon = R.drawable.explicit,
                            color = colorPalette().text,
                            enabled = true,
                            onClick = {},
                            modifier = Modifier
                                .size(18.dp)
                        )
                    BasicText(
                        text = cleanPrefix(title),
                        style = typography().xs.semiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                            .weight(1f)
                    )
                if ( playlistindicator && isSongMappedToPlaylist ) {
                    IconButton(
                        icon = R.drawable.add_in_playlist,
                        color = if (colorPaletteName == ColorPaletteName.PureBlack) Color.Black else colorPalette().text,
                        enabled = true,
                        onClick = {
                            menuState.display {
                                if (binder != null) {
                                    AddToPlaylistPlayerMenu(
                                        navController = navController,
                                        onDismiss = {
                                            menuState.hide()
                                        },
                                        mediaItem = mediaItem,
                                        binder = binder,
                                        onClosePlayer = {},
                                    )
                                }
                            }
                        },
                        onLongClick = {
                            Toaster.i( R.string.playlistindicatorinfo2 )
                        },
                        modifier = Modifier
                            .size(18.dp)
                            .background(colorPalette().accent, CircleShape)
                            .padding(all = 3.dp)
                            .clip(uiRoundnessShape())
                    )
                }
            }


            Row(verticalAlignment = Alignment.CenterVertically) {

                /*
                if ((downloadState == Download.STATE_DOWNLOADING
                            || downloadState == Download.STATE_QUEUED
                            || downloadState == Download.STATE_RESTARTING
                        )
                    && !isDownloaded) {
                    val context = LocalContext.current
                    IconButton(
                        onClick = {
                            DownloadService.sendRemoveDownload(
                                context,
                                MyDownloadService::class.java,
                                mediaId,
                                false
                            )
                        },
                        icon = R.drawable.download_progress,
                        color = colorPalette().text,
                        modifier = Modifier
                            .size(16.dp)
                    )
                    /*
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colorPalette().text,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(uiRoundnessShape()).clickable {
                                DownloadService.sendRemoveDownload(
                                        context,
                                        MyDownloadService::class.java,
                                        mediaId,
                                        false
                                    )
                            }
                    )
                     */
                } else {
                   IconButton(
                        onClick = onDownloadClick,
                        icon = if (isDownloaded) R.drawable.downloaded else R.drawable.download,
                        color = if (isDownloaded) colorPalette().text else colorPalette().textDisabled,
                        modifier = Modifier
                            .size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                */

                BasicText(
                    text = cleanPrefix(authors),
                    style = typography().xs.semiBold.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .weight(1f)
                        .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                )

                duration?.let {
                    BasicText(
                        text = duration,
                        style = typography().xxs.secondary.medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.padding(horizontal = 4.dp))

                //Timber.d("downloadutil $mediaId $downloadState: $downloadState")

                if ((downloadState == Download.STATE_DOWNLOADING
                            || downloadState == Download.STATE_QUEUED
                            || downloadState == Download.STATE_RESTARTING
                            )
                    && downloadedStateMedia == DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(uiRoundnessShape()).clickable(
                                onClick = {
                                    DownloadService.sendRemoveDownload(
                                        context,
                                        MyDownloadService::class.java,
                                        mediaItem.mediaId,
                                        false
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val progress = getDownloadProgress(mediaItem.mediaId)
                        if (progress > 0.01f && downloadState == Download.STATE_DOWNLOADING) {
                            CircularWavyProgressIndicator(
                                progress = { progress },
                                color = colorPalette().accent,
                                trackColor = colorPalette().textDisabled,
                                modifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_NORMAL.dp),
                                stroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                                trackStroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() })
                            )
                        } else {
                            CircularWavyProgressIndicator(
                                color = colorPalette().accent,
                                trackColor = colorPalette().textDisabled,
                                modifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_NORMAL.dp),
                                stroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                                trackStroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() })
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onDownloadClick,
                        icon = downloadedStateMedia.iconId,
                        color = when(downloadedStateMedia) {
                            DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED -> colorPalette().textDisabled
                            else -> colorPalette().text
                        },
                        modifier = Modifier
                            .size(20.dp)
                    )
                }

            }
        }
    }
}

/**
 * New component is more resemble to the final
 * SongItem that's currently being used.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongItemPlaceholder( modifier: Modifier = Modifier ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy( 12.dp ),
        modifier = modifier.fillMaxWidth()
                           .padding(
                               vertical = 8.dp,
                               horizontal = 16.dp
                           )
    ) {
        Box(
            Modifier.size( Dimensions.thumbnails.song )
                    .clip( uiRoundnessShape() )
                    .shimmerEffect()
        )

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth( .7f )
            ) {
                BasicText(
                    text = "",
                    style = typography().xs.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight( 1f ).shimmerEffect()
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box( Modifier.weight( 1f ).fillMaxWidth() ) {
                    BasicText(
                        text = "",
                        style = typography().xs.semiBold.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxWidth( .3f ).shimmerEffect()
                    )
                }

                BasicText(
                    text = "0:00",
                    style = typography().xxs.secondary.medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding( top = 4.dp )
                )

                Spacer(modifier = Modifier.padding( horizontal = 4.dp ))

                IconButton(
                    onClick = {},
                    icon = DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED.iconId,
                    color = colorPalette().textDisabled,
                    modifier = Modifier.size( 20.dp ),
                    enabled = false
                )
            }
        }
    }
}





