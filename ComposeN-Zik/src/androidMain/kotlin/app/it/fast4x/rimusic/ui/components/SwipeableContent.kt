@file:kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
package app.it.fast4x.rimusic.ui.components

import app.n_zik.android.core.database.*
import app.n_zik.android.uiRoundnessShape

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.drawscope.Stroke
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_SIZE_SWIPE
import app.it.fast4x.rimusic.utils.DOWNLOAD_INDICATOR_STROKE_WIDTH
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import it.fast4x.innertube.Innertube
import app.n_zik.android.core.database.Database
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.AlbumSwipeAction
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import app.it.fast4x.rimusic.enums.PlaylistSwipeAction
import app.it.fast4x.rimusic.enums.QueueSwipeAction
import app.n_zik.android.download.services.MyDownloadService
import app.n_zik.android.playback.services.isLocal
import app.it.fast4x.rimusic.utils.albumSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.albumSwipeRightActionKey
import app.it.fast4x.rimusic.utils.downloadedStateMedia
import app.it.fast4x.rimusic.utils.getDownloadState
import app.it.fast4x.rimusic.utils.isSwipeToActionEnabledKey
import app.it.fast4x.rimusic.utils.playlistSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.playlistSwipeRightActionKey
import app.it.fast4x.rimusic.utils.queueSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.queueSwipeRightActionKey
import app.it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import app.kreate.android.me.knighthat.sync.YouTubeSync
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.R
import androidx.compose.ui.platform.LocalDensity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableContent(
    swipeToLeftIcon: Int? = null,
    swipeToRightIcon: Int? = null,
    onSwipeToLeft: () -> Unit,
    onSwipeToRight: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = colorPalette().background1,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance: Float -> distance * 0.25f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {onSwipeToRight();hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)}
            else if (value == SwipeToDismissBoxValue.EndToStart) {onSwipeToLeft();hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)}

            return@rememberSwipeToDismissBoxState false
        }
    )
    val isSwipeToActionEnabled by rememberPreference(isSwipeToActionEnabledKey, true)

    val current = LocalViewConfiguration.current
    CompositionLocalProvider(LocalViewConfiguration provides object : ViewConfiguration by current{
        override val touchSlop: Float
            get() = current.touchSlop * 2f
    }) {
        SwipeToDismissBox(
            gesturesEnabled = isSwipeToActionEnabled,
            modifier = modifier
                .clip(uiRoundnessShape()),
            state = dismissState,
            backgroundContent = {
                val offset = try { dismissState.requireOffset() } catch (e: Exception) { 0f }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = when {
                        offset > 0 -> Arrangement.Start
                        offset < 0 -> Arrangement.End
                        else -> Arrangement.Center
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconId = when {
                        offset > 0 -> swipeToRightIcon
                        offset < 0 -> swipeToLeftIcon
                        else -> null
                    }
                    if (iconId == R.drawable.download_progress) {
                        CircularWavyProgressIndicator(
                            color = colorPalette().accent,
                            trackColor = colorPalette().textDisabled,
                            modifier = Modifier.size(DOWNLOAD_INDICATOR_SIZE_SWIPE.dp),
                            stroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() }),
                            trackStroke = Stroke(width = with(LocalDensity.current) { DOWNLOAD_INDICATOR_STROKE_WIDTH.dp.toPx() })
                        )
                    } else if (iconId != null) {
                        Icon(
                            imageVector = ImageVector.vectorResource(iconId),
                            contentDescription = null,
                            tint = colorPalette().accent,
                        )
                    }
                }
            }
        ) {
            content()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SwipeableQueueItem(
    mediaItem: MediaItem,
    onPlayNext: (() -> Unit) = {},
    onDownload: (() -> Unit) = {},
    onRemoveFromQueue: (() -> Unit) = {},
    onEnqueue: (() -> Unit) = {},
    modifier: Modifier = Modifier,
    backgroundColor: Color = colorPalette().background0,
    skipLikeQuery: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val downloadState = getDownloadState(mediaItem.mediaId)
    var downloadedStateMedia by remember { mutableStateOf(DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED) }
    downloadedStateMedia = if (!mediaItem.isLocal) downloadedStateMedia(mediaItem.mediaId)
    else DownloadedStateMedia.DOWNLOADED

    val onDownloadButtonClick: () -> Unit = {
        if (
            (
                    (downloadState == Download.STATE_DOWNLOADING
                    || downloadState == Download.STATE_QUEUED
                    || downloadState == Download.STATE_RESTARTING
                    )  && downloadedStateMedia == DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
            ) ||
            (
                    downloadedStateMedia == DownloadedStateMedia.DOWNLOADED
                   || downloadedStateMedia == DownloadedStateMedia.CACHED_AND_DOWNLOADED
            )
        ) {
            DownloadService.sendRemoveDownload(
                context,
                MyDownloadService::class.java,
                mediaItem.mediaId,
                false
            )
        } else {
            onDownload()
        }
    }

    val songLikeState by remember {
        if (skipLikeQuery) {
            kotlinx.coroutines.flow.flowOf(null)
        } else {
            Database.songTable
                    .likeState( mediaItem.mediaId )
                    .distinctUntilChanged()
        }
    }.collectAsState( null, Dispatchers.IO )

    val onFavourite: () -> Unit = {
        CoroutineScope( Dispatchers.IO ).launch {
            YouTubeSync.toggleSongLike( context, mediaItem )
        }
    }

    val queueSwipeLeftAction by rememberPreference(queueSwipeLeftActionKey, QueueSwipeAction.RemoveFromQueue)
    val queueSwipeRightAction by rememberPreference(queueSwipeRightActionKey, QueueSwipeAction.PlayNext)

    fun getActionCallback(actionName: QueueSwipeAction): () -> Unit {
        return when (actionName) {
            QueueSwipeAction.PlayNext -> onPlayNext
            QueueSwipeAction.Download -> onDownloadButtonClick
            QueueSwipeAction.Favourite -> onFavourite
            QueueSwipeAction.RemoveFromQueue -> onRemoveFromQueue
            QueueSwipeAction.Enqueue -> onEnqueue
            else -> ({})
        }
    }
    val swipeLeftCallback = getActionCallback(queueSwipeLeftAction)
    val swipeRighCallback = getActionCallback(queueSwipeRightAction)

    SwipeableContent(
        swipeToLeftIcon = queueSwipeLeftAction.getStateIcon(
            songLikeState,
            downloadState,
            downloadedStateMedia
        ),
        swipeToRightIcon = queueSwipeRightAction.getStateIcon(
            songLikeState,
            downloadState,
            downloadedStateMedia
        ),
        onSwipeToLeft = swipeLeftCallback,
        onSwipeToRight = swipeRighCallback,
        modifier = modifier,
        backgroundColor = backgroundColor
    ) {
        content()
    }

}

@OptIn(UnstableApi::class)
@Composable
fun SwipeablePlaylistItem(
    mediaItem: MediaItem,
    onPlayNext: (() -> Unit) = {},
    onDownload: (() -> Unit) = {},
    onEnqueue: (() -> Unit) = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val downloadState = getDownloadState(mediaItem.mediaId)
    var downloadedStateMedia by remember { mutableStateOf(DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED) }
    downloadedStateMedia = if (!mediaItem.isLocal) downloadedStateMedia(mediaItem.mediaId)
    else DownloadedStateMedia.DOWNLOADED

    val songLikeState by remember {
        Database.songTable
            .likeState( mediaItem.mediaId )
            .distinctUntilChanged()
    }.collectAsState( null, Dispatchers.IO )

    val onFavourite: () -> Unit = {
        CoroutineScope( Dispatchers.IO ).launch {
            YouTubeSync.toggleSongLike( context, mediaItem )
        }
    }

    val playlistSwipeLeftAction by rememberPreference(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
    val playlistSwipeRightAction by rememberPreference(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)

    fun getActionCallback(actionName: PlaylistSwipeAction): () -> Unit {
        return when (actionName) {
            PlaylistSwipeAction.PlayNext -> onPlayNext
            PlaylistSwipeAction.Download -> onDownload
            PlaylistSwipeAction.Favourite -> onFavourite
            PlaylistSwipeAction.Enqueue -> onEnqueue
            else -> ({})
        }
    }
    val swipeLeftCallback = getActionCallback(playlistSwipeLeftAction)
    val swipeRighCallback = getActionCallback(playlistSwipeRightAction)

    SwipeableContent(
        swipeToLeftIcon =  playlistSwipeLeftAction.getStateIcon(
            songLikeState,
            downloadState,
            downloadedStateMedia
        ),
        swipeToRightIcon =  playlistSwipeRightAction.getStateIcon(
            songLikeState,
            downloadState,
            downloadedStateMedia
        ),
        onSwipeToLeft = swipeLeftCallback,
        onSwipeToRight = swipeRighCallback
    ) {
        content()
    }

}

@OptIn(UnstableApi::class)
@Composable
fun SwipeableAlbumItem(
    albumItem: Innertube.AlbumItem,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onBookmark: () -> Unit,
    content: @Composable () -> Unit
) {
    val album by remember( albumItem.key ) {
        Database.albumTable
                .findById( albumItem.key )
    }.collectAsState( null, Dispatchers.IO )

    val albumSwipeLeftAction by rememberPreference(albumSwipeLeftActionKey, AlbumSwipeAction.PlayNext)
    val albumSwipeRightAction by rememberPreference(albumSwipeRightActionKey, AlbumSwipeAction.Bookmark)

    fun getActionCallback(actionName: AlbumSwipeAction): () -> Unit {
        return when (actionName) {
            AlbumSwipeAction.PlayNext -> onPlayNext
            AlbumSwipeAction.Bookmark -> onBookmark
            AlbumSwipeAction.Enqueue -> onEnqueue
            else -> ({})
        }
    }

    SwipeableContent(
        swipeToLeftIcon =  albumSwipeLeftAction.getStateIcon( album?.bookmarkedAt ),
        swipeToRightIcon =  albumSwipeRightAction.getStateIcon( album?.bookmarkedAt ),
        onSwipeToLeft = getActionCallback( albumSwipeLeftAction ),
        onSwipeToRight = getActionCallback( albumSwipeRightAction )
    ) {
        content()
    }

}




