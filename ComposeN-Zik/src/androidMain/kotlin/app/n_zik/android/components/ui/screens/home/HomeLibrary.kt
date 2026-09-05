@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components.ui.screens.home

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.it.fast4x.rimusic.ui.styling.overlay
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.semiBold
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.ReorderableItem
import app.kreate.android.themed.rimusic.component.playlist.PositionLock
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.menu.playlist.LocalPlaylistItemMenu
import androidx.navigation.NavController
import app.it.fast4x.compose.persist.persistList
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.MONTHLY_PREFIX
import app.it.fast4x.rimusic.PINNED_PREFIX
import app.n_zik.android.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow

import app.it.fast4x.rimusic.YTP_PREFIX
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.PlaylistsType
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.ui.components.ButtonsRow
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.ItemSize
import app.it.fast4x.rimusic.ui.components.tab.TabHeader
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.components.themed.HeaderInfo
import app.it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.n_zik.android.components.tab.ItemSelector
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.CheckMonthlyPlaylist
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_ITEM_SIZE
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_PLAYLIST_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER

import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER
import app.it.fast4x.rimusic.utils.autoSyncToolbutton
import app.it.fast4x.rimusic.utils.autosyncPlaylistsKey
import app.it.fast4x.rimusic.utils.importYTMPlaylists
import app.it.fast4x.rimusic.utils.importYTMLibrarySongs
import app.it.fast4x.rimusic.utils.importYTMUploadedSongs
import app.it.fast4x.rimusic.utils.importYTMUploadedAlbums
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.enableCreateMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.playlistTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.showPinnedPlaylistsKey

import app.it.fast4x.rimusic.utils.showYtPlaylistsKey
import app.it.fast4x.rimusic.utils.homePlaylistsOrderKey
import app.it.fast4x.rimusic.utils.homeLibraryToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeLibraryYTPlaylistToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeLibraryPinnedPlaylistToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeLibraryMonthlyPlaylistToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeLibraryPlaylistSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeLibraryYTPlaylistSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeLibraryPinnedPlaylistSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeLibraryMonthlyPlaylistSortMenuOrderKey
import app.it.fast4x.rimusic.utils.semiBold
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import app.kreate.android.me.knighthat.utils.PropUtils
import app.n_zik.android.components.Sort
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.components.tab.ImportSongsFromCSV
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SongShuffler
import timber.log.Timber
import it.fast4x.innertube.requests.playlistPage
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import it.fast4x.innertube.Innertube
import app.it.fast4x.rimusic.utils.asSong
import app.n_zik.android.appContext
import app.n_zik.android.LocalPlayerServiceBinder
import kotlinx.coroutines.flow.first
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.dialog.media.YouTubeLinkImportDialog
import app.n_zik.android.components.tab.ImportPlaylistsMenu
import app.n_zik.android.components.tab.ImportSongsFromServices
import app.n_zik.android.typography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import app.n_zik.android.components.dialog.common.RetrySyncDialog
import app.n_zik.android.components.dialog.settings.HomeLibraryToolbarSettingsDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.utils.formatAsTime
import app.n_zik.android.components.AppPullToRefreshBox
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import androidx.core.content.ContextCompat
import java.util.ArrayList
import android.content.Intent


@ExperimentalMaterial3Api
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun HomeLibrary(
    navController: NavController,
    onPlaylistClick: (Playlist) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    
    // Essentials
    val lazyGridState = rememberLazyGridState()

    // Non-vital
    var playlistType by rememberPreference(playlistTypeKey, PlaylistsType.Playlist)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    var items by persistList<PlaylistPreview>("home/playlists/items")

    var itemsOnDisplay by persistList<PlaylistPreview>("home/playlists/on_display")

    val search = Search(lazyGridState)
    val itemSelector = ItemSelector<PlaylistPreview>()

    val sort = when( playlistType ) {
        PlaylistsType.Playlist -> Sort( HOME_LIBRARY_PLAYLIST_SORT_BY, HOME_LIBRARY_PLAYLIST_SORT_ORDER, homeLibraryPlaylistSortMenuOrderKey, "lib_pl" )
        PlaylistsType.YTPlaylist -> Sort( HOME_LIBRARY_YT_PLAYLIST_SORT_BY, HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER, homeLibraryYTPlaylistSortMenuOrderKey, "lib_yt" )

        PlaylistsType.PinnedPlaylist -> Sort( HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY, HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER, homeLibraryPinnedPlaylistSortMenuOrderKey, "lib_pin" )
        PlaylistsType.MonthlyPlaylist -> Sort( HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY, HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER, homeLibraryMonthlyPlaylistSortMenuOrderKey, "lib_mon" )
    }
    val positionLock = remember( sort.sortOrder ) { PositionLock(sort.sortOrder) }
    val itemSize = ItemSize.init( HOME_LIBRARY_ITEM_SIZE )

    suspend fun getSelectedSongs(): List<Song> = withContext(Dispatchers.IO) {
        val selected = itemSelector.ifEmpty { itemsOnDisplay }
        val seen = HashSet<String>()
        val result = ArrayList<Song>()
        for( preview in selected ) {
            val songs = Database.songPlaylistMapTable.allSongsOf( preview.playlist.id ).first()
            for( song in songs ) {
                if( seen.add( song.id ) ) result.add( song )
            }
        }
        result
    }
    var selectedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(itemSelector.size, itemsOnDisplay) {
        runCatching {
            selectedSongs = getSelectedSongs()
        }.onFailure { e ->
            Timber.tag("HomeLibrary").e(e, "Failed to load selected songs")
        }
    }
    fun getSelectedMediaItems(): List<androidx.media3.common.MediaItem> =
        selectedSongs.map { it.asMediaItem }

    val shuffle = SongShuffler { selectedSongs }
    //</editor-fold>

    val importPlaylistDialog = ImportSongsFromCSV(onImportComplete = {
        appContext().preferences.edit().putString(HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.Custom.name).apply()
    })
    val importSpotifyDialog = ImportSongsFromServices.init(source = "SPOTIFY_IMPORT", onImportComplete = {
        appContext().preferences.edit().putString(HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.Custom.name).apply()
    })
    val importRiPlayDialog = ImportSongsFromServices.init(source = "RIPLAY_IMPORT", onImportComplete = {
        appContext().preferences.edit().putString(HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.Custom.name).apply()
    })
    
    var showYouTubeLinkDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    if (showYouTubeLinkDialog) {
        YouTubeLinkImportDialog(
            onImport = { playlistId ->
                coroutineScope.launch(Dispatchers.IO) {
                    val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
                    Innertube.playlistPage(browseId = browseId)?.getOrNull()?.let { playlistPage ->
                        val playlistName = playlistPage.title ?: "YouTube Playlist"
                        val playlist = Playlist(name = playlistName, browseId = browseId)
                        val playlistRowId = Database.playlistTable.insert(playlist)
                        
                        val songs = playlistPage.songsPage?.items?.mapNotNull { it.asSong.copy(totalPlayTimeMs = 1L) }
                        if (songs != null) {
                            Database.asyncTransaction {
                                songTable.upsert(songs)
                                songs.forEach { song ->
                                    songPlaylistMapTable.map(song.id, playlistRowId)
                                }
                            }
                            appContext().preferences.edit().putString(HOME_LIBRARY_PLAYLIST_SORT_BY.key, PlaylistSortBy.Custom.name).apply()
                            Toaster.done()
                        }
                    }
                }
            },
            onDismiss = { showYouTubeLinkDialog = false }
        )
    }

    val importMenu = remember {
        ImportPlaylistsMenu(
            onImportNzik = { importPlaylistDialog.onShortClick() },
            onImportSpotify = { importSpotifyDialog.onShortClick() },
            onImportRiplay = { importRiPlayDialog.onShortClick() },
            onImportYoutubeLink = { showYouTubeLinkDialog = true }
        )
    }
    val sync = autoSyncToolbutton(R.string.autosync, autosyncPlaylistsKey) {
        CoroutineScope(Dispatchers.IO).launch {
            importYTMPlaylists(force = true)
            importYTMLibrarySongs(force = true)
            importYTMUploadedSongs(force = true)
            importYTMUploadedAlbums(force = true)
        }
    }

    val playNext = PlayNext {
        coroutineScope.launch {
            val mediaItems = withContext(Dispatchers.IO) { getSelectedSongs().map { it.asMediaItem } }
            binder?.player?.addNext( mediaItems, appContext() )
            itemSelector.isActive = false
        }
    }
    val enqueue = Enqueue {
        coroutineScope.launch {
            val mediaItems = withContext(Dispatchers.IO) { getSelectedSongs().map { it.asMediaItem } }
            binder?.player?.enqueue( mediaItems, appContext() )
            itemSelector.isActive = false
        }
    }
    val addToPlaylist = PlaylistsMenu.init(
        navController = navController,
        mediaItems = { _ -> getSelectedMediaItems() },
        onFailure = { throwable, preview ->
            Timber.tag("HomeLibrary").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        finalAction = { itemSelector.isActive = false }
    )
    val exportDialog = ExportSongsToCSVDialog(
        playlistName = "Library",
        songs = { selectedSongs }
    )

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val deletePlaylistsButton = remember {
        object : MenuIcon, Descriptive {
            override val iconId: Int = R.drawable.trash
            override val messageId: Int = R.string.delete_playlists_label
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() { showDeleteConfirmDialog = true }
            override fun onLongClick() {}
        }
    }

    LaunchedEffect( sort.sortBy, sort.sortOrder ) {
        Database.playlistTable
                .sortPreviews( sort.sortBy, sort.sortOrder )
                .distinctUntilChanged()
                .collect { items = it }
    }
    LaunchedEffect( items, search.inputValue ) {
        itemsOnDisplay = items.filter {
            it.playlist.name.contains( search.inputValue, true )
        }
    }

    // START: Additional playlists
    val showPinnedPlaylists by rememberPreference(showPinnedPlaylistsKey, true)
    val showMonthlyPlaylists by rememberPreference(showMonthlyPlaylistsKey, true)
    val showYtPlaylists by rememberPreference(showYtPlaylistsKey, true)
    val isSyncEnabled = isYouTubeSyncEnabled()
    val homePlaylistsOrderPref by rememberPreference(homePlaylistsOrderKey, "")

    val playlistsDefaultOrder = listOf("all", "pinned_playlists", "monthly_playlists", "yt_playlists")
    val toggleMap = mapOf(
        "yt_playlists" to (showYtPlaylists && isSyncEnabled),
        "pinned_playlists" to showPinnedPlaylists,
        "monthly_playlists" to showMonthlyPlaylists
    )
    val typeMap = mapOf(
        "yt_playlists" to PlaylistsType.YTPlaylist,
        "pinned_playlists" to PlaylistsType.PinnedPlaylist,
        "monthly_playlists" to PlaylistsType.MonthlyPlaylist
    )
    val allLabel = stringResource(R.string.all)
    val ytLabel = stringResource(R.string.yt_playlists)
    val pinnedLabel = stringResource(R.string.pinned_playlists)
    val monthlyLabel = stringResource(R.string.monthly_playlists)
    val labelMap = mapOf(
        "yt_playlists" to ytLabel,
        "pinned_playlists" to pinnedLabel,
        "monthly_playlists" to monthlyLabel
    )
    val buttonsList = remember(showPinnedPlaylists, showMonthlyPlaylists, showYtPlaylists, homePlaylistsOrderPref, allLabel, ytLabel, pinnedLabel, monthlyLabel) {
        val order = try {
            val arr = JSONArray(homePlaylistsOrderPref)
            val parsed = (0 until arr.length()).map { arr.getString(it) }
            val valid = parsed.filter { it in playlistsDefaultOrder }.toMutableList()
            for (id in playlistsDefaultOrder) { if (id !in valid) valid.add(id) }
            valid
        } catch (_: Exception) { playlistsDefaultOrder }
        val result = mutableListOf<Pair<PlaylistsType, String>>()
        result.add(PlaylistsType.Playlist to allLabel)
        for (id in order) {
            if (id == "all") continue
            if (toggleMap[id] == true) {
                val type = typeMap[id] ?: continue
                val label = labelMap[id] ?: continue
                result.add(type to label)
            }
        }
        result
    }
    // END - Additional playlists

    LaunchedEffect(showPinnedPlaylists, showMonthlyPlaylists, showYtPlaylists) {
        if (!showPinnedPlaylists && playlistType == PlaylistsType.PinnedPlaylist) playlistType = PlaylistsType.Playlist
        if (!showMonthlyPlaylists && playlistType == PlaylistsType.MonthlyPlaylist) playlistType = PlaylistsType.Playlist
        if (!showYtPlaylists && playlistType == PlaylistsType.YTPlaylist) playlistType = PlaylistsType.Playlist
        if (!showYtPlaylists && playlistType == PlaylistsType.YTPlaylist) playlistType = PlaylistsType.Playlist
    }



    
    // START - Import menu
    importMenu.Render()
    // END - Import menu

    // START - Monthly playlist
    val enableCreateMonthlyPlaylists by rememberPreference(enableCreateMonthlyPlaylistsKey, true)
    if (enableCreateMonthlyPlaylists)
        CheckMonthlyPlaylist()
    // END - Monthly playlist

    val doAutoSync by rememberPreference(autosyncPlaylistsKey, false)
    var justSynced by rememberSaveable { mutableStateOf(!doAutoSync) }

    // START: Import YTM playlists
    LaunchedEffect(justSynced, doAutoSync) {
        if (!justSynced && importYTMPlaylists())
            justSynced = true
    }
    // END: Import YTM playlists


    var refreshing by remember { mutableStateOf(false) }

    fun refresh(itemsToRefresh: List<PlaylistPreview>? = null) {
        if (refreshing || HomeSyncState.isSyncingPlaylists) {
            Toaster.e(appContext().getString(R.string.already_syncing))
            return
        }
        val targetPlaylists = itemsToRefresh ?: itemsOnDisplay
        val ids = ArrayList(targetPlaylists.map { it.playlist.id.toString() })
        
        val intent = Intent(appContext(), HomeSyncService::class.java).apply {
            action = HomeSyncService.ACTION_SYNC_PLAYLISTS
            putStringArrayListExtra(HomeSyncService.EXTRA_IDS, ids)
        }
        try {
            ContextCompat.startForegroundService(appContext(), intent)
        } catch (e: Exception) {
            Timber.tag("HomeLibrary").e(e, "Failed to start HomeSyncService")
            Toaster.e("Failed to start sync service")
        }
    }

    val retryDialog = RetrySyncDialog(
        failedCount = HomeSyncState.failedPlaylistsList.size,
        onRetry = { 
            val items = HomeSyncState.failedPlaylistsList
            HomeSyncState.failedPlaylistsList = emptyList()
            refresh(items) 
        }
    )
    retryDialog.Render()
    LaunchedEffect(HomeSyncState.failedPlaylistsList) {
        if (HomeSyncState.failedPlaylistsList.isNotEmpty()) retryDialog.showDialog()
    }

    AppPullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refresh() }
    ) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth()
        ) {

            Column( Modifier.fillMaxSize() ) {

                val homeLibraryToolbarOrderPrefLibrary by rememberPreference(homeLibraryToolbarOrderKey, "")
                val homeLibraryToolbarOrderPrefYT by rememberPreference(homeLibraryYTPlaylistToolbarOrderKey, "")
                val homeLibraryToolbarOrderPrefPinned by rememberPreference(homeLibraryPinnedPlaylistToolbarOrderKey, "")
                val homeLibraryToolbarOrderPrefMonthly by rememberPreference(homeLibraryMonthlyPlaylistToolbarOrderKey, "")

                val currentToolbarOrderPref = when (playlistType) {
                    PlaylistsType.YTPlaylist -> homeLibraryToolbarOrderPrefYT
                    PlaylistsType.PinnedPlaylist -> homeLibraryToolbarOrderPrefPinned
                    PlaylistsType.MonthlyPlaylist -> homeLibraryToolbarOrderPrefMonthly
                    else -> homeLibraryToolbarOrderPrefLibrary
                }

                val toolbarButtons = remember { mutableStateListOf<Button>() }

                LaunchedEffect(sort.sortBy, sort.sortOrder, currentToolbarOrderPref) {
                    val defaultToolbarOrder = HomeLibraryToolbarSettingsDialog.allButtonIds
                    val order = try {
                        if (currentToolbarOrderPref.isBlank()) defaultToolbarOrder else {
                            val arr = JSONArray(currentToolbarOrderPref)
                            (0 until arr.length()).map { arr.getString(it) }.distinct()
                        }
                    } catch (_: Exception) { defaultToolbarOrder }

                    toolbarButtons.clear()
                    order.forEach { id ->
                        when(id) {
                            "sort" -> toolbarButtons.add(sort)
                            "position_lock" -> { if (sort.sortBy == PlaylistSortBy.Custom) toolbarButtons.add(positionLock) }
                            "sync" -> { if (isYouTubeSyncEnabled()) toolbarButtons.add(sync) }
                            "search" -> toolbarButtons.add(search)
                            "shuffle" -> toolbarButtons.add(shuffle)
                            "item_selector" -> toolbarButtons.add(itemSelector)
                            "play_next" -> toolbarButtons.add(playNext)
                            "enqueue" -> toolbarButtons.add(enqueue)
                            "add_to_playlist" -> toolbarButtons.add(addToPlaylist)
                            "export_dialog" -> toolbarButtons.add(exportDialog)
                            "delete_playlists" -> toolbarButtons.add(deletePlaylistsButton)
                            "import_menu" -> toolbarButtons.add(importMenu)
                            "item_size" -> toolbarButtons.add(itemSize)
                        }
                    }
                }


                val listPrefix =
                    when( playlistType ) {
                        PlaylistsType.Playlist -> ""    // Matches everything
                        PlaylistsType.PinnedPlaylist -> PINNED_PREFIX
                        PlaylistsType.MonthlyPlaylist -> MONTHLY_PREFIX
                        PlaylistsType.YTPlaylist -> YTP_PREFIX
                    }
                val condition: (PlaylistPreview) -> Boolean = {
                    when (playlistType) {
                        PlaylistsType.YTPlaylist -> it.playlist.isYoutubePlaylist
                        PlaylistsType.Playlist -> {
                            val isMonthly = it.playlist.name.startsWith(MONTHLY_PREFIX, true)
                            val isPinned = it.playlist.name.startsWith(PINNED_PREFIX, true)
                            
                            (!isMonthly || showMonthlyPlaylists) && 
                            (!isPinned || showPinnedPlaylists)
                        }
                        else -> it.playlist.name.startsWith(listPrefix, true)
                    }
                }
                val filteredItems = itemsOnDisplay.filter( condition )

                val hapticFeedback = LocalHapticFeedback.current
                val reorderableLazyGridState = rememberReorderableLazyGridState(
                    lazyGridState = lazyGridState
                ) { from, to ->
                    val mutableItemsOnDisplay = itemsOnDisplay.toMutableList()
                    val fromIndex = mutableItemsOnDisplay.indexOfFirst { it.playlist.id == from.key }
                    val toIndex = mutableItemsOnDisplay.indexOfFirst { it.playlist.id == to.key }
                    
                    if (fromIndex != -1 && toIndex != -1) {
                        val movedItem = mutableItemsOnDisplay.removeAt(fromIndex)
                        mutableItemsOnDisplay.add(toIndex, movedItem)
                        itemsOnDisplay = mutableItemsOnDisplay
                    }
                }

                Column {
                    TabHeader( R.string.playlists ) {
                        HeaderInfo( items.size.toString(), R.drawable.playlist )
                    }
                    exportDialog.Render()
                    
                    if (showDeleteConfirmDialog) {
                        val selected = itemSelector.ifEmpty { itemsOnDisplay }
                        val hasSelection = itemSelector.size > 0
                        val itemsToDelete = selected.toList()
                        val text = if (hasSelection) {
                            stringResource(R.string.delete_playlists_confirm_multiple, itemsToDelete.size)
                        } else {
                            stringResource(R.string.delete_playlists_confirm_all, itemsToDelete.size)
                        }
                        ConfirmationDialog(
                            text = text,
                            onDismiss = { showDeleteConfirmDialog = false },
                            onConfirm = {
                                showDeleteConfirmDialog = false
                                coroutineScope.launch(Dispatchers.IO) {
                                    Database.asyncTransaction {
                                        itemsToDelete.forEach { preview ->
                                            playlistTable.delete(preview.playlist)
                                        }
                                    }
                                    withContext(Dispatchers.Main) { itemSelector.isActive = false }
                                }
                            }
                        )
                    }
                    
                    TabToolBar.Buttons( toolbarButtons )
                    search.SearchBar( this@Column )
                }

                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                            .fillMaxWidth()
                    ) {
                        Box {
                            ButtonsRow(
                                chips = buttonsList,
                                currentValue = playlistType,
                                onValueUpdate = { playlistType = it },
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    }
                    if (HomeSyncState.isSyncingPlaylists) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                BasicText(
                                    text = stringResource(R.string.syncing_item, HomeSyncState.playlistSyncCurrentName),
                                    style = typography().xxs.semiBold.copy(color = colorPalette().textSecondary),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp).basicMarquee(iterations = Int.MAX_VALUE)
                                )
                                Row {
                                    BasicText(
                                        text = stringResource(R.string.syncing_progress, HomeSyncState.playlistSyncCurrentIndex, HomeSyncState.playlistSyncTotal),
                                        style = typography().xxs.semiBold.copy(color = colorPalette().textSecondary)
                                    )
                                    if (HomeSyncState.playlistSyncFailed > 0) {
                                        BasicText(
                                            text = " " + stringResource(R.string.syncing_failed, HomeSyncState.playlistSyncFailed),
                                            style = typography().xxs.semiBold.copy(color = colorPalette().red)
                                        )
                                    }
                                }
                            }
                            LinearWavyProgressIndicator(
                                progress = { HomeSyncState.playlistSyncProgress },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                color = colorPalette().accent,
                                trackColor = colorPalette().background2
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Adaptive( itemSize.size.dp ),
                        modifier = Modifier
                            .background(colorPalette().background0)
                            .fillMaxSize(),
                        contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer )
                    ) {
                    items(
                        items = filteredItems,
                        key = { it.playlist.id }
                    ) { preview ->
                        ReorderableItem(
                            reorderableLazyGridState,
                            key = preview.playlist.id
                        ) { isDraggingItem ->
                            Box(modifier = Modifier) {
                                if (!positionLock.isLocked() && sort.sortBy == PlaylistSortBy.Custom && sort.sortOrder == SortOrder.Ascending) {
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(32.dp)
                                            .align(Alignment.TopEnd)
                                            .zIndex(2f)
                                            .draggableHandle(
                                                onDragStarted = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragStopped = {
                                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    val currentItems = itemsOnDisplay.toList()
                                                    Database.asyncTransaction {
                                                        currentItems.forEachIndexed { index, preview ->
                                                            playlistTable.updatePosition(preview.playlist.id, index)
                                                        }
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.reorder),
                                            contentDescription = null,
                                            tint = if (isDraggingItem) colorPalette().accent else colorPalette().textDisabled
                                        )
                                    }
                                }

                                PlaylistItem(
                                    playlist = preview,
                                    thumbnailSizeDp = itemSize.size.dp,
                                    thumbnailSizePx = itemSize.size.px,
                                    alternative = true,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(uiRoundnessShape())
                                        .combinedClickable(
                                            onClick = {
                                                if( itemSelector.isActive ) {
                                                    if( preview in itemSelector ) itemSelector.remove( preview )
                                                    else itemSelector.add( preview )
                                                } else {
                                                    search.hideIfEmpty()
                                                    onPlaylistClick(preview.playlist)
                                                }
                                            },
                                            onLongClick = {
                                                menuState.display {
                                                    LocalPlaylistItemMenu(
                                                        navController = navController,
                                                        playlistPreview = preview
                                                    ).MenuComponent()
                                                }
                                            }
                                        ),
                                    disableScrollingText = disableScrollingText,
                                    isYoutubePlaylist = preview.playlist.isYoutubePlaylist,
                                    isEditable = preview.playlist.isEditable,
                                    thumbnailOverlay = {
                                        if( itemSelector.isActive ) {
                                            key(itemSelector.size) {
                                                Icon(
                                                    painter = painterResource(if (preview in itemSelector) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                                    contentDescription = null,
                                                    tint = if (preview in itemSelector) colorPalette().accent else colorPalette().text,
                                                    modifier = Modifier
                                                        .padding(4.dp)
                                                        .size(24.dp)
                                                        .align(Alignment.TopStart)
                                                        .zIndex(2f)
                                                )
                                            }
                                        } else if (sort.sortBy == PlaylistSortBy.SongCount) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(thumbnailShape())
                                                    .background(colorPalette().overlay)
                                            ) {
                                                BasicText(
                                                    text = preview.songCount.toString(),
                                                    style = typography().s.semiBold.center.color(colorPalette().onOverlay),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.align(Alignment.Center).basicMarquee(iterations = Int.MAX_VALUE)
                                                )
                                            }
                                        } else if (sort.sortBy == PlaylistSortBy.PlayCount) {
                                            val playCount by Database.eventTable.getPlaylistPlayCount(preview.playlist.id).collectAsStateWithLifecycle(initialValue = 0)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(thumbnailShape())
                                                    .background(colorPalette().overlay)
                                            ) {
                                                BasicText(
                                                    text = playCount.toString(),
                                                    style = typography().s.semiBold.center.color(colorPalette().onOverlay),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.align(Alignment.Center).basicMarquee(iterations = Int.MAX_VALUE)
                                                )
                                            }
                                        } else if (sort.sortBy == PlaylistSortBy.ListeningTime) {
                                            val playTime by Database.eventTable.getPlaylistTotalPlayTime(preview.playlist.id).collectAsStateWithLifecycle(initialValue = 0L)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(thumbnailShape())
                                                    .background(colorPalette().overlay)
                                            ) {
                                                BasicText(
                                                    text = formatAsTime(playTime),
                                                    style = typography().s.semiBold.center.color(colorPalette().onOverlay),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.align(Alignment.Center).basicMarquee(iterations = Int.MAX_VALUE)
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(bottom = 47.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = stringResource(R.string.no_items),
                            style = typography().m.semiBold.copy(
                                color = colorPalette().textSecondary
                            )
                        )
                    }
                }
            }
            }

            FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if (showFloatingIcon)
                MultiFloatingActionsContainer(
                    iconId = R.drawable.search,
                    onClick = onSearchClick,
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )
        }
    }
}










