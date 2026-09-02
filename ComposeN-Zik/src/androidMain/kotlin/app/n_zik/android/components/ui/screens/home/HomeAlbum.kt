@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.uiRoundnessShape

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.draw.scale
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.ReorderableItem
import app.kreate.android.themed.rimusic.component.playlist.PositionLock
import app.it.fast4x.rimusic.enums.AlbumSortBy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.it.fast4x.compose.persist.persistList
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.AlbumsType
import app.it.fast4x.rimusic.enums.FilterBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.ui.components.ButtonsRow
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.ItemSize
import app.it.fast4x.rimusic.ui.components.tab.TabHeader
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Randomizer
import app.n_zik.android.components.menu.album.AlbumItemMenu
import app.n_zik.android.components.menu.FilterMenu
import app.n_zik.android.components.tab.ItemSelector
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.HeaderInfo
import app.it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.utils.Preference.HOME_ALBUMS_FAVORITES_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_ALBUMS_FAVORITES_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_ALBUMS_LIBRARY_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_ALBUMS_LIBRARY_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_ALBUM_ITEM_SIZE
import app.it.fast4x.rimusic.utils.albumTypeKey
import app.it.fast4x.rimusic.utils.autoSyncToolbutton
import app.it.fast4x.rimusic.utils.autosyncKey
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.filterByKey
import app.it.fast4x.rimusic.utils.importYTMLikedAlbums
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.formatAsTime
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.showFavoritesAlbumKey
import app.it.fast4x.rimusic.utils.homeAlbumsOrderKey
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import app.it.fast4x.rimusic.utils.homeAlbumsLibraryToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeAlbumsFavoritesToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeAlbumsFavoritesSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeAlbumsLibrarySortMenuOrderKey
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import app.n_zik.android.components.Sort
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SongShuffler
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.albumPage
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import app.kreate.android.me.knighthat.utils.PropUtils
import app.it.fast4x.rimusic.utils.parseArtists
import app.it.fast4x.rimusic.models.SongAlbumMap
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.ui.styling.overlay
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.enqueue
import kotlinx.coroutines.CoroutineScope
import app.n_zik.android.appContext
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.dialog.common.RetrySyncDialog
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsToolbarSettingsDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import app.n_zik.android.components.AppPullToRefreshBox
import androidx.core.content.ContextCompat
import java.util.ArrayList
import android.content.Intent
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
    onSettingsClick: () -> Unit
) {
    // Essentials
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    val lazyGridState = rememberLazyGridState()

    // Settings
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    var albumType by rememberPreference(albumTypeKey, AlbumsType.Library )

    var items by persistList<Album>( "home/albums/items" )
    var itemsToFilter by persistList<Album>( "home/albums/itemsToFilter" )
    var filterBy by rememberPreference(filterByKey, FilterBy.All)
    val (colorPalette, typography) = LocalAppearance.current

    var itemsOnDisplay by persistList<Album>( "home/albums/on_display" )

    val search = Search(lazyGridState)
    val itemSelector = ItemSelector<Album>()

    val sort = when( albumType ) {
        AlbumsType.Favorites -> Sort( HOME_ALBUMS_FAVORITES_SORT_BY, HOME_ALBUMS_FAVORITES_SORT_ORDER, homeAlbumsFavoritesSortMenuOrderKey, "alb_fav" )
        AlbumsType.Library -> Sort( HOME_ALBUMS_LIBRARY_SORT_BY, HOME_ALBUMS_LIBRARY_SORT_ORDER, homeAlbumsLibrarySortMenuOrderKey, "alb_lib" )
    }
    val positionLock = remember( sort.sortOrder ) { PositionLock(sort.sortOrder) }

    val itemSize = ItemSize.init( HOME_ALBUM_ITEM_SIZE )

    val randomizer = object: Randomizer<Album> {
        override fun getItems(): List<Album> = itemSelector.ifEmpty { itemsOnDisplay }
        override fun onClick(index: Int) {
            val items = itemSelector.ifEmpty { itemsOnDisplay }
            onAlbumClick( items[index] )
        }
    }

    val scope = rememberCoroutineScope()
    suspend fun getSelectedSongs(): List<Song> = withContext(Dispatchers.IO) {
        val selected = itemSelector.ifEmpty { itemsOnDisplay }
        val seen = HashSet<String>()
        val result = ArrayList<Song>()
        for( album in selected ) {
            for( song in Database.songAlbumMapTable.allSongsOfDirect( album.id ) ) {
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
            Timber.tag("HomeAlbum").e(e, "Failed to load selected songs")
        }
    }
    fun getSelectedMediaItems(): List<androidx.media3.common.MediaItem> =
        selectedSongs.map { it.asMediaItem }

    val shuffle = SongShuffler { selectedSongs }
    val playNext = PlayNext {
        scope.launch {
            val mediaItems = withContext(Dispatchers.IO) { getSelectedSongs().map { it.asMediaItem } }
            binder?.player?.addNext( mediaItems, appContext() )
            itemSelector.isActive = false
        }
    }
    val enqueue = Enqueue {
        scope.launch {
            val mediaItems = withContext(Dispatchers.IO) { getSelectedSongs().map { it.asMediaItem } }
            binder?.player?.enqueue( mediaItems, appContext() )
            itemSelector.isActive = false
        }
    }
    val addToPlaylist = PlaylistsMenu.init(
        navController = navController,
        mediaItems = { _ -> getSelectedMediaItems() },
        onFailure = { throwable, preview ->
            Timber.tag("HomeAlbum").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        finalAction = { itemSelector.isActive = false }
    )
    val exportDialog = ExportSongsToCSVDialog(
        playlistName = "Albums",
        songs = { selectedSongs }
    )

    val showFavoritesAlbum by rememberPreference(showFavoritesAlbumKey, true)
    val homeAlbumsOrderPref by rememberPreference(homeAlbumsOrderKey, "")

    val favoritesLabel = stringResource(R.string.favorites)
    val allLabel = stringResource(R.string.all)
    val albumsDefaultOrder = listOf("all", "favorites")
    val labelMap = mapOf("favorites" to favoritesLabel, "all" to allLabel)
    val typeMap = mapOf("favorites" to AlbumsType.Favorites, "all" to AlbumsType.Library)
    val toggleMap = mapOf("favorites" to showFavoritesAlbum, "all" to true)
    val buttonsList = remember(showFavoritesAlbum, homeAlbumsOrderPref, favoritesLabel, allLabel) {
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

    LaunchedEffect(showFavoritesAlbum) {
        if (!showFavoritesAlbum && albumType == AlbumsType.Favorites) albumType = AlbumsType.Library
    }

    if (!isYouTubeSyncEnabled()) {
        filterBy = FilterBy.All
    }

    LaunchedEffect( sort.sortBy, sort.sortOrder, albumType ) {
        when ( albumType ) {
            AlbumsType.Favorites -> Database.albumTable.sortBookmarked( sort.sortBy, sort.sortOrder )
            AlbumsType.Library -> Database.albumTable.sortInLibrary( sort.sortBy, sort.sortOrder )
        }.collect { itemsToFilter = it }
    }
    LaunchedEffect( Unit, itemsToFilter, filterBy ) {
        items = when(filterBy) {
            FilterBy.All -> itemsToFilter
            FilterBy.YoutubeLibrary -> itemsToFilter.filter { it.isYoutubeAlbum }
            FilterBy.Local -> itemsToFilter.filterNot { it.isYoutubeAlbum }
        }

    }
    LaunchedEffect( items, search.inputValue ) {
        itemsOnDisplay = items.filter {
            it.title?.contains( search.inputValue, true) ?: false
                    || it.year?.contains( search.inputValue, true) ?: false
                    || it.authorsText?.contains( search.inputValue, true) ?: false
        }
    }

    LaunchedEffect( Unit ) {
        // TODO Convert to fetch from the internet
        Database.asyncTransaction {
            // Only occurs when album doesn't have thumbnailUrl assigned
            items.filter { it.thumbnailUrl == null }
                 .forEach { album ->
                     /**
                      * Topology:
                      *
                      * Return the most frequently occurring [Song.thumbnailUrl]
                      * among all songs of this album.
                      *
                      * Explanation:
                      *
                      * [Song.thumbnailUrl] can be changed by user.
                      * If 1 song has its thumbnail changed, the result
                      * remains the same because all others have the same url.
                      *
                      * Even when most changed to different urls, it only needs
                      * 2 songs to have the same [Song.thumbnailUrl] to return
                      * the same result.
                      */
                     val coverUrl = songAlbumMapTable.allSongsOfDirect( album.id )
                                          .groupingBy( Song::thumbnailUrl )
                                          .eachCount()
                                          .maxByOrNull { it.value }
                                          ?.key
                     coverUrl?.let { albumTable.updateCover( album.id, it ) }
                 }
        }
    }

    val sync = autoSyncToolbutton(R.string.autosync_albums)

    val doAutoSync by rememberPreference(autosyncKey, false)
    var justSynced by rememberSaveable { mutableStateOf(!doAutoSync) }


    var refreshing by remember { mutableStateOf(false) }

    fun refresh(itemsToRefresh: List<Album>? = null) {
        if (refreshing || HomeSyncState.isSyncingAlbums) {
            Toaster.e(appContext().getString(R.string.already_syncing))
            return
        }
        val targetItems = itemsToRefresh ?: itemsOnDisplay
        val ids = ArrayList(targetItems.map { it.id })
        if (ids.isEmpty()) return
        
        val intent = Intent(appContext(), HomeSyncService::class.java).apply {
            action = HomeSyncService.ACTION_SYNC_ALBUMS
            putStringArrayListExtra(HomeSyncService.EXTRA_IDS, ids)
        }
        try {
            ContextCompat.startForegroundService(appContext(), intent)
        } catch (e: Exception) {
            Timber.tag("HomeAlbum").e(e, "Failed to start HomeSyncService")
            Toaster.e("Failed to start sync service")
        }
    }

    // START: Import YTM subscribed channels
    LaunchedEffect(justSynced, doAutoSync) {
        if (!justSynced && importYTMLikedAlbums())
            justSynced = true
    }

    val retryDialog = RetrySyncDialog(
        failedCount = HomeSyncState.failedAlbumsList.size,
        onRetry = { 
            val items = HomeSyncState.failedAlbumsList
            HomeSyncState.failedAlbumsList = emptyList()
            refresh(items) 
        }
    )
    retryDialog.Render()
    LaunchedEffect(HomeSyncState.failedAlbumsList) {
        if (HomeSyncState.failedAlbumsList.isNotEmpty()) retryDialog.showDialog()
    }

    AppPullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refresh() }
    ) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxHeight()
                .fillMaxWidth()
        ) {

            Column( Modifier.fillMaxSize() ) {

                val homeAlbumsToolbarOrderPrefLibrary by rememberPreference(homeAlbumsLibraryToolbarOrderKey, "")
                val homeAlbumsToolbarOrderPrefFavorites by rememberPreference(homeAlbumsFavoritesToolbarOrderKey, "")

                val currentToolbarOrderPref = when (albumType) {
                    AlbumsType.Favorites -> homeAlbumsToolbarOrderPrefFavorites
                    else -> homeAlbumsToolbarOrderPrefLibrary
                }

                val toolbarButtons = remember { mutableStateListOf<Button>() }

                LaunchedEffect(sort.sortBy, sort.sortOrder, currentToolbarOrderPref) {
                    val defaultToolbarOrder = HomeAlbumsToolbarSettingsDialog.allButtonIds
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
                            "position_lock" -> { if (sort.sortBy == AlbumSortBy.Custom) toolbarButtons.add(positionLock) }
                            "sync" -> { if (isYouTubeSyncEnabled()) toolbarButtons.add(sync) }
                            "search" -> toolbarButtons.add(search)
                            "randomizer" -> toolbarButtons.add(randomizer)
                            "shuffle" -> toolbarButtons.add(shuffle)
                            "item_selector" -> toolbarButtons.add(itemSelector)
                            "play_next" -> toolbarButtons.add(playNext)
                            "enqueue" -> toolbarButtons.add(enqueue)
                            "add_to_playlist" -> toolbarButtons.add(addToPlaylist)
                            "export_dialog" -> toolbarButtons.add(exportDialog)
                            "item_size" -> toolbarButtons.add(itemSize)
                        }
                    }
                }


                val hapticFeedback = LocalHapticFeedback.current
                val reorderableLazyGridState = rememberReorderableLazyGridState(
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



                Column {
                    TabHeader(R.string.albums) {
                        HeaderInfo(items.size.toString(), R.drawable.album)
                    }
                    exportDialog.Render()
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
                                currentValue = albumType,
                                onValueUpdate = { albumType = it },
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            if (isYouTubeSyncEnabled()) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                ) {
                                    BasicText(
                                        text = when (filterBy) {
                                            FilterBy.All -> stringResource(R.string.all)
                                            FilterBy.Local -> stringResource(R.string.on_device)
                                            FilterBy.YoutubeLibrary -> stringResource(R.string.ytm_library)
                                        },
                                        style = typography.xs.semiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .padding(end = 5.dp)
                                            .clip(uiRoundnessShape()).clickable {
                                                menuState.display {
                                                    FilterMenu(
                                                        title = stringResource(R.string.filter_by),
                                                        onDismiss = menuState::hide,
                                                        onAll = { filterBy = FilterBy.All },
                                                        onYoutubeLibrary = {
                                                            filterBy = FilterBy.YoutubeLibrary
                                                        },
                                                        onLocal = { filterBy = FilterBy.Local }
                                                    )
                                                }

                                            }
                                    )
                                    HeaderIconButton(
                                        icon = R.drawable.playlist,
                                        color = colorPalette.text,
                                        onClick = {},
                                        modifier = Modifier
                                            .offset(0.dp, 2.5.dp)
                                            .clip(uiRoundnessShape()).clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {}
                                            )
                                    )
                                }
                            }
                        }
                    }
                    if (HomeSyncState.isSyncingAlbums) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                BasicText(
                                    text = stringResource(R.string.syncing_item, HomeSyncState.albumSyncCurrentName),
                                    style = typography.xxs.semiBold.copy(color = colorPalette.textSecondary),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp).basicMarquee(iterations = Int.MAX_VALUE)
                                )
                                Row {
                                    BasicText(
                                        text = stringResource(R.string.syncing_progress, HomeSyncState.albumSyncCurrentIndex, HomeSyncState.albumSyncTotal),
                                        style = typography.xxs.semiBold.copy(color = colorPalette.textSecondary)
                                    )
                                    if (HomeSyncState.albumSyncFailed > 0) {
                                        BasicText(
                                            text = " " + stringResource(R.string.syncing_failed, HomeSyncState.albumSyncFailed),
                                            style = typography.xxs.semiBold.copy(color = colorPalette.red)
                                        )
                                    }
                                }
                            }
                            LinearWavyProgressIndicator(
                                progress = { HomeSyncState.albumSyncProgress },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                color = colorPalette.accent,
                                trackColor = colorPalette.background2
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Adaptive( itemSize.size.dp ),
                        modifier = Modifier.background( colorPalette().background0 ).fillMaxSize(),
                        contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer )
                    ) {
                    items(
                        items = itemsOnDisplay.distinctBy { it.id },
                        key = { it.id }
                    ) { album ->
                        ReorderableItem(
                            reorderableLazyGridState,
                            key = album.id
                        ) { isDraggingItem ->
                            Box(modifier = Modifier) {
                                if (!positionLock.isLocked() && sort.sortBy == AlbumSortBy.Custom && sort.sortOrder == SortOrder.Ascending) {
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

                                var position by remember {
                            mutableIntStateOf(0)
                        }
                        val context = LocalContext.current

                        AlbumItem(
                            alternative = true,
                            showAuthors = true,
                            album = album,
                            thumbnailSizeDp = itemSize.size.dp,
                            thumbnailSizePx = itemSize.size.px,
                            modifier = Modifier
                                .clip(uiRoundnessShape()).combinedClickable(

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
                                            if( album in itemSelector ) itemSelector.remove( album )
                                            else itemSelector.add( album )
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
                                        Icon(
                                            painter = painterResource(if (album in itemSelector) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                            contentDescription = null,
                                            tint = if (album in itemSelector) colorPalette().accent else colorPalette().text,
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .size(24.dp)
                                                .align(Alignment.TopStart)
                                                .zIndex(2f)
                                        )
                                    }
                                } else if (sort.sortBy == AlbumSortBy.PlayCount) {
                                    val playCount by Database.eventTable.getAlbumPlayCount(album.id).collectAsStateWithLifecycle(initialValue = 0)
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
                                    val playTime by Database.eventTable.getAlbumTotalPlayTime(album.id).collectAsStateWithLifecycle(initialValue = 0L)
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

                if (itemsOnDisplay.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(bottom = 47.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = stringResource(R.string.no_items),
                            style = typography.m.semiBold.copy(
                                color = colorPalette().textSecondary
                            )
                        )
                    }
                }
            }
            }

            FloatingActionsContainerWithScrollToTop( lazyGridState )

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
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








