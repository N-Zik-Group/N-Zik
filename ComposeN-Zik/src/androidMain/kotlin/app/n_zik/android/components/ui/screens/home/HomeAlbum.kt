@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components.ui.screens.home


import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo
import app.n_zik.android.components.ui.screens.home.onDevice.DeviceLibraryPullRefresh
import app.n_zik.android.components.navigation.FloatingActionSiblingSpace
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchNoResults

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.size
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.ReorderableItem
import app.n_zik.android.themed.n_zik.component.playlist.PositionLock
import app.n_zik.android.enums.AlbumSortBy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.n_zik.android.core.ondevice.observeDeviceLibrary
import app.n_zik.android.core.ondevice.DeviceLibraryResults
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.utils.getActiveProfile
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.components.menu.isCurrentMenu
import app.n_zik.android.R
import app.n_zik.android.compose.persist.persistList
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.glassFloatingActionContent
import app.n_zik.android.components.glass.rememberGlassFloatingActionBackdrop
import app.n_zik.android.enums.AlbumsType
import app.n_zik.android.enums.FilterBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Album
import app.n_zik.android.models.Song
import app.n_zik.android.thumbnailShape
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.components.glass.GlassToolbarButtons
import app.n_zik.android.ui.components.tab.ItemSize
import app.n_zik.android.ui.components.tab.TabHeader
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.tab.toolbar.Randomizer
import app.n_zik.android.components.menu.album.AlbumItemMenu
import app.n_zik.android.components.menu.FilterMenu
import app.n_zik.android.components.menu.originLabelRes
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.ui.components.themed.HeaderInfo
import app.n_zik.android.ui.components.themed.MultiFloatingActionsContainer
import app.n_zik.android.ui.items.AlbumItem
import app.n_zik.android.ui.items.AlbumItemPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.utils.Preference.HOME_ALBUMS_FAVORITES_SORT_BY
import app.n_zik.android.utils.Preference.HOME_ALBUMS_FAVORITES_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_ALBUMS_LIBRARY_SORT_BY
import app.n_zik.android.utils.Preference.HOME_ON_DEVICE_ALBUMS_SORT_BY
import app.n_zik.android.utils.Preference.HOME_ALBUMS_LIBRARY_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_ON_DEVICE_ALBUMS_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_ALBUMS_DISLIKED_SORT_BY
import app.n_zik.android.utils.Preference.HOME_ALBUMS_DISLIKED_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_ALBUM_ITEM_SIZE
import app.n_zik.android.utils.albumTypeKey
import app.n_zik.android.utils.autoSyncToolbutton
import app.n_zik.android.utils.autosyncAlbumsKey
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.filterByKey
import app.n_zik.android.utils.importYTMLikedAlbums
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.utils.center
import app.n_zik.android.utils.color
import app.n_zik.android.utils.showFavoritesAlbumKey
import app.n_zik.android.utils.showOnDeviceAlbumsKey
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.core.ondevice.deviceLibraryEnabled
import app.n_zik.android.utils.excludeDislikedAlbumsKey
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.utils.homeAlbumsOrderKey
import app.n_zik.android.utils.showFloatingIconKey
import app.n_zik.android.utils.homeAlbumsFavoritesSortMenuOrderKey
import app.n_zik.android.utils.homeAlbumsLibrarySortMenuOrderKey
import app.n_zik.android.utils.homeAlbumsOnDeviceSortMenuOrderKey
import app.n_zik.android.utils.homeAlbumsDislikedSortMenuOrderKey
import org.json.JSONArray
import kotlinx.coroutines.launch

import app.n_zik.android.components.Sort
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SongShuffler
import app.n_zik.innertube.utils.from
import app.n_zik.android.ui.styling.onOverlay
import app.n_zik.android.ui.styling.overlay
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.player.addNextOffMain
import app.n_zik.android.utils.player.enqueueOffMain
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.n_zik.android.ui.components.themed.Enqueue
import app.n_zik.android.ui.components.themed.PlayNext
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.visibleHomeToolbarIds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeAlbums(
    navController: NavController,
    onAlbumClick: (Album) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    topContentInsetProvider: () -> Dp = { 0.dp },
    scrollContentInsetProvider: () -> Dp = topContentInsetProvider,
) {
    // Essentials
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current

    // Settings
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    var albumType by rememberPreference(albumTypeKey, AlbumsType.Library )
    val lazyGridState = rememberLazyGridState()
    val headerOffsetState = rememberSaveable { androidx.compose.runtime.mutableFloatStateOf(0f) }

    var items by key(albumType) { persistList<Album>( "home/albums/$albumType/items" ) }
    var itemsToFilter by key(albumType) { persistList<Album>( "home/albums/$albumType/itemsToFilter" ) }
    var filterBy by rememberPreference(filterByKey, FilterBy.All)
    val (colorPalette, typography) = LocalAppearance.current

    var itemsOnDisplay by key(albumType) { persistList<Album>( "home/albums/$albumType/on_display" ) }

    val search = Search.forHome()
    val itemSelector = ItemSelector<Album>()

    val sort = when( albumType ) {
        AlbumsType.Favorites -> Sort( HOME_ALBUMS_FAVORITES_SORT_BY, HOME_ALBUMS_FAVORITES_SORT_ORDER, homeAlbumsFavoritesSortMenuOrderKey, "alb_fav" )
        AlbumsType.OnDevice -> Sort( HOME_ON_DEVICE_ALBUMS_SORT_BY, HOME_ON_DEVICE_ALBUMS_SORT_ORDER, homeAlbumsOnDeviceSortMenuOrderKey, "alb_dev" )
        AlbumsType.Library -> Sort( HOME_ALBUMS_LIBRARY_SORT_BY, HOME_ALBUMS_LIBRARY_SORT_ORDER, homeAlbumsLibrarySortMenuOrderKey, "alb_lib" )
        AlbumsType.Disliked -> Sort( HOME_ALBUMS_DISLIKED_SORT_BY, HOME_ALBUMS_DISLIKED_SORT_ORDER, homeAlbumsDislikedSortMenuOrderKey, "alb_dis" )
    }
    val positionLock = remember( sort.sortOrder ) { PositionLock(sort.sortOrder) }

    val itemSize = ItemSize.init( HOME_ALBUM_ITEM_SIZE )

    fun actionTargets() = itemSelector.actionTargets(itemsOnDisplay, Album::id, Album::id, selectionOrder = true)
    val randomizer = object: Randomizer<Album> {
        override fun getItems(): List<Album> = actionTargets()
        override fun onClick(index: Int) {
            getItems().getOrNull(index)?.let(onAlbumClick)
        }
    }

    val scope = rememberCoroutineScope()
    val showOnDeviceTab by rememberPreference(showOnDeviceAlbumsKey, true)
    val showOnDevice = deviceLibraryEnabled() && showOnDeviceTab
    val onDeviceLabel = stringResource(R.string.library_on_device_title)
    val showFavoritesAlbum by rememberPreference(showFavoritesAlbumKey, true)
    val showDislikedAlbum by rememberPreference(excludeDislikedAlbumsKey, DislikeMode.Enabled)
    val homeAlbumsOrderPref by rememberPreference(homeAlbumsOrderKey, "")

    val favoritesLabel = stringResource(R.string.library_favorites_title)
    val allLabel = stringResource(R.string.common_option_all)
    val dislikedLabel = stringResource(R.string.library_disliked_title)
    val albumsDefaultOrder = listOf("all", "favorites", "disliked", "on_device")
    val labelMap = mapOf("on_device" to onDeviceLabel, "favorites" to favoritesLabel, "all" to allLabel, "disliked" to dislikedLabel)
    val typeMap = mapOf("on_device" to AlbumsType.OnDevice, "favorites" to AlbumsType.Favorites, "all" to AlbumsType.Library, "disliked" to AlbumsType.Disliked)
    val toggleMap = mapOf("on_device" to showOnDevice, "favorites" to showFavoritesAlbum, "all" to true, "disliked" to showDislikedAlbum.isEnabled)
    val buttonsList = remember(showOnDevice, onDeviceLabel, showDislikedAlbum, showFavoritesAlbum, homeAlbumsOrderPref, favoritesLabel, allLabel, dislikedLabel) {
        val order = try {
            val arr = JSONArray(homeAlbumsOrderPref)
            val parsed = (0 until arr.length()).map { arr.getString(it) }
            val valid = parsed.filter { it in albumsDefaultOrder }.toMutableList()
            for (id in albumsDefaultOrder) { if (id !in valid) valid.add(id) }
            valid
        } catch (_: Exception) { albumsDefaultOrder }
        order.mapNotNull { id ->
            if (toggleMap[id] == true) {
                val type = typeMap[id] ?: return@mapNotNull null
                val label = labelMap[id] ?: return@mapNotNull null
                type to label
            } else null
        }
    }

    LaunchedEffect(showFavoritesAlbum, showOnDevice, albumType) {
        if (!showOnDevice && albumType == AlbumsType.OnDevice) albumType = AlbumsType.Library
        if (!showFavoritesAlbum && albumType == AlbumsType.Favorites) albumType = AlbumsType.Library
    }

    val showOriginFilter = albumType != AlbumsType.OnDevice && isYouTubeSyncEnabled()
    val sourceFilter = if (showOriginFilter) filterBy else FilterBy.All

    val context = LocalContext.current
    val preview = LocalThemePreview.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val profile = getActiveProfile(context)
    var eligibleDeviceGroups by remember(profile) {
        mutableStateOf(if (preview == null) DeviceLibraryResults.current(profile)?.albumIds else emptySet())
    }
    LaunchedEffect(context, lifecycleOwner, albumType) {
        if (preview != null || albumType != AlbumsType.OnDevice) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            context.observeDeviceLibrary().collect { eligibleDeviceGroups = it.albumIds }
        }
    }

    var searchDataReady by remember(sort.sortBy, sort.sortOrder, albumType) { mutableStateOf(false) }
    val groupsReady = albumType != AlbumsType.OnDevice || eligibleDeviceGroups != null
    val forceShimmer = forceLoadingShimmerPreview()
    LaunchedEffect( sort.sortBy, sort.sortOrder, albumType ) {
        if (preview != null) {
            val demoItems = ThemePreviewDemo.albums
            itemsToFilter = sort.sortOrder.applyTo(when (sort.sortBy) {
                AlbumSortBy.Title -> demoItems.sortedBy { it.title.orEmpty() }
                AlbumSortBy.Artist -> demoItems.sortedBy { it.authorsText.orEmpty() }
                AlbumSortBy.Year -> demoItems.sortedBy { it.year.orEmpty() }
                AlbumSortBy.Songs, AlbumSortBy.Duration -> demoItems.sortedBy { ThemePreviewDemo.songsForAlbum(it.id).size }
                else -> demoItems
            })
            searchDataReady = true
            return@LaunchedEffect
        }
        when ( albumType ) {
            AlbumsType.Favorites -> Database.albumTable.sortBookmarked( sort.sortBy, sort.sortOrder )
            AlbumsType.Library -> Database.albumTable.sortInLibrary( sort.sortBy, sort.sortOrder )
            AlbumsType.OnDevice -> {
                val sorted = Database.albumTable.sortInLibrary(sort.sortBy, sort.sortOrder)
                if (sort.sortBy == AlbumSortBy.Title) sorted else kotlinx.coroutines.flow.combine(
                    sorted,
                    Database.albumTable.sortInLibrary(AlbumSortBy.Title, sort.sortOrder),
                ) { selected, alphabetical -> (selected + alphabetical).distinctBy { it.id } }
            }
            AlbumsType.Disliked -> Database.albumTable.allDisliked()
        }.flowOn(NzikDispatchers.DATA).collect {
            itemsToFilter = it
            searchDataReady = true
        }
    }
    val groups = eligibleDeviceGroups
    var baseReady by remember(searchDataReady, groupsReady, itemsToFilter, albumType, sourceFilter, groups) {
        mutableStateOf(false)
    }
    LaunchedEffect(searchDataReady, groupsReady, itemsToFilter, albumType, sourceFilter, groups) {
        if (!searchDataReady || !groupsReady) return@LaunchedEffect
        val type = albumType
        val source = sourceFilter
        val candidates = itemsToFilter
        items = withContext(NzikDispatchers.DATA) {
            candidates.filter { item ->
                if (type == AlbumsType.OnDevice) item.id in groups.orEmpty()
                else !isDeviceAlbumId(item.id) &&
                    (type == AlbumsType.Disliked || item.dislikedAt == null) &&
                    when (source) {
                        FilterBy.All -> true
                        FilterBy.YoutubeLibrary -> item.isYoutubeAlbum
                        else -> !item.isYoutubeAlbum
                    }
            }
        }
        baseReady = true
    }
    var completedSearchQuery by remember(baseReady, items) { mutableStateOf<String?>(null) }
    LaunchedEffect(baseReady, items, search.filterQuery) {
        if (!baseReady) return@LaunchedEffect
        val query = search.filterQuery
        val candidates = items
        itemsOnDisplay = withContext(NzikDispatchers.DATA) {
            candidates.filter { it.title?.contains(query, true) == true ||
                    it.year?.contains(query, true) == true ||
                    it.authorsText?.contains(query, true) == true
            }.distinctBy(Album::id)
        }
        completedSearchQuery = query
    }
    val gridReady = baseReady && completedSearchQuery == search.filterQuery
    val displayedGrid = rememberHomeFilteredGrid("albums", lazyGridState, gridReady, itemsOnDisplay) { it.id }
    val action = rememberHomeSongAction("HomeAlbum") {
        if (!gridReady || preview != null) return@rememberHomeSongAction null
        val deviceOnly = albumType == AlbumsType.OnDevice
        val eligibleIds = actionTargets().map { it.id }.filter { isDeviceAlbumId(it) == deviceOnly }
        if (itemSelector.isActive && eligibleIds.isEmpty()) return@rememberHomeSongAction null
        val load: suspend () -> List<Song> = {
            val groups = if (deviceOnly) context.scanDeviceLibrary().albumSongIds else emptyMap()
            loadHomeParentSongs(eligibleIds, Database.songAlbumMapTable::songsForHomeAlbums) { id, song ->
                song.isLocal == deviceOnly && (!deviceOnly || song.id in groups[id].orEmpty())
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
            Timber.tag("HomeAlbum").e(error, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        itemSelector = itemSelector,
    )
    val exportDialog = ExportSongsToCSVDialog.deferred(
        "Albums",
        action::snapshot,
        selectionActive = { itemSelector.isActive },
    )
    val eligibleSelection by remember(gridReady, albumType) {
        derivedStateOf {
            gridReady && itemSelector.isNotEmpty() &&
                actionTargets().any { isDeviceAlbumId(it.id) == (albumType == AlbumsType.OnDevice) }
        }
    }
    val hasEligibleSelection = { eligibleSelection }

    SearchEmptyFeedback(
        query = search.filterQuery,
        empty = itemsOnDisplay.isEmpty(),
        loading = !gridReady
    )


    val sync = autoSyncToolbutton(R.string.sync_albums, autosyncAlbumsKey) { scope.launch { importYTMLikedAlbums(force = true) } }

    // Full offline mode (spec-full-offline-mode): the network sync action is removed.
    val offlineMode by OfflineMode.rememberEnabled()

    val attemptedCovers = remember(profile) { HashSet<String>() }
    val observeListening = preview == null &&
        (sort.sortBy == AlbumSortBy.PlayCount || sort.sortBy == AlbumSortBy.ListeningTime)
    val listeningTotals by remember(observeListening, profile) {
        if (observeListening) Database.eventTable.getAlbumListeningTotals()
            .map { rows -> rows.associateBy { it.albumId } }
        else flowOf(emptyMap())
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    val doAutoSync by rememberPreference(autosyncAlbumsKey, false)
    var justSynced by rememberSaveable { mutableStateOf(!doAutoSync) }


    LaunchedEffect(justSynced, doAutoSync) {
        if (preview == null && !justSynced && importYTMLikedAlbums())
            justSynced = true
    }

    val floatingBackdrop = rememberGlassFloatingActionBackdrop()

    DeviceLibraryPullRefresh(albumType == AlbumsType.OnDevice) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxHeight()
                .fillMaxWidth()
        ) {

            Column( Modifier.fillMaxSize() ) {

                val toolbarTab = when (albumType) {
                    AlbumsType.OnDevice -> "dev"
                    AlbumsType.Favorites -> "favs"
                    AlbumsType.Disliked -> "disliked"
                    else -> "all"
                }
                val toolbarOrder = HomeAlbumsToolbarSettingsDialog.portableLists().getValue(toolbarTab).visibleHomeToolbarIds()

                val toolbarButtons = buildList<Button> {
                    toolbarOrder.forEach { id ->
                        when(id) {
                            "sort" -> add(sort)
                            "position_lock" -> { if (sort.sortBy == AlbumSortBy.Custom) add(positionLock) }
                            "sync" -> { if (isYouTubeSyncEnabled() && !offlineMode) add(sync) }
                            "search" -> add(search)
                            "randomizer" -> add(randomizer)
                            "shuffle" -> add(itemSelector.guard(shuffle, hasEligibleSelection))
                            "item_selector" -> add(itemSelector)
                            "play_next" -> add(itemSelector.guard(playNext, hasEligibleSelection))
                            "enqueue" -> add(itemSelector.guard(enqueue, hasEligibleSelection))
                            "add_to_playlist" -> add(itemSelector.guard(addToPlaylist, hasEligibleSelection))
                            "export_dialog" -> add(itemSelector.guard(exportDialog, hasEligibleSelection))
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
                            TabHeader(R.string.library_albums_title) {
                                HeaderInfo(items.size.toString(), R.drawable.album)
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
                            currentValue = albumType,
                            onValueUpdate = { albumType = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AnimatedVisibility(
                        visible = showOriginFilter,
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
                            contentPadding = rememberPageChromeContentPadding(
                                topInsetProvider = scrollContentInsetProvider,
                                base = PaddingValues(bottom = Dimensions.bottomSpacer),
                                extraTopProvider = { with(headerDensity) { headerHeight.toDp() } },
                            ),
                            modifier = Modifier.background( colorPalette().background0 ).fillMaxSize()
                        ) {
                            val albums = displayedGrid.orEmpty()
                            if (displayedGrid == null) items(HomePlaceholderCount, key = { "loading_album_$it" }) {
                                AlbumItemPlaceholder(itemSize.size.dp, alternative = true, metadataLines = 2)
                            }
                            items(
                                items = albums,
                                key = { it.id }
                            ) { album ->
                                if (forceShimmer) {
                                    val year = album.year?.takeIf { it.isNotBlank() }
                                    val metadata = listOfNotNull(album.authorsText?.takeIf { it.isNotBlank() }, year)
                                    AlbumItemPlaceholder(
                                        itemSize.size.dp, alternative = true,
                                        title = album.title, metadataLines = metadata.size,
                                        metadata = metadata, finalMetadataIsYear = year != null,
                                    )
                                    return@items
                                }
                                if (preview == null && album.thumbnailUrl == null) LaunchedEffect(album.id) {
                                    val id = album.id
                                    if (attemptedCovers.add(id)) Database.asyncTransaction {
                                        songAlbumMapTable.mostCommonCover(id)?.let { url ->
                                            albumTable.fillMissingCover(id, url)
                                        }
                                    }
                                }
                                ReorderableItem(
                                    reorderableLazyGridState,
                                    key = album.id
                                ) { isDraggingItem ->
                                    Box {
                                        if (gridReady && !positionLock.isLocked() && sort.sortBy == AlbumSortBy.Custom && sort.sortOrder == SortOrder.Ascending) {
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
                                                                currentItems.forEachIndexed { index, album ->
                                                                    albumTable.updatePosition(album.id, index)
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

                                        AlbumItem(
                                            alternative = true,
                                            showAuthors = true,
                                            album = album,
                                            thumbnailSizeDp = itemSize.size.dp,
                                            thumbnailSizePx = itemSize.size.px,
                                            bookmarkState = when {
                                                album.dislikedAt != null -> false
                                                album.bookmarkedAt != null -> true
                                                else -> null
                                            },
                                            observeBookmarkState = false,
                                            modifier = Modifier
                                                .clip(uiRoundnessShape()).combinedClickable(
                                                    enabled = gridReady,
                                                    onLongClick = {
                                                        menuState.display {
                                                            AlbumItemMenu(
                                                                navController = navController,
                                                                album = album,
                                                                binder = binder
                                                            ).MenuComponent()
                                                        }
                                                    },
                                                    onClick = {
                                                        if( itemSelector.isActive ) {
                                                            itemSelector.toggle(album, Album::id)
                                                        } else {
                                                            search.hideIfEmpty()
                                                            onAlbumClick( album )
                                                        }
                                                    }
                                                )
                                                .clip(thumbnailShape()),
                                            disableScrollingText = disableScrollingText,
                                            isYoutubeAlbum = album.isYoutubeAlbum,
                                            thumbnailOverlay = {
                                                if( itemSelector.isActive ) {
                                                    key(itemSelector.size) {
                                                        val checked = itemSelector.isSelected(album, Album::id)
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
                                                } else if (sort.sortBy == AlbumSortBy.PlayCount) {
                                                    val playCount = listeningTotals[album.id]?.playCount ?: 0
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(thumbnailShape())
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
                                                } else if (sort.sortBy == AlbumSortBy.ListeningTime) {
                                                    val playTime = listeningTotals[album.id]?.totalPlayTimeMs ?: 0L
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(thumbnailShape())
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

                if ( showFloatingIcon )
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








