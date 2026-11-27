package app.n_zik.android.ui.screens.artist

import android.content.Context
import androidx.media3.datasource.cache.Cache
import androidx.compose.ui.res.pluralStringResource
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.formatAsDuration
import app.n_zik.android.components.ui.screens.CollectionArtworkHeader
import app.n_zik.android.components.ui.screens.CollectionArtworkShareButton
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.draw.clip

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.uiRoundnessShape


import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import app.n_zik.android.compose.persist.persist
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.ui.components.themed.HeaderIconButton
import app.n_zik.android.components.ui.screens.LayoutWithAdaptiveThumbnail
import app.n_zik.android.ui.components.themed.MultiFloatingActionsContainer
import app.n_zik.android.ui.items.FullSongListPlaceholderCount
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.items.SongItemPlaceholders
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.px
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.excludeMediaItems
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.forcePlayFromBeginning
import app.n_zik.android.utils.getDownloadState
import app.n_zik.android.utils.isDownloadedSong
import app.n_zik.android.utils.manageDownload
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showFloatingIconKey
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.ExternalUris
import app.n_zik.android.colorPalette
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.playback.services.isLocal
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.ExperimentalTextApi
import app.n_zik.android.cleanPrefix
import app.n_zik.android.typography
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import app.n_zik.android.utils.conditional
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.semiBold
import app.n_zik.android.components.artist.FollowButton
import app.n_zik.android.components.artist.artistStatisticsText
import app.n_zik.android.components.ui.CollectionButtonToolbar
import app.n_zik.android.components.ui.CollectionFavoritesKind
import app.n_zik.android.components.ui.rememberCollectionFavoritesButton
import app.n_zik.android.components.tab.Locator
import app.n_zik.android.components.settings.ui.ToolbarAction as ToolbarControl
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.components.settings.ui.ordered

import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.matchesCollectionSearch
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Search
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.utils.addNext
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.shuffleButtonIcon
import app.n_zik.android.models.Artist
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.innertube.requests.ArtistPage
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.MODIFIED_PREFIX
import androidx.compose.material3.Text
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.PlaylistSwipeAction
import app.n_zik.android.utils.playlistSwipeLeftActionKey
import app.n_zik.android.utils.playlistSwipeRightActionKey
import app.n_zik.android.download.utils.MyDownloadHelper
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.enqueueOffMain
import timber.log.Timber

@Composable
internal fun artistLibraryToolbarAction(
    action: ToolbarControl,
    icon: Int = action.icon,
    label: Int = action.label,
    enabled: Boolean = true,
    dismissOnClick: Boolean = true,
    onClick: () -> Unit = {},
): MenuIcon {
    val menuState = LocalMenuState.current
    return object : MenuIcon, Descriptive {
        override val iconId = icon
        override val messageId = label
        override val isEnabled = enabled
        override val color: Color
            @Composable get() = if (isEnabled) colorPalette().text else colorPalette().textDisabled
        override val menuIconTitle: String
            @Composable get() = stringResource(messageId)

        override fun onShortClick() {
            if (isEnabled) {
                if (dismissOnClick) menuState.hide()
                onClick()
            }
        }
    }
}

private data class ArtistDownloadRequest(
    val songs: List<Song>,
    val remove: Boolean,
)

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
    thumbnailPainter: Painter,
    songsState: MutableState<List<Song>?>,
    contentPadding: PaddingValues = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
    lazyListState: LazyListState = rememberLazyListState(),
) {
    localArtist ?: return
    val browseId = localArtist.id
    val context = LocalContext.current
    val forceShimmer = forceLoadingShimmerPreview()
    val binder = LocalPlayerServiceBinder.current
    var songs by songsState
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    var pendingDownload by remember(browseId) { mutableStateOf<ArtistDownloadRequest?>(null) }
    val search = key(browseId) { Search.detail() }
    val query = search.filterQuery
    val visibleSongs = remember(songs, query) {
        if (query.isBlank()) songs.orEmpty() else songs.orEmpty().filter { it.matchesCollectionSearch(query) }
    }
    SearchEmptyFeedback(query, visibleSongs.isEmpty(), loading = songs == null || forceShimmer)
    val itemSelector = key(browseId) { ItemSelector<Song>() }
    fun actionSongs() = itemSelector.actionTargets(visibleSongs, Song::id, Song::id)
    val offlineMode by OfflineMode.rememberEnabled()
    val addToPlaylist = PlaylistsMenu.forSongs(
        navController = navController,
        songs = ::actionSongs,
        onFailure = { error, preview ->
            Timber.tag("ArtistLibrary").e(error, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        itemSelector = itemSelector,
    )

    LaunchedEffect(browseId) {
        Database.artistSongs(browseId).collect { songs = it }
    }

    val songCount = songs?.size ?: 0

    val songIds = remember(songs) { songs?.map { it.id }.orEmpty() }
    val likeStatesMap by remember(songIds) {
        LikeStateManager.getLikeStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val favoriteTargets = actionSongs()
    val favorites = rememberCollectionFavoritesButton(
        mediaItems = remember(favoriteTargets) { favoriteTargets.map(Song::asMediaItem) },
        kind = CollectionFavoritesKind.Artist,
        enabled = songs != null && !forceShimmer,
        collectionKey = browseId,
        likeStates = likeStatesMap,
    )
    // Header, actions, search and summary precede the song rows.
    val locator = Locator(lazyListState, listItemOffset = 4) { visibleSongs }
    val playlistStatesMap by remember(songIds) {
        PlaylistStateManager.getPlaylistStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    // Download state cache
    val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle(initialValue = MyDownloadHelper.downloads.value, context = NzikDispatchers.DATA)
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

    val totalDurationMs = songs?.sumOf { it.durationText?.let(::durationTextToMillis) ?: 0L } ?: 0L

    // Hoisted swipe action preferences
    val playlistSwipeLeftAction by rememberPreference(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
    val playlistSwipeRightAction by rememberPreference(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)

    DialogPresence(target = pendingDownload) { request ->
        ConfirmationDialog(
            title = stringResource(
                if (request.remove) R.string.downloads_delete_dialog_title else R.string.downloads_action_download_all,
            ),
            confirmText = stringResource(
                if (request.remove) R.string.common_action_delete else R.string.downloads_title,
            ),
            text = stringResource(
                if (request.remove) R.string.downloads_delete_all_confirmation else R.string.downloads_download_all_confirmation,
            ),
            onDismiss = { if (pendingDownload == request) pendingDownload = null },
            onConfirm = {
                if (pendingDownload == request) {
                    pendingDownload = null
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        processArtistDownloadBatch(request.songs, binder?.cache, context, downloadState = request.remove)
                    }
                }
            },
        )
    }

    CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
    LazyColumn(
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize()
    ) {
        item("header") {
            ArtistHeader(
                localArtist = localArtist,
                artistPage = artistPage,
                thumbnailPainter = thumbnailPainter
            )
        }
        item("action_buttons") {
            val targets = actionSongs()
            val hasTargets = targets.isNotEmpty()
            val remoteTargets = targets.filterNot { it.isLocal }
            val downloadTargets = remoteTargets.filterNot { it.id in downloadsMapState }
            val removeDownloadTargets = remoteTargets.filter { it.id in downloadsMapState }
            val actions = mapOf(
                ToolbarControl.SEARCH to search,
                ToolbarControl.LOCATE to locator,
                ToolbarControl.DOWNLOAD to artistLibraryToolbarAction(ToolbarControl.DOWNLOAD, enabled = downloadTargets.isNotEmpty()) {
                    pendingDownload = ArtistDownloadRequest(downloadTargets, remove = false)
                },
                ToolbarControl.DELETE_DOWNLOADS to artistLibraryToolbarAction(ToolbarControl.DELETE_DOWNLOADS, enabled = removeDownloadTargets.isNotEmpty()) {
                    pendingDownload = ArtistDownloadRequest(removeDownloadTargets, remove = true)
                },
                ToolbarControl.PLAY_NEXT to artistLibraryToolbarAction(ToolbarControl.PLAY_NEXT, enabled = hasTargets) {
                    val player = binder?.player ?: return@artistLibraryToolbarAction
                    val snapshot = actionSongs().toList().ifEmpty { return@artistLibraryToolbarAction }
                    val completeSelection = itemSelector.captureSelectionCompletion()
                    NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                        val items = withContext(NzikDispatchers.DATA) { snapshot.map(Song::asMediaItem) }
                        player.addNextOffMain(items, context)
                        completeSelection()
                    }
                },
                ToolbarControl.ENQUEUE to artistLibraryToolbarAction(
                    ToolbarControl.ENQUEUE,
                    label = R.string.player_action_enqueue_songs,
                    enabled = hasTargets,
                ) {
                    val player = binder?.player ?: return@artistLibraryToolbarAction
                    val snapshot = actionSongs().toList().ifEmpty { return@artistLibraryToolbarAction }
                    val completeSelection = itemSelector.captureSelectionCompletion()
                    NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                        val items = withContext(NzikDispatchers.DATA) { snapshot.map(Song::asMediaItem) }
                        player.enqueueOffMain(items, context)
                        completeSelection()
                    }
                },
                ToolbarControl.SHUFFLE to artistLibraryToolbarAction(
                    ToolbarControl.SHUFFLE,
                    icon = shuffleButtonIcon(),
                    enabled = hasTargets,
                ) {
                    val snapshot = actionSongs().toList()
                    if (snapshot.isNotEmpty() && binder != null) {
                        binder.triggerShuffleOkFlash()
                        Shuffler.play(binder, snapshot)
                    }
                },
                ToolbarControl.SELECT to itemSelector,
                ToolbarControl.FAVORITES to favorites,
                ToolbarControl.ADD_TO_PLAYLIST to artistLibraryToolbarAction(
                    ToolbarControl.ADD_TO_PLAYLIST,
                    enabled = hasTargets,
                    onClick = addToPlaylist::onShortClick,
                ),
            )
            CollectionButtonToolbar(
                bookmark = { FollowButton { localArtist }.ToolBarButton() },
                bookmarkWidth = 100.dp,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                buttons = ToolbarSurface.ARTIST_LIBRARY.ordered(
                    if (offlineMode) actions - ToolbarControl.DOWNLOAD else actions,
                ),
            )
        }
        item("detail-search") {
            Column { search.SearchBar(this) }
        }
        item {
            if (songCount > 0) {
                val text = if (totalDurationMs > 0) {
                    pluralStringResource(R.plurals.library_song_count_with_duration, songCount, songCount, formatAsDuration(totalDurationMs))
                } else {
                    pluralStringResource(R.plurals.library_song_count, songCount, songCount)
                }
                val style = MaterialTheme.typography.bodyLarge
                val modifier = Modifier.padding(top = 12.dp, bottom = 5.dp)
                    .fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                if (forceShimmer) MeasuredMetadataPlaceholder(text, style, modifier, maxLines = 1)
                else Text(text, style = style, color = colorPalette().text, modifier = modifier)
            }
        }
        if (songs == null) {
            item(key = "loading") { SongItemPlaceholders(count = FullSongListPlaceholderCount) }
        } else if (query.isBlank() && visibleSongs.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.artist_songs_unavailable_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorPalette().textSecondary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = visibleSongs,
                key = { _, song -> song.id }
            ) { index, song ->
                if (forceShimmer) {
                    SongItemPlaceholder(title = song.cleanTitle(), artist = song.cleanArtistsText())
                    return@itemsIndexed
                }
                Box(
                    Modifier.animateItem().fillMaxWidth()
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
                            inPlaylist = playlistStatesMap[song.id],
                            navController = navController,
                            itemSelector = itemSelector,

                            onClick = {
                                if (!itemSelector.isActive) {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(
                                        visibleSongs.map(Song::asMediaItem),
                                        index,
                                    )
                                }
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
    val forceShimmer = forceLoadingShimmerPreview()

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
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val playlistStatesMap by remember(artistSongIds) {
        PlaylistStateManager.getPlaylistStates(artistSongIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    var showConfirmDeleteDownloadDialog by remember {
        mutableStateOf(false)
    }

    var showConfirmDownloadAllDialog by remember {
        mutableStateOf(false)
    }

    // Hoisted download states
    val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle(initialValue = MyDownloadHelper.downloads.value, context = NzikDispatchers.DATA)
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

    LayoutWithAdaptiveThumbnail(
        thumbnailContent = thumbnailContent,
        thumbnailContentPadding = rememberPageChromeContentPadding(),
    ) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = lazyListState,
                contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
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
                                            Toaster.i(context.resources.getString(R.string.downloads_action_download_all))
                                        }
                            )

                            DialogPresence(visible = showConfirmDownloadAllDialog) {
                                ConfirmationDialog(
                                    title = stringResource(R.string.downloads_action_download_all),
                                    confirmText = stringResource(R.string.downloads_title),
                                    text = stringResource(R.string.downloads_download_all_confirmation),
                                    onDismiss = { showConfirmDownloadAllDialog = false },
                                    onConfirm = {
                                        showConfirmDownloadAllDialog = false
                                        downloadState = Download.STATE_DOWNLOADING
                                        val snapshot = songs?.toList()
                                        if (snapshot?.isNotEmpty() == true)
                                            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                                processArtistDownloadBatch(snapshot, binder?.cache, context, downloadState = false)
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
                                            Toaster.i(context.resources.getString(R.string.downloads_action_remove_all))
                                        }
                            )

                            DialogPresence(visible = showConfirmDeleteDownloadDialog) {
                                ConfirmationDialog(
                                    title = stringResource(R.string.downloads_delete_dialog_title),
                                    confirmText = stringResource(R.string.common_action_delete),
                                    text = stringResource(R.string.downloads_delete_all_confirmation),
                                    onDismiss = { showConfirmDeleteDownloadDialog = false },
                                    onConfirm = {
                                        showConfirmDeleteDownloadDialog = false
                                        downloadState = Download.STATE_DOWNLOADING
                                        val snapshot = songs?.toList()
                                        if (snapshot?.isNotEmpty() == true)
                                            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                                processArtistDownloadBatch(snapshot, binder?.cache, context, downloadState = true)
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
                                            val snapshot = songs?.toList().orEmpty()
                                            if (snapshot.isNotEmpty()) {
                                                NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                                                    val player = binder?.player ?: return@launch
                                                    val mediaItems = withContext(NzikDispatchers.DATA) {
                                                        player.excludeMediaItems(snapshot.map(Song::asMediaItem), context)
                                                    }
                                                    player.enqueue(mediaItems)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i(context.resources.getString(R.string.player_action_enqueue_songs))
                                        }
                            )
                            HeaderIconButton(
                                // Issue #866 (gh-866): app-wide shuffle confirmation flash.
                                icon = shuffleButtonIcon(),
                                enabled = !songs.isNullOrEmpty(),
                                color = if (!songs.isNullOrEmpty()) colorPalette().text else colorPalette().textDisabled,
                                iconSize = 24.dp,
                                modifier = Modifier.clip(uiRoundnessShape()),
                                        onClick = {
                                            songs?.let { songs ->
                                                if (songs.isNotEmpty()) {
                                                    binder?.let {
                                                        it.triggerShuffleOkFlash()
                                                        Shuffler.play(it, songs)
                                                    }
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i(context.resources.getString(R.string.player_action_shuffle_hint))
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
                        if (forceShimmer) {
                            SongItemPlaceholder(
                                modifier = Modifier.padding(vertical = 4.dp),
                                title = song.cleanTitle(), artist = song.cleanArtistsText(),
                            )
                            return@itemsIndexed
                        }
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
                                    inPlaylist = playlistStatesMap[song.id],
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
                    SongItemPlaceholders(count = FullSongListPlaceholderCount)
                }
            }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if( showFloatingIcon )
                MultiFloatingActionsContainer(
                    // Issue #866 (gh-866): app-wide shuffle confirmation flash.
                    iconId = shuffleButtonIcon(),
                    onClick = {
                        songs?.let { songs ->
                            if (songs.isNotEmpty()) {
                                binder?.let {
                                    it.triggerShuffleOkFlash()
                                    Shuffler.play(it, songs)
                                }
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
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    CollectionArtworkHeader(
        title = localArtist.name ?: "...",
        artwork = thumbnailPainter,
        disableScrollingText = disableScrollingText,
        subtitle = {
            val statsText = artistStatisticsText(artistPage?.subscribers, artistPage?.listeners)
            if (statsText.isNotBlank()) {
                if (forceLoadingShimmerPreview()) {
                    MeasuredMetadataPlaceholder(statsText, typography().s, maxLines = 1)
                } else BasicText(statsText, style = typography().s.copy(colorPalette().textSecondary))
            }
        },
    ) {
        CollectionArtworkShareButton(ExternalUris.youtubeMusicChannel(localArtist.id.removePrefix(MODIFIED_PREFIX)))
    }
}

@UnstableApi
private suspend fun processArtistDownloadBatch(
    songs: List<Song>,
    cache: Cache?,
    context: Context,
    downloadState: Boolean,
) {
    Database.awaitWriteBatch(retries = 1) {
        // Retry the SQL before starting side effects that cannot roll back.
        awaitWriteBatch {
            songs.forEach { song ->
                formatTable.deleteBySongId(song.asMediaItem.mediaId)
            }
        }
        songs.forEach { song ->
            val mediaItem = song.asMediaItem
            cache?.removeResource(mediaItem.mediaId)
            MyDownloadHelper.songUrlCache.invalidate(mediaItem.mediaId)
            manageDownload(context, mediaItem, downloadState)
        }
    }
}
