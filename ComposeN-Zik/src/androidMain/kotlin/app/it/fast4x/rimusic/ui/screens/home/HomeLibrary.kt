package app.it.fast4x.rimusic.ui.screens.home

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import app.n_zik.android.core.database.*

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.combinedClickable
import app.it.fast4x.rimusic.PIPED_PREFIX
import app.it.fast4x.rimusic.YTP_PREFIX
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavigationBarPosition
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
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.CheckMonthlyPlaylist
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_ITEM_SIZE
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_LIBRARY_SORT_ORDER
import app.it.fast4x.rimusic.utils.autoSyncToolbutton
import app.it.fast4x.rimusic.utils.autosyncKey
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.enableCreateMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.playlistTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.showFloatingIconKey
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.showPinnedPlaylistsKey
import app.it.fast4x.rimusic.utils.showPipedPlaylistsKey
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import app.n_zik.android.components.Sort
import app.n_zik.android.components.playlist.NewPlaylistDialog
import app.n_zik.android.components.tab.ImportSongsFromCSV
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.tab.SongShuffler


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
    val menuState = app.it.fast4x.rimusic.ui.components.LocalMenuState.current
    
    // Essentials
    val lazyGridState = rememberLazyGridState()

    // Non-vital
    var playlistType by rememberPreference(playlistTypeKey, PlaylistsType.Playlist)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    var items by persistList<PlaylistPreview>("home/playlists/items")

    var itemsOnDisplay by persistList<PlaylistPreview>("home/playlists/on_display")

    val search = Search(lazyGridState)

    val sort = Sort( HOME_LIBRARY_SORT_BY, HOME_LIBRARY_SORT_ORDER )
    val positionLock = remember( sort.sortOrder ) { PositionLock(sort.sortOrder) }
    val itemSize = ItemSize.init( HOME_LIBRARY_ITEM_SIZE )

    //<editor-fold desc="Songs shuffler">
    /**
     * Previous implementation calls this every time shuffle button is clicked.
     * It is extremely slow since the database needs some time to look for and
     * sort songs before it can go through and start playing.
     *
     * This implementation will make sure that new list is fetched when [PlaylistsType]
     * is changed, but this process happens in the background, therefore, there's no
     * visible penalty. Furthermore, this will reduce load time significantly.
     */
    val shuffle = SongShuffler(
        databaseCall = when( playlistType ) {
            PlaylistsType.Playlist          -> Database.playlistTable::allSongs
            PlaylistsType.PinnedPlaylist    -> Database.playlistTable::allPinnedSongs
            PlaylistsType.MonthlyPlaylist   -> Database.playlistTable::allMonthlySongs
            PlaylistsType.PipedPlaylist     -> Database.playlistTable::allPipedSongs
            PlaylistsType.YTPlaylist        -> Database.playlistTable::allYTPlaylistSongs
        },
        key = arrayOf( playlistType )
    )
    //</editor-fold>
    //<editor-fold desc="New playlist dialog">
    val newPlaylistDialog = NewPlaylistDialog()
    //</editor-fold>
    val importPlaylistDialog = ImportSongsFromCSV()
    val importSpotifyDialog = app.n_zik.android.components.tab.ImportSongsFromSpotifyCSV.init()
    val importMenu = remember { app.n_zik.android.components.tab.ImportPlaylistsMenu(
        onImportNzik = { importPlaylistDialog.onShortClick() },
        onImportSpotify = { importSpotifyDialog.onShortClick() }
    ) }
    val sync = autoSyncToolbutton(R.string.autosync)

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
    val showPipedPlaylists by rememberPreference(showPipedPlaylistsKey, true)

    val buttonsList = mutableListOf(PlaylistsType.Playlist to stringResource(R.string.playlists))
    buttonsList += PlaylistsType.YTPlaylist to stringResource(R.string.yt_playlists)
    if (showPipedPlaylists) buttonsList +=
        PlaylistsType.PipedPlaylist to stringResource(R.string.piped_playlists)
    if (showPinnedPlaylists) buttonsList +=
        PlaylistsType.PinnedPlaylist to stringResource(R.string.pinned_playlists)
    if (showMonthlyPlaylists) buttonsList +=
        PlaylistsType.MonthlyPlaylist to stringResource(R.string.monthly_playlists)
    // END - Additional playlists

    LaunchedEffect(showPinnedPlaylists, showMonthlyPlaylists, showPipedPlaylists) {
        if (!showPinnedPlaylists && playlistType == PlaylistsType.PinnedPlaylist) playlistType = PlaylistsType.Playlist
        if (!showMonthlyPlaylists && playlistType == PlaylistsType.MonthlyPlaylist) playlistType = PlaylistsType.Playlist
        if (!showPipedPlaylists && playlistType == PlaylistsType.PipedPlaylist) playlistType = PlaylistsType.Playlist
    }


    // START - New playlist
    newPlaylistDialog.Render()
    // END - New playlist
    
    // START - Import menu
    importMenu.Render()
    // END - Import menu

    // START - Monthly playlist
    val enableCreateMonthlyPlaylists by rememberPreference(enableCreateMonthlyPlaylistsKey, true)
    if (enableCreateMonthlyPlaylists)
        CheckMonthlyPlaylist()
    // END - Monthly playlist

    val doAutoSync by rememberPreference(autosyncKey, false)
    var justSynced by rememberSaveable { mutableStateOf(!doAutoSync) }

    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()

    fun refresh() {
        if (refreshing) return
        refreshScope.launch(Dispatchers.IO) {
            refreshing = true
            justSynced = false
            delay(500)
            refreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = ::refresh
    ) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth()
        ) {

            Column( Modifier.fillMaxSize() ) {
                // Sticky tab's title
                TabHeader( R.string.playlists ) {
                    HeaderInfo( items.size.toString(), R.drawable.playlist )
                }

                val toolbarButtons = remember { mutableStateListOf<app.it.fast4x.rimusic.ui.components.tab.toolbar.Button>() }

                LaunchedEffect(sort.sortBy, sort.sortOrder) {
                    toolbarButtons.clear()
                    toolbarButtons.add(sort)
                    if (sort.sortBy == app.it.fast4x.rimusic.enums.PlaylistSortBy.Custom)
                        toolbarButtons.add(positionLock)
                    toolbarButtons.add(sync)
                    toolbarButtons.add(search)
                    toolbarButtons.add(shuffle)
                    toolbarButtons.add(newPlaylistDialog)
                    toolbarButtons.add(importMenu)
                    toolbarButtons.add(itemSize)
                }

                TabToolBar.Buttons( toolbarButtons )

                search.SearchBar( this )

                val listPrefix =
                    when( playlistType ) {
                        PlaylistsType.Playlist -> ""    // Matches everything
                        PlaylistsType.PinnedPlaylist -> PINNED_PREFIX
                        PlaylistsType.MonthlyPlaylist -> MONTHLY_PREFIX
                        PlaylistsType.PipedPlaylist -> PIPED_PREFIX
                        PlaylistsType.YTPlaylist -> YTP_PREFIX
                    }
                val condition: (PlaylistPreview) -> Boolean = {
                    when (playlistType) {
                        PlaylistsType.YTPlaylist -> it.playlist.isYoutubePlaylist
                        PlaylistsType.Playlist -> {
                            val isMonthly = it.playlist.name.startsWith(MONTHLY_PREFIX, true)
                            val isPinned = it.playlist.name.startsWith(PINNED_PREFIX, true)
                            val isPiped = it.playlist.name.startsWith(PIPED_PREFIX, true)
                            
                            (!isMonthly || showMonthlyPlaylists) && 
                            (!isPinned || showPinnedPlaylists) && 
                            (!isPiped || showPipedPlaylists)
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

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = lazyGridState,
                        columns = GridCells.Adaptive( itemSize.size.dp ),
                    modifier = Modifier
                        .background(colorPalette().background0)
                        .fillMaxSize(),
                    contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer )
                ) {
                    item(
                        key = "separator",
                        contentType = 0,
                        span = { GridItemSpan(maxLineSpan) }) {
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
                    }

                    items(
                        items = filteredItems,
                        key = { it.playlist.id }
                    ) { preview ->
                        ReorderableItem(
                            reorderableLazyGridState,
                            key = preview.playlist.id
                        ) { isDraggingItem ->
                            Box(modifier = Modifier) {
                                if (!positionLock.isLocked() && sort.sortBy == app.it.fast4x.rimusic.enums.PlaylistSortBy.Custom && sort.sortOrder == app.it.fast4x.rimusic.enums.SortOrder.Ascending) {
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(24.dp)
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
                                        androidx.compose.material3.Icon(
                                            painter = androidx.compose.ui.res.painterResource(R.drawable.reorder),
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
                                                search.hideIfEmpty()
                                                onPlaylistClick(preview.playlist)
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
                                    isEditable = preview.playlist.isEditable
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
                        androidx.compose.foundation.text.BasicText(
                            text = stringResource(R.string.no_items),
                            style = app.n_zik.android.typography().m.semiBold.copy(
                                color = colorPalette().textSecondary
                            )
                        )
                    }
                }
            }
            }

            FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if (UiType.ViMusic.isCurrent() && showFloatingIcon)
                MultiFloatingActionsContainer(
                    iconId = R.drawable.search,
                    onClick = onSearchClick,
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )
        }
    }
}








