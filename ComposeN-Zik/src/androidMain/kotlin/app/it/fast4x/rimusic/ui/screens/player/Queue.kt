package app.it.fast4x.rimusic.ui.screens.player

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape


import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.glance.layout.Box
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavController
import app.n_zik.android.R
import com.valentinilk.shimmer.shimmer
import app.it.fast4x.compose.persist.persist
import app.it.fast4x.compose.persist.persistList
import app.it.fast4x.compose.persist.persistMap
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.binder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.QueueLoopType
import app.it.fast4x.rimusic.enums.QueueType
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.SwipeableQueueItem
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Dialog
import app.it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.it.fast4x.rimusic.ui.components.themed.IconButton
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import app.it.fast4x.rimusic.ui.items.SongItemPlaceholder
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.DisposableListener
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.autoLoadSongsInQueueKey
import app.it.fast4x.rimusic.utils.enqueue
import app.it.fast4x.rimusic.utils.findMediaItemIndexById
import app.it.fast4x.rimusic.utils.isDownloadedSong
import app.it.fast4x.rimusic.utils.isLandscape
import app.it.fast4x.rimusic.utils.isNowPlaying
import app.it.fast4x.rimusic.utils.manageDownload
import app.it.fast4x.rimusic.utils.mediaItems
import app.it.fast4x.rimusic.utils.queueTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.rememberQueueScrollPositionKey
import app.it.fast4x.rimusic.utils.shouldBePlaying
import app.it.fast4x.rimusic.utils.showButtonPlayerDiscoverKey
import app.it.fast4x.rimusic.utils.windows
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.dialog.export.ExportSongsToCSVDialog
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Locator
import app.n_zik.android.components.tab.Search
import app.n_zik.android.components.ui.screens.player.DeleteFromQueue
import app.n_zik.android.components.ui.screens.player.Discover
import app.n_zik.android.components.ui.screens.player.QueueArrow
import app.n_zik.android.components.ui.screens.player.Repeat
import app.n_zik.android.components.ui.screens.player.ShuffleQueue
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import app.kreate.android.themed.rimusic.component.playlist.PositionLock
import androidx.compose.ui.res.stringResource
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
) {
    // Essentials
    val context = LocalContext.current
    val windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val binder = LocalPlayerServiceBinder.current
    val player = binder?.player ?: return
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val rippleIndication = ripple(bounded = false)

    Box( Modifier.fillMaxSize() ) {
        var items by remember {
            mutableStateOf(player.currentTimeline.mediaItems.map( MediaItem::asSong ))
        }
        var windowsInQueue by remember {
            mutableStateOf(player.currentTimeline.windows)
        }
        var isDragging by remember { mutableStateOf(false) }
        var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }

        player.DisposableListener {
            object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (!isDragging) {
                        items = timeline.mediaItems.map( MediaItem::asSong )
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
            rememberLazyListState(initialFirstVisibleItemIndex = binder.player.currentMediaItemIndex.coerceAtLeast(0))
        }

        val reorderableLazyListState = rememberReorderableLazyListState(
            lazyListState = lazyListState,
        ) { from, to ->
            val fromIndex = windowsInQueue.indexOfFirst { it.uid.toString() == from.key }
            val toIndex = windowsInQueue.indexOfFirst { it.uid.toString() == to.key }

            if (fromIndex != -1 && toIndex != -1) {
                windowsInQueue = windowsInQueue.toMutableList().apply {
                    val currentDragInfo = dragInfo
                    dragInfo = if (currentDragInfo == null)
                        fromIndex to toIndex
                    else currentDragInfo.first to toIndex

                    val item = removeAt(fromIndex)
                    add(toIndex, item)
                }
            }
        }

        LaunchedEffect(reorderableLazyListState.isAnyItemDragging) {
            if (reorderableLazyListState.isAnyItemDragging) {
                isDragging = true
            } else {
                dragInfo?.let { (from, to) ->
                    player.moveMediaItem(from, to)
                    dragInfo = null
                }
                isDragging = false
            }
        }

        val positionLock = remember { PositionLock() }

        val itemSelector = ItemSelector<Song>()
        LaunchedEffect( itemSelector.isActive ) {
            // Setting this field to true means disable it
            if( itemSelector.isActive )
                positionLock.isFirstIcon = true
        }

        fun getSongs() = itemSelector.ifEmpty { items }

        val search = Search(lazyListState)
        val windowsOnDisplay = remember(windowsInQueue, search.inputValue) {
            if (search.inputValue.isEmpty()) {
                windowsInQueue
            } else {
                windowsInQueue.filter {
                    val song = it.mediaItem.asSong
                    // Without cleaning, user can search explicit songs with "e:"
                    // I kinda want this to be a feature, but it seems unnecessary
                    val containsTitle = song.cleanTitle().contains( search.inputValue, true )
                    val containsArtist = song.cleanArtistsText().contains( search.inputValue, true )

                    containsTitle || containsArtist
                }
            }
        }

        LaunchedEffect(windowsOnDisplay) {
            itemsOnDisplay = windowsOnDisplay.map { win -> win.mediaItem.asSong }
        }

        val plistName = remember { mutableStateOf("") }
        val exportDialog = ExportSongsToCSVDialog(
            playlistName = plistName.value,
            songs = ::getSongs
        )
        val shuffle = ShuffleQueue( player, lazyListState, coroutineScope, itemSelector )
        val isAutoFillEnabled by rememberPreference(autoLoadSongsInQueueKey, true)
        val isDiscoverClickable = binder.service.nzikRadio.isRadioActive || isAutoFillEnabled
        val discover = Discover( isDiscoverClickable, onDiscoverClick )
        val repeat = Repeat.init()
        val deleteDialog = DeleteFromQueue( itemSelector ) {
            try {
                if( itemSelector.isEmpty() ) {
                    player.stop()
                    player.clearMediaItems()
                    Toaster.s( R.string.deleted )
                } else {
                    val count = itemSelector.size
                    val selectedIds = itemSelector.map { it.id }.toSet()
                    // Remove from end to avoid index shift
                    (player.mediaItemCount - 1 downTo 0)
                        .filter { player.getMediaItemAt(it).mediaId in selectedIds }
                        .forEach( player::removeMediaItem )
                    Toaster.s( R.string.deleted_item, formatArgs = *arrayOf( "$count" ) )
                }
            } catch( e: Exception ) {
                Timber.tag( "Queue" ).e( e, "Failed to delete from queue" )
            }

            itemSelector.isActive = false

            onDismiss()
        }
        val addToPlaylist = PlaylistsMenu.init(
            navController = navController,
            mediaItems = { getSongs().map( Song::asMediaItem ) },
            onFailure = { throwable, preview ->
                Timber.tag("Queue").e(throwable, "Failed to add songs to playlist ${preview.playlist.name}")
            },
            finalAction = {
                // Turn of selector clears the selected list
                itemSelector.isActive = false
            }
        )
        val queueArrow = QueueArrow { onDismiss( repeat.type ) }
        val locator = Locator( lazyListState, ::getSongs )

        // Dialog renders
        exportDialog.Render()
        (deleteDialog as Dialog).Render()

        val showDiscover = rememberPreference( showButtonPlayerDiscoverKey, false ).value
        val buttonsList = buildList {
            add( locator )
            add( search )
            if( showDiscover )
                add( discover )
            add( positionLock )
            add( repeat )
            add( shuffle )
            add( itemSelector )
            add( deleteDialog )
            add( addToPlaylist )
            add( exportDialog )
        }

        QueueToolBarState.mediaItemCount = player.mediaItemCount
        QueueToolBarState.buttons = buttonsList
        QueueToolBarState.queueArrow = queueArrow
        QueueToolBarState.onBarClick = { onDismiss( repeat.type ) }
        QueueToolBarState.isVisible = if (search.isVisible) 1 else 0

        DisposableEffect( Unit ) {
            onDispose { QueueToolBarState.reset() }
        }

        Column {
            val queueType by rememberPreference( queueTypeKey, QueueType.Modern )
            val backgroundAlpha = if( queueType == QueueType.Modern ) .5f else 1f
            val itemBackground = if ( queueType == QueueType.Modern ) androidx.compose.ui.graphics.Color.Transparent else colorPalette().background0

            LazyColumn(
                state = lazyListState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = WindowInsets(bottom = Dimensions.bottomSpacer).asPaddingValues(),
                modifier = Modifier.weight( 1f )
                                   .background(
                                       colorPalette().background0.copy( alpha = backgroundAlpha )
                                   )

            ) {
                itemsIndexed(
                    items = windowsOnDisplay,
                    key = { _, window -> window.uid.toString() }
                ) { _, window ->
                    val song = window.mediaItem.asSong
                    val index = windowsInQueue.indexOf(window)

                    val isLocal by remember { derivedStateOf { song.isLocal } }
                    val isDownloaded = isLocal || isDownloadedSong(song.id)

                    ReorderableItem(
                        reorderableLazyListState,
                        key = window.uid.toString()
                    ) { isDraggingItem ->
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                               .animateItem()
                        ) {
                            // Drag anchor
                            if ( !positionLock.isLocked() && search.inputValue.isEmpty() ) {
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
                                        ),
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

                            val mediaItem = song.asMediaItem
                            SwipeableQueueItem(
                                mediaItem = mediaItem,
                                backgroundColor = itemBackground,
                                onPlayNext = {
                                    val currentIndex = binder.player.currentMediaItemIndex
                                    val targetIndex = currentIndex + 1
                                    // if the song is already after the current, do nothing
                                    if (index > currentIndex) {
                                         // Only move if not already in the next position
                                         if (index != targetIndex) {
                                            binder.player.moveMediaItem(index, targetIndex)
                                        }
                                    } else {
                                        // If it's before or in the current position, move it to the next
                                        binder.player.moveMediaItem(index, targetIndex)
                                    }
                                },
                                onDownload = {
                                    binder.cache.removeResource(song.id)
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
                                    val actualIndex = player.findMediaItemIndexById( song.id )
                                    if (actualIndex >= 0 && actualIndex < player.mediaItemCount) {
                                        try {
                                            player.removeMediaItem( actualIndex )
                                            Toaster.s(
                                                context.resources.getString(R.string.deleted_item, song.cleanTitle())
                                            )
                                        } catch (e: IllegalArgumentException) {
                                            // Media item may have already been removed or index is invalid
                                            Timber.tag("Queue").e(e, "Failed to remove media item at index $actualIndex")
                                        }
                                    }
                                },
                                onEnqueue = {
                                    binder.player.enqueue(
                                        mediaItem,
                                        context
                                    )
                                }
                            ) {
                                SongItem(
                                    song = song,
                                    itemSelector = itemSelector,
                                    navController = navController,
                                    backgroundColor = itemBackground,
                                    trailingContent = {
                                        if( !positionLock.isLocked() )
                                        // Create a fake box to store drag anchor and checkbox
                                            Box( Modifier.width( 24.dp ) )
                                    },
                                    onClick = {
                                        if( player.isNowPlaying(song.id) ) {
                                            if(player.shouldBePlaying)
                                                player.pause()
                                            else
                                                player.play()
                                        } else {
                                            player.seekToDefaultPosition(index)
                                            player.prepare()
                                            player.playWhenReady = true
                                        }

                                        /*
                                            Due to the small size of checkboxes,
                                            we shouldn't disable [itemSelector]
                                         */

                                        search.hideIfEmpty()
                                    }
                                )
                            }
                        }
                    }
                }

                if( binder.isLoadingRadio ) {
                    item {
                        Column( Modifier.shimmer() ) {
                            repeat(3) { index ->
                                SongItemPlaceholder( Modifier.alpha( 1f - index * 0.125f ) )
                            }
                        }
                    }
                }
            }

            // Search box
            val searchBottomPadding by animateDpAsState(if (search.isVisible) 64.dp else 0.dp)
            Box(
                modifier = Modifier.fillMaxWidth()
                                   .background( colorPalette().background1 )
                                   .padding(bottom = searchBottomPadding)
                                   .imePadding(),
            ) { search.SearchBar( this@Column ) }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = lazyListState,
            modifier = Modifier.padding(bottom = Dimensions.miniPlayerHeight)
        )
    }
}








