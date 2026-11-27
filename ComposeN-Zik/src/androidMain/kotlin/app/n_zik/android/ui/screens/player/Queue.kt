package app.n_zik.android.ui.screens.player

import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import androidx.compose.ui.layout.onGloballyPositioned
import app.n_zik.android.components.glass.glassGlobalIndication

import app.n_zik.android.components.glass.queueToolbarBottomInset
import app.n_zik.android.components.glass.glassQueueListBackdrop
import app.n_zik.android.components.glass.LocalQueueRowBackdrop
import app.n_zik.android.components.glass.LocalQueueGlassBackdrop
import app.n_zik.android.components.glass.GlassFloatingActions
import app.n_zik.android.components.glass.glassQueueObjectBackdrop
import app.n_zik.android.components.glass.QueueGlassRowObjects
import app.n_zik.android.components.glass.rememberQueueRowGlassBackdrop
import app.n_zik.android.components.glass.glassQueueListBackground
import app.n_zik.android.components.glass.queueContentBackground
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSongRowSurface

import app.n_zik.android.components.rememberSongRowMetadata
import app.n_zik.android.components.search.SearchEmptyFeedback

import app.n_zik.android.components.ui.CollectionFavoritesKind
import app.n_zik.android.components.ui.rememberCollectionFavoritesButton
import app.n_zik.android.components.search.SearchEmptySpace




import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.glance.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.components.menu.playlist.rememberCollectionAddAction
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.components.settings.ui.visibleActions
import app.n_zik.android.compose.persist.persistList
import app.n_zik.android.compose.persist.persistMap
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.enums.QueueLoopType
import app.n_zik.android.enums.QueueType
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.ui.components.SwipeableQueueItem
import app.n_zik.android.ui.components.tab.toolbar.Dialog
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.ui.components.themed.PlaylistsMenu
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.favoritesOverlay
import app.n_zik.android.utils.DisposableListener
import app.n_zik.android.components.menu.player.playerMenuMediaItem
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.podcastBrowseId
import app.n_zik.android.utils.autoLoadSongsInQueueKey
import app.n_zik.android.utils.currentMediaItemIdAsState
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.isDownloadedSong
import app.n_zik.android.utils.manageDownload
import app.n_zik.android.utils.normalizedSongId
import app.n_zik.android.utils.queueTypeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.rememberQueueScrollPositionKey
import app.n_zik.android.utils.shouldBePlaying
import app.n_zik.android.utils.windows
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.player.rememberPlayerQueueSnapshot
import app.n_zik.android.components.player.rememberPlayerQueueArtists
import app.n_zik.android.components.player.playerQueueBulkSongs
import app.n_zik.android.components.player.findPlayerQueueWindowIndex
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import app.n_zik.android.components.dialog.common.RenderAboveMenu
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.components.settings.ui.ToolbarAction
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Locator
import app.n_zik.android.utils.currentWindow
import app.n_zik.android.components.tab.Search
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.QueueSwipeAction
import app.n_zik.android.utils.queueSwipeLeftActionKey
import app.n_zik.android.utils.queueSwipeRightActionKey
import androidx.compose.runtime.CompositionLocalProvider
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.download.utils.completedDownloadRequestIds
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.components.ui.screens.player.DeleteFromQueue
import app.n_zik.android.components.ui.screens.player.Discover
import app.n_zik.android.listentogether.guestLockedAlpha
import app.n_zik.android.components.ui.screens.player.QueueArrow
import app.n_zik.android.components.ui.screens.player.QueueSelectionCheckbox
import app.n_zik.android.components.ui.screens.player.Repeat
import app.n_zik.android.components.ui.screens.player.ShuffleQueue
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.coroutines.NzikDispatchers
import timber.log.Timber
import app.n_zik.android.themed.n_zik.component.playlist.PositionLock
import androidx.media3.common.util.UnstableApi
import androidx.compose.runtime.DisposableEffect


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun Queue(
    navController: NavController,
    onDismiss: (QueueLoopType) -> Unit,
    onDiscoverClick: () -> Unit,
    onSearchOpened: () -> Unit = {},
    onDestinationOpened: ((QueueLoopType) -> Unit)? = null,
) {
    // Essentials
    val context = LocalContext.current
    val forceShimmer = forceLoadingShimmerPreview()
    val windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val preview = LocalThemePreview.current
    val binder = LocalPlayerServiceBinder.current
    val player = preview?.player ?: binder?.player ?: return
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val rippleIndication = glassGlobalIndication(ripple(bounded = false))

    // Listen Together guest lock (spec-listen-together-guest-lock-hardening): a guest in a
    // room cannot edit the queue (reorder, remove, play-next swipe, clear) — it is host-owned.

    // `by` delegation so every event-time read gets the latest lock state.
    val serviceGuestLock by app.n_zik.android.listentogether.listenTogetherGuestLock
    val listenTogetherGuestLock = LocalThemePreview.current == null && serviceGuestLock
    val guestLockAlpha = guestLockedAlpha(listenTogetherGuestLock)

    Box( Modifier.fillMaxSize() ) {
        var windowsInQueue by remember {
            mutableStateOf(player.currentTimeline.windows)
        }
        var pendingDragOrder by remember { mutableStateOf<List<Timeline.Window>?>(null) }
        var isDragging by remember { mutableStateOf(false) }
        var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        player.DisposableListener {
            object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (!isDragging) {
                        windowsInQueue = timeline.windows
                    }
                }
            }
        }
        var itemsOnDisplay by persistList<Song>( "queue/on_display" )

        val rememberQueueScrollPosition by rememberPreference(rememberQueueScrollPositionKey, true)
        val lazyListState = if (rememberQueueScrollPosition) {
            remember {
                context.persistMap?.getOrPut("queue/scroll_state") { LazyListState() } as? LazyListState ?: LazyListState()
            }
        } else {
            rememberLazyListState(initialFirstVisibleItemIndex = player.currentMediaItemIndex.coerceAtLeast(0))
        }

        val reorderableLazyListState = rememberReorderableLazyListState(
            lazyListState = lazyListState,
        ) { from, to ->
            val fromIndex = windowsInQueue.indexOfFirst { it.uid.toString() == from.key }
            val toIndex = windowsInQueue.indexOfFirst { it.uid.toString() == to.key }

            // Queue reorder is host-only in a Listen Together room: a locked guest's drag
            // snaps back instead of reordering
            if (!listenTogetherGuestLock && fromIndex != -1 && toIndex != -1) {
                windowsInQueue = windowsInQueue.toMutableList().apply {
                    val currentDragInfo = dragInfo
                    dragInfo = if (currentDragInfo == null)
                        fromIndex to toIndex
                    else currentDragInfo.first to toIndex

                    val item = removeAt(fromIndex)
                    add(toIndex, item)
                }
                pendingDragOrder = windowsInQueue
            }
        }

        LaunchedEffect(reorderableLazyListState.isAnyItemDragging) {
            if (reorderableLazyListState.isAnyItemDragging) {
                isDragging = true
            } else {
                dragInfo?.let { (from, to) ->
                    // Queue reorder is host-only in a Listen Together room
                    if (!listenTogetherGuestLock) player.moveMediaItem(from, to)
                    dragInfo = null
                }
                isDragging = false
            }
        }

        val positionLock = remember { PositionLock() }

        val itemSelector = ItemSelector<Any>()
        LaunchedEffect( itemSelector.isActive ) {
            // Setting this field to true means disable it
            if( itemSelector.isActive )
                positionLock.isFirstIcon = true
        }

        // Guest lock (spec-listen-together-guest-lock-hardening): while a guest is locked the
        // reorder lock ("cadena") stays forced-on — the guest cannot unlock it to

        // reorder the shared queue. The button itself is grayed + disabled by
        // PositionLock.isEnabled.

        // When the lock clears (guest leaves the room), the guest's previous setting is restored
        // (review finding: the forced-on was never rolled back).
        var preLockReorderLock by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect( listenTogetherGuestLock ) {
            if ( listenTogetherGuestLock ) {
                if ( preLockReorderLock == null ) preLockReorderLock = positionLock.isFirstIcon
                positionLock.isFirstIcon = true
            } else {
                preLockReorderLock?.let { previous ->
                    positionLock.isFirstIcon = previous
                    preLockReorderLock = null
                }
            }
        }

        val search = Search(lazyListState)
        LaunchedEffect(search.isVisible) {
            if (search.isVisible) onSearchOpened()
        }
        val storedQueueSongIds = remember(windowsInQueue) {
            windowsInQueue.map { it.mediaItem.normalizedSongId }.distinct()
        }
        val storedQueueSongs by rememberSongRowMetadata(storedQueueSongIds)
        val storedQueueArtists = rememberPlayerQueueArtists(if (preview == null) storedQueueSongIds else emptyList())
        val queueSnapshot = rememberPlayerQueueSnapshot(
            windowsInQueue,
            search.filterQuery,
            storedSongs = storedQueueSongs,
            storedArtists = storedQueueArtists,
            unknownArtist = stringResource(R.string.metadata_unknown_artist),
        )
        // Drag order must update synchronously; all metadata conversion/search stays on MEDIA.
        val rowsOnDisplay = if ((isDragging || pendingDragOrder === windowsInQueue) && search.filterQuery.isEmpty()) {
            remember(windowsInQueue, queueSnapshot) {
                windowsInQueue.mapNotNull { queueSnapshot.rowsByUid[it.uid] }
            }
        } else queueSnapshot.rows
        val queueSearchPending = queueSnapshot.source !== windowsInQueue ||
            queueSnapshot.input != search.filterQuery
        fun selectedQueueWindows(): List<Timeline.Window> {
            val candidates = when {
                !itemSelector.isActive || search.filterQuery.isBlank() -> windowsInQueue
                queueSearchPending -> emptyList()
                else -> rowsOnDisplay.map { it.window }
            }
            return itemSelector.actionTargets(candidates, { it }, { it.uid }, selectionOrder = true)
        }
        fun getSongs() = playerQueueBulkSongs(queueSnapshot, selectedQueueWindows(), emptyList())
        fun hasCurrentSelection(): Boolean = selectedQueueWindows().isNotEmpty()
        fun currentQueueIndex(uid: Any): Int = findPlayerQueueWindowIndex(player.currentTimeline, uid)

        LaunchedEffect(queueSnapshot) {
            itemsOnDisplay = queueSnapshot.songs
        }

        val exportDialog = ExportSongsToCSVDialog(
            playlistName = "",
            songs = ::getSongs,
            selectionActive = { itemSelector.isActive },
        )
        val shuffle = ShuffleQueue(player, lazyListState, coroutineScope, itemSelector) {
            selectedQueueWindows().mapTo(LinkedHashSet()) { it.uid }
        }
        val isAutoFillEnabled by rememberPreference(autoLoadSongsInQueueKey, true)
        val isDiscoverClickable = binder?.service?.nzikRadio?.isRadioActive == true || isAutoFillEnabled
        val discover = Discover( isDiscoverClickable, onDiscoverClick )
        // Full offline mode (spec-full-offline-mode): network queue actions removed
        val offlineMode by OfflineMode.rememberEnabled()
        val repeat = Repeat.init()
        val revealMenuDestination = onDestinationOpened?.let { reveal -> { reveal(repeat.type) } }
        val deleteDialog = DeleteFromQueue(player, itemSelector) {
            selectedQueueWindows().mapTo(LinkedHashSet()) { it.uid }
        }
        val addToPlaylist = PlaylistsMenu.deferred(
            navController = navController,
            captureMediaItems = {
                val songs = getSongs()
                if (itemSelector.isActive && songs.isEmpty()) null else suspend { songs.map(Song::asMediaItem) }
            },
            onFailure = { throwable, preview ->
                Timber.tag("Queue").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
            },
            itemSelector = itemSelector,
            allowImportChoice = false,
            localDestinationsOnly = selectedQueueWindows().any { it.mediaItem.isLocal },
            onMenuDestinationOpened = revealMenuDestination,
        )
        val queueArrow = QueueArrow { onDismiss( repeat.type ) }
        val locator = Locator.forQueue(lazyListState) {
            if (queueSearchPending) -1
            else {
                val currentUid = player.currentWindow?.uid
                rowsOnDisplay.indexOfFirst { row -> row.window.uid == currentUid }
            }
        }

        fun downloadableSongs() = getSongs().filterNot { it.isLocal }.distinctBy(Song::id)
        val downloadAllDialog = DownloadAllSongsDialog(::downloadableSongs)
        val deleteAllDownloadedDialog = DeleteAllDownloadedSongsDialog(
            getSongs = ::downloadableSongs,
            removableSongIds = { MyDownloadHelper.downloads.value.completedDownloadRequestIds() },
        )

        exportDialog.Render()
        val menuState = LocalMenuState.current
        (deleteDialog as Dialog).RenderAboveMenu(menuState)
        downloadAllDialog.RenderAboveMenu(menuState)
        deleteAllDownloadedDialog.RenderAboveMenu(menuState)

        val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle(
            initialValue = MyDownloadHelper.downloads.value,
            context = NzikDispatchers.DATA,
        )
        val downloadedIds by remember {
            derivedStateOf { downloadsMapState.completedDownloadRequestIds() }
        }
        val favorites = rememberCollectionFavoritesButton(
            mediaItems = if (!itemSelector.isActive || queueSearchPending) emptyList()
                else selectedQueueWindows().map { it.mediaItem },
            kind = CollectionFavoritesKind.Queue,
        )
        val visibleToolbarActions = ToolbarSurface.QUEUE.visibleActions()
        val playlistActions = rememberCollectionAddAction(
            sourceId = "player-queue",
            menuState = menuState,
            canImportCollection = false,
            hasTracks = ToolbarAction.ADD_TO_PLAYLIST in visibleToolbarActions && getSongs().isNotEmpty(),
            importFromCurrentCollection = false,
            canExport = ToolbarAction.EXPORT in visibleToolbarActions && getSongs().isNotEmpty(),
            openImport = {},
            openPicker = addToPlaylist::openPicker,
            openExport = exportDialog::onShortClick,
        )
        val actions = buildMap<ToolbarAction, Button> {
            put(ToolbarAction.LOCATE, locator)
            put(ToolbarAction.SEARCH, search)
            if (!offlineMode) {
                put(ToolbarAction.DISCOVER, discover)
                put(ToolbarAction.DOWNLOAD, itemSelector.guard(downloadAllDialog) {
                    selectedQueueWindows().any {
                        !it.mediaItem.isLocal && it.mediaItem.normalizedSongId !in downloadedIds
                    }
                })
            }
            put(ToolbarAction.DELETE_DOWNLOADS, itemSelector.guard(deleteAllDownloadedDialog) {
                selectedQueueWindows().any {
                    !it.mediaItem.isLocal && it.mediaItem.normalizedSongId in downloadedIds
                }
            })
            put(ToolbarAction.POSITION_LOCK, positionLock)
            put(ToolbarAction.REPEAT, repeat)
            put(ToolbarAction.SHUFFLE, itemSelector.guard(shuffle) { selectedQueueWindows().size > 1 })
            put(ToolbarAction.SELECT, itemSelector)
            put(ToolbarAction.FAVORITES, favorites)
            put(ToolbarAction.REMOVE, deleteDialog)
            val playlistAnchor = ToolbarAction.ADD_TO_PLAYLIST.takeIf { it in visibleToolbarActions }
                ?: ToolbarAction.EXPORT.takeIf { it in visibleToolbarActions }
            if (playlistAnchor != null) put(playlistAnchor, playlistActions)
        }
        val buttonsList = visibleToolbarActions.mapNotNull(actions::get)

        val searchVisible = search.isVisible
        SideEffect {
            QueueToolBarState.mediaItemCount = player.mediaItemCount
            QueueToolBarState.buttons = buttonsList
            QueueToolBarState.queueArrow = queueArrow
            QueueToolBarState.onBarClick = { onDismiss(repeat.type) }
            QueueToolBarState.isVisible = if (searchVisible) 1 else 0
        }

        DisposableEffect( Unit ) {
            onDispose { QueueToolBarState.reset() }
        }

        Box(Modifier.fillMaxSize()) {
            val queueType by rememberPreference( queueTypeKey, QueueType.Modern )
            val backgroundAlpha = if( queueType == QueueType.Modern ) .5f else 1f
            val queueGlassSettings = rememberGlassSettings(GlassScope.Queue)
            val itemBackground = queueContentBackground(
                if (queueType == QueueType.Modern) androidx.compose.ui.graphics.Color.Transparent else colorPalette().background0,
                queueGlassSettings,
            )
            val queueBackground = colorPalette().background0.copy(alpha = backgroundAlpha)
            val timelineWindows = windowsInQueue
            val placeholderCount = queueSnapshot.initialPlaceholderCount(timelineWindows.size)
            val currentMediaId = if (forceShimmer || placeholderCount > 0)
                player.currentMediaItemIdAsState(player).value else null
            val hasReorderSlot = !positionLock.isLocked()
            val showReorderHandle = hasReorderSlot && search.filterQuery.isEmpty()

            val queueSongIds = queueSnapshot.songIds
            val likeStatesMap by remember(queueSongIds) {
                if (preview != null) kotlinx.coroutines.flow.flowOf(emptyMap()) else LikeStateManager.getLikeStates(queueSongIds)
            }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
            val playlistStatesMap by remember(queueSongIds) {
                if (preview != null) kotlinx.coroutines.flow.flowOf(emptyMap()) else PlaylistStateManager.getPlaylistStates(queueSongIds)
            }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

            val queueSongs = queueSnapshot.songs

            // Hoisted swipe action preferences
            val queueSwipeLeftAction by rememberPreference(queueSwipeLeftActionKey, QueueSwipeAction.RemoveFromQueue)
            val queueSwipeRightAction by rememberPreference(queueSwipeRightActionKey, QueueSwipeAction.PlayNext)

            val downloadStatesMap by remember(queueSongs) {
                derivedStateOf {
                    queueSongs.associate { song ->
                        song.id to if (song.id in downloadedIds) DownloadedStateMedia.DOWNLOADED else DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                    }
                }
            }

            SearchEmptyFeedback(
                query = search.filterQuery,
                empty = rowsOnDisplay.isEmpty(),
                loading = forceShimmer || queueSearchPending,
            )
            CompositionLocalProvider(
                LocalDownloadStatesMap provides downloadStatesMap,
                LocalQueueRowBackdrop provides rememberQueueRowGlassBackdrop(queueBackground, queueGlassSettings),
            ) {
            LazyColumn(
                state = lazyListState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = WindowInsets(
                    top = 8.dp,
                    bottom = Dimensions.bottomSpacer + queueToolbarBottomInset(),
                ).asPaddingValues(),
                modifier = Modifier.fillMaxSize()
                                   .glassQueueListBackdrop(lazyListState)
                                   .glassQueueListBackground(queueBackground, queueGlassSettings)

            ) {
                if (!forceShimmer && search.filterQuery.isNotBlank() && !queueSearchPending && rowsOnDisplay.isEmpty()) {
                    item(key = "search_no_results") {
                        SearchEmptySpace(Modifier.fillParentMaxSize())
                    }
                }
                items(placeholderCount, key = { timelineWindows[it].uid.toString() }) { index ->
                    QueueGlassRowObjects(index, false) {
                        QueueSongShimmer(
                            song = null,
                            background = itemBackground,
                            isCurrent = timelineWindows[index].mediaItem.mediaId == currentMediaId,
                            hasReorderSlot = hasReorderSlot,
                            showReorderHandle = showReorderHandle,
                            showSelection = itemSelector.isActive,
                            handleAlpha = guestLockAlpha,
                        )
                    }
                }
                itemsIndexed(
                    items = rowsOnDisplay,
                    key = { _, row -> row.window.uid.toString() }
                ) { displayIndex, row ->
                    val window = row.window
                    val song = row.song

                    if (forceShimmer) {
                        QueueGlassRowObjects(displayIndex, false) {
                            QueueSongShimmer(
                                song = row.displaySong,
                                background = itemBackground,
                                isCurrent = currentMediaId == song.id,
                                hasReorderSlot = hasReorderSlot,
                                showReorderHandle = showReorderHandle,
                                showSelection = itemSelector.isActive,
                                handleAlpha = guestLockAlpha,
                            )
                        }
                        return@itemsIndexed
                    }

                    val isLocal by remember(song) { derivedStateOf { song.isLocal } }
                    val isDownloaded = isLocal || preview == null && isDownloadedSong(song.id)

                    val isLiked = likeStatesMap[song.id]
                    val inPlaylist = playlistStatesMap[song.id]

                    ReorderableItem(
                        reorderableLazyListState,
                        key = window.uid.toString()
                    ) { isDraggingItem ->
                        QueueGlassRowObjects(displayIndex, isDraggingItem) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                               .animateItem()
                                               .then(if (preview != null && displayIndex == 0) {
                                                   Modifier.onGloballyPositioned { preview.previewRowsPlaced.value = it.size.height > 0 }
                                               } else Modifier)
                        ) {
                            // Drag anchor
                            if (showReorderHandle) {
                                Box(
                                    modifier = Modifier.padding( end = 16.dp ) // Accommodate horizontal padding of SongItem
                                        .size( 24.dp )
                                        .zIndex( 2f )
                                        .align( Alignment.CenterEnd )
                                        .draggableHandle(
                                            onDragStarted = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        )
                                        // Capture only this passive sibling, including its native guest opacity.
                                        .glassQueueObjectBackdrop(isDraggingItem to guestLockAlpha)
                                        // Reorder handle is visibly disabled for a locked guest
                                        .alpha( guestLockAlpha ),
                                    contentAlignment = Alignment.Center
                                ) {

                                    IconButton(
                                        icon = R.drawable.reorder,
                                        color = if (isDraggingItem) colorPalette().accent else colorPalette().textDisabled,
                                        indication = rippleIndication,
                                        onClick = {},
                                    )
                                }
                            }

                            val mediaItem = playerMenuMediaItem(window.mediaItem, song)
                            SwipeableQueueItem(
                                mediaItem = mediaItem,
                                backgroundColor = itemBackground,
                                skipLikeQuery = true,
                                downloadStateParam = downloadsMapState[song.id]?.state ?: Download.STATE_STOPPED,
                                downloadedStateMediaParam = downloadStatesMap[song.id] ?: DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED,
                                swipeLeftActionParam = queueSwipeLeftAction,
                                swipeRightActionParam = queueSwipeRightAction,
                                onPlayNext = {
                                    // "Play next" swipes reorder the queue: host-only in a
                                    // Listen Together room
                                    val index = currentQueueIndex(window.uid)
                                    if (!listenTogetherGuestLock && index >= 0) {
                                        val currentIndex = player.currentMediaItemIndex
                                        val targetIndex = currentIndex + 1
                                        // if the song is already after the current, do nothing
                                        if (index > currentIndex) {
                                             // Only move if not already in the next position
                                             if (index != targetIndex) {
                                                player.moveMediaItem(index, targetIndex)
                                            }
                                        } else {
                                            // If it's before or in the current position, move it to the next
                                            player.moveMediaItem(index, targetIndex)
                                        }
                                    }
                                },
                                onDownload = {
                                    binder?.cache?.removeResource(song.id)
                                    if (!isLocal)
                                        manageDownload(
                                            context = context,
                                            mediaItem = mediaItem,
                                            downloadState = isDownloaded
                                        )
                                },
                                onRemoveFromQueue = {
                                    /*
                                         Compose gotcha here, variables passed into this
                                         block will be held through recomposition.

                                         Meaning, if index at initialization is 0
                                         then 0 will stay here through recomposition.

                                         To bypass it, pass another function that requires
                                         computation to extract data.
                                     */
                                    // Removing from the queue is host-only in a Listen Together room
                                    if (!listenTogetherGuestLock) {
                                        val actualIndex = currentQueueIndex(window.uid)
                                        if (actualIndex >= 0 && actualIndex < player.mediaItemCount) {
                                            try {
                                                player.removeMediaItem( actualIndex )
                                                Toaster.s(
                                                    context.resources.getString(R.string.player_queue_deleted_item_format, song.cleanTitle())
                                                )
                                            } catch (e: IllegalArgumentException) {
                                                // Media item may have already been removed or index is invalid
                                                Timber.tag("Queue").e(e, "Failed to remove media item at index $actualIndex")
                                            }
                                        }
                                    }
                                },
                                onEnqueue = {
                                    // Enqueue swipes modify the host-owned queue: host-only
                                    // in a Listen Together room
                                    if (!listenTogetherGuestLock) {
                                        player.enqueue(
                                            mediaItem,
                                            context
                                        )
                                    }
                                }
                            ) {
                                SongItem(
                                    song = row.displaySong,
                                    observeStoredMetadata = false,
                                    navController = navController,
                                    isLiked = isLiked,
                                    inPlaylist = inPlaylist,
                                    backgroundColor = itemBackground,
                                    playbackAlbumId = mediaItem.mediaMetadata.extras?.getString("albumId"),
                                    playbackAlbumTitle = mediaItem.mediaMetadata.albumTitle?.toString(),
                                    podcastBrowseId = window.mediaItem.podcastBrowseId,
                                    sourceMediaItem = { window.mediaItem },
                                    onMenuDestinationOpened = revealMenuDestination,
                                    trailingContent = {
                                        QueueSelectionCheckbox(window.uid, itemSelector)
                                        if (hasReorderSlot)
                                        // Create a fake box to store drag anchor and checkbox
                                            Box( Modifier.width( 24.dp ) )
                                    },
                                    onClick = {
                                        val index = currentQueueIndex(window.uid)
                                        if (index < 0) return@SongItem
                                        if (player.currentMediaItemIndex == index) {
                                            if(player.shouldBePlaying)
                                                player.pause()
                                            else
                                                player.play()
                                        } else {
                                            player.seekToDefaultPosition(index)
                                            player.prepare()
                                            player.playWhenReady = true
                                        }

                                        search.hideIfEmpty()
                                    }
                                )
                            }
                        }
                        }
                    }
                }

                if (binder?.isLoadingRadio == true) {
                    items(3, key = { "loading_song_$it" }) {
                        QueueSongShimmer(
                            song = null,
                            background = itemBackground,
                            isCurrent = false,
                            hasReorderSlot = hasReorderSlot,
                            showReorderHandle = showReorderHandle,
                            showSelection = itemSelector.isActive,
                            handleAlpha = guestLockAlpha,
                        )
                    }
                }
            }
            }

            // Search overlays the queue: opening the keyboard must not resize the list.
            // The toolbar overlaps the panel's bottom only while the keyboard is closed.
            val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(bottom = if (search.isVisible && !keyboardOpen) 60.dp + queueToolbarBottomInset() else 0.dp)
                    .imePadding(),
            ) { search.SearchBar(this) }
        }

        GlassFloatingActions(LocalQueueGlassBackdrop.current) {
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                modifier = Modifier.padding(bottom = Dimensions.miniPlayerHeight)
            )
        }
    }
}

@Composable
private fun QueueSongShimmer(
    song: Song?,
    background: Color,
    isCurrent: Boolean,
    hasReorderSlot: Boolean,
    showReorderHandle: Boolean,
    showSelection: Boolean,
    handleAlpha: Float,
) {
    val palette = colorPalette()
    val shape = uiRoundnessShape()
    val reorderSpace = if (hasReorderSlot) 36.dp else 0.dp
    val selectionSpace = if (showSelection) 52.dp else 0.dp
    GlassSongRowSurface(background, palette.favoritesOverlay.takeIf { isCurrent }, shape) { rowBackground ->
        Box(Modifier.fillMaxWidth().clip(shape).then(rowBackground)) {
            SongItemPlaceholder(
                modifier = Modifier.padding(end = reorderSpace + selectionSpace),
                title = song?.cleanTitle(),
                artist = song?.cleanArtistsText(),
            )
            if (showSelection) {
                IconButton(
                    icon = R.drawable.unchecked_outline,
                    color = palette.textDisabled,
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .padding(end = 24.dp + reorderSpace)
                        .size(24.dp),
                )
            }
            if (showReorderHandle) {
                IconButton(
                    icon = R.drawable.reorder,
                    color = palette.textDisabled,
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .size(24.dp)
                        .alpha(handleAlpha),
                )
            }
        }
    }
}







