package app.n_zik.android.ui.screens.playlist

import app.n_zik.android.components.glass.GlassArtworkBackdrop
import app.n_zik.android.components.ui.screens.CollectionPageStatus
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.listentogether.guestLockedAlpha
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.core.database.editStoredPlaylistMembership
import app.n_zik.android.core.database.canEditStoredPlaylistMembership
import app.n_zik.android.R
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
import app.n_zik.android.cleanPrefix

import app.n_zik.android.colorPalette
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
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
import app.n_zik.android.components.ui.CollectionToolbar
import app.n_zik.android.components.settings.ui.ToolbarAction as ToolbarControl
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.components.settings.ui.visibleActions
import app.n_zik.android.components.ui.CollectionFavoritesKind
import app.n_zik.android.components.ui.rememberCollectionFavoritesButton
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.stopActiveRadio
import app.n_zik.android.ui.components.tab.toolbar.EllipsisMenuComponent
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.components.dialog.playlist.ImportPlaylistDialog
import app.n_zik.android.components.ui.screens.LayoutWithAdaptiveThumbnail
import app.n_zik.android.ui.components.themed.PlaylistsItemMenu
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
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.favoritesIcon
import app.n_zik.android.utils.addToYtPlaylist
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.align
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.color
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.excludeMediaItems
import app.n_zik.android.utils.fadingEdge
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.utils.isDownloadedSong
import app.n_zik.android.utils.isLandscape
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.enums.Languages
import app.n_zik.android.utils.otherLanguageAppPlaylistKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.manageDownload
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
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()
    val forceSongPlaceholders = forceLoadingShimmerPreview()

    // Listen Together guest lock (spec-listen-together-guest-lock-hardening): the
    // playlist radio control is a host-owned stateful control.

    // Grayed out and non-interactive for a guest.
    val ltGuestLocked = rememberListenTogetherGuestLock()
    val guestLockAlpha = guestLockedAlpha(ltGuestLocked)

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

    val importPlaylistDialog = ImportPlaylistDialog(
        initialValue = playlistPage?.playlist?.title ?: ""
    ) { text ->
        Database.asyncTransaction {
            val playlist = Playlist(name = text, browseId = browseId)

            filteredPageSongs
                        ?.map( Innertube.SongItem::asMediaItem )
                        ?.let {
                            mapIgnore( playlist, *it.toTypedArray() )
                        }

            Toaster.done()
        }
    }
    importPlaylistDialog.Render()

    val isInitialLoading = playlistPage == null && playlistSongs.isEmpty() && !initialLoadFailed
    val showSongPlaceholders = forceSongPlaceholders || isInitialLoading
    SearchEmptyFeedback(
        query = filter.orEmpty(),
        empty = filteredPageSongs.isNullOrEmpty(),
        loading = playlistPage == null || continuationLoading || forceSongPlaceholders,
    )
    val headerPlaylist = (playlistPage ?: initialPlaylistDetails)?.playlist
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
    val canDownload = !itemSelector.isActive || hasNonDislikedSongs
    val canTargetSongs = !itemSelector.isActive || actionSongs.isNotEmpty()
    val radioActive = binder?.isRadioActive == true
    val canUseRadio = !ltGuestLocked && (hasNonDislikedSongs || radioActive)
    val favoriteMediaItems = if (itemSelector.isActive) {
        remember(actionSongs) { actionSongs.map(Innertube.SongItem::asMediaItem) }
    } else collectionMediaItems
    val playNext = PlayNext(enabled = hasNonDislikedSongs && !ltGuestLocked && binder != null) {
        val player = binder?.player ?: return@PlayNext
        val items = playableSelection().map(Innertube.SongItem::asMediaItem).ifEmpty { return@PlayNext }
        val completeSelection = itemSelector.captureSelectionCompletion()
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            player.addNextOffMain(items, context)
            completeSelection()
        }
    }
    val favorites = rememberCollectionFavoritesButton(
        mediaItems = favoriteMediaItems,
        kind = CollectionFavoritesKind.Playlist,
        enabled = playlistPage != null && playlistPage?.songsContinuation == null && !forceSongPlaceholders && preview == null,
        collectionKey = browseId,
        likeStates = likeStatesMap,
    )
    val toolbarOrder = ToolbarSurface.ONLINE_PLAYLIST.visibleActions()
    val overflowActions = toolbarOrder.mapNotNull {
        when (it) {
            ToolbarControl.PLAY_NEXT -> playNext
            ToolbarControl.FAVORITES -> favorites
            ToolbarControl.SELECT -> itemSelector
            else -> null
        }
    }
    val moreActions = EllipsisMenuComponent.init { overflowActions }
    val inlineActionIds = toolbarOrder.filterNot {
        it == ToolbarControl.BOOKMARK || it == ToolbarControl.PLAY_NEXT ||
            it == ToolbarControl.FAVORITES || it == ToolbarControl.SELECT
    } + listOfNotNull(ToolbarControl.MENU.takeIf { overflowActions.isNotEmpty() })
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
                    val offlineMode by OfflineMode.rememberEnabled()
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

                            val headerTitle = headerPlaylist?.title
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

                        //if (!isLandscape) thumbnailContent()

                        if (playlistPage != null) {

                            //actionsContent()

                            val bookmarkAction: @Composable () -> Unit = {
                                HeaderIconButton(
                                    icon = if (localPlaylist?.isYoutubePlaylist == true) R.drawable.bookmark else R.drawable.bookmark_outline,
                                    color = if (localPlaylist?.isYoutubePlaylist == true) colorPalette().favoritesIcon else colorPalette().text,
                                    modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                    onClick = {
                                        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                            val stored = Database.playlistTable.findMusicBookmarkByBrowseId(browseId).first()
                                            if (stored?.isYoutubePlaylist == true) {
                                                if (isYouTubeSyncEnabled() && isNetworkConnected(context)) {
                                                    YtMusic.removelikePlaylistOrAlbum(browseId.removePrefix("VL"))
                                                }
                                                Database.awaitWriteBatch {
                                                    transaction {
                                                        playlistTable.findMusicBookmarkRows(browseId).first()
                                                            .filter { isPlaylistBookmarked(it) }
                                                            .forEach { existing ->
                                                                if (existing.isEditable) {
                                                                    playlistTable.update(existing.copy(isYoutubePlaylist = false))
                                                                } else {
                                                                    playlistTable.delete(existing)
                                                                }
                                                            }
                                                    }
                                                }
                                            } else {
                                                if (isYouTubeSyncEnabled() && isNetworkConnected(context)) {
                                                    YtMusic.likePlaylistOrAlbum(browseId.removePrefix("VL"))
                                                }
                                                val completeTracks = playlistPage
                                                    ?.takeIf { it.songsContinuation == null }
                                                    ?.songs
                                                    ?.map(Innertube.SongItem::asMediaItem)
                                                val saved = Database.awaitWriteBatch {
                                                    var addedPlaylist: Playlist? = null
                                                    transaction {
                                                        val existing = playlistTable.findMusicBookmarkByBrowseId(browseId).first()
                                                        if (existing?.isYoutubePlaylist == true) return@transaction

                                                        val playlist = existing?.copy(isYoutubePlaylist = true) ?: Playlist(
                                                            name = playlistPage?.playlist?.title.orEmpty(),
                                                            browseId = bookmarkBrowseId,
                                                            isYoutubePlaylist = true,
                                                            isEditable = false,
                                                        )
                                                        val id = if (existing == null) playlistTable.insert(playlist) else {
                                                            if (playlistTable.update(playlist) == 0) return@transaction
                                                            existing.id
                                                        }
                                                        if (id <= 0) return@transaction

                                                        val savedPlaylist = playlist.copy(id = id)
                                                        if (!savedPlaylist.isEditable) {
                                                            playlistSongs?.map(Innertube.SongItem::asMediaItem)
                                                                ?.let { mapIgnore(savedPlaylist, *it.toTypedArray()) }
                                                        }
                                                        addedPlaylist = savedPlaylist
                                                    }
                                                    addedPlaylist
                                                }
                                                if (saved != null) {
                                                    MyDownloadHelper.downloadBookmarkedPlaylist(
                                                        context, saved.browseId, saved, completeTracks.takeUnless { saved.isEditable },
                                                    )
                                                }
                                            }
                                            Toaster.done()
                                        }
                                    },
                                    onLongClick = {
                                        Toaster.i( R.string.playlist_action_save_to_youtube_library )
                                    }
                                )
                            }

                            val toolbarActions = mapOf<ToolbarControl, @Composable () -> Unit>(
                                ToolbarControl.SEARCH to {
                                    HeaderIconButton(
                                        onClick = search::onShortClick,
                                        icon = R.drawable.search_circle,
                                        color = colorPalette().text,
                                        iconSize = 24.dp,
                                        modifier = Modifier
                                            .padding(horizontal = 5.dp)
                                    )

                                },
                                ToolbarControl.DOWNLOAD to {
                                    HeaderIconButton(
                                        icon = R.drawable.download,
                                        enabled = canDownload,
                                        color = if (canDownload) colorPalette().text else colorPalette().textDisabled,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                                onClick = {
                                                    pendingDownloadSongs = playableSelection().toList()
                                                },
                                                onLongClick = {
                                                    Toaster.i(R.string.downloads_action_download_all)
                                                }
                                    )

                                    DialogPresence(visible = pendingDownloadSongs != null) {
                                        ConfirmationDialog(
                                            title = stringResource(R.string.downloads_action_download_all),
                                            confirmText = stringResource(R.string.downloads_title),
                                            text = stringResource(R.string.downloads_download_all_confirmation),
                                            onDismiss = { pendingDownloadSongs = null },
                                            onConfirm = {
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

                                },
                                ToolbarControl.DELETE_DOWNLOADS to {
                                    HeaderIconButton(
                                        icon = R.drawable.downloaded,
                                        enabled = canTargetSongs,
                                        color = if (canTargetSongs) colorPalette().text else colorPalette().textDisabled,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                                onClick = {
                                                    pendingDeleteDownloads = selectedSongs().takeIf { songs -> itemSelector.isActive || songs.any { it.key !in dislikedSongs } }.orEmpty().toList()
                                                },
                                                onLongClick = {
                                                    Toaster.n(R.string.downloads_action_remove_all)
                                                }
                                    )

                                    DialogPresence(visible = pendingDeleteDownloads != null) {
                                        ConfirmationDialog(
                                            title = stringResource(R.string.downloads_delete_dialog_title),
                                            confirmText = stringResource(R.string.common_action_delete),
                                            text = stringResource(R.string.downloads_delete_all_confirmation),
                                            onDismiss = { pendingDeleteDownloads = null },
                                            onConfirm = {
                                                val songs = pendingDeleteDownloads.orEmpty()
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



                                },
                                ToolbarControl.ENQUEUE to {
                                    HeaderIconButton(
                                        icon = R.drawable.enqueue,
                                        enabled = hasNonDislikedSongs,
                                        color =  if (hasNonDislikedSongs) colorPalette().text else colorPalette().textDisabled,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                                onClick = {
                                                    val songs = playableSelection()
                                                    if (songs.isNotEmpty()) {
                                                        val mediaItems = songs.map(Innertube.SongItem::asMediaItem)
                                                        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                                                            val filtered = withContext(NzikDispatchers.DATA) {
                                                                val player = binder?.player ?: return@withContext emptyList()
                                                                player.excludeMediaItems(mediaItems, context)
                                                            }
                                                            val player = binder?.player ?: return@launch
                                                            player.enqueue(filtered)
                                                        }
                                                    } else if (!itemSelector.isActive) {
                                                        Toaster.e(R.string.playlist_error_action_on_all_disliked_songs)
                                                    }
                                                },
                                                onLongClick = {
                                                    Toaster.i(R.string.player_action_enqueue_songs)
                                                }
                                    )

                                },
                                ToolbarControl.SHUFFLE to {
                                    HeaderIconButton(
                                        // Issue #866 (gh-866): app-wide shuffle confirmation flash.
                                        icon = shuffleButtonIcon(),
                                        enabled = hasNonDislikedSongs,
                                        color = if (hasNonDislikedSongs) colorPalette().text else colorPalette().textDisabled,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                                onClick = ::shuffleSelection,
                                                onLongClick = {
                                                    Toaster.i( R.string.player_action_shuffle_hint )
                                                }
                                    )

                                },
                                ToolbarControl.RADIO to {
                                    HeaderIconButton(
                                        // Issue #866 (gh-866): state icon — radio_stop while the radio
                                        // is active (the state change is the press feedback).
                                        icon = radioButtonIcon(),
                                        enabled = canUseRadio,
                                        color = if (!canUseRadio) colorPalette().textDisabled
                                                else if (radioActive) colorPalette().accent
                                                else colorPalette().text,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()).alpha(guestLockAlpha),
                                                onClick = {
                                                    if (binder?.stopActiveRadio() == true) return@HeaderIconButton
                                                    val active = itemSelector.isActive
                                                    val songs = selectedSongs()
                                                    if (!active && songs.fastAny { it.key in dislikedSongs }) {
                                                        Toaster.e(R.string.playlist_error_action_on_all_disliked_songs)
                                                        return@HeaderIconButton
                                                    }
                                                    val eligible = songs.filter { it.key !in dislikedSongs }
                                                        .map(Innertube.SongItem::asMediaItem)
                                                    val first = eligible.firstOrNull() ?: return@HeaderIconButton
                                                    val current = binder?.player?.currentMediaItem
                                                        ?.takeIf { playing -> !active || eligible.any { it.mediaId == playing.mediaId } }
                                                    binder?.startRadio(current ?: first, false, null, true)
                                                },
                                                onLongClick = {
                                                    Toaster.i( binder?.radioActionTextRes ?: R.string.radio_action_start_hint )
                                                }
                                    )


                                },
                                ToolbarControl.ADD_TO_PLAYLIST to {
                                    HeaderIconButton(
                                        icon = R.drawable.add_in_playlist,
                                        enabled = canTargetSongs,
                                        color = if (canTargetSongs) colorPalette().text else colorPalette().textDisabled,
                                        modifier = Modifier.padding(horizontal = 5.dp).clip(uiRoundnessShape()),
                                                onClick = {
                                                    val usingSelection = itemSelector.isActive
                                                    val songs = selectedSongs().map(Innertube.SongItem::asMediaItem)
                                                    if (usingSelection && songs.isEmpty()) return@HeaderIconButton
                                                    menuState.display {
                                                        PlaylistsItemMenu(
                                                            navController = navController,
                                                            modifier = Modifier.fillMaxHeight(0.4f),
                                                            onDismiss = menuState::hide,
                                                            onImportOnlinePlaylist = if (usingSelection) null else {
                                                                { importPlaylistDialog.onShortClick() }
                                                            },
                                                            onAddToPlaylist = addToPlaylist@{ playlistPreview ->
                                                                val position = if (playlistPreview.songCount > 1) playlistPreview.songCount else 0

                                                                val playlistSize = songs.size

                                                                if ((playlistSize + playlistPreview.songCount) > 5000 && playlistPreview.playlist.isYoutubePlaylist && isYouTubeSyncEnabled()){
                                                                    Toaster.e( R.string.playlist_youtube_song_limit_message )
                                                                } else if (!isYouTubeSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist) {
                                                                    Database.asyncTransaction {
                                                                        editStoredPlaylistMembership(playlistPreview.playlist.id) {
                                                                            mapIgnore(playlistPreview.playlist, *songs.toTypedArray())
                                                                        }
                                                                    }
                                                                } else if (usingSelection) {
                                                                    val target = playlistPreview.playlist
                                                                    val youtubeId = target.browseId?.let(::cleanPrefix) ?: return@addToPlaylist
                                                                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                                                        if (!isYouTubeSyncEnabled() || !canEditStoredPlaylistMembership(target.id)) return@launch
                                                                        val current = Database.playlistTable.findById(target.id).first() ?: return@launch
                                                                        if (!current.isYoutubePlaylist || current.browseId?.let(::cleanPrefix) != youtubeId) return@launch
                                                                        addToYtPlaylist(target.id, position, youtubeId, songs)
                                                                    }
                                                                } else {
                                                                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                                                        if (!canEditStoredPlaylistMembership(playlistPreview.playlist.id)) return@launch
                                                                        YtMusic.addPlaylistToPlaylist(
                                                                            cleanPrefix(playlistPreview.playlist.browseId ?: ""),
                                                                            browseId.removePrefix("VL")

                                                                            )
                                                                        }
                                                                }
                                                                Toaster.done()
                                                            },
                                                            onGoToPlaylist = {
                                                                navController.navigate("${NavRoutes.localPlaylist.name}/$it")
                                                            },
                                                            disableScrollingText = disableScrollingText
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    Toaster.i( R.string.playlist_action_add_hint )
                                                }
                                    )
                                },
                                ToolbarControl.MENU to {
                                    val menuLabel = stringResource(R.string.common_menu_label)
                                    HeaderIconButton(
                                        icon = moreActions.iconId,
                                        color = if (showSongPlaceholders) colorPalette().textDisabled else colorPalette().text,
                                        enabled = !showSongPlaceholders,
                                        modifier = Modifier
                                            .padding(horizontal = 5.dp)
                                            .semantics { contentDescription = menuLabel },
                                        onClick = moreActions::onShortClick,
                                    )
                                },
                            )
                            CollectionToolbar(
                                bookmark = bookmarkAction,
                                actions = inlineActionIds.mapNotNull(toolbarActions::get),
                            )



                        } else if (isInitialLoading) {
                            val actionIcons = inlineActionIds.map { action ->
                                when (action) {
                                    ToolbarControl.SHUFFLE -> shuffleButtonIcon()
                                    ToolbarControl.RADIO -> radioButtonIcon()
                                    ToolbarControl.MENU -> moreActions.iconId
                                    else -> action.icon
                                }
                            }
                            CollectionToolbar(
                                bookmark = {
                                    DisabledPlaylistAction(
                                        if (localPlaylist?.isYoutubePlaylist == true) R.drawable.bookmark
                                        else R.drawable.bookmark_outline,
                                    )
                                },
                                actions = actionIcons.map { icon -> @Composable { DisabledPlaylistAction(icon) } },
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
                    val isLocal by remember { derivedStateOf { ytSong.asMediaItem.isLocal } }
                    val isDownloaded = !isLocal && isDownloadedSong( ytSong.key )

                    SwipeablePlaylistItem(
                        mediaItem = ytSong.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(ytSong.asMediaItem)
                        },
                        onDownload = {
                            binder?.cache?.removeResource( ytSong.key )
                            Database.asyncTransaction {
                                formatTable.updateContentLengthOf( ytSong.key )
                            }

                            if (!isLocal)
                                manageDownload(
                                    context = context,
                                    mediaItem = ytSong.asMediaItem,
                                    downloadState = isDownloaded
                                )
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
                            itemSelector = itemSelector,
                            navController = navController,
                            modifier = Modifier.animateItem(),

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
        songs.forEach { songItem ->
            val mediaItem = songItem.asMediaItem
            binder?.cache?.removeResource(mediaItem.mediaId)
            manageDownload(context, mediaItem, downloadState)
        }
    }
}








