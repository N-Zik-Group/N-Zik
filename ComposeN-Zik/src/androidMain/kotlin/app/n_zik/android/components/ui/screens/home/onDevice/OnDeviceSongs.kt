package app.n_zik.android.components.ui.screens.home.onDevice

import kotlinx.coroutines.flow.map
import app.n_zik.android.components.ui.screens.home.HomeFilterScrollAnchor
import app.n_zik.android.compose.persist.persist
import app.n_zik.android.core.database.Database
import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind

import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchNoResults

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.items.SongItemPlaceholder
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import app.n_zik.android.components.glass.PageButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import timber.log.Timber
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.n_zik.android.models.Song
import app.n_zik.android.typography
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.Preference.HOME_ON_DEVICE_SONGS_SORT_BY
import app.n_zik.android.utils.Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER
import app.n_zik.android.utils.homeSongsOnDeviceSortMenuOrderKey
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.bold
import app.n_zik.android.utils.enqueue
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.isAtLeastAndroid13
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.showFoldersOnDeviceKey
import app.n_zik.android.utils.defaultFolderKey
import app.n_zik.android.utils.getActiveProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import app.n_zik.android.components.rowLikeState
import app.n_zik.android.components.ui.screens.home.searchHomeSongs
import app.n_zik.android.utils.playlistindicatorKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.components.AppPullToRefreshBox
import app.n_zik.android.components.FolderItem
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.Sort
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.utils.PathUtils
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.enums.PlaylistSwipeAction
import app.n_zik.android.utils.playlistSwipeLeftActionKey
import app.n_zik.android.utils.playlistSwipeRightActionKey
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.exoplayer.offline.Download
import java.io.IOException

@UnstableApi
@ExperimentalFoundationApi
@OptIn(FlowPreview::class)
@Composable
fun OnDeviceSong(
    navController: NavController,
    lazyListState: LazyListState,
    itemSelector: ItemSelector<Song>,
    search: Search,
    buttons: MutableList<Button>,
    itemsOnDisplay: SnapshotStateList<Song>,
    headerPadding: androidx.compose.ui.unit.Dp = 0.dp,
    topContentInsetProvider: () -> androidx.compose.ui.unit.Dp = { 0.dp },
    visibleHeaderPadding: () -> androidx.compose.ui.unit.Dp = { headerPadding },
) {
    // Essentials
    val context = LocalContext.current
    val forceShimmer = forceLoadingShimmerPreview()
    val binder = LocalPlayerServiceBinder.current
    val profile = getActiveProfile(context)
    val lifecycleOwner = LocalLifecycleOwner.current

    //<editor-fold defaultstate="collapsed" desc="Settings">
    val parentalControlEnabled by rememberPreference( parentalControlEnabledKey, false )
    val showFolder4LocalSongs by rememberPreference( showFoldersOnDeviceKey, true )
    val startFolder by rememberPreference(defaultFolderKey, "/")
    val latestStartFolder by rememberUpdatedState(startFolder)
    //</editor-fold>

    var songsOnDevice by remember(profile) {
        mutableStateOf(emptyMap<Song, String>(), referentialEqualityPolicy())
    }
    var selectedFolder by remember(profile) {
        mutableStateOf<String?>(null)
    }
    val currentPath = selectedFolder.orEmpty()
    var pendingRefreshRevision by remember(profile) { mutableStateOf<Long?>(null) }

    //<editor-fold defaultstate="collapsed" desc="Permission handler">
    val permission = rememberSaveable {
        if( isAtLeastAndroid13 ) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var isPermissionGranted by remember { mutableStateOf(
        ContextCompat.checkSelfPermission( context, permission ) == PackageManager.PERMISSION_GRANTED
    ) }

    /** Opens a prompt saying that a permission (should be either [Manifest.permission.READ_MEDIA_AUDIO] or [Manifest.permission.READ_EXTERNAL_STORAGE]) Then apply result of that prompt. */
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        isPermissionGranted = it
        PermissionUsageJournal.record(context, PermissionFeature.LOCAL_LIBRARY, JournalPermission.MEDIA_AUDIO,
            if (it) PermissionEventKind.GRANTED else PermissionEventKind.DENIED)
    }

    /**
     * Starts new activity (should be [Settings.ACTION_APPLICATION_DETAILS_SETTINGS]).
     * Then wait until user exits the activity, check for permission changes.
     */
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isPermissionGranted = ContextCompat.checkSelfPermission( context, permission ) == PackageManager.PERMISSION_GRANTED
    }
    //</editor-fold>

    val odSort = Sort( HOME_ON_DEVICE_SONGS_SORT_BY, HOME_ON_DEVICE_SONGS_SORT_ORDER, homeSongsOnDeviceSortMenuOrderKey, "dev")

    var searchDataReady by remember(profile, isPermissionGranted, odSort.sortBy, odSort.sortOrder) { mutableStateOf(false) }
    var completedSearchQuery by remember { mutableStateOf<String?>(null) }
    var completedSearchPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lifecycleOwner, profile, isPermissionGranted, odSort.sortBy, odSort.sortOrder) {
        if (!isPermissionGranted) return@LaunchedEffect

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            localSongRefreshRequests.forProfile(profile).collectLatest { revision ->
                try {
                    context.getProfileLocalSongs(odSort.sortBy, odSort.sortOrder, profile)
                        .collect { songs ->
                            PermissionUsageJournal.record(context, PermissionFeature.LOCAL_LIBRARY, JournalPermission.MEDIA_AUDIO, PermissionEventKind.USED)
                            if (songs.isNotEmpty() || selectedFolder != null) {
                                selectedFolder = PathUtils.resolveFolder(songs.values, selectedFolder, latestStartFolder)
                            }
                            if (itemSelector.isNotEmpty()) {
                                val eligibleIds = songs.keys.mapTo(HashSet()) { it.id }
                                itemSelector.removeAll { it.id !in eligibleIds }
                            }
                            songsOnDevice = songs
                            searchDataReady = true
                        }
                } catch (error: SecurityException) {
                    Timber.tag("OnDeviceSongs").w(error, "Media access changed during scan")
                    isPermissionGranted = false
                    itemSelector.clear()
                } catch (error: Exception) {
                    if (error !is IOException && error !is IllegalArgumentException && error !is SQLiteException) throw error
                    Timber.tag("OnDeviceSongs").w(error, "Could not scan on-device music")
                    searchDataReady = true
                } finally {
                    if (pendingRefreshRevision?.let { revision >= it } == true) {
                        pendingRefreshRevision = null
                    }
                }
            }
        }
    }
    val songEntries by remember(profile) {
        derivedStateOf { songsOnDevice.entries.toList() }
    }
    val storedSongs by remember(profile) {
        Database.songTable.allOnDevice().map { songs -> songs.associateBy(Song::id) }
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val query = search.inputValue
    var baseSongs by remember(profile) { mutableStateOf(emptyList<Song>()) }
    var baseReady by remember(songEntries, storedSongs, currentPath, showFolder4LocalSongs, parentalControlEnabled, isPermissionGranted, searchDataReady) {
        mutableStateOf(false)
    }
    LaunchedEffect(songEntries, storedSongs, currentPath, showFolder4LocalSongs, parentalControlEnabled, isPermissionGranted, searchDataReady) {
        if (isPermissionGranted && !searchDataReady) return@LaunchedEffect
        val entries = songEntries
        val stored = storedSongs
        baseSongs = if (!isPermissionGranted) emptyList() else withContext(NzikDispatchers.DATA) {
            val coroutine = currentCoroutineContext()
            entries.asSequence()
                .filter {
                    coroutine.ensureActive()
                    !showFolder4LocalSongs || it.value == currentPath
                }
                .map { stored[it.key.id] ?: it.key }
                .filter { !parentalControlEnabled || !it.title.startsWith(EXPLICIT_PREFIX, true) }
                .toList()
        }
        baseReady = true
    }
    var displayedSongs by remember(profile, isPermissionGranted) { mutableStateOf<List<Song>>(emptyList()) }
    var hasCompletedDeviceProjection by remember(profile, isPermissionGranted) { mutableStateOf(false) }
    val filterScrollAnchor = persist("home/device-songs/filter-scroll", HomeFilterScrollAnchor()).value
    LaunchedEffect(baseReady, baseSongs, query, currentPath) {
        if (!baseReady) return@LaunchedEffect
        val candidates = baseSongs
        val visibleSongs = searchHomeSongs(candidates, query)
        if (displayedSongs != visibleSongs) {
            val index = lazyListState.firstVisibleItemIndex
            filterScrollAnchor.update(
                displayedSongs.isNotEmpty(), visibleSongs, Song::id,
                index, lazyListState.firstVisibleItemScrollOffset,
                lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key,
                lazyListState::requestScrollToItem,
            )
        }
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            displayedSongs = visibleSongs
            hasCompletedDeviceProjection = isPermissionGranted
            if (itemsOnDisplay != visibleSongs) {
                itemsOnDisplay.clear()
                itemsOnDisplay.addAll(visibleSongs)
            }
            completedSearchQuery = query
            completedSearchPath = currentPath
        }
    }
    val folders by remember(profile) {
        derivedStateOf {
            if (showFolder4LocalSongs) {
                PathUtils.getAvailablePaths(songsOnDevice.values, selectedFolder.orEmpty())
            } else emptyList()
        }
    }
    val initialScanPending = isPermissionGranted && !hasCompletedDeviceProjection && folders.isEmpty()
    SearchEmptyFeedback(
        query = search.inputValue,
        empty = itemsOnDisplay.isEmpty() && folders.isEmpty(),
        loading = !isPermissionGranted || !searchDataReady || !baseReady ||
            completedSearchQuery != search.inputValue || completedSearchPath != currentPath
    )
    LaunchedEffect( Unit ) {
        if( !isPermissionGranted )
            try {
                PermissionUsageJournal.record(context, PermissionFeature.LOCAL_LIBRARY, JournalPermission.MEDIA_AUDIO, PermissionEventKind.REQUESTED)
                permissionLauncher.launch( permission )
            } catch ( e: Exception ) {
                Timber.tag("OnDeviceSongs").w(e, "Could not request media access")
                Toaster.e(R.string.library_on_device_permission_request_failed)
            }
    }



    val songIds = remember(baseSongs) { baseSongs.map(Song::id) }
    val showPlaylistIndicator by rememberPreference(playlistindicatorKey, false)
    val playlistStatesMap by remember(songIds, showPlaylistIndicator) {
        if (showPlaylistIndicator) PlaylistStateManager.getPlaylistStates(songIds) else flowOf(emptyMap())
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val playbackScope = rememberCoroutineScope()
    var playbackJob by remember { mutableStateOf<Job?>(null) }

    AppPullToRefreshBox(
        isRefreshing = pendingRefreshRevision != null,
        onRefresh = {
            if (isPermissionGranted) {
                pendingRefreshRevision = localSongRefreshRequests.request(profile)
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
    // Hoisted swipe action preferences
    val playlistSwipeLeftAction by rememberPreference(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
    val playlistSwipeRightAction by rememberPreference(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)

    LazyColumn(
        state = lazyListState,
        userScrollEnabled = songsOnDevice.isNotEmpty(),
        contentPadding = rememberPageChromeContentPadding(
            topInsetProvider = topContentInsetProvider,
            base = PaddingValues(bottom = Dimensions.bottomSpacer),
            extraTopProvider = visibleHeaderPadding,
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        if( !isPermissionGranted ) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillParentMaxSize()
                ) {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.locked),
                            tint = colorPalette().textDisabled,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize( .4f )
                        )

                        BasicText(
                            text = stringResource( R.string.library_local_media_permission_message ),
                            style = typography().m.copy( color = colorPalette().textDisabled )
                        )

                        Spacer( Modifier.height( 20.dp ) )

                        PageButton(
                            border = BorderStroke( 2.dp, colorPalette().accent ),
                            colors = ButtonDefaults.buttonColors().copy( containerColor = Color.Transparent ),
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null )
                                    }
                                    settingsLauncher.launch( intent )
                                } catch ( e: Exception ) {
                                    Timber.tag("OnDeviceSongs").w(e, "Could not open permission settings")
                                    Toaster.e(R.string.library_on_device_permission_settings_failed)
                                }
                            }
                        ) {
                            BasicText(
                                text = stringResource( R.string.visualizer_action_open_permission_settings ),
                                style = typography().l.bold.copy( color = colorPalette().accent )
                            )
                        }
                    }
                }
            }
        } else {
            if (showFolder4LocalSongs && (songsOnDevice.isNotEmpty() || initialScanPending)) {
                item( "folder_paths" ) {
                    PathUtils.AddressBar(
                        currentPath = currentPath,
                        enabled = !initialScanPending,
                        onSpecificAddressClick = { selectedFolder = it }
                    )
                }

            items(
                items = folders,
                key = { "folder:${PathUtils.childPath(currentPath, it)}" }
            ) {
                Box(Modifier.animateItem()) {
                    FolderItem(it, shimmer = forceShimmer) { selectedFolder = PathUtils.childPath(currentPath, it) }
                }
            }
        }

        if (initialScanPending) {
            if (showFolder4LocalSongs) {
                items(4, key = { "loading-device-folder:$it" }) { FolderItem(text = null, shimmer = true) }
            } else {
                items(4, key = { "loading-device-song:$it" }) { SongItemPlaceholder(onDevice = true) }
            }
        }

        if (hasCompletedDeviceProjection && displayedSongs.isEmpty() && folders.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (search.inputValue.isBlank()) SearchNoResults(text = stringResource(R.string.common_empty_items))
                }
            }
        }

        items(
            items = displayedSongs,
            key = { song -> song.id }
        ) { song ->
            if (forceShimmer) {
                SongItemPlaceholder(title = song.cleanTitle(), artist = song.cleanArtistsText(), onDevice = true)
                return@items
            }
            val mediaItem = remember(song) { song.asMediaItem }

            Box(Modifier.animateItem()) {
                SwipeablePlaylistItem(
                    mediaItem = mediaItem,
                    onPlayNext = { binder?.player?.addNext( mediaItem ) },
                    onEnqueue = {
                        binder?.player?.enqueue(mediaItem)
                    },
                    downloadStateParam = Download.STATE_STOPPED,
                    downloadedStateMediaParam = DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED,
                    swipeLeftActionParam = playlistSwipeLeftAction,
                    swipeRightActionParam = playlistSwipeRightAction
                ) {
                    SongItem(
                        song = song,
                        isLiked = song.rowLikeState(),
                        observeLikeState = false,
                        observeStoredMetadata = false,
                        inPlaylist = playlistStatesMap[song.id] == true,
                        itemSelector = itemSelector,
                        navController = navController,
                        onClick = {
                            search.hideIfEmpty()
                            val playerBinder = binder
                            if (playerBinder != null) {
                                val targets = itemSelector.toList().ifEmpty { itemsOnDisplay.toList() }
                                playbackJob?.cancel()
                                playbackJob = playbackScope.launch {
                                    val mediaItems = withContext(NzikDispatchers.MEDIA) {
                                        targets.filter(Song::isLocal).map(Song::asMediaItem)
                                    }
                                    val index = mediaItems.indexOfFirst { it.mediaId == song.id }
                                    if (index >= 0) {
                                        playerBinder.stopRadio()
                                        playerBinder.player.forcePlayAtIndex(mediaItems, index)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
        }
    }
    }
}

