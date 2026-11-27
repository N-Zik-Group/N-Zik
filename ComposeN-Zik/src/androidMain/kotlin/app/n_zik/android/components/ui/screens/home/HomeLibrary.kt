@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.components.playlist.launchLocalPlaylistDeletion
import app.n_zik.android.components.playlist.playlistDeletionConfirmationText


import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo
import app.n_zik.android.core.database.isYouTubePlaylistSource
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.components.ui.screens.home.onDevice.OnDeviceCoverBadge
import app.n_zik.android.components.ui.screens.home.onDevice.DeviceLibraryPullRefresh
import app.n_zik.android.appTextResources
import androidx.compose.ui.res.pluralStringResource
import app.n_zik.android.components.navigation.FloatingActionSiblingSpace
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchNoResults

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.ui.styling.overlay
import app.n_zik.android.ui.styling.onOverlay
import app.n_zik.android.utils.center
import app.n_zik.android.utils.color
import app.n_zik.android.utils.semiBold
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.n_zik.android.components.dialog.common.ConfirmationDialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.ReorderableItem
import app.n_zik.android.themed.n_zik.component.playlist.PositionLock
import app.n_zik.android.enums.PlaylistSortBy
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.menu.playlist.LocalPlaylistItemMenu
import androidx.navigation.NavController
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.compose.persist.persistList
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.R
import app.n_zik.android.components.menu.playlist.LocalLibraryPlaylistImport
import app.n_zik.android.components.menu.playlist.rememberLibraryPlaylistImportMenu
import app.n_zik.android.components.menu.playlist.rememberCollectionAddAction
import app.n_zik.android.components.menu.playlist.playlistActionsAnchor
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.glassFloatingActionContent
import app.n_zik.android.components.glass.rememberGlassFloatingActionBackdrop
import app.n_zik.android.enums.PlaylistsType
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.components.glass.GlassToolbarButtons
import app.n_zik.android.ui.components.tab.ItemSize
import app.n_zik.android.ui.components.tab.TabHeader
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.ui.components.themed.HeaderInfo
import app.n_zik.android.ui.components.themed.MultiFloatingActionsContainer
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.items.PlaylistItemPlaceholder
import app.n_zik.android.cleanPrefix
import app.n_zik.android.core.rewind.RewindPlaylists.rewindDisplayName
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.Preference.HOME_LIBRARY_ITEM_SIZE
import app.n_zik.android.utils.Preference.HOME_LIBRARY_PLAYLIST_SORT_BY
import app.n_zik.android.utils.Preference.HOME_ON_DEVICE_PLAYLISTS_SORT_BY
import app.n_zik.android.utils.Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_ON_DEVICE_PLAYLISTS_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY
import app.n_zik.android.utils.Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER

import app.n_zik.android.utils.Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY
import app.n_zik.android.utils.Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY
import app.n_zik.android.utils.Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER
import app.n_zik.android.utils.autoSyncToolbutton
import app.n_zik.android.utils.autosyncPlaylistsKey
import app.n_zik.android.utils.importYTMPlaylists
import app.n_zik.android.utils.importYTMLibrarySongs
import app.n_zik.android.utils.importYTMUploadedSongs
import app.n_zik.android.utils.importYTMUploadedAlbums
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.playlistTypeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.showMonthlyPlaylistsKey
import app.n_zik.android.utils.showPinnedPlaylistsKey

import app.n_zik.android.utils.showYtPlaylistsKey
import app.n_zik.android.utils.showOnDevicePlaylistsKey
import app.n_zik.android.core.ondevice.deviceLibraryEnabled
import app.n_zik.android.utils.homePlaylistsOrderKey
import app.n_zik.android.utils.homeLibraryPlaylistSortMenuOrderKey
import app.n_zik.android.utils.homeLibraryOnDeviceSortMenuOrderKey
import app.n_zik.android.utils.homeLibraryYTPlaylistSortMenuOrderKey
import app.n_zik.android.utils.homeLibraryPinnedPlaylistSortMenuOrderKey
import app.n_zik.android.utils.homeLibraryMonthlyPlaylistSortMenuOrderKey
import org.json.JSONArray
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import app.n_zik.android.components.Sort
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SongShuffler
import timber.log.Timber
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.appContext
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.models.Song
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.enqueueOffMain
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.preferences
import app.n_zik.android.typography
import kotlinx.coroutines.withContext
import app.n_zik.android.components.dialog.settings.HomeLibrarySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibraryToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.visibleHomeToolbarIds
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.rememberDataStoreBooleanPreference
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.thumbnailShape
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog


internal fun Modifier.playlistSelectionSemantics(isSelected: Boolean?): Modifier =
    if (isSelected == null) this else semantics { selected = isSelected }

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
    onSettingsClick: () -> Unit,
    topContentInsetProvider: () -> Dp = { 0.dp },
    scrollContentInsetProvider: () -> Dp = topContentInsetProvider,
) {
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    
    // Essentials

    // Non-vital
    var playlistType by rememberPreference(playlistTypeKey, PlaylistsType.Playlist)
    val lazyGridState = rememberLazyGridState()
    val headerOffsetState = rememberSaveable { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var youtubeLibraryFilter by rememberPreference(YOUTUBE_LIBRARY_FILTER_KEY, YouTubeLibraryFilter.All)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    // Full offline mode (spec-full-offline-mode): network toolbar actions removed;
    // the import dialog prunes its network choices.
    val offlineMode by OfflineMode.rememberEnabled()

    var items by key(playlistType) { persistList<PlaylistPreview>("home/playlists/$playlistType/items") }

    var itemsOnDisplay by key(playlistType) { persistList<PlaylistPreview>("home/playlists/$playlistType/on_display") }

    val context = LocalContext.current
    val previewSession = LocalThemePreview.current
    val search = Search.forHome()
    val itemSelector = ItemSelector<PlaylistPreview>()
    val showPinnedPlaylists by rememberPreference(showPinnedPlaylistsKey, true)
    val showRewindPlaylists by rememberPreference(showMonthlyPlaylistsKey, true)
    val rewindEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_ENABLED, true)
    val showRewind = rewindEnabled && showRewindPlaylists
    val showOnDeviceTab by rememberPreference(showOnDevicePlaylistsKey, true)
    val showOnDevice = deviceLibraryEnabled() && showOnDeviceTab
    val showYtPlaylists by rememberPreference(showYtPlaylistsKey, true)
    val rewindMonthlyPlaylistEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true)
    val rewindYearlyPlaylistEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true)
    var rewindPlaylistsFilter by rememberPreference(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, RewindPlaylists.Filter.All)
    val libraryScope = HomeLibraryPlaylistScope(
        playlistType, youtubeLibraryFilter, search.filterQuery,
        rewindEnabled && rewindMonthlyPlaylistEnabled, rewindEnabled && rewindYearlyPlaylistEnabled, rewindPlaylistsFilter,
    )
    val devicePlaylistIds by remember(previewSession) {
        if (previewSession != null) kotlinx.coroutines.flow.flowOf(emptyList())
        else Database.songPlaylistMapTable.onDeviceOnlyPlaylistIds()
    }
        .collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    val playlistIdsWithDeviceTracks by remember(previewSession) {
        if (previewSession != null) flowOf(emptySet<Long>())
        else Database.songPlaylistMapTable.playlistIdsWithDeviceTracks().map { it.toSet() }
    }.collectAsStateWithLifecycle(emptySet(), context = NzikDispatchers.DATA)
    val scopeWithoutQuery = remember(libraryScope) { libraryScope.copy(query = "") }
    val deviceIds = remember(devicePlaylistIds) { devicePlaylistIds?.toHashSet() }
    var filteredItems by key(playlistType) { persistList<PlaylistPreview>("home/playlists/$playlistType/filtered") }
    LaunchedEffect(libraryScope) { itemSelector.isActive = false }

    val sort = when( playlistType ) {
        PlaylistsType.OnDevice -> Sort(
            HOME_ON_DEVICE_PLAYLISTS_SORT_BY, HOME_ON_DEVICE_PLAYLISTS_SORT_ORDER,
            homeLibraryOnDeviceSortMenuOrderKey, "lib_dev",
            optionIds = HomeLibrarySortSettingsDialog.menuIds(homeLibraryOnDeviceSortMenuOrderKey),
        )
        PlaylistsType.Playlist -> Sort(
            HOME_LIBRARY_PLAYLIST_SORT_BY, HOME_LIBRARY_PLAYLIST_SORT_ORDER,
            homeLibraryPlaylistSortMenuOrderKey, "lib_pl",
            optionIds = HomeLibrarySortSettingsDialog.menuIds(homeLibraryPlaylistSortMenuOrderKey),
        )
        PlaylistsType.YTPlaylist -> Sort(
            HOME_LIBRARY_YT_PLAYLIST_SORT_BY, HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER,
            homeLibraryYTPlaylistSortMenuOrderKey, "lib_yt",
            optionIds = HomeLibrarySortSettingsDialog.menuIds(homeLibraryYTPlaylistSortMenuOrderKey),
        )

        PlaylistsType.PinnedPlaylist -> Sort(
            HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY, HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER,
            homeLibraryPinnedPlaylistSortMenuOrderKey, "lib_pin",
            optionIds = HomeLibrarySortSettingsDialog.menuIds(homeLibraryPinnedPlaylistSortMenuOrderKey),
        )
        PlaylistsType.MonthlyPlaylist -> Sort(
            HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY, HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER,
            homeLibraryMonthlyPlaylistSortMenuOrderKey, "lib_rw",
            optionIds = HomeLibrarySortSettingsDialog.menuIds(homeLibraryMonthlyPlaylistSortMenuOrderKey),
        )
    }
    val positionLock = remember( sort.sortOrder ) { PositionLock(sort.sortOrder) }
    val itemSize = ItemSize.init( HOME_LIBRARY_ITEM_SIZE )

    //</editor-fold>

    val importMenu = LocalLibraryPlaylistImport.current ?: rememberLibraryPlaylistImportMenu(offlineMode)
    val coroutineScope = rememberCoroutineScope()
    val sync = autoSyncToolbutton(R.string.sync_imported_playlists_automatic_title, autosyncPlaylistsKey) {
        coroutineScope.launch {
            importYTMPlaylists(force = true)
            importYTMLibrarySongs(force = true)
            importYTMUploadedSongs(force = true)
            importYTMUploadedAlbums(force = true)
        }
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var searchDataReady by remember(playlistType, sort.sortBy, sort.sortOrder) { mutableStateOf(false) }
    var completedSearchQuery by remember(playlistType, sort.sortBy, sort.sortOrder, items) { mutableStateOf<String?>(null) }
    val forceShimmer = forceLoadingShimmerPreview()
    LaunchedEffect( playlistType, sort.sortBy, sort.sortOrder ) {
        if (previewSession != null) {
            val demoPlaylists = ThemePreviewDemo.playlists
            items = sort.sortOrder.applyTo(when (sort.sortBy) {
                PlaylistSortBy.Name -> demoPlaylists.sortedBy { it.playlist.name }
                PlaylistSortBy.SongCount -> demoPlaylists.sortedBy { it.songCount }
                else -> demoPlaylists
            })
            searchDataReady = true
            return@LaunchedEffect
        }
        Database.playlistTable
                .sortPreviews( sort.sortBy, sort.sortOrder )
                .distinctUntilChanged()
                .flowOn(NzikDispatchers.DATA)
                .collect {
                    items = it
                    searchDataReady = true
                }
    }
    fun playlistDisplayName(name: String) = context.rewindDisplayName(cleanPrefix(name))

    LaunchedEffect(searchDataReady, items, libraryScope.query, context) {
        if (!searchDataReady) return@LaunchedEffect
        val query = libraryScope.query
        val candidates = items
        itemsOnDisplay = withContext(NzikDispatchers.DATA) {
            if (query.isBlank()) candidates
            else candidates.filter { libraryScope.matchesName(it.playlist.name, ::playlistDisplayName) }
        }
        completedSearchQuery = query
    }

    val searchReady = searchDataReady && completedSearchQuery == search.filterQuery
    var projectionReady by remember(searchReady, itemsOnDisplay, scopeWithoutQuery, deviceIds) {
        mutableStateOf(false)
    }
    LaunchedEffect(searchReady, itemsOnDisplay, scopeWithoutQuery, deviceIds) {
        if (!searchReady || deviceIds == null) return@LaunchedEffect
        val candidates = itemsOnDisplay
        val deviceTab = playlistType == PlaylistsType.OnDevice
        filteredItems = withContext(NzikDispatchers.DATA) {
            val visible = scopeWithoutQuery.visible(candidates)
            if (deviceTab) visible.filter { it.playlist.id in deviceIds } else visible
        }
        projectionReady = true
    }
    val gridReady = searchReady && projectionReady
    val displayedGrid = rememberHomeFilteredGrid("playlists", lazyGridState, gridReady, filteredItems) { it.playlist.id }
    fun actionTargets() = if (gridReady) {
        homeLibraryActionTargets(filteredItems, itemSelector, itemSelector.isActive)
    } else emptyList()
    val eligibleSelection by remember(gridReady, playlistType) {
        derivedStateOf { itemSelector.isNotEmpty() && actionTargets().isNotEmpty() }
    }
    val hasEligibleSelection = { eligibleSelection }
    var deleteSelectionMode by remember { mutableStateOf(false) }
    val deletePlaylistsButton = object : MenuIcon, Descriptive {
        override val iconId: Int = R.drawable.trash
        override val messageId: Int = R.string.playlist_action_delete_selected
        @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
        override fun onShortClick() {
            deleteSelectionMode = itemSelector.isActive
            showDeleteConfirmDialog = true
        }
        override fun onLongClick() {}
    }
    val action = rememberHomeSongAction("HomeLibrary") {
        if (!gridReady || previewSession != null) return@rememberHomeSongAction null
        val ids = actionTargets().map { it.playlist.id }
        if (itemSelector.isActive && ids.isEmpty()) return@rememberHomeSongAction null
        val deviceOnly = playlistType == PlaylistsType.OnDevice
        val load: suspend () -> List<Song> = {
            val eligible = if (deviceOnly) context.scanDeviceLibrary().songIds else emptySet()
            loadHomeParentSongs(ids, Database.songPlaylistMapTable::songsForHomePlaylists) { _, song ->
                !deviceOnly || (song.isLocal && song.id in eligible)
            }
        }
        load
    }
    val shuffle = SongShuffler.deferred(action::snapshot)
    fun queueSelection(playNext: Boolean) {
        val player = binder?.player ?: return
        val load = action.snapshot() ?: return
        val endSelection = itemSelector.captureSelectionCompletion()
        val menuKey = menuState.transitionKey
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            val items = withContext(NzikDispatchers.DATA) { load()?.map { it.asMediaItem } ?: emptyList() }
            val queued = if (playNext) player.addNextOffMain(items, context)
                else player.enqueueOffMain(items, context)
            if (queued) {
                endSelection()
                if (menuState.isCurrentMenu(menuKey)) menuState.hide()
            }
        }
    }

    val playNext = PlayNext(dismissOnClick = false) { queueSelection(playNext = true) }
    val enqueue = Enqueue(dismissOnClick = false) { queueSelection(playNext = false) }
    val addToPlaylist = PlaylistsMenu.deferred(
        navController = navController,
        captureMediaItems = {
            action.snapshot()?.let { load -> { load()?.map { it.asMediaItem } } }
                ?: if (itemSelector.isActive) null else suspend { emptyList() }
        },
        onFailure = { error, preview ->
            Timber.tag("HomeLibrary").e(error, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        itemSelector = itemSelector,
        localDestinationsOnly = playlistType == PlaylistsType.OnDevice ||
            (playlistIdsWithDeviceTracks.isNotEmpty() && actionTargets().any { it.playlist.id in playlistIdsWithDeviceTracks }),
    )
    val exportDialog = ExportSongsToCSVDialog.deferred(
        "Library",
        action::snapshot,
        selectionActive = { itemSelector.isActive },
    )

    val observeListening = previewSession == null &&
        (sort.sortBy == PlaylistSortBy.PlayCount || sort.sortBy == PlaylistSortBy.ListeningTime)
    val listeningTotals by remember(observeListening) {
        if (observeListening) Database.eventTable.getPlaylistListeningTotals()
            .map { rows -> rows.associateBy { it.playlistId } }
        else flowOf(emptyMap())
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    // START: Additional playlists
    val homePlaylistsOrderPref by rememberPreference(homePlaylistsOrderKey, "")

    // YT playlists can come from imports (not only live sync), so the tab
    // visibility follows the showYtPlaylists setting alone.
    val toggleMap = mapOf(
        "on_device" to showOnDevice,
        "yt_playlists" to showYtPlaylists,
        "pinned_playlists" to showPinnedPlaylists,
        "rewind" to showRewind
    )
    val typeMap = mapOf(
        "on_device" to PlaylistsType.OnDevice,
        "yt_playlists" to PlaylistsType.YTPlaylist,
        "pinned_playlists" to PlaylistsType.PinnedPlaylist,
        "rewind" to PlaylistsType.MonthlyPlaylist
    )
    val allLabel = stringResource(R.string.common_option_all)
    val ytLabel = stringResource(R.string.library_youtube_playlists_label)
    val pinnedLabel = stringResource(R.string.playlist_pinned_title)
    val rewindLabel = stringResource(R.string.rewind_title)
    val onDeviceLabel = stringResource(R.string.library_on_device_title)
    val labelMap = mapOf(
        "on_device" to onDeviceLabel,
        "yt_playlists" to ytLabel,
        "pinned_playlists" to pinnedLabel,
        "rewind" to rewindLabel
    )
    val buttonsList = remember(showOnDevice, onDeviceLabel, showPinnedPlaylists, showRewind, showYtPlaylists, homePlaylistsOrderPref, allLabel, ytLabel, pinnedLabel, rewindLabel) {
        val order = libraryPlaylistChipOrder(homePlaylistsOrderPref)
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

    LaunchedEffect(showPinnedPlaylists, showRewind, showYtPlaylists, showOnDevice, playlistType) {
        if (!showOnDevice && playlistType == PlaylistsType.OnDevice) playlistType = PlaylistsType.Playlist
        if (!showPinnedPlaylists && playlistType == PlaylistsType.PinnedPlaylist) playlistType = PlaylistsType.Playlist
        if (!showRewind && playlistType == PlaylistsType.MonthlyPlaylist) playlistType = PlaylistsType.Playlist
        if (!showYtPlaylists && playlistType == PlaylistsType.YTPlaylist) playlistType = PlaylistsType.Playlist
    }



    

    val doAutoSync by rememberPreference(autosyncPlaylistsKey, false)
    var justSynced by rememberSaveable { mutableStateOf(!doAutoSync) }

    // START: Import YTM playlists
    LaunchedEffect(justSynced, doAutoSync) {
        if (previewSession == null && !justSynced && importYTMPlaylists())
            justSynced = true
    }
    // END: Import YTM playlists


    var refreshing by remember { mutableStateOf(false) }

    fun refreshPodcasts() {
        if (refreshing) {
            Toaster.e(appTextResources().getString(R.string.library_sync_already_running_message))
            return
        }
        val targetPlaylists = actionTargets()
        if (targetPlaylists.isEmpty()) return
        refreshing = true
        coroutineScope.launch {
            try {
                val result = refreshHomeLibraryPodcasts(targetPlaylists)
                if (result.failedPodcasts > 0) Toaster.e(R.string.common_error_title)
                else if (result.refreshedPodcasts > 0) Toaster.done()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("HomeLibrary").e(error, "Failed to refresh library playlists")
                Toaster.e(R.string.common_error_title)
            } finally {
                refreshing = false
            }
        }
    }

    val isOnline by NetworkQualityHelper.isNetworkAvailableComposable(context)
    val floatingBackdrop = rememberGlassFloatingActionBackdrop()

    DeviceLibraryPullRefresh(playlistType == PlaylistsType.OnDevice, isOnline, refreshing, ::refreshPodcasts) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth()
        ) {

            Column( Modifier.fillMaxSize() ) {

                val toolbarTab = when (playlistType) {
                    PlaylistsType.OnDevice -> "dev"
                    PlaylistsType.YTPlaylist -> "yt"
                    PlaylistsType.PinnedPlaylist -> "pin"
                    PlaylistsType.MonthlyPlaylist -> "rw"
                    else -> "all"
                }
                val toolbarOrder = HomeLibraryToolbarSettingsDialog.portableLists().getValue(toolbarTab).visibleHomeToolbarIds()
                val playlistAnchor = playlistActionsAnchor(toolbarOrder)
                val hasTracksToAdd = actionTargets().any { it.songCount > 0 }
                val canCreateFirstPlaylist = gridReady && !itemSelector.isActive &&
                    playlistType == PlaylistsType.Playlist && search.filterQuery.isBlank() && itemsOnDisplay.isEmpty()
                val addPlaylistAction = rememberCollectionAddAction(
                    sourceId = "library",
                    menuState = menuState,
                    canImportCollection = "import_menu" in toolbarOrder && playlistType != PlaylistsType.OnDevice,
                    hasTracks = "add_to_playlist" in toolbarOrder && (hasTracksToAdd || canCreateFirstPlaylist),
                    importFromCurrentCollection = false,
                    canExport = "export_dialog" in toolbarOrder && (playlistType != PlaylistsType.OnDevice && hasTracksToAdd),
                    openExport = exportDialog::onShortClick,
                    importDialogOpen = importMenu.isOpen,
                    openImport = importMenu::open,
                    openPicker = addToPlaylist::openPicker,
                )

                val toolbarButtons = buildList<Button> {
                    toolbarOrder.forEach { id ->
                        when(id) {
                            "sort" -> add(sort)
                            "position_lock" -> { if (sort.sortBy == PlaylistSortBy.Custom) add(positionLock) }
                            "sync" -> { if (isYouTubeSyncEnabled() && !offlineMode) add(sync) }
                            "search" -> add(search)
                            "shuffle" -> add(itemSelector.guard(shuffle, hasEligibleSelection))
                            "item_selector" -> add(itemSelector)
                            "play_next" -> add(itemSelector.guard(playNext, hasEligibleSelection))
                            "enqueue" -> add(itemSelector.guard(enqueue, hasEligibleSelection))
                            playlistAnchor -> add(addPlaylistAction)
                            "delete_playlists" -> add(itemSelector.guard(deletePlaylistsButton, hasEligibleSelection))
                            "item_size" -> add(itemSize)
                        }
                    }
                }


                SearchEmptyFeedback(
                    query = search.filterQuery,
                    empty = filteredItems.isEmpty(),
                    loading = !gridReady
                )

                val hapticFeedback = LocalHapticFeedback.current
                val reorderableLazyGridState = sh.calvin.reorderable.rememberReorderableLazyGridState(
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

                val header: @Composable (MutableFloatState, MutableIntState) -> Unit = { titleOffsetState, titleHeightState ->
                    Column {
                        Column {
                            search.HeaderContent {
                                CollapsibleTitleRow( titleOffsetState, titleHeightState ) {
                                    TabHeader( R.string.library_playlists_title ) {
                                        HeaderInfo( items.size.toString(), R.drawable.playlist )
                                    }
                                }
                            }
                    exportDialog.Render()
                    
                    DialogPresence(visible = showDeleteConfirmDialog) {
                        val itemsToDelete = actionTargets()
                        val hasSelection = deleteSelectionMode
                        val text = if (hasSelection) {
                            pluralStringResource(R.plurals.playlist_delete_selected_confirmation_count, itemsToDelete.size, itemsToDelete.size)
                        } else {
                            pluralStringResource(R.plurals.playlist_delete_all_confirmation_count, itemsToDelete.size, itemsToDelete.size)
                        }
                        ConfirmationDialog(
                            title = stringResource(R.string.playlist_delete_multiple_dialog_title),
                            confirmText = stringResource(R.string.common_action_delete),
                            text = playlistDeletionConfirmationText(text, itemsToDelete.map { it.playlist }),
                            onDismiss = { showDeleteConfirmDialog = false },
                            onConfirm = {
                                showDeleteConfirmDialog = false
                                if (itemSelector.isActive != hasSelection) return@ConfirmationDialog
                                launchLocalPlaylistDeletion(context, itemsToDelete.map { it.playlist }) {
                                    if (itemSelector.isActive == hasSelection) itemSelector.isActive = false
                                }
                            }
                        )
                    }
                    
                    GlassToolbarButtons( toolbarButtons )
                    search.SearchBar( this@Column )
                }

                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .fillMaxWidth()
                    ) {
                        Box {
                            GlassHomeChips(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 28.dp),
                                chips = buttonsList,
                                currentValue = playlistType,
                                onValueUpdate = { playlistType = it },
                                modifier = Modifier
                            )
                        }
                    }
                    val subfilterKind = when {
                        playlistType == PlaylistsType.YTPlaylist -> HomeLibrarySubfilter.YouTube
                        showRewind && playlistType == PlaylistsType.MonthlyPlaylist &&
                            RewindPlaylists.rowVisible(rewindMonthlyPlaylistEnabled, rewindYearlyPlaylistEnabled) ->
                            HomeLibrarySubfilter.Rewind
                        else -> null
                    }
                    HomeLibrarySubfilterRow(subfilterKind) { kind ->
                        when (kind) {
                            HomeLibrarySubfilter.YouTube -> YouTubeLibraryFilterChip(youtubeLibraryFilter) {
                                youtubeLibraryFilter = it
                            }
                            HomeLibrarySubfilter.Rewind -> RewindLibraryFilterChip(rewindPlaylistsFilter) {
                                rewindPlaylistsFilter = it
                            }
                        }
                    }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().glassFloatingActionContent(floatingBackdrop)) {
                    val headerHeightState = remember { androidx.compose.runtime.mutableIntStateOf(0) }
                    val headerHeight by headerHeightState
                    val headerOffset by headerOffsetState

                        val nestedScrollConnection = rememberHomeHeaderConnection(headerHeight, headerOffsetState, filteredItems.isNotEmpty(), scrollState = lazyGridState)

                    HomeChromeLayout(
                        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                        header = {
                            HomeCollapsingHeader(
                                headerOffset = headerOffsetState,
                                headerHeight = headerHeightState,
                                topContentInset = topContentInsetProvider,
                            ) { header(headerOffsetState, headerHeightState) }
                        },
                    ) {
                        val headerDensity = androidx.compose.ui.platform.LocalDensity.current
                        LazyVerticalGrid(
                            state = if (displayedGrid == null) rememberLazyGridState() else lazyGridState,
                            columns = GridCells.Adaptive( itemSize.size.dp ),
                            modifier = Modifier
                                .background(colorPalette().background0)
                                .fillMaxSize(),
                            contentPadding = rememberPageChromeContentPadding(
                                topInsetProvider = scrollContentInsetProvider,
                                base = PaddingValues(bottom = Dimensions.bottomSpacer),
                                extraTopProvider = { with(headerDensity) { headerHeight.toDp() } },
                            )
                        ) {
                    if (displayedGrid == null) items(HomePlaceholderCount, key = { "loading_playlist_$it" }) {
                        PlaylistItemPlaceholder(itemSize.size.dp, alternative = true, showChannel = false)
                    }
                    items(
                        items = displayedGrid.orEmpty(),
                        key = { it.playlist.id }
                    ) { preview ->
                        if (forceShimmer) {
                            PlaylistItemPlaceholder(
                                itemSize.size.dp, alternative = true, showChannel = false,
                                title = playlistDisplayName(preview.playlist.name),
                            )
                            return@items
                        }
                        ReorderableItem(
                            reorderableLazyGridState,
                            key = preview.playlist.id
                        ) { isDraggingItem ->
                            Box(modifier = Modifier) {
                                if ((gridReady || isDraggingItem) && !positionLock.isLocked() &&
                                    sort.sortBy == PlaylistSortBy.Custom && sort.sortOrder == SortOrder.Ascending
                                ) {
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

                                val selected = if (itemSelector.isActive) {
                                    itemSelector.isSelected(preview) { it.playlist.id }
                                } else null
                                PlaylistItem(
                                    playlist = preview,
                                    thumbnailSizeDp = itemSize.size.dp,
                                    thumbnailSizePx = itemSize.size.px,
                                    alternative = true,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(uiRoundnessShape())
                                        .playlistSelectionSemantics(selected)
                                        .combinedClickable(
                                            enabled = gridReady,
                                            onClick = {
                                                if (itemSelector.isActive) {
                                                    itemSelector.toggle(preview) { it.playlist.id }
                                                } else {
                                                    search.hideIfEmpty()
                                                    if (playlistType == PlaylistsType.OnDevice) {
                                                        navController.navigate("localPlaylist/${preview.playlist.id}?onDevice=true")
                                                    } else {
                                                        onPlaylistClick(preview.playlist)
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                menuState.display {
                                                    LocalPlaylistItemMenu(
                                                        navController = navController,
                                                        playlistPreview = preview,
                                                        onDevice = playlistType == PlaylistsType.OnDevice,
                                                    ).MenuComponent()
                                                }
                                            }
                                        ),
                                    disableScrollingText = disableScrollingText,
                                    isYoutubePlaylist = preview.playlist.isYoutubePlaylist,
                                    isEditable = preview.playlist.isEditable,
                                    thumbnailOverlay = {
                                        if (preview.playlist.id in devicePlaylistIds.orEmpty() &&
                                            !isYouTubePlaylistSource(preview.playlist.browseId, preview.playlist.isYoutubePlaylist)
                                        ) {
                                            OnDeviceCoverBadge(Modifier.align(Alignment.TopEnd).zIndex(3f))
                                        }
                                        if (selected != null) {
                                            Icon(
                                                painter = painterResource(if (selected) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                                contentDescription = null,
                                                tint = if (selected) colorPalette().accent else colorPalette().text,
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .size(24.dp)
                                                    .align(Alignment.TopStart)
                                                    .zIndex(2f)
                                            )
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
                                            val playCount = listeningTotals[preview.playlist.id]?.playCount ?: 0
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
                                            val playTime = listeningTotals[preview.playlist.id]?.totalPlayTimeMs ?: 0L
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

                if (gridReady && displayedGrid?.isEmpty() == true) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(rememberPageChromeContentPadding(topInsetProvider = scrollContentInsetProvider, base = PaddingValues(top = with(androidx.compose.ui.platform.LocalDensity.current) { (headerHeight + headerOffset).coerceAtLeast(0f).toDp() }, bottom = Dimensions.bottomSpacer))),
                        contentAlignment = Alignment.Center
                    ) {
                        if (search.filterQuery.isBlank()) {
                            val message = if (playlistType == PlaylistsType.OnDevice)
                                R.string.library_on_device_no_local_playlists else R.string.common_empty_items
                            SearchNoResults(text = stringResource(message))
                        }
                    }
                }
                    }
            }
            }

            GlassFloatingActions(floatingBackdrop) {
                val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
                val barsToggle = app.n_zik.android.components.navigation.sideAwareLandscapeBarsToggleButton()
                FloatingActionsContainerWithScrollToTop(
                    reservedEndSpace = if (showFloatingIcon) FloatingActionSiblingSpace else 0.dp,
                    lazyGridState = lazyGridState,
                    iconId = barsToggle?.iconId,
                    onClick = barsToggle?.onClick
                )

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
}

internal fun libraryPlaylistChipOrder(serializedOrder: String): List<String> {
    val defaults = listOf("all", "pinned_playlists", "rewind", "yt_playlists", "on_device")
    val saved = runCatching {
        val items = JSONArray(serializedOrder)
        List(items.length(), items::getString).filter { it in defaults }
    }.getOrDefault(emptyList())
    return (saved + defaults).distinct()
}
