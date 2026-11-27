package app.n_zik.android.ui.screens.playlist

import app.n_zik.android.components.glass.GlassArtworkBackdrop
import app.n_zik.android.components.ui.screens.CollectionPageStatus
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.draw.clip

import app.n_zik.android.components.dialog.common.AboveMenuDialogPresence
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.RenderAboveMenu
import app.n_zik.android.listentogether.rememberListenTogetherGuestLock
import app.n_zik.android.uiRoundnessShape


import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import app.n_zik.android.components.glass.GlassDescriptionExpansion
import app.n_zik.android.components.glass.GlassDescriptionTranslate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.components.menu.playlist.canPushPlaylistBookmark
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.R
import app.n_zik.android.components.menu.playlist.withSourcePlaylistOwner
import app.n_zik.android.components.playlist.launchLoadedPlaylistImport
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.coil.resize
import app.n_zik.android.compose.persist.persist
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.requests.PlaylistPage
import app.n_zik.android.components.ui.UiButton
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.findMusicBookmarkByBrowseId
import app.n_zik.android.core.database.findMusicBookmarkRows
import app.n_zik.android.core.database.isPlaylistBookmarked
import app.n_zik.android.LocalPlayerServiceBinder

import app.n_zik.android.colorPalette
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Playlist
import app.n_zik.android.core.rewind.RewindPlaylists.portablePlaylistName
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import app.n_zik.android.ui.components.themed.AutoResizeText
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.ui.components.themed.FontSizeRange
import app.n_zik.android.ui.components.themed.HeaderIconButton
import app.n_zik.android.components.ui.screens.CollectionArtworkShareButton
import app.n_zik.android.components.ui.screens.CollectionArtworkTopAction
import app.n_zik.android.components.ui.CollectionButtonToolbar
import app.n_zik.android.components.ui.collectionToolbarAction
import app.n_zik.android.components.settings.ui.ToolbarAction as ToolbarControl
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.components.settings.ui.visibleActions
import app.n_zik.android.components.ui.CollectionFavoritesKind
import app.n_zik.android.components.ui.rememberCollectionFavoritesButton
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.queueMediaItemsOffMain
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.stopActiveRadio
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.components.dialog.playlist.ChangePlaylistBrowseIdDialog
import app.n_zik.android.components.dialog.playlist.ImportPlaylistDialog
import app.n_zik.android.components.menu.playlist.CollectionImportStatus
import app.n_zik.android.components.menu.playlist.rememberCollectionAddAction
import app.n_zik.android.components.dialog.playlist.RenamePlaylistAuthorDialog
import app.n_zik.android.components.dialog.playlist.RenamePlaylistDialog
import app.n_zik.android.components.ui.screens.LayoutWithAdaptiveThumbnail
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.ui.components.themed.adaptiveThumbnailContent
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.ui.components.ArtworkShimmerPlaceholder
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview

import app.n_zik.android.ui.items.FullSongListPlaceholderCount
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.items.SongItemPlaceholders
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.align
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.color
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.fadingEdge
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.enqueue
import app.n_zik.android.enums.Languages
import app.n_zik.android.utils.otherLanguageAppPlaylistKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.manageDownload
import app.n_zik.android.utils.startPlaylistRowDownload
import app.n_zik.android.utils.medium
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.secondary
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.showFloatingIconKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.ExternalUris
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.radioButtonIcon
import app.n_zik.android.components.shuffleButtonIcon
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.collectionFavoriteSongId
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.playback.utils.Shuffler
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.playlist.rememberPlaylistDownloadStates
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.PlaylistSwipeAction
import app.n_zik.android.utils.playlistSwipeLeftActionKey
import app.n_zik.android.utils.playlistSwipeRightActionKey
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import timber.log.Timber
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.rememberSongRowMetadata

import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.matchesCollectionSearch
import app.n_zik.android.components.song.resolveSongDisplayMetadata
import app.n_zik.android.utils.contentTranslationChoices
import app.n_zik.android.utils.contentTranslationLanguage
import app.n_zik.android.utils.contentTranslationTarget
import java.util.Locale


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun PlaylistSongList(
    navController: NavController,
    browseId: String,
) {
    // Context
    val preview = app.n_zik.android.components.settings.appearance.preview.LocalThemePreview.current
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    var bookmarkPending by remember(browseId) { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val forceSongPlaceholders = forceLoadingShimmerPreview()

    // Listen Together guest lock (spec-listen-together-guest-lock-hardening): the
    // playlist radio control is a host-owned stateful control.

    // Grayed out and non-interactive for a guest.
    val ltGuestLocked = rememberListenTogetherGuestLock()

    // Settings
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    var playlistPage by persist<PlaylistPage?>("playlist/$browseId/playlistPage")
    val playlistSongs = remember(playlistPage, parentalControlEnabled) {
        playlistPage?.songs.orEmpty()
            .fastFilter { !parentalControlEnabled || !it.explicit }
            .fastDistinctBy(Innertube.SongItem::key)
    }
    var initialPlaylistDetails by remember(browseId) { mutableStateOf<PlaylistPage?>(null) }
    var loadedSongsCount by remember(browseId) { mutableIntStateOf(0) }
    var initialLoadFailed by remember(browseId) { mutableStateOf(false) }
    var continuationLoading by remember(browseId) { mutableStateOf(false) }
    var retryLoad by remember(browseId) { mutableIntStateOf(0) }

    LaunchedEffect(browseId, retryLoad) {
        if (preview != null) {
            val demo = app.n_zik.android.components.settings.appearance.preview.themePreviewOnlinePlaylist()
            playlistPage = demo
            loadedSongsCount = demo.songs.size
            return@LaunchedEffect
        }
        val cachedPage = playlistPage
        if (cachedPage != null && cachedPage.songsContinuation == null) return@LaunchedEffect

        initialLoadFailed = false
        val firstPage = cachedPage ?: loadPlaylistFirstPage(browseId).getOrElse { error ->
            if (error is CancellationException) throw error
            Timber.tag("PlaylistSongList").w(error, "Unable to load playlist tracks")
            initialLoadFailed = true
            return@LaunchedEffect
        }
        initialPlaylistDetails = firstPage
        val loadedKeys = mutableSetOf<String>()
        val showProgress: (List<Innertube.SongItem>) -> Unit = { songs ->
            songs.forEach { song ->
                if (!parentalControlEnabled || !song.explicit) loadedKeys.add(song.key)
            }
            loadedSongsCount = loadedKeys.size
        }
        showProgress(firstPage.songs)
        continuationLoading = firstPage.songsContinuation != null
        try {
            playlistPage = loadPlaylistContinuations(firstPage, onSongsLoaded = showProgress)
        } finally {
            continuationLoading = false
        }
    }

    val search = key(browseId) { Search.detail() }
    val filter = search.filterQuery
    var pendingDownloadSongs by remember(browseId) { mutableStateOf<List<Innertube.SongItem>?>(null) }
    var pendingDeleteDownloads by remember(browseId) { mutableStateOf<List<Innertube.SongItem>?>(null) }

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)

    var translateEnabled by remember {
        mutableStateOf(false)
    }
    var showTranslateLanguageDialog by remember { mutableStateOf(false) }

    val translator = Translator(NetworkClientFactory.getTranslatorClient())
    var otherLanguageApp by rememberPreference(otherLanguageAppPlaylistKey, Languages.System)
    val appLang = Locale.getDefault()
    val activeTranslateLang = remember(otherLanguageApp, appLang) {
        if (otherLanguageApp != Languages.System) otherLanguageApp
        else contentTranslationLanguage(appLang)
    }
    val languageDestination = activeTranslateLang.contentTranslationTarget

    val bookmarkBrowseId = if (browseId.startsWith("VL")) browseId else "VL$browseId"
    val localPlaylist by remember(browseId) {
        Database.playlistTable.findMusicBookmarkByBrowseId(browseId)
    }.collectAsStateWithLifecycle(null, context = NzikDispatchers.DATA)

    // Derived from playlistSongs (parental-control filtered + deduplicated), not the raw page songs
    val playlistSongIds = remember(playlistSongs) { playlistSongs.map(Innertube.SongItem::key) }
    val storedSongs by rememberSongRowMetadata(playlistSongIds)
    val filteredPageSongs = remember(filter, playlistSongs, storedSongs) {
        playlistSongs.applyFilter(filter, storedSongs)
    }



    var totalPlayTimes = 0L
    filteredPageSongs?.forEach {
        totalPlayTimes += it.durationText?.let { it1 ->
            durationTextToMillis(it1) }?.toLong() ?: 0
    }

    val dislikedSongs by remember {
        Database.songTable
                .allDisliked()
                .map { list ->
                    list.map( Song::id )
                }
                .distinctUntilChanged()
    }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)

    val isInitialLoading = playlistPage == null && playlistSongs.isEmpty() && !initialLoadFailed
    val showSongPlaceholders = forceSongPlaceholders || isInitialLoading
    SearchEmptyFeedback(
        query = filter.orEmpty(),
        empty = filteredPageSongs.isNullOrEmpty(),
        loading = playlistPage == null || continuationLoading || forceSongPlaceholders,
    )
    val headerPlaylist = (playlistPage ?: initialPlaylistDetails)?.playlist
    val sourceOwner = headerPlaylist?.channel
    val storedPlaylist = localPlaylist
    LaunchedEffect(
        storedPlaylist?.id,
        storedPlaylist?.browseId,
        storedPlaylist?.authorBrowseId,
        storedPlaylist?.authorName,
        sourceOwner,
    ) {
        val ownerName = sourceOwner?.name?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        val ownerId = sourceOwner.endpoint?.browseId?.takeIf(String::isNotBlank) ?: storedPlaylist?.authorBrowseId
        val storedBrowseId = storedPlaylist?.browseId ?: return@LaunchedEffect
        if (storedPlaylist.authorBrowseId == ownerId && storedPlaylist.authorName == ownerName) return@LaunchedEffect
        val table = Database.playlistTable
        withContext(NzikDispatchers.DATA) {
            table.updateSourceAuthor(storedPlaylist.id, storedBrowseId, ownerId, ownerName)
        }
    }
    val thumbnailContent = adaptiveThumbnailContent(
        isLoading = isInitialLoading,
        url = playlistPage?.playlist?.thumbnail?.url,
        isYoutubePlaylist = true
    )

    val likeStatesMap by remember(playlistSongIds) {
        LikeStateManager.getLikeStates(playlistSongIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val itemSelector = key(browseId) { ItemSelector<Song>() }
    val collectionMediaItems = remember(playlistSongs) { playlistSongs.map(Innertube.SongItem::asMediaItem) }
    fun selectedSongs() = itemSelector.actionTargets(filteredPageSongs.orEmpty(), Song::id,
        { collectionFavoriteSongId(it.key) })
    fun playableSelection() = selectedSongs().filter { it.key !in dislikedSongs }
    fun shuffleSelection() {
        val songs = playableSelection()
        if (songs.isEmpty()) {
            if (!itemSelector.isActive) Toaster.e(R.string.playlist_error_action_on_all_disliked_songs)
            return
        }
        binder?.let {
            it.triggerShuffleOkFlash()
            Shuffler.play(it, songs.map(Innertube.SongItem::asMediaItem))
        }
    }
    val actionSongs = selectedSongs()
    val hasNonDislikedSongs = actionSongs.any { it.key !in dislikedSongs }
    val canTargetSongs = !itemSelector.isActive || actionSongs.isNotEmpty()
    val radioActive = binder?.isRadioActive == true
    val canUseRadio = !ltGuestLocked && (hasNonDislikedSongs || radioActive)
    val favoriteMediaItems = if (itemSelector.isActive) {
        remember(actionSongs) { actionSongs.map(Innertube.SongItem::asMediaItem) }
    } else collectionMediaItems
    val playNext = PlayNext(enabled = hasNonDislikedSongs && !ltGuestLocked && binder != null, dismissOnClick = false) {
        val player = binder?.player ?: return@PlayNext
        val items = playableSelection().map(Innertube.SongItem::asMediaItem).ifEmpty { return@PlayNext }
        val completeSelection = itemSelector.captureSelectionCompletion()
        val menuKey = menuState.transitionKey
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            if (player.addNextOffMain(items, context)) {
                completeSelection()
                if (menuState.isCurrentMenu(menuKey)) menuState.hide()
            }
        }
    }
    val favorites = rememberCollectionFavoritesButton(
        mediaItems = favoriteMediaItems,
        kind = CollectionFavoritesKind.Playlist,
        enabled = playlistPage != null && playlistPage?.songsContinuation == null && !forceSongPlaceholders && preview == null,
        collectionKey = browseId,
        likeStates = likeStatesMap,
    )
    val renamePlaylist = RenamePlaylistDialog { localPlaylist }
    val renameAuthor = RenamePlaylistAuthorDialog { localPlaylist }
    val changePlaylistId = ChangePlaylistBrowseIdDialog(menuState) { localPlaylist }
    renamePlaylist.RenderAboveMenu(menuState)
    renameAuthor.RenderAboveMenu(menuState)
    changePlaylistId.RenderAboveMenu(menuState)

    // Once pagination settles, the picker can add the tracks already available; full import still requires every page.
    val canAddLoadedSongs = playlistPage != null && !continuationLoading && !forceSongPlaceholders
    val addToPlaylist = PlaylistsMenu.deferred(
        navController = navController,
        captureMediaItems = {
            val items = if (itemSelector.isActive || canAddLoadedSongs) {
                selectedSongs().map(Innertube.SongItem::asMediaItem)
            } else emptyList()
            if (items.isEmpty()) null else suspend { items }
        },
        onFailure = { error, _ -> Timber.tag("PlaylistSongList").e(error, "Could not add items to playlist") },
        itemSelector = itemSelector,
    )
    val offlineMode by OfflineMode.rememberEnabled()
    val completePage = playlistPage?.takeIf { it.songsContinuation == null }
    val canImportCollection = browseId.isNotBlank() && preview == null && !forceSongPlaceholders
    val importStatus = when {
        !canImportCollection -> CollectionImportStatus.Unavailable
        completePage != null && playlistSongs.isEmpty() -> CollectionImportStatus.Empty
        completePage != null -> CollectionImportStatus.Ready
        !offlineMode && (isInitialLoading || continuationLoading) -> CollectionImportStatus.Loading
        else -> CollectionImportStatus.Unavailable
    }
    val importPlaylistDialog = key(browseId) {
        ImportPlaylistDialog(
            initialValue = headerPlaylist?.title.orEmpty(),
            unavailableMessage = importStatus.unavailableMessage,
        ) { text ->
            val playlist = Playlist(name = text, browseId = browseId).withSourcePlaylistOwner(sourceOwner)
            launchLoadedPlaylistImport(context, playlist, playlistSongs.map { it.asMediaItem.asSong })
        }
    }
    importPlaylistDialog.RenderAboveMenu(menuState)
    val addAction = rememberCollectionAddAction(
        sourceId = browseId,
        menuState = menuState,
        canImportCollection = importStatus.canOpenImport && !itemSelector.isActive,
        hasTracks = actionSongs.isNotEmpty() && (itemSelector.isActive || canAddLoadedSongs),
        importDialogOpen = importPlaylistDialog.isActive,
        openImport = importPlaylistDialog::open,
        openPicker = addToPlaylist::openPicker,
    )
    val toolbarSurface = ToolbarSurface.ONLINE_PLAYLIST
    val toolbarOrder = toolbarSurface.visibleActions()
    val toolbarLoading = isInitialLoading || forceSongPlaceholders
    val overflowActions = mapOf(
        ToolbarControl.PLAY_NEXT to playNext,
        ToolbarControl.FAVORITES to favorites,
        ToolbarControl.SELECT to itemSelector,
        ToolbarControl.CHANGE_TITLE to renamePlaylist.takeIf { localPlaylist != null },
        ToolbarControl.CHANGE_ARTISTS to renameAuthor.takeIf { localPlaylist != null },
        ToolbarControl.CHANGE_PLAYLIST_BROWSE_ID to changePlaylistId.takeIf { localPlaylist != null },
    ).mapValues { (action, button) ->
        if (toolbarLoading && button != null) collectionToolbarAction(action, enabled = false) else button
    }

    val directToolbarActions = mapOf(
        ToolbarControl.SEARCH to if (toolbarLoading) collectionToolbarAction(ToolbarControl.SEARCH, enabled = false)
            else search,
        ToolbarControl.DOWNLOAD to collectionToolbarAction(
            ToolbarControl.DOWNLOAD,
            enabled = !toolbarLoading && !offlineMode && hasNonDislikedSongs,
            dismissMenu = false,
        ) { pendingDownloadSongs = playableSelection().takeIf { it.isNotEmpty() } },
        ToolbarControl.DELETE_DOWNLOADS to collectionToolbarAction(
            ToolbarControl.DELETE_DOWNLOADS,
            enabled = !toolbarLoading && canTargetSongs,
            dismissMenu = false,
        ) {
            val downloadRequests = MyDownloadHelper.downloads.value
            val targets = selectedSongs()
                .filter { it.key in downloadRequests }
                .distinctBy { it.key }
            if (targets.isEmpty()) Toaster.i(R.string.downloads_nothing_to_delete)
            else pendingDeleteDownloads = targets
        },
        ToolbarControl.ENQUEUE to collectionToolbarAction(
            ToolbarControl.ENQUEUE,
            enabled = !toolbarLoading && hasNonDislikedSongs && !ltGuestLocked && binder != null,
            label = R.string.player_action_enqueue_songs,
            dismissMenu = false,
        ) {
            val items = playableSelection().map(Innertube.SongItem::asMediaItem)
            val menuKey = menuState.transitionKey
            NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                if (queueMediaItemsOffMain(items, context, playNext = false) { binder?.player } &&
                    menuState.isCurrentMenu(menuKey)
                ) menuState.hide()
            }
        },
        ToolbarControl.SHUFFLE to collectionToolbarAction(
            ToolbarControl.SHUFFLE,
            enabled = !toolbarLoading && hasNonDislikedSongs && !ltGuestLocked,
            icon = shuffleButtonIcon(),
            onClick = ::shuffleSelection,
        ),
        ToolbarControl.RADIO to collectionToolbarAction(
            ToolbarControl.RADIO,
            enabled = !toolbarLoading && canUseRadio && (!offlineMode || radioActive),
            icon = radioButtonIcon(),
            label = binder?.radioActionTextRes ?: R.string.radio_action_start,
            selected = radioActive,
        ) {
            if (binder?.stopActiveRadio() == true) return@collectionToolbarAction
            val eligible = playableSelection().map(Innertube.SongItem::asMediaItem)
            val first = eligible.firstOrNull() ?: return@collectionToolbarAction
            val currentId = binder?.player?.currentMediaItem?.mediaId
            val seed = eligible.firstOrNull { it.mediaId == currentId } ?: first
            binder?.startRadio(seed, isExplicit = true)
        },
        ToolbarControl.ADD_TO_PLAYLIST to addAction,
    )
    val toolbarButtons = toolbarOrder.mapNotNull { directToolbarActions[it] ?: overflowActions[it] }
    val menuButtons = toolbarOrder.filter(toolbarSurface.overflowActions::contains).mapNotNull(overflowActions::get).toSet()

    AboveMenuDialogPresence(menuState, visible = pendingDownloadSongs != null) {
        ConfirmationDialog(
            title = stringResource(R.string.downloads_action_download_all),
            confirmText = stringResource(R.string.downloads_title),
            text = stringResource(R.string.downloads_download_all_confirmation),
            onDismiss = { pendingDownloadSongs = null },
            dismissOnConfirm = false,
            onConfirm = {
                if (!isNetworkConnected(context)) {
                    Toaster.noInternet()
                    return@ConfirmationDialog
                }
                val songs = pendingDownloadSongs.orEmpty()
                pendingDownloadSongs = null
                if (songs.isNotEmpty())
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        processPlaylistBatch(
                            songs = songs,
                            binder = binder,
                            context = context,
                            downloadState = false
                        )
                    }
            }
        )
    }

    AboveMenuDialogPresence(menuState, target = pendingDeleteDownloads) { songs ->
        ConfirmationDialog(
            title = stringResource(R.string.downloads_delete_dialog_title),
            confirmText = stringResource(R.string.common_action_delete),
            text = stringResource(
                R.string.downloads_delete_all_count_confirmation,
                songs.size,
            ),
            onDismiss = { pendingDeleteDownloads = null },
            onConfirm = {
                pendingDeleteDownloads = null
                if (songs.isNotEmpty())
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        processPlaylistBatch(
                            songs = songs,
                            binder = binder,
                            context = context,
                            downloadState = true
                        )
                    }
            }
        )
    }

    val playlistStatesMap by remember(playlistSongIds) {
        PlaylistStateManager.getPlaylistStates(playlistSongIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    LayoutWithAdaptiveThumbnail(
        thumbnailContent = thumbnailContent,
        thumbnailContentPadding = rememberPageChromeContentPadding(),
    ) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (playlistPage == null && playlistSongs.isEmpty() && initialLoadFailed) {
                CollectionPageStatus {
                    val isOnline by NetworkQualityHelper.isNetworkAvailableComposable(context)
                    BasicText(
                        text = stringResource(if (offlineMode || !isOnline) R.string.common_load_failed_no_connection else R.string.common_load_failed),
                        style = typography().s.copy(color = colorPalette().textSecondary),
                    )
                }
            } else {
                val songIds = remember(playlistSongs) { playlistSongs.map { it.key.orEmpty() } }
                val (downloadsMapState, downloadStatesMap) = rememberPlaylistDownloadStates(songIds)

                // Hoisted swipe action preferences
                val playlistSwipeLeftAction by rememberPreference(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
                val playlistSwipeRightAction by rememberPreference(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)

                CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = rememberPageChromeContentPadding(PaddingValues(bottom = Dimensions.bottomSpacer)),
                    modifier = Modifier.background( colorPalette().background0 )
                                       .fillMaxSize()
                ) {
                    item(
                        key = "header"
                    ) {
                    val modifierArt = Modifier.fillMaxWidth()

                    GlassArtworkBackdrop(hasArtwork = !isLandscape && playlistPage != null && !forceSongPlaceholders) { artworkBackdrop ->
                    Box(
                        modifier = modifierArt
                    ) {
                        if (headerPlaylist != null || isInitialLoading) {
                            if (!isLandscape && showSongPlaceholders) {
                                ArtworkShimmerPlaceholder()
                            } else if (!isLandscape)
                                Box {
                                    ImageCacheFactory.AsyncImage(
                                        shape = null,
                                        thumbnailUrl = playlistPage?.playlist?.thumbnail?.url?.resize(
                                            1000,
                                            1000
                                        ),
                                        contentDescription = stringResource(R.string.accessibility_loading),
                                        modifier = Modifier.then(artworkBackdrop)
                                            .fillMaxWidth()
                                            .align(Alignment.Center)
                                            .fadingEdge(
                                                top = WindowInsets.systemBars
                                                    .asPaddingValues()
                                                    .calculateTopPadding() + Dimensions.fadeSpacingTop,
                                                bottom = Dimensions.fadeSpacingBottom
                                            )
                                    )
                                }
                                Image(
                                    painter = when {
                                        browseId?.startsWith("SPOTIFY_IMPORT") == true -> painterResource(R.drawable.spotify)
                                        browseId?.startsWith("RIPLAY_IMPORT") == true -> painterResource(R.drawable.riplay)
                                        browseId.isNullOrEmpty() -> painterResource(R.drawable.musical_notes)
                                        else -> painterResource(R.drawable.ytmusic)
                                    },
                                    contentDescription = null,
                                    colorFilter = when {
                                        browseId?.startsWith("SPOTIFY_IMPORT") == true || browseId?.startsWith("RIPLAY_IMPORT") == true -> null
                                        else -> ColorFilter.tint(Color.Red.copy(0.75f).compositeOver(Color.White))
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .offset(5.dp, 5.dp)
                                )

                            val headerTitle = localPlaylist?.cleanName() ?: headerPlaylist?.title
                            if (showSongPlaceholders && headerTitle.isNullOrBlank()) {
                                ItemTextPlaceholder(
                                    text = null,
                                    style = typography().l.semiBold.copy(fontSize = 38.sp),
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                        .padding(horizontal = 30.dp)
                                        .padding(bottom = 20.dp),
                                )
                            } else AutoResizeText(
                                text = headerTitle.orEmpty(),
                                style = typography().l.semiBold,
                                fontSizeRange = FontSizeRange(32.sp, 38.sp),
                                fontWeight = typography().l.semiBold.fontWeight,
                                fontFamily = typography().l.semiBold.fontFamily,
                                color = typography().l.semiBold.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                shimmer = showSongPlaceholders,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 30.dp)
                                    .padding(bottom = 20.dp)
                            )

                            val playlistSummary = if (isInitialLoading) {
                                if (loadedSongsCount > 0) {
                                    pluralStringResource(R.plurals.album_loading_song_count, loadedSongsCount, loadedSongsCount)
                                } else stringResource(R.string.common_loading_message)
                            } else {
                                val songCount = filteredPageSongs?.size ?: 0
                                pluralStringResource(
                                    R.plurals.library_song_count_with_duration,
                                    songCount,
                                    songCount,
                                    formatAsTime(totalPlayTimes),
                                )
                            }
                            if (!isInitialLoading && showSongPlaceholders) {
                                ItemTextPlaceholder(
                                    text = playlistSummary,
                                    style = typography().xs.medium,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                    width = 0.32f,
                                )
                            } else BasicText(
                                text = playlistSummary,
                                style = if (isInitialLoading) typography().xs.medium.copy(color = colorPalette().textSecondary)
                                    else typography().xs.medium,
                                maxLines = 1,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )


                            CollectionArtworkTopAction {
                                CollectionArtworkShareButton(
                                    ExternalUris.youtubeMusicPlaylist(browseId.removePrefix(MODIFIED_PREFIX).removePrefix("VL")),
                                )
                            }

                        }
                    }
                    }

                }

                item(
                    key = "actions",
                    contentType = 0
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                    ) {

                        if (playlistPage != null) {
                            val bookmarkAction: @Composable () -> Unit = {
                                val isBookmarked = isPlaylistBookmarked(localPlaylist)
                                HeaderIconButton(
                                    icon = if (isBookmarked) R.drawable.bookmark else R.drawable.bookmark_outline,
                                    color = if (isBookmarked) colorPalette().favoritesIcon else colorPalette().text,
                                    contentDescription = stringResource(
                                        if (isBookmarked) R.string.library_action_remove_bookmark else R.string.library_action_bookmark,
                                    ),
                                    enabled = !bookmarkPending,
                                    modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                    onClick = {
                                        if (bookmarkPending) return@HeaderIconButton
                                        val app = context.applicationContext
                                        val owner = ProfileWorkOwner.capture(app) ?: return@HeaderIconButton
                                        val loadedPage = playlistPage ?: return@HeaderIconButton
                                        bookmarkPending = true
                                        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                            try {
                                                if (!ProfileWorkOwner.isBound(app, owner)) return@launch
                                                var nowBookmarked: Boolean? = null
                                                var addedPlaylist: Playlist? = null
                                                Database.awaitWriteBatch {
                                                    transaction {
                                                        if (!ProfileWorkOwner.isBound(app, owner)) return@transaction
                                                        val existing = playlistTable.findMusicBookmarkByBrowseId(browseId).first()
                                                        if (isPlaylistBookmarked(existing)) {
                                                            val bookmarked = playlistTable.findMusicBookmarkRows(browseId).first()
                                                                .filter { isPlaylistBookmarked(it) }
                                                            if (!ProfileWorkOwner.isBound(app, owner)) return@transaction
                                                            bookmarked.forEach { row ->
                                                                if (row.isEditable) {
                                                                    playlistTable.update(row.copy(isYoutubePlaylist = false))
                                                                } else {
                                                                    playlistTable.delete(row)
                                                                }
                                                            }
                                                            nowBookmarked = false
                                                            return@transaction
                                                        }

                                                        val playlist = (existing?.copy(isYoutubePlaylist = true) ?: Playlist(
                                                            name = app.portablePlaylistName(loadedPage.playlist.title.orEmpty()),
                                                            browseId = bookmarkBrowseId,
                                                            isYoutubePlaylist = true,
                                                            isEditable = false,
                                                        )).withSourcePlaylistOwner(headerPlaylist?.channel)
                                                        val id = if (existing == null) playlistTable.insert(playlist) else {
                                                            if (playlistTable.update(playlist) == 0) return@transaction
                                                            existing.id
                                                        }
                                                        if (id <= 0) return@transaction

                                                        val savedPlaylist = playlist.copy(id = id)
                                                        if (!savedPlaylist.isEditable) {
                                                            mapIgnore(savedPlaylist, *playlistSongs.map(Innertube.SongItem::asMediaItem).toTypedArray())
                                                        }
                                                        addedPlaylist = savedPlaylist
                                                        nowBookmarked = true
                                                    }
                                                }
                                                val bookmarked = nowBookmarked ?: return@launch
                                                if (!ProfileWorkOwner.isBound(app, owner)) return@launch
                                                addedPlaylist?.let { saved ->
                                                    val completeTracks = loadedPage.takeIf { it.songsContinuation == null && !saved.isEditable }
                                                        ?.songs?.map(Innertube.SongItem::asMediaItem)
                                                    MyDownloadHelper.downloadBookmarkedPlaylist(
                                                        app, saved.browseId, saved, completeTracks,
                                                    )
                                                }
                                                if (!ProfileWorkOwner.isBound(app, owner)) return@launch
                                                Toaster.done()
                                                if (canPushPlaylistBookmark(app) && ProfileWorkOwner.isBound(app, owner)) {
                                                    val id = browseId.removePrefix("VL")
                                                    val result = if (bookmarked) YtMusic.likePlaylistOrAlbum(id)
                                                        else YtMusic.removelikePlaylistOrAlbum(id)
                                                    val error = result.exceptionOrNull()
                                                    if (error is CancellationException) throw error
                                                    if (error != null) Toaster.e(R.string.common_load_failed)
                                                }
                                            } finally {
                                                withContext(NzikDispatchers.UI) { bookmarkPending = false }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        Toaster.i( R.string.playlist_action_save_to_youtube_library )
                                    }
                                )
                            }

                            CollectionButtonToolbar(
                                bookmark = bookmarkAction,
                                buttons = toolbarButtons,
                                menuButtons = menuButtons,
                                overflowEnabled = !toolbarLoading,
                            )
                        } else if (isInitialLoading) {
                            CollectionButtonToolbar(
                                bookmark = {
                                    DisabledPlaylistAction(
                                        if (localPlaylist?.isYoutubePlaylist == true) R.drawable.bookmark
                                        else R.drawable.bookmark_outline,
                                    )
                                },
                                buttons = toolbarButtons,
                                menuButtons = menuButtons,
                                overflowEnabled = false,
                            )
                        } else {
                            BasicText(
                                text = stringResource(R.string.playlist_operation_wait_message),
                                style = typography().xxs.medium,
                                maxLines = 1
                            )
                        }
                    }
                    Column { search.SearchBar(this, barPadding = PaddingValues(10.dp)) }
                }

                (playlistPage ?: initialPlaylistDetails)?.description?.let { description ->
                    if( description.isNotBlank() ) {
                        item(
                            key = "playlistInfo"
                        ) {
                            var isDescriptionExpanded by remember { mutableStateOf(false) }
                            val attributionsIndex = description.lastIndexOf("\n\nFrom Wikipedia")
                            val nonTranslatedText = if (attributionsIndex == -1) description
                            else description.substring(0, attributionsIndex)

                            val translationTarget = languageDestination.takeIf { translateEnabled == true }
                            var translatedText by remember(nonTranslatedText, translationTarget) {
                                mutableStateOf<String?>(null)
                            }
                            if (translationTarget != null && !showSongPlaceholders) {
                                LaunchedEffect(nonTranslatedText, translationTarget) {
                                    translatedText = withContext(NzikDispatchers.DATA) {
                                        try {
                                            translator.translate(
                                                nonTranslatedText,
                                                translationTarget,
                                                Language.AUTO
                                            ).translatedText.takeIf { it.isNotBlank() }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            Timber.tag("PlaylistSongList").e(e, "Failed to translate text")
                                            null
                                        }
                                    }
                                }
                            }

                            var showMoreButton by remember { mutableStateOf(false) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .animateContentSize()
                                    .clip(uiRoundnessShape())
                                    .clickable(enabled = showMoreButton || isDescriptionExpanded) { isDescriptionExpanded = !isDescriptionExpanded }
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    GlassDescriptionTranslate(
                                        color = if (translateEnabled == true) colorPalette().text else colorPalette().textDisabled,
                                        onClick = { translateEnabled = !translateEnabled },
                                        onLongClick = { showTranslateLanguageDialog = true },
                                    ) {
                                        IconButton(
                                            icon = R.drawable.translate,
                                            color = if (translateEnabled == true) colorPalette().text else colorPalette().textDisabled,
                                            enabled = true,
                                            modifier = Modifier.padding(all = 8.dp).size(18.dp).clip(uiRoundnessShape()),
                                            onClick = { translateEnabled = !translateEnabled },
                                            onLongClick = { showTranslateLanguageDialog = true }
                                        )
                                    }

                                    DialogPresence(visible = showTranslateLanguageDialog) {
                                        ValueSelectorDialog(
                                            title = stringResource(R.string.common_translation_title),
                                            selectedValue = otherLanguageApp,
                                            onValueSelected = {
                                                otherLanguageApp = it
                                                translateEnabled = it.translatorLanguage != Language.ENGLISH
                                                showTranslateLanguageDialog = false
                                            },
                                            valueText = { it.text },
                                            values = contentTranslationChoices,
                                            onDismiss = { showTranslateLanguageDialog = false }
                                        )
                                    }

                                    BasicText(
                                        text = "“",
                                        style = typography().xxl.semiBold,
                                        modifier = Modifier.offset(y = (-8).dp)
                                    )

                                    if (showSongPlaceholders) {
                                        MeasuredMetadataPlaceholder(
                                            text = translatedText ?: nonTranslatedText,
                                            style = typography().xs.secondary.align(TextAlign.Justify),
                                            maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
                                        )
                                    } else BasicText(
                                        text = translatedText ?: nonTranslatedText,
                                        style = typography().xs.secondary.align(TextAlign.Justify),
                                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
                                        onTextLayout = { textLayoutResult ->
                                            if (textLayoutResult.hasVisualOverflow) {
                                                showMoreButton = true
                                            } else if (!isDescriptionExpanded) {
                                                showMoreButton = false
                                            }
                                        }
                                    )

                                    BasicText(
                                        text = "”",
                                        style = typography().xxl.semiBold,
                                        modifier = Modifier.offset(y = 4.dp).align(Alignment.Bottom)
                                    )
                                }

                                if (showMoreButton || isDescriptionExpanded) {
                                    GlassDescriptionExpansion(
                                        label = stringResource(if (isDescriptionExpanded) R.string.common_action_show_less else R.string.common_action_show_more),
                                        onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                    )
                                }

                                if (isDescriptionExpanded && attributionsIndex != -1) {
                                    BasicText(
                                        text = stringResource(R.string.metadata_wikipedia_attribution),
                                        style = typography().xxs
                                            .color(colorPalette().textDisabled)
                                            .align(TextAlign.Start),
                                        modifier = Modifier.padding(top = 8.dp, start = 8.dp)
                                    )
                                }
                            }
                    }
                }
                }

                if (showSongPlaceholders) {
                    val knownSongs = if (isInitialLoading) {
                        initialPlaylistDetails?.songs
                            ?.fastFilter { !parentalControlEnabled || !it.explicit }
                            ?.fastDistinctBy(Innertube.SongItem::key)
                            .applyFilter(filter)
                    } else filteredPageSongs
                    items(
                        count = if (isInitialLoading && knownSongs.isNullOrEmpty()) FullSongListPlaceholderCount
                            else knownSongs?.size ?: FullSongListPlaceholderCount,
                        key = { index -> knownSongs?.getOrNull(index)?.key ?: "playlist-song-loading-$index" },
                        contentType = { "song-placeholder" },
                    ) { index ->
                        val song = knownSongs?.getOrNull(index)?.asSong
                        SongItemPlaceholder(title = song?.cleanTitle(), artist = song?.cleanArtistsText())
                    }
                } else items(
                    items = filteredPageSongs.orEmpty(),
                    key = Innertube.SongItem::key,
                    contentType = { "song" },
                ) { ytSong ->
                    val storedSong = storedSongs[ytSong.key]
                    val song = remember(ytSong, storedSong) { resolveSongDisplayMetadata(ytSong.asSong, storedSong) }

                    SwipeablePlaylistItem(
                        mediaItem = ytSong.asMediaItem,
                        modifier = Modifier.animateItem(),
                        onPlayNext = {
                            binder?.player?.addNext(ytSong.asMediaItem)
                        },
                        onDownload = {
                            startPlaylistRowDownload(context, ytSong.asMediaItem, binder?.cache)
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(ytSong.asMediaItem)
                        },
                        downloadStateParam = downloadsMapState[ytSong.key]?.state ?: Download.STATE_STOPPED,
                        downloadedStateMediaParam = downloadStatesMap[ytSong.key] ?: DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED,
                        swipeLeftActionParam = playlistSwipeLeftAction,
                        swipeRightActionParam = playlistSwipeRightAction
                    ) {
                        SongItem(
                            song = song,
                            observeStoredMetadata = false,
                            isLiked = likeStatesMap[ytSong.key],
                            inPlaylist = playlistStatesMap[ytSong.key],
                            sourceMediaItem = { ytSong.asMediaItem },
                            itemSelector = itemSelector,
                            navController = navController,

                            onClick = {
                                if (itemSelector.isActive) {
                                    itemSelector.toggle(song, Song::id)
                                } else if (ytSong.key in dislikedSongs) {
                                    Toaster.e( R.string.playlist_error_action_on_disliked_song )
                                } else {
                                    filteredPageSongs.orEmpty()
                                        .playlistSongPlayback(ytSong.key, dislikedSongs)
                                        ?.let { (tracks, index) ->
                                            search.clearAndClose()
                                            binder?.stopRadio()
                                            binder?.player?.forcePlayAtIndex(
                                                tracks.map(Innertube.SongItem::asMediaItem),
                                                index,
                                            )
                                        }
                                }
                            }
                        )
                    }
                }

                if (!forceSongPlaceholders && playlistPage?.songsContinuation != null) {
                    item("playlist-continuation") {
                        if (continuationLoading) {
                            SongItemPlaceholders(count = 1)
                        } else {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                BasicText(stringResource(R.string.common_load_failed), style = typography().s.secondary)
                                UiButton(onClick = { retryLoad++ }) { Text(stringResource(R.string.common_action_retry)) }
                            }
                        }
                    }
                }
            }
                }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if( showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                // Issue #866 (gh-866): app-wide shuffle confirmation flash.
                iconId = shuffleButtonIcon(),
                actionEnabled = !itemSelector.isActive || hasNonDislikedSongs,
                onClick = ::shuffleSelection
            )
            }
        }
    }
}

@Composable
private fun DisabledPlaylistAction(icon: Int) {
    HeaderIconButton(
        icon = icon,
        color = colorPalette().textDisabled,
        enabled = false,
        iconSize = if (icon == R.drawable.search_circle) 24.dp else 20.dp,
        modifier = Modifier.padding(horizontal = 5.dp),
        onClick = {},
    )
}

/** Filters the full playlist so successive search queries do not narrow prior results. */
internal fun List<Innertube.SongItem>?.applyFilter(
    filter: String?,
    storedSongs: Map<String, Song> = emptyMap(),
): List<Innertube.SongItem>? {
    if (this == null || filter.isNullOrBlank()) return this
    return filter { songItem ->
        resolveSongDisplayMetadata(songItem.asSong, storedSongs[songItem.key]).matchesCollectionSearch(filter) ||
            songItem.album?.name.orEmpty().contains(filter, ignoreCase = true)
    }
}

internal fun List<Innertube.SongItem>.playlistSongPlayback(
    clickedKey: String,
    dislikedSongs: Collection<String>,
): Pair<List<Innertube.SongItem>, Int>? {
    val playableSongs = filter { it.key !in dislikedSongs }
    val index = playableSongs.indexOfFirst { it.key == clickedKey }
    return if (index >= 0) playableSongs to index else null
}

/**
 * Runs the download batch on the caller's dispatcher so all songs share one scope.
 *
 * @param downloadState `false` queues downloads and `true` removes them.
 */
@UnstableApi
internal suspend fun processPlaylistBatch(
    songs: List<Innertube.SongItem>?,
    binder: PlayerServiceModern.Binder?,
    context: Context,
    downloadState: Boolean
) {
    if (songs.isNullOrEmpty()) return

    Database.awaitWriteBatch(retries = 1) {
        for (songItem in songs) {
            val mediaItem = songItem.asMediaItem
            val existingDownload = MyDownloadHelper.downloads.value[mediaItem.mediaId]
            if (downloadState && existingDownload == null) continue
            if (!downloadState && existingDownload?.state == Download.STATE_COMPLETED) continue
            if (!downloadState && !isNetworkConnected(context)) {
                Toaster.noInternet()
                break
            }
            manageDownload(context, mediaItem, downloadState) {
                binder?.cache?.removeResource(mediaItem.mediaId)
            }
        }
    }
}








