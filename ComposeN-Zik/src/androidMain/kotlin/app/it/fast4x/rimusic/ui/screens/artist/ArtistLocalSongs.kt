package app.it.fast4x.rimusic.ui.screens.artist

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.LocalPlayerAwareWindowInsets

import app.n_zik.android.core.database.*

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.it.fast4x.compose.persist.persist
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.ShimmerHost
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.LayoutWithAdaptiveThumbnail
import app.it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import app.it.fast4x.rimusic.ui.items.SongItemPlaceholder
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.it.fast4x.rimusic.utils.forcePlayFromBeginning
import app.it.fast4x.rimusic.utils.getDownloadState
import app.it.fast4x.rimusic.utils.isDownloadedSong
import app.it.fast4x.rimusic.utils.manageDownload
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import app.it.fast4x.rimusic.utils.ExternalUris
import app.n_zik.android.colorPalette
import app.kreate.android.me.knighthat.utils.Toaster
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.themed.AutoResizeText
import app.it.fast4x.rimusic.ui.components.themed.FontSizeRange
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.fadingEdge
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.components.artist.FollowButton
import app.n_zik.android.playback.utils.Shuffler
import app.it.fast4x.rimusic.utils.addNext
import app.n_zik.android.components.SongItem
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.core.database.LikeStateManager
import it.fast4x.innertube.requests.ArtistPage
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicText
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import androidx.compose.material3.Text
import app.n_zik.android.LocalDownloadStatesMap
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import app.it.fast4x.rimusic.enums.PlaylistSwipeAction
import app.it.fast4x.rimusic.utils.playlistSwipeLeftActionKey
import app.it.fast4x.rimusic.utils.playlistSwipeRightActionKey
import app.n_zik.android.download.utils.MyDownloadHelper
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun ArtistLocalSongs(
    navController: NavController,
    localArtist: Artist?,
    artistPage: ArtistPage?,
    thumbnailPainter: Painter
) {
    localArtist ?: return
    val browseId = localArtist.id
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    var songs by persist<List<Song>?>("artist/$browseId/localSongs")
    var downloadState by remember { mutableStateOf(Download.STATE_STOPPED) }
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    var showConfirmDeleteDownloadDialog by remember { mutableStateOf(false) }
    var showConfirmDownloadAllDialog by remember { mutableStateOf(false) }
    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px
    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        Database.artistSongs(browseId).collect { songs = it }
    }

    val songCount = songs?.size ?: 0

    val songIds = remember(songs) { songs?.map { it.id }.orEmpty() }
    val likeStatesMap by remember(songIds) {
        LikeStateManager.getLikeStates(songIds)
    }.collectAsState(emptyMap(), Dispatchers.IO)

    // Download state cache
    val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle()
    val downloadedIds by remember {
        derivedStateOf {
            downloadsMapState.values
                .filter { it.state == Download.STATE_COMPLETED }
                .mapTo(HashSet()) { it.request.id }
        }
    }
    val downloadStatesMap by remember {
        derivedStateOf {
            songs.orEmpty().associate { song ->
                song.id to when {
                    song.id in downloadedIds -> DownloadedStateMedia.DOWNLOADED
                    else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                }
            }
        }
    }

    val totalDuration = songs?.sumOf { it.durationText?.split(":")?.let { parts ->
        if (parts.size == 2) parts[0].toInt() * 60 + parts[1].toInt() else 0
    } ?: 0 } ?: 0
    val totalDurationText = if (totalDuration > 0) {
        val hours = totalDuration / 3600
        val minutes = (totalDuration % 3600) / 60
        val seconds = totalDuration % 60
        if (hours > 0) "%dh %02dm %02ds".format(hours, minutes, seconds)
        else "%dm %02ds".format(minutes, seconds)
    } else ""

    // Hoisted swipe action preferences
    val playlistSwipeLeftAction by rememberPreference(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
    val playlistSwipeRightAction by rememberPreference(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)

    CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ArtistHeader(
                localArtist = localArtist,
                artistPage = artistPage,
                thumbnailPainter = thumbnailPainter
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
            ) {
                FollowButton { localArtist }.ToolBarButton()
                HeaderIconButton(
                    icon = R.drawable.download,
                    color = colorPalette().text,
                    iconSize = 24.dp,
                    modifier = Modifier.clip(uiRoundnessShape()),
                    onClick = { showConfirmDownloadAllDialog = true },
                    onLongClick = { Toaster.i(context.resources.getString(R.string.info_download_all_songs)) }
                )
                HeaderIconButton(
                    icon = R.drawable.downloaded,
                    color = colorPalette().text,
                    iconSize = 24.dp,
                    modifier = Modifier.clip(uiRoundnessShape()),
                    onClick = { showConfirmDeleteDownloadDialog = true },
                    onLongClick = { Toaster.i(context.resources.getString(R.string.info_remove_all_downloaded_songs)) }
                )
                HeaderIconButton(
                    icon = R.drawable.enqueue,
                    enabled = !songs.isNullOrEmpty(),
                    color = if (!songs.isNullOrEmpty()) colorPalette().text else colorPalette().textDisabled,
                    iconSize = 24.dp,
                    modifier = Modifier.clip(uiRoundnessShape()),
                    onClick = { if (!songs.isNullOrEmpty()) binder?.player?.enqueue(songs!!.map(Song::asMediaItem), context) },
                    onLongClick = { Toaster.i(context.resources.getString(R.string.info_enqueue_songs)) }
                )
                HeaderIconButton(
                    icon = R.drawable.shuffle,
                    enabled = !songs.isNullOrEmpty(),
                    color = if (!songs.isNullOrEmpty()) colorPalette().text else colorPalette().textDisabled,
                    iconSize = 24.dp,
                    modifier = Modifier.clip(uiRoundnessShape()),
                    onClick = {
                        songs?.let { songs ->
                            if (songs.isNotEmpty()) {
                                binder?.let { Shuffler.play(it, songs) }
                            }
                        }
                    },
                    onLongClick = { Toaster.i(context.resources.getString(R.string.info_shuffle)) }
                )
            }
        }
        item {
            if (showConfirmDownloadAllDialog) {
                ConfirmationDialog(
                    text = stringResource(R.string.do_you_really_want_to_download_all),
                    onDismiss = { showConfirmDownloadAllDialog = false },
                    onConfirm = {
                        showConfirmDownloadAllDialog = false
                        downloadState = Download.STATE_DOWNLOADING
                        if (songs?.isNotEmpty() == true)
                            songs?.forEach {
                                binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                CoroutineScope(Dispatchers.IO).launch {
                                    Database.formatTable.deleteBySongId(it.asMediaItem.mediaId)
                                }
                                manageDownload(
                                    context = context,
                                    mediaItem = it.asMediaItem,
                                    downloadState = false
                                )
                            }
                    }
                )
            }
            if (showConfirmDeleteDownloadDialog) {
                ConfirmationDialog(
                    text = stringResource(R.string.do_you_really_want_to_delete_download),
                    onDismiss = { showConfirmDeleteDownloadDialog = false },
                    onConfirm = {
                        showConfirmDeleteDownloadDialog = false
                        downloadState = Download.STATE_DOWNLOADING
                        if (songs?.isNotEmpty() == true)
                            songs?.forEach {
                                binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                CoroutineScope(Dispatchers.IO).launch {
                                    Database.formatTable.deleteBySongId(it.asMediaItem.mediaId)
                                }
                                manageDownload(
                                    context = context,
                                    mediaItem = it.asMediaItem,
                                    downloadState = true
                                )
                            }
                    }
                )
            }
        }
        item {
            if (songCount > 0) {
                Text(
                    text = stringResource(R.string.artist_songs_count_duration, songCount, totalDurationText),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorPalette().text,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 5.dp)
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }
        if (songs.isNullOrEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.info_no_songs_yet),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorPalette().textSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = songs ?: emptyList(),
                key = { _, song -> song.id }
            ) { index, song ->
                val isDownloaded = isDownloadedSong(song.asMediaItem.mediaId)
                Box(
                    Modifier
                        .fillMaxWidth()
                ) {
                    SwipeablePlaylistItem(
                        mediaItem = song.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(song.asMediaItem)
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(song.asMediaItem)
                        },
                        downloadStateParam = downloadsMapState[song.id]?.state ?: Download.STATE_STOPPED,
                        downloadedStateMediaParam = downloadStatesMap[song.id] ?: DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED,
                        swipeLeftActionParam = playlistSwipeLeftAction,
                        swipeRightActionParam = playlistSwipeRightAction
                    ) {
                        SongItem(
                            song = song,
                            isLiked = likeStatesMap[song.id],
                            navController = navController,
                            modifier = Modifier,

                            onClick = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(
                                    (songs ?: emptyList()).map(Song::asMediaItem),
                                    index
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun ArtistLocalSongs(
    navController: NavController,
    browseId: String,
    headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    thumbnailContent: @Composable () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    var songs by persist<List<Song>?>("artist/$browseId/localSongs")

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }

    val context = LocalContext.current

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    LaunchedEffect(Unit) {
        Database.artistSongs(browseId).collect { songs = it }
    }

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px

    val lazyListState = rememberLazyListState()

    val artistSongIds = remember(songs) { songs?.map { it.id }.orEmpty() }
    val likeStatesMap by remember(artistSongIds) {
        LikeStateManager.getLikeStates(artistSongIds)
    }.collectAsState(emptyMap(), Dispatchers.IO)

    var showConfirmDeleteDownloadDialog by remember {
        mutableStateOf(false)
    }

    var showConfirmDownloadAllDialog by remember {
        mutableStateOf(false)
    }

    // Hoisted download states
    val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle()
    val downloadedIds by remember {
        derivedStateOf {
            downloadsMapState.values
                .filter { it.state == Download.STATE_COMPLETED }
                .mapTo(HashSet()) { it.request.id }
        }
    }
    val downloadStatesMap by remember {
        derivedStateOf {
            songs.orEmpty().associate { song ->
                song.id to when {
                    song.id in downloadedIds -> DownloadedStateMedia.DOWNLOADED
                    else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                }
            }
        }
    }

    // Hoisted swipe action preferences
    val playlistSwipeLeftAction by rememberPreference(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
    val playlistSwipeRightAction by rememberPreference(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)

    LayoutWithAdaptiveThumbnail(thumbnailContent = thumbnailContent) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        headerContent {

                            HeaderIconButton(
                                icon = R.drawable.download,
                                color = colorPalette().text,
                                iconSize = 24.dp,
                                modifier = Modifier.clip(uiRoundnessShape()),
                                        onClick = {
                                            showConfirmDownloadAllDialog = true
                                        },
                                        onLongClick = {
                                            Toaster.i(context.resources.getString(R.string.info_download_all_songs))
                                        }
                            )

                            if (showConfirmDownloadAllDialog) {
                                ConfirmationDialog(
                                    text = stringResource(R.string.do_you_really_want_to_download_all),
                                    onDismiss = { showConfirmDownloadAllDialog = false },
                                    onConfirm = {
                                        showConfirmDownloadAllDialog = false
                                        downloadState = Download.STATE_DOWNLOADING
                                        if (songs?.isNotEmpty() == true)
                                            songs?.forEach {
                                                binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    Database.formatTable.deleteBySongId( it.asMediaItem.mediaId )
                                                }
                                                manageDownload(
                                                    context = context,
                                                    mediaItem = it.asMediaItem,
                                                    downloadState = false
                                                )
                                            }
                                    }
                                )
                            }

                            HeaderIconButton(
                                icon = R.drawable.downloaded,
                                color = colorPalette().text,
                                iconSize = 24.dp,
                                modifier = Modifier.clip(uiRoundnessShape()),
                                        onClick = {
                                            showConfirmDeleteDownloadDialog = true
                                        },
                                        onLongClick = {
                                            Toaster.i(context.resources.getString(R.string.info_remove_all_downloaded_songs))
                                        }
                            )

                            if (showConfirmDeleteDownloadDialog) {
                                ConfirmationDialog(
                                    text = stringResource(R.string.do_you_really_want_to_delete_download),
                                    onDismiss = { showConfirmDeleteDownloadDialog = false },
                                    onConfirm = {
                                        showConfirmDeleteDownloadDialog = false
                                        downloadState = Download.STATE_DOWNLOADING
                                        if (songs?.isNotEmpty() == true)
                                            songs?.forEach {
                                                binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    Database.formatTable.deleteBySongId( it.asMediaItem.mediaId )
                                                }
                                                manageDownload(
                                                    context = context,
                                                    mediaItem = it.asMediaItem,
                                                    downloadState = true
                                                )
                                            }
                                    }
                                )
                            }

                            HeaderIconButton(
                                icon = R.drawable.enqueue,
                                enabled = !songs.isNullOrEmpty(),
                                color = if (!songs.isNullOrEmpty()) colorPalette().text else colorPalette().textDisabled,
                                iconSize = 24.dp,
                                modifier = Modifier.clip(uiRoundnessShape()),
                                        onClick = {
                                            binder?.player?.enqueue(songs!!.map(Song::asMediaItem), context)
                                        },
                                        onLongClick = {
                                            Toaster.i(context.resources.getString(R.string.info_enqueue_songs))
                                        }
                            )
                            HeaderIconButton(
                                icon = R.drawable.shuffle,
                                enabled = !songs.isNullOrEmpty(),
                                color = if (!songs.isNullOrEmpty()) colorPalette().text else colorPalette().textDisabled,
                                iconSize = 24.dp,
                                modifier = Modifier.clip(uiRoundnessShape()),
                                        onClick = {
                                            songs?.let { songs ->
                                                if (songs.isNotEmpty()) {
                                                    binder?.let { Shuffler.play(it, songs) }
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i(context.resources.getString(R.string.info_shuffle))
                                        }
                            )
                        }

                        thumbnailContent()
                    }
                }

                songs?.let { songs ->
                    itemsIndexed(
                        items = songs,
                        key = { _, song -> song.id }
                    ) { index, song ->

                        val isDownloaded = isDownloadedSong(song.asMediaItem.mediaId)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            SwipeablePlaylistItem(
                                mediaItem = song.asMediaItem,
                                onPlayNext = {
                                    binder?.player?.addNext(song.asMediaItem)
                                },
                                onEnqueue = {
                                    binder?.player?.enqueue(song.asMediaItem)
                                },
                                downloadStateParam = downloadsMapState[song.id]?.state ?: Download.STATE_STOPPED,
                                downloadedStateMediaParam = downloadStatesMap[song.id] ?: DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED,
                                swipeLeftActionParam = playlistSwipeLeftAction,
                                swipeRightActionParam = playlistSwipeRightAction
                            ) {
                                SongItem(
                                    song = song,
                                    isLiked = likeStatesMap[song.id],
                                    navController = navController,
                                    onClick = {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlayAtIndex(
                                            songs.map(Song::asMediaItem),
                                            index
                                        )
                                    }
                                )
                            }
                        }
                    }
                } ?: item(key = "loading") {
                    ShimmerHost {
                        repeat(4) {
                            SongItemPlaceholder()
                        }
                    }
                }
            }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if( showFloatingIcon )
                MultiFloatingActionsContainer(
                    iconId = R.drawable.shuffle,
                    onClick = {
                        songs?.let { songs ->
                            if (songs.isNotEmpty()) {
                                binder?.let { Shuffler.play(it, songs) }
                            }
                        }
                    },
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )

        }
    }
    } // CompositionLocalProvider
}

@Composable
fun ArtistHeader(
    localArtist: Artist,
    artistPage: ArtistPage?,
    thumbnailPainter: Painter
) {
    val context = LocalContext.current
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    val isLandscape = isLandscape

    Box(Modifier.fillMaxWidth()) {
        if (!isLandscape)
            Image(
                painter = thumbnailPainter,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .aspectRatio(4f / 3)
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .fadingEdge(
                        top = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + Dimensions.fadeSpacingTop,
                        bottom = Dimensions.fadeSpacingBottom
                    )
            )

        Column(Modifier.align(Alignment.BottomCenter)) {
            AutoResizeText(
                text = cleanPrefix(localArtist.name ?: "..."),
                style = typography().l.semiBold,
                fontSizeRange = FontSizeRange(32.sp, 38.sp),
                fontWeight = typography().l.semiBold.fontWeight,
                fontFamily = typography().l.semiBold.fontFamily,
                color = typography().l.semiBold.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 30.dp)
                    .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    .align(Alignment.CenterHorizontally)
            )
            BasicText(
                text = artistPage?.subscribers.orEmpty(),
                style = typography().s.copy(colorPalette().textSecondary),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        HeaderIconButton(
            icon = R.drawable.share_social,
            color = colorPalette().text,
            iconSize = 24.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 5.dp, end = 5.dp),
            onClick = {
                val url = ExternalUris.youtubeMusicChannel(localArtist.id.removePrefix(MODIFIED_PREFIX))
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }
        )
    }
}







