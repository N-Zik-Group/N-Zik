@file:Suppress("OPT_IN_USAGE", "OPT_IN_USAGE_ERROR")
package app.n_zik.android.components.ui.screens.home

import androidx.compose.ui.draw.clip

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
import app.it.fast4x.rimusic.enums.ArtistSortBy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.it.fast4x.compose.persist.persistList
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import it.fast4x.innertube.YtMusic
import app.n_zik.android.core.database.Database
import app.n_zik.android.colorPalette
import app.n_zik.android.appContext
import app.n_zik.android.LocalPlayerServiceBinder
import kotlinx.coroutines.flow.first
import app.it.fast4x.rimusic.ui.components.themed.Enqueue
import app.it.fast4x.rimusic.ui.components.themed.PlayNext
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.enums.ArtistsType
import app.it.fast4x.rimusic.enums.FilterBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.ui.components.ButtonsRow
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.ItemSize
import app.it.fast4x.rimusic.ui.components.tab.TabHeader
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Randomizer
import app.n_zik.android.components.menu.FilterMenu
import app.n_zik.android.components.tab.ItemSelector
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import app.it.fast4x.rimusic.ui.components.themed.HeaderInfo
import app.it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import app.it.fast4x.rimusic.ui.items.ArtistItem
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.ui.styling.overlay
import app.it.fast4x.rimusic.utils.Preference.HOME_ARTISTS_FAVORITES_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_ARTISTS_FAVORITES_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_ARTISTS_LIBRARY_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_ARTISTS_LIBRARY_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_ARTIST_ITEM_SIZE
import app.it.fast4x.rimusic.utils.artistTypeKey
import app.it.fast4x.rimusic.utils.autoSyncToolbutton
import app.it.fast4x.rimusic.utils.autosyncKey
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.filterByKey
import app.it.fast4x.rimusic.utils.importYTMSubscribedChannels
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.formatAsTime
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.showFavoritesArtistKey
import app.it.fast4x.rimusic.utils.homeArtistsOrderKey
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import app.it.fast4x.rimusic.utils.homeArtistsLibraryToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeArtistsFavoritesToolbarOrderKey
import app.it.fast4x.rimusic.utils.homeArtistsFavoritesSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeArtistsLibrarySortMenuOrderKey
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.components.Sort
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SongShuffler
import app.kreate.android.me.knighthat.utils.PropUtils
import app.n_zik.android.components.menu.artist.LocalArtistItemMenu
import kotlinx.coroutines.CoroutineScope
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.dialog.common.RetrySyncDialog
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsToolbarSettingsDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.n_zik.android.components.AppPullToRefreshBox
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import java.util.ArrayList
import android.content.Intent
import kotlinx.coroutines.runBlocking
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
    onSettingsClick: () -> Unit
) {
    // Essentials
    val lazyGridState = rememberLazyGridState()
    val (colorPalette, typography) = LocalAppearance.current
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    val coroutineScope = rememberCoroutineScope()

    // Settings
    var artistType by rememberPreference(artistTypeKey, ArtistsType.Library )
    var filterBy by rememberPreference(filterByKey, FilterBy.All)


    var items by persistList<Artist>( "home/artists/items")
    var itemsToFilter by persistList<Artist>( "home/artists/itemsToFilter" )

    var itemsOnDisplay by persistList<Artist>( "home/artists/on_display" )

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val search = Search(lazyGridState)
    val itemSelector = ItemSelector<Artist>()

    val sort = when( artistType ) {
        ArtistsType.Favorites -> Sort( HOME_ARTISTS_FAVORITES_SORT_BY, HOME_ARTISTS_FAVORITES_SORT_ORDER, homeArtistsFavoritesSortMenuOrderKey, "art_fav" )
        ArtistsType.Library -> Sort( HOME_ARTISTS_LIBRARY_SORT_BY, HOME_ARTISTS_LIBRARY_SORT_ORDER, homeArtistsLibrarySortMenuOrderKey, "art_lib" )
    }
    val positionLock = remember( sort.sortOrder ) { PositionLock(sort.sortOrder) }

    val itemSize = ItemSize.init( HOME_ARTIST_ITEM_SIZE )

    val randomizer = object: Randomizer<Artist> {
        override fun getItems(): List<Artist> = itemSelector.ifEmpty { itemsOnDisplay }
        override fun onClick(index: Int) {
            val items = itemSelector.ifEmpty { itemsOnDisplay }
            onArtistClick( items[index] )
        }
    }

    suspend fun getSelectedSongs(): List<Song> = withContext(Dispatchers.IO) {
        val selected = itemSelector.ifEmpty { itemsOnDisplay }
        val seen = HashSet<String>()
        val result = ArrayList<Song>()
        for( artist in selected ) {
            val songs = Database.songArtistMapTable.allSongsBy( artist.id ).first()
            for( song in songs ) {
                if( seen.add( song.id ) ) result.add( song )
            }
        }
        result
    }
    fun getSelectedMediaItems(): List<androidx.media3.common.MediaItem> =
        runBlocking(Dispatchers.IO) { getSelectedSongs().map { it.asMediaItem } }

    val shuffle = SongShuffler {
        runBlocking(Dispatchers.IO) { getSelectedSongs() }
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
            Timber.tag("HomeArtist").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
        },
        finalAction = { itemSelector.isActive = false }
    )
    val exportDialog = ExportSongsToCSVDialog(
        playlistName = "Artists",
        songs = { runBlocking(Dispatchers.IO) { getSelectedSongs() } }
    )

    val showFavoritesArtist by rememberPreference(showFavoritesArtistKey, true)
    val homeArtistsOrderPref by rememberPreference(homeArtistsOrderKey, "")

    val favoritesLabel = stringResource(R.string.favorites)
    val allLabel = stringResource(R.string.all)
    val artistsDefaultOrder = listOf("all", "favorites")
    val labelMap = mapOf("favorites" to favoritesLabel, "all" to allLabel)
    val typeMap = mapOf("favorites" to ArtistsType.Favorites, "all" to ArtistsType.Library)
    val toggleMap = mapOf("favorites" to showFavoritesArtist, "all" to true)
    val buttonsList = remember(showFavoritesArtist, homeArtistsOrderPref, favoritesLabel, allLabel) {
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

    LaunchedEffect(showFavoritesArtist) {
        if (!showFavoritesArtist && artistType == ArtistsType.Favorites) artistType = ArtistsType.Library
    }

    if (!isYouTubeSyncEnabled()) {
        filterBy = FilterBy.All
    }

    LaunchedEffect( Unit, sort.sortBy, sort.sortOrder, artistType ) {
        when( artistType ) {
            ArtistsType.Favorites -> Database.artistTable.sortFollowing( sort.sortBy, sort.sortOrder )
            ArtistsType.Library -> Database.artistTable.sortInLibrary( sort.sortBy, sort.sortOrder )
        }.collect { itemsToFilter = it }
    }
    LaunchedEffect( Unit, itemsToFilter, filterBy ) {
        items = when(filterBy) {
            FilterBy.All -> itemsToFilter
            FilterBy.YoutubeLibrary -> itemsToFilter.filter { it.isYoutubeArtist }
            FilterBy.Local -> itemsToFilter.filterNot { it.isYoutubeArtist }
        }

    }
    LaunchedEffect( items, search.inputValue ) {
        itemsOnDisplay = items.filter {
            it.name?.contains( search.inputValue, true ) ?: false
        }
    }
    if (items.any{it.thumbnailUrl == null}) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                items.filter { it.thumbnailUrl == null }.forEach { artist ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val artistThumbnail = YtMusic.getArtistPage(artist.id.removePrefix(MODIFIED_PREFIX)).getOrNull()?.artist?.thumbnail?.url
                        Database.asyncTransaction {
                            artistTable.update( artist.copy(thumbnailUrl = artistThumbnail) )
                        }
                    }
                }
            }
        }
    }

    val sync = autoSyncToolbutton(R.string.autosync_channels)

    val doAutoSync by rememberPreference(autosyncKey, false)
    var justSynced by rememberSaveable { mutableStateOf(!doAutoSync) }


    var refreshing by remember { mutableStateOf(false) }

    fun refresh(itemsToRefresh: List<Artist>? = null) {
        if (refreshing || HomeSyncState.isSyncingArtists) {
            Toaster.e(appContext().getString(R.string.already_syncing))
            return
        }
        val targetItems = itemsToRefresh ?: itemsOnDisplay
        val ids = ArrayList(targetItems.map { it.id })
        if (ids.isEmpty()) return
        
        val intent = Intent(appContext(), HomeSyncService::class.java).apply {
            action = HomeSyncService.ACTION_SYNC_ARTISTS
            putStringArrayListExtra(HomeSyncService.EXTRA_IDS, ids)
        }
        try {
            ContextCompat.startForegroundService(appContext(), intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start HomeSyncService")
            Toaster.e("Failed to start sync service")
        }
    }

    // START: Import YTM subscribed channels
    LaunchedEffect(justSynced, doAutoSync) {
        if (!justSynced && importYTMSubscribedChannels())
                justSynced = true
    }

    val retryDialog = RetrySyncDialog(
        failedCount = HomeSyncState.failedArtistsList.size,
        onRetry = { 
            val items = HomeSyncState.failedArtistsList
            HomeSyncState.failedArtistsList = emptyList()
            refresh(items) 
        }
    )
    retryDialog.Render()
    LaunchedEffect(HomeSyncState.failedArtistsList) {
        if (HomeSyncState.failedArtistsList.isNotEmpty()) retryDialog.showDialog()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    AppPullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refresh() }
    ) {
        Box (
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxHeight()
                .fillMaxWidth()
        ) {

            Column( Modifier.fillMaxSize() ) {

                val homeArtistsToolbarOrderPrefLibrary by rememberPreference(homeArtistsLibraryToolbarOrderKey, "")
                val homeArtistsToolbarOrderPrefFavorites by rememberPreference(homeArtistsFavoritesToolbarOrderKey, "")

                val currentToolbarOrderPref = when (artistType) {
                    ArtistsType.Favorites -> homeArtistsToolbarOrderPrefFavorites
                    else -> homeArtistsToolbarOrderPrefLibrary
                }

                val toolbarButtons = remember { mutableStateListOf<Button>() }

                LaunchedEffect(sort.sortBy, sort.sortOrder, currentToolbarOrderPref) {
                    val defaultToolbarOrder = HomeArtistsToolbarSettingsDialog.allButtonIds
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
                            "position_lock" -> { if (sort.sortBy == ArtistSortBy.Custom) toolbarButtons.add(positionLock) }
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



                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Adaptive( itemSize.size.dp ),
                    modifier = Modifier.background( colorPalette().background0 )
                                       .fillMaxSize(),
                    contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer )
                ) {
                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Column {
                            TabHeader( R.string.artists ) {
                                HeaderInfo(items.size.toString(), R.drawable.people)
                            }
                            exportDialog.Render()
                            TabToolBar.Buttons( toolbarButtons )
                            search.SearchBar( this )
                        }
                    }

                    item(
                        key = "separator",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
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
                                    currentValue = artistType,
                                    onValueUpdate = { artistType = it },
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
                                                .clip(uiRoundnessShape()).combinedClickable(
                                                    onClick = {
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
                                        )
                                        HeaderIconButton(
                                            icon = R.drawable.playlist,
                                            color = colorPalette.text,
                                            onClick = {},
                                            modifier = Modifier
                                                .offset(0.dp, 2.5.dp)
                                                .clip(uiRoundnessShape()).combinedClickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {}
                                                )
                                        )
                                    }
                                }
                            }
                        }
                        if (HomeSyncState.isSyncingArtists) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    BasicText(
                                        text = stringResource(R.string.syncing_item, HomeSyncState.artistSyncCurrentName),
                                        style = typography.xxs.semiBold.copy(color = colorPalette.textSecondary),
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp).basicMarquee(iterations = Int.MAX_VALUE)
                                    )
                                    Row {
                                        BasicText(
                                            text = stringResource(R.string.syncing_progress, HomeSyncState.artistSyncCurrentIndex, HomeSyncState.artistSyncTotal),
                                            style = typography.xxs.semiBold.copy(color = colorPalette.textSecondary)
                                        )
                                        if (HomeSyncState.artistSyncFailed > 0) {
                                            BasicText(
                                                text = " " + stringResource(R.string.syncing_failed, HomeSyncState.artistSyncFailed),
                                                style = typography.xxs.semiBold.copy(color = colorPalette.red)
                                            )
                                        }
                                    }
                                }
                                LinearWavyProgressIndicator(
                                    progress = { HomeSyncState.artistSyncProgress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    color = colorPalette.accent,
                                    trackColor = colorPalette.background2
                                )
                            }
                        }
                        }
                    }
                    items(items = itemsOnDisplay.distinctBy { it.id }, key = { it.id }) { artist ->
                        ReorderableItem(
                            reorderableLazyGridState,
                            key = artist.id
                        ) { isDraggingItem ->
                            Box(modifier = Modifier) {
                                if (!positionLock.isLocked() && sort.sortBy == ArtistSortBy.Custom && sort.sortOrder == SortOrder.Ascending) {
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
                                    modifier = Modifier.clip(uiRoundnessShape()).combinedClickable(
                                        onClick = {
                                            if( itemSelector.isActive ) {
                                                if( artist in itemSelector ) itemSelector.remove( artist )
                                                else itemSelector.add( artist )
                                            } else {
                                                search.hideIfEmpty()
                                                onArtistClick( artist )
                                            }
                                        },
                                        onLongClick = {
                                            menuState.display { LocalArtistItemMenu(artist = artist).MenuComponent() }
                                        }
                                    ),
                                    disableScrollingText = disableScrollingText,
                                    isYoutubeArtist = artist.isYoutubeArtist,
                                    thumbnailOverlay = {
                                        if( itemSelector.isActive ) {
                                            key(itemSelector.size) {
                                                Icon(
                                                    painter = painterResource(if (artist in itemSelector) R.drawable.checked_filled else R.drawable.unchecked_outline),
                                                    contentDescription = null,
                                                    tint = if (artist in itemSelector) colorPalette().accent else colorPalette().text,
                                                    modifier = Modifier
                                                        .padding(4.dp)
                                                        .size(24.dp)
                                                        .align(Alignment.TopStart)
                                                        .zIndex(2f)
                                                )
                                            }
                                        } else if (sort.sortBy == ArtistSortBy.PlayCount) {
                                            val playCount by Database.eventTable.getArtistPlayCount(artist.id).collectAsState(0, Dispatchers.IO)
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
                                        } else if (sort.sortBy == ArtistSortBy.ListeningTime) {
                                            val playTime by Database.eventTable.getArtistTotalPlayTime(artist.id).collectAsState(0L, Dispatchers.IO)
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

            FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
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










