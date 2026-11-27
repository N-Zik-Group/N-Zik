@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components.ui.screens.home


import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo
import app.n_zik.android.components.ui.screens.home.onDevice.DeviceLibraryPullRefresh
import app.n_zik.android.components.navigation.FloatingActionSiblingSpace
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchNoResults

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape


import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.n_zik.android.core.ondevice.observeDeviceLibrary
import app.n_zik.android.core.ondevice.groupByDeviceAvailability
import app.n_zik.android.core.ondevice.DeviceLibraryResults
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.utils.getActiveProfile
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.size
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.ReorderableItem
import app.n_zik.android.themed.n_zik.component.playlist.PositionLock
import app.n_zik.android.enums.ArtistSortBy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.R
import app.n_zik.android.components.menu.playlist.rememberCollectionAddAction
import app.n_zik.android.components.menu.playlist.playlistActionsAnchor
import app.n_zik.android.compose.persist.persistList
import app.n_zik.innertube.utils.from
import app.n_zik.innertube.YtMusic
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.glassFloatingActionContent
import app.n_zik.android.components.glass.rememberGlassFloatingActionBackdrop
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.models.Song
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.enqueueOffMain
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.enums.ArtistsType
import app.n_zik.android.enums.FilterBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Artist
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.components.glass.GlassToolbarButtons
import app.n_zik.android.ui.components.tab.ItemSize
import app.n_zik.android.ui.components.tab.TabHeader
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.Randomizer
import app.n_zik.android.components.menu.FilterMenu
import app.n_zik.android.components.menu.originLabelRes
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.ui.components.themed.HeaderInfo
import app.n_zik.android.ui.components.themed.MultiFloatingActionsContainer
import app.n_zik.android.ui.items.ArtistItem
import app.n_zik.android.ui.items.ArtistItemPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.onOverlay
import app.n_zik.android.ui.styling.overlay
import app.n_zik.android.utils.Preference.HOME_ARTISTS_FAVORITES_SORT_BY
import app.n_zik.android.utils.Preference.HOME_ARTISTS_FAVORITES_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_ARTISTS_LIBRARY_SORT_BY
import app.n_zik.android.utils.Preference.HOME_ON_DEVICE_ARTISTS_SORT_BY
import app.n_zik.android.utils.Preference.HOME_ARTISTS_LIBRARY_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_ON_DEVICE_ARTISTS_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_ARTISTS_DISLIKED_SORT_BY
import app.n_zik.android.utils.Preference.HOME_ARTISTS_DISLIKED_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_ARTIST_ITEM_SIZE
import app.n_zik.android.utils.artistTypeKey
import app.n_zik.android.utils.autoSyncToolbutton
import app.n_zik.android.utils.autosyncArtistsKey
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.filterByKey
import app.n_zik.android.utils.YouTubeArtistProvenance
import app.n_zik.android.utils.importYTMSubscribedChannels
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.utils.center
import app.n_zik.android.utils.color
import app.n_zik.android.utils.showFavoritesArtistKey
import app.n_zik.android.utils.showOnDeviceArtistsKey
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.core.ondevice.deviceLibraryEnabled
import app.n_zik.android.utils.excludeDislikedArtistsKey
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.utils.homeArtistsOrderKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.homeArtistsFavoritesSortMenuOrderKey
import app.n_zik.android.utils.homeArtistsLibrarySortMenuOrderKey
import app.n_zik.android.utils.homeArtistsOnDeviceSortMenuOrderKey
import app.n_zik.android.utils.homeArtistsDislikedSortMenuOrderKey
import org.json.JSONArray
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import app.n_zik.android.components.Sort
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.android.components.menu.artist.LocalArtistItemMenu
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.visibleHomeToolbarIds
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import androidx.navigation.NavController

import timber.log.Timber

@ExperimentalMaterial3Api
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@Composable
fun HomeArtists(
    navController: NavController,
    onArtistClick: (Artist) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    topContentInsetProvider: () -> Dp = { 0.dp },
    scrollContentInsetProvider: () -> Dp = topContentInsetProvider,
) {
    // Essentials
    val (colorPalette, typography) = LocalAppearance.current
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    val coroutineScope = rememberCoroutineScope()

    // Settings
    var artistType by rememberPreference(artistTypeKey, ArtistsType.Library )
    val lazyGridState = rememberLazyGridState()
    val headerOffsetState = rememberSaveable { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var filterBy by rememberPreference(filterByKey, FilterBy.All)


    var items by key(artistType) { persistList<Artist>( "home/artists/$artistType/items") }
    var itemsToFilter by key(artistType) { persistList<Artist>( "home/artists/$artistType/itemsToFilter" ) }

    var itemsOnDisplay by key(artistType) { persistList<Artist>( "home/artists/$artistType/on_display" ) }

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val search = Search.forHome()
    val itemSelector = ItemSelector<Artist>()

    val sort = when( artistType ) {
        ArtistsType.Favorites -> Sort(
            HOME_ARTISTS_FAVORITES_SORT_BY, HOME_ARTISTS_FAVORITES_SORT_ORDER,
            homeArtistsFavoritesSortMenuOrderKey, "art_fav",
            optionIds = HomeArtistsSortSettingsDialog.menuIds(homeArtistsFavoritesSortMenuOrderKey),
        )
        ArtistsType.OnDevice -> Sort(
            HOME_ON_DEVICE_ARTISTS_SORT_BY, HOME_ON_DEVICE_ARTISTS_SORT_ORDER,
            homeArtistsOnDeviceSortMenuOrderKey, "art_dev",
            optionIds = HomeArtistsSortSettingsDialog.menuIds(homeArtistsOnDeviceSortMenuOrderKey),
        )
        ArtistsType.Library -> Sort(
            HOME_ARTISTS_LIBRARY_SORT_BY, HOME_ARTISTS_LIBRARY_SORT_ORDER,
            homeArtistsLibrarySortMenuOrderKey, "art_lib",
            optionIds = HomeArtistsSortSettingsDialog.menuIds(homeArtistsLibrarySortMenuOrderKey),
        )
        ArtistsType.Disliked -> Sort(
            HOME_ARTISTS_DISLIKED_SORT_BY, HOME_ARTISTS_DISLIKED_SORT_ORDER,
            homeArtistsDislikedSortMenuOrderKey, "art_dis",
            optionIds = HomeArtistsSortSettingsDialog.menuIds(homeArtistsDislikedSortMenuOrderKey),
        )
    }
    val positionLock = remember( sort.sortOrder ) { PositionLock(sort.sortOrder) }

    val itemSize = ItemSize.init( HOME_ARTIST_ITEM_SIZE )

    fun actionTargets() = itemSelector.actionTargets(itemsOnDisplay, Artist::id, Artist::id, selectionOrder = true)
    val randomizer = object: Randomizer<Artist> {
        override fun getItems(): List<Artist> = actionTargets()
        override fun onClick(index: Int) {
            getItems().getOrNull(index)?.let(onArtistClick)
        }
    }

    val showOnDeviceTab by rememberPreference(showOnDeviceArtistsKey, true)
    val deviceLibraryAvailable = deviceLibraryEnabled()
    val showOnDevice = deviceLibraryAvailable && showOnDeviceTab
    val onDeviceLabel = stringResource(R.string.library_on_device_title)
    val showFavoritesArtist by rememberPreference(showFavoritesArtistKey, true)
    val showDislikedArtist by rememberPreference(excludeDislikedArtistsKey, DislikeMode.Enabled)
    val homeArtistsOrderPref by rememberPreference(homeArtistsOrderKey, "")

    val favoritesLabel = stringResource(R.string.library_favorites_title)
    val allLabel = stringResource(R.string.common_option_all)
    val dislikedLabel = stringResource(R.string.library_disliked_title)
    val artistsDefaultOrder = listOf("all", "favorites", "disliked", "on_device")
    val labelMap = mapOf("on_device" to onDeviceLabel, "favorites" to favoritesLabel, "all" to allLabel, "disliked" to dislikedLabel)
    val typeMap = mapOf("on_device" to ArtistsType.OnDevice, "favorites" to ArtistsType.Favorites, "all" to ArtistsType.Library, "disliked" to ArtistsType.Disliked)
    val toggleMap = mapOf("on_device" to showOnDevice, "favorites" to showFavoritesArtist, "all" to true, "disliked" to showDislikedArtist.isEnabled)
    val buttonsList = remember(showOnDevice, onDeviceLabel, showDislikedArtist, showFavoritesArtist, homeArtistsOrderPref, favoritesLabel, allLabel, dislikedLabel) {
        val order = try {
            val arr = JSONArray(homeArtistsOrderPref)
            val parsed = (0 until arr.length()).map { arr.getString(it) }
            val valid = parsed.filter { it in artistsDefaultOrder }.toMutableList()
            for (id in artistsDefaultOrder) { if (id !in valid) valid.add(id) }
            valid
        } catch (_: Exception) { artistsDefaultOrder }
        order.mapNotNull { id ->
            if (toggleMap[id] == true) {
                val type = typeMap[id] ?: return@mapNotNull null
                val label = labelMap[id] ?: return@mapNotNull null
                type to label
            } else null
        }
    }

    LaunchedEffect(showFavoritesArtist, showOnDevice, artistType) {
        if (!showOnDevice && artistType == ArtistsType.OnDevice) artistType = ArtistsType.Library
        if (!showFavoritesArtist && artistType == ArtistsType.Favorites) artistType = ArtistsType.Library
    }

    val showYouTubeOrigin by YouTubeArtistProvenance.showOrigin.collectAsStateWithLifecycle()
    val sourceFilter = if (showYouTubeOrigin) filterBy else FilterBy.All
    val includeDeviceGroups = deviceLibraryAvailable &&
        (artistType == ArtistsType.OnDevice || sourceFilter != FilterBy.YoutubeLibrary)

    val context = LocalContext.current
    val preview = LocalThemePreview.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val profile = getActiveProfile(context)
    var eligibleDeviceGroups by remember(profile) {
        mutableStateOf(if (preview == null) DeviceLibraryResults.current(profile)?.artistIds else emptySet())
    }
    LaunchedEffect(context, lifecycleOwner, profile, includeDeviceGroups, preview) {
        if (preview != null || !includeDeviceGroups) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            context.observeDeviceLibrary().collect { eligibleDeviceGroups = it.artistIds }
        }
    }

    sort.onDeviceCriterionAvailable = includeDeviceGroups && sourceFilter != FilterBy.YoutubeLibrary &&
        artistType != ArtistsType.OnDevice
    val deviceSortOrder = sort.sortOrder.takeIf { sort.sortBy == ArtistSortBy.OnDevice }
    val databaseOrder = if (deviceSortOrder != null) SortOrder.Ascending else sort.sortOrder
    var searchDataReady by remember(sort.sortBy, sort.sortOrder, artistType) { mutableStateOf(false) }
    val groupsReady = (artistType != ArtistsType.OnDevice && deviceSortOrder == null) || eligibleDeviceGroups != null
    val forceShimmer = forceLoadingShimmerPreview()
    LaunchedEffect(sort.sortBy, sort.sortOrder, artistType, includeDeviceGroups) {
        if (preview != null) {
            val demoItems = ThemePreviewDemo.artists
            itemsToFilter = sort.sortOrder.applyTo(when (sort.sortBy) {
                ArtistSortBy.Name -> demoItems.sortedBy { it.name.orEmpty() }
                else -> demoItems
            })
            searchDataReady = true
            return@LaunchedEffect
        }
        when( artistType ) {
            ArtistsType.Favorites -> Database.artistTable.sortFollowing(
                sort.sortBy, databaseOrder, includeDeviceGroups = includeDeviceGroups,
            )
            ArtistsType.Library, ArtistsType.OnDevice -> Database.artistTable.sortInLibrary(
                sort.sortBy, databaseOrder, includeDeviceGroups = includeDeviceGroups,
            )
            ArtistsType.Disliked -> Database.artistTable.allDisliked().map { rows ->
                if (deviceSortOrder != null) rows.sortedBy { it.name.orEmpty().lowercase() } else rows
            }
        }.flowOn(NzikDispatchers.DATA).collect {
            itemsToFilter = it
            searchDataReady = true
        }
    }
    val groups = eligibleDeviceGroups
    var baseReady by remember(searchDataReady, groupsReady, itemsToFilter, artistType, sourceFilter, groups, includeDeviceGroups, deviceSortOrder) {
        mutableStateOf(false)
    }
    LaunchedEffect(searchDataReady, groupsReady, itemsToFilter, artistType, sourceFilter, groups, includeDeviceGroups, deviceSortOrder) {
        if (!searchDataReady || !groupsReady) return@LaunchedEffect
        val type = artistType
        val source = sourceFilter
        val candidates = itemsToFilter
        items = withContext(NzikDispatchers.DATA) {
            val visible = candidates.filter { item ->
                when {
                    type != ArtistsType.OnDevice && type != ArtistsType.Disliked && item.dislikedAt != null -> false
                    isDeviceArtistId(item.id) -> includeDeviceGroups && item.id in groups.orEmpty()
                    type == ArtistsType.OnDevice -> false
                    else -> when (source) {
                        FilterBy.All -> true
                        FilterBy.YoutubeLibrary -> item.isYoutubeArtist
                        else -> !item.isYoutubeArtist
                    }
                }
            }
            if (deviceSortOrder != null) visible.groupByDeviceAvailability(deviceSortOrder) { it.id in groups.orEmpty() }
            else visible
        }
        baseReady = true
    }
    var completedSearchQuery by remember(baseReady, items) { mutableStateOf<String?>(null) }
    LaunchedEffect(baseReady, items, search.filterQuery) {
        if (!baseReady) return@LaunchedEffect
        val query = search.filterQuery
        val candidates = items
        itemsOnDisplay = withContext(NzikDispatchers.DATA) {
            candidates.filter { it.name?.contains(query, true) == true }.distinctBy(Artist::id)
        }
        completedSearchQuery = query
    }
    val gridReady = baseReady && completedSearchQuery == search.filterQuery
    val displayedGrid = rememberHomeFilteredGrid("artists", lazyGridState, gridReady, itemsOnDisplay) { it.id }
    val action = rememberHomeSongAction("HomeArtist") {
        if (!gridReady || preview != null) return@rememberHomeSongAction null
        val eligibleIds = actionTargets().map { it.id }
        if (itemSelector.isActive && eligibleIds.isEmpty()) return@rememberHomeSongAction null
        val load: suspend () -> List<Song> = {
            loadHomeLibraryGroupSongs(eligibleIds, Database.songArtistMapTable::songsForHomeArtists, ::isDeviceArtistId) {
                context.scanDeviceLibrary().artistSongIds
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
        },
        onFailure = { error, preview ->
            Timber.tag("HomeArtist").e(error, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        itemSelector = itemSelector,
        localDestinationsOnly = actionTargets().any { isDeviceArtistId(it.id) },
    )
    val exportDialog = ExportSongsToCSVDialog.deferred(
        "Artists",
        action::snapshot,
        selectionActive = { itemSelector.isActive },
    )
    val eligibleSelection by remember(gridReady, artistType) {
        derivedStateOf { gridReady && itemSelector.isNotEmpty() && actionTargets().isNotEmpty() }
    }
    val hasEligibleSelection = { eligibleSelection }
    val trackCounts by remember(preview) {
        if (preview == null) Database.songArtistMapTable.observeNonDeviceSongCounts() else flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList(), context = NzikDispatchers.DATA)
    val collectionIdsWithTracks = remember(trackCounts) { trackCounts.mapTo(hashSetOf()) { it.id } }
    val hasPlaylistTargets = actionTargets().any { it.id in collectionIdsWithTracks || isDeviceArtistId(it.id) }


    SearchEmptyFeedback(
        query = search.filterQuery,
        empty = itemsOnDisplay.isEmpty(),
        loading = !gridReady
    )

    val sync = autoSyncToolbutton(R.string.sync_channels, autosyncArtistsKey) { coroutineScope.launch { importYTMSubscribedChannels(force = true) } }

    // Full offline mode (spec-full-offline-mode): the network sync action is removed.
    val offlineMode by OfflineMode.rememberEnabled()

    val attemptedCovers = remember(profile) { HashSet<String>() }
    val coverConcurrency = remember { Semaphore(3) }
    val observeListening = preview == null &&
        (sort.sortBy == ArtistSortBy.PlayCount || sort.sortBy == ArtistSortBy.ListeningTime)
    val listeningTotals by remember(observeListening, profile) {
        if (observeListening) Database.eventTable.getArtistListeningTotals()
            .map { rows -> rows.associateBy { it.artistId } }
        else flowOf(emptyMap())
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    val doAutoSync by rememberPreference(autosyncArtistsKey, false)
    val artistImportEnabled by YouTubeArtistProvenance.canImport.collectAsStateWithLifecycle()
    var justSynced by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(artistImportEnabled, doAutoSync, preview) {
        if (!artistImportEnabled) {
            justSynced = false
        } else if (preview == null && doAutoSync && !justSynced && importYTMSubscribedChannels()) {
            justSynced = true
        }
    }

    val floatingBackdrop = rememberGlassFloatingActionBackdrop()

    DeviceLibraryPullRefresh(includeDeviceGroups) {
        Box (
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxHeight()
                .fillMaxWidth()
        ) {

            Column( Modifier.fillMaxSize() ) {

                val toolbarTab = when (artistType) {
                    ArtistsType.OnDevice -> "dev"
                    ArtistsType.Favorites -> "favs"
                    ArtistsType.Disliked -> "disliked"
                    else -> "all"
                }
                val toolbarOrder = HomeArtistsToolbarSettingsDialog.portableLists().getValue(toolbarTab).visibleHomeToolbarIds()
                val playlistAnchor = playlistActionsAnchor(toolbarOrder)
                val playlistActions = rememberCollectionAddAction(
                    sourceId = "home-artists",
                    menuState = menuState,
                    canImportCollection = false,
                    hasTracks = "add_to_playlist" in toolbarOrder && gridReady && hasPlaylistTargets,
                    importFromCurrentCollection = false,
                    canExport = "export_dialog" in toolbarOrder && (gridReady && artistType != ArtistsType.OnDevice && hasPlaylistTargets),
                    openImport = {},
                    openPicker = addToPlaylist::openPicker,
                    openExport = exportDialog::onShortClick,
                )

                val toolbarButtons = buildList<Button> {
                    toolbarOrder.forEach { id ->
                        when(id) {
                            "sort" -> add(sort)
                            "position_lock" -> { if (sort.sortBy == ArtistSortBy.Custom) add(positionLock) }
                            "sync" -> { if (isYouTubeSyncEnabled() && !offlineMode) add(sync) }
                            "search" -> add(search)
                            "randomizer" -> add(randomizer)
                            "shuffle" -> add(itemSelector.guard(shuffle, hasEligibleSelection))
                            "item_selector" -> add(itemSelector)
                            "play_next" -> add(itemSelector.guard(playNext, hasEligibleSelection))
                            "enqueue" -> add(itemSelector.guard(enqueue, hasEligibleSelection))
                            playlistAnchor -> add(playlistActions)
                            "item_size" -> add(itemSize)
                        }
                    }
                }


                val hapticFeedback = LocalHapticFeedback.current

                val reorderableLazyGridState = sh.calvin.reorderable.rememberReorderableLazyGridState(
                    lazyGridState = lazyGridState
                ) { from, to ->
                    val mutableItems = itemsOnDisplay.toMutableList()
                    val fromIndex = mutableItems.indexOfFirst { it.id == from.key }
                    val toIndex = mutableItems.indexOfFirst { it.id == to.key }

                    if (fromIndex != -1 && toIndex != -1) {
                        val movedItem = mutableItems.removeAt(fromIndex)
                        mutableItems.add(toIndex, movedItem)
                        itemsOnDisplay = mutableItems
                    }
                }

                val header: @Composable (MutableFloatState, MutableIntState) -> Unit = { titleOffsetState, titleHeightState ->
                    Column {
                        Column {
                            search.HeaderContent {
                                CollapsibleTitleRow( titleOffsetState, titleHeightState ) {
                                    TabHeader( R.string.library_artists_title ) {
                                        HeaderInfo(items.size.toString(), R.drawable.people)
                                    }
                                }
                            }
                    exportDialog.Render()
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
                        GlassHomeChips(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp),
                            chips = buttonsList,
                            currentValue = artistType,
                            onValueUpdate = { artistType = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AnimatedVisibility(
                        visible = artistType != ArtistsType.OnDevice && showYouTubeOrigin,
                        enter = fadeIn(animationSpec = tween(200)) + expandVertically(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp)
                                .fillMaxWidth()
                        ) {
                            GlassHomeFilterChip(
                                label = {
                                    Text(
                                        text = stringResource(filterBy.originLabelRes)
                                    )
                                },
                                selected = true,
                                shape = uiRoundnessShape(),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = colorPalette().background1,
                                    labelColor = colorPalette().text,
                                    selectedContainerColor = colorPalette().accent,
                                    selectedLabelColor = colorPalette().onAccent,
                                ),
                                onClick = {
                                    menuState.display {
                                        FilterMenu(selected = filterBy) { selected ->
                                            filterBy = selected
                                            menuState.hide()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().glassFloatingActionContent(floatingBackdrop)) {


                    val headerHeightState = remember { androidx.compose.runtime.mutableIntStateOf(0) }
                    val headerHeight by headerHeightState
                    val headerOffset by headerOffsetState

                    val nestedScrollConnection = rememberHomeHeaderConnection(headerHeight, headerOffsetState, itemsOnDisplay.isNotEmpty(), scrollState = lazyGridState)

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
                            modifier = Modifier.background( colorPalette().background0 ).fillMaxSize(),
                            contentPadding = rememberPageChromeContentPadding(
                                topInsetProvider = scrollContentInsetProvider,
                                base = PaddingValues(bottom = Dimensions.bottomSpacer),
                                extraTopProvider = { with(headerDensity) { headerHeight.toDp() } },
                            )
                        ) {
                    val artists = displayedGrid.orEmpty()
                    if (displayedGrid == null) items(HomePlaceholderCount, key = { "loading_artist_$it" }) {
                        ArtistItemPlaceholder(itemSize.size.dp, alternative = true)
                    }
                    items(items = artists, key = { it.id }) { artist ->
                        if (forceShimmer) {
                            ArtistItemPlaceholder(itemSize.size.dp, alternative = true, title = artist.name)
                            return@items
                        }
                        if (preview == null && artist.thumbnailUrl == null && !isDeviceArtistId(artist.id) && !offlineMode) LaunchedEffect(artist.id) {
                            val id = artist.id
                            if (attemptedCovers.add(id)) coroutineScope.launch(NzikDispatchers.DATA) {
                                coverConcurrency.withPermit {
                                    YtMusic.getArtistPage(id.removePrefix(MODIFIED_PREFIX))
                                        .onSuccess { page ->
                                            page?.artist?.thumbnail?.url?.let { url ->
                                                Database.asyncTransaction { artistTable.fillMissingCover(id, url) }
                                            }
                                        }
                                        .onFailure { error ->
                                            if (error is CancellationException) throw error
                                            Timber.tag("HomeArtist").w(error, "Could not repair artist cover")
                                        }
                                }
                            }
                        }
                        ReorderableItem(
                            reorderableLazyGridState,
                            key = artist.id
                        ) { isDraggingItem ->
                            Box {
                                if (gridReady && !positionLock.isLocked() && sort.sortBy == ArtistSortBy.Custom && sort.sortOrder == SortOrder.Ascending) {
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
                                                        currentItems.forEachIndexed { index, artist ->
                                                            artistTable.updatePosition(artist.id, index)
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

                                ArtistItem(
                                    artist = artist,
                                    thumbnailSizeDp = itemSize.size.dp,
                                    thumbnailSizePx = itemSize.size.px,
                                    alternative = true,
                                    bookmarkState = when {
                                        artist.dislikedAt != null -> false
                                        artist.bookmarkedAt != null -> true
                                        else -> null
                                    },
                                    observeBookmarkState = false,
                                    modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                        enabled = gridReady,
                                        onClick = {
                                            if( itemSelector.isActive ) {
                                                itemSelector.toggle(artist, Artist::id)
                                            } else {
                                                search.hideIfEmpty()
                                                onArtistClick( artist )
                                            }
                                        },
                                        onLongClick = {
                                            menuState.display { LocalArtistItemMenu(navController = navController, artist = artist).MenuComponent() }
                                        }
                                    ),
                                    disableScrollingText = disableScrollingText,
                                    isYoutubeArtist = showYouTubeOrigin && artist.isYoutubeArtist,
                                    thumbnailOverlay = {
                                        if( itemSelector.isActive ) {
                                            key(itemSelector.size) {
                                                val checked = itemSelector.isSelected(artist, Artist::id)
                                                Icon(
                                                    painter = painterResource(if (checked) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                                    contentDescription = null,
                                                    tint = if (checked) colorPalette().accent else colorPalette().text,
                                                    modifier = Modifier
                                                        .padding(4.dp)
                                                        .size(24.dp)
                                                        .align(Alignment.TopStart)
                                                        .zIndex(2f)
                                                )
                                            }
                                        } else if (sort.sortBy == ArtistSortBy.PlayCount) {
                                            val playCount = listeningTotals[artist.id]?.playCount ?: 0
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(artistThumbnailShape())
                                                    .background(colorPalette().overlay)
                                            ) {
                                                BasicText(
                                                    text = playCount.toString(),
                                                    style = typography.s.semiBold.center.color(colorPalette().onOverlay),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.align(Alignment.Center).basicMarquee(iterations = Int.MAX_VALUE)
                                                )
                                            }
                                        } else if (sort.sortBy == ArtistSortBy.ListeningTime) {
                                            val playTime = listeningTotals[artist.id]?.totalPlayTimeMs ?: 0L
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(artistThumbnailShape())
                                                    .background(colorPalette().overlay)
                                            ) {
                                                BasicText(
                                                    text = formatAsTime(playTime),
                                                    style = typography.s.semiBold.center.color(colorPalette().onOverlay),
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
                        if (search.filterQuery.isBlank()) SearchNoResults(text = stringResource(R.string.common_empty_items))
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

                if( showFloatingIcon )
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










