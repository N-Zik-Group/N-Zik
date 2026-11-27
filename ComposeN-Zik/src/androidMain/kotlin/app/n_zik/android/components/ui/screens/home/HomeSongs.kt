package app.n_zik.android.components.ui.screens.home

import app.n_zik.android.compose.persist.persist
import app.n_zik.android.utils.compactPlayTimeLabel
import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewDemo
import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import app.n_zik.android.components.search.SearchEmptyFeedback
import app.n_zik.android.components.search.SearchNoResults

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.cache
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.util.fastFilter
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.enums.BuiltInPlaylist
import app.n_zik.android.enums.DurationInMinutes
import app.n_zik.android.enums.FilterBy
import app.n_zik.android.enums.MaxTopPlaylistItems
import app.n_zik.android.enums.RecommendationsNumber
import app.n_zik.android.enums.SongSortBy
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.onOverlay
import app.n_zik.android.ui.styling.overlay
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.clip
import app.n_zik.android.utils.Preference.HOME_SONGS_SORT_BY
import app.n_zik.android.utils.Preference.HOME_SONGS_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_SONGS_DISLIKED_SORT_BY
import app.n_zik.android.utils.Preference.HOME_SONGS_DISLIKED_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_SONGS_FAVORITES_SORT_BY
import app.n_zik.android.utils.Preference.HOME_SONGS_FAVORITES_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_SONGS_OFFLINE_SORT_BY
import app.n_zik.android.utils.Preference.HOME_SONGS_OFFLINE_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_SONGS_DOWNLOADED_SORT_BY
import app.n_zik.android.utils.Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_SONGS_TOP_SORT_BY
import app.n_zik.android.utils.Preference.HOME_SONGS_TOP_SORT_ORDER
import app.n_zik.android.utils.Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD
import app.n_zik.android.utils.MaxTopPlaylistItemsCustomValueKey
import app.n_zik.android.utils.MaxTopPlaylistItemsKey
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.align
import app.n_zik.android.playback.utils.asSmartShuffleRecommendation
import app.n_zik.android.playback.utils.smartShuffleInsertionPositions
import app.n_zik.android.playback.utils.smartShuffleRecommendationIds
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.center
import app.n_zik.android.utils.color
import app.n_zik.android.utils.durationTextToMillis
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.excludeSongsWithDurationLimitKey
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.homeSongsAllSortMenuOrderKey
import app.n_zik.android.utils.homeSongsCachedSortMenuOrderKey
import app.n_zik.android.utils.homeSongsDislikedSortMenuOrderKey
import app.n_zik.android.utils.homeSongsDownloadedSortMenuOrderKey
import app.n_zik.android.utils.homeSongsFavoritesSortMenuOrderKey
import app.n_zik.android.utils.homeSongsTopSortMenuOrderKey
import app.n_zik.android.utils.manageDownload
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.recommendationsNumberKey
import app.n_zik.android.utils.playlistindicatorKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.themed.n_zik.component.playlist.PositionLock
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.bridge.library.SongRecommendations
import app.n_zik.android.colorPalette
import app.n_zik.android.components.rememberSongRowArtistCredits
import app.n_zik.android.components.SongItem
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.items.SongItemPlaceholders
import app.n_zik.android.components.Sort
import app.n_zik.android.components.song.PeriodSelector
import app.n_zik.android.components.tab.HiddenSongs
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.components.tab.Search

import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.artistEntryNames
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.core.database.ext.FormatWithSong
import app.n_zik.android.playback.services.hasCachedStream
import app.n_zik.android.utils.autosyncLikesKey
import app.n_zik.android.utils.importYTMLikedSongs
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshotFlow
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.isUnmatched
import app.n_zik.android.enums.PlaylistSwipeAction
import app.n_zik.android.utils.playlistSwipeLeftActionKey
import app.n_zik.android.utils.playlistSwipeRightActionKey
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.relatedSongs
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import app.n_zik.android.core.ondevice.DeviceLibrarySnapshot
import app.n_zik.android.core.ondevice.observeDeviceLibrary
import app.n_zik.android.components.LocalSongRowCachedIds
import app.n_zik.android.components.LocalSongRowDownloads
import app.n_zik.android.components.rememberSongRowCache
import app.n_zik.android.components.rememberSongRowDownloads
import app.n_zik.android.components.rememberSongRowMetadata
import app.n_zik.android.components.rowLikeState
import app.n_zik.android.components.song.resolveSongDisplayMetadata
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import timber.log.Timber
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.utils.excludeDislikedSongsKey
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon

private const val TAG = "SmartShuffle"

@UnstableApi
@ExperimentalFoundationApi
@Composable
fun HomeSongs(
    navController: NavController,
    builtInPlaylist: BuiltInPlaylist,
    lazyListState: LazyListState,
    itemSelector: ItemSelector<Song>,
    search: Search,
    buttons: MutableList<Button>,
    itemsOnDisplay: SnapshotStateList<Song>,
    recommendationsOnDisplay: MutableState<Set<String>>,
    matchButton: Button? = null,
    onRecommendationCountChange: (Int) -> Unit = {},
    onRecommendationsLoadingChange: (Boolean) -> Unit = {},
    isRecommendationEnabled: Boolean = false,
    refreshKey: Int = 0,
    onMatchClick: (() -> Unit)? = null,
    headerPadding: androidx.compose.ui.unit.Dp = 0.dp,
    visibleHeaderPadding: () -> androidx.compose.ui.unit.Dp = { headerPadding },
    topContentInsetProvider: () -> androidx.compose.ui.unit.Dp = { 0.dp },
    sourceFilter: FilterBy = FilterBy.All,
    includeDeviceSongs: Boolean = false,
) {
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current

    val parentalControlEnabled by rememberPreference( parentalControlEnabledKey, false )
    val maxTopPlaylistItems by rememberPreference( MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10` )
    val maxTopPlaylistItemsCustomValue by rememberPreference( MaxTopPlaylistItemsCustomValueKey, 10 )
    val excludeSongWithDurationLimit by rememberPreference( excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled )
    val showDislikedPlaylist by rememberPreference(excludeDislikedSongsKey, DislikeMode.Enabled)

    val preview = LocalThemePreview.current
    var items by remember(preview) { mutableStateOf(if (preview != null) ThemePreviewDemo.songs else emptyList()) }

    val downloads = rememberSongRowDownloads()
    val playbackScope = rememberCoroutineScope()
    var playbackJob by remember { mutableStateOf<Job?>(null) }

    val songSort = when( builtInPlaylist ) {
        BuiltInPlaylist.Favorites -> Sort( HOME_SONGS_FAVORITES_SORT_BY, HOME_SONGS_FAVORITES_SORT_ORDER, homeSongsFavoritesSortMenuOrderKey, "favs" )
        BuiltInPlaylist.Offline -> Sort( HOME_SONGS_OFFLINE_SORT_BY, HOME_SONGS_OFFLINE_SORT_ORDER, homeSongsCachedSortMenuOrderKey, "off" )
        BuiltInPlaylist.Downloaded -> Sort( HOME_SONGS_DOWNLOADED_SORT_BY, HOME_SONGS_DOWNLOADED_SORT_ORDER, homeSongsDownloadedSortMenuOrderKey, "dl" )
        BuiltInPlaylist.Top -> Sort( HOME_SONGS_TOP_SORT_BY, HOME_SONGS_TOP_SORT_ORDER, homeSongsTopSortMenuOrderKey, "top" )
        BuiltInPlaylist.Disliked -> Sort( HOME_SONGS_DISLIKED_SORT_BY, HOME_SONGS_DISLIKED_SORT_ORDER, homeSongsDislikedSortMenuOrderKey, "disliked" )
        else -> Sort( HOME_SONGS_SORT_BY, HOME_SONGS_SORT_ORDER, homeSongsAllSortMenuOrderKey, "all" )
    }
    songSort.onDeviceCriterionAvailable = includeDeviceSongs && sourceFilter != FilterBy.YoutubeLibrary
    val groupByDevice = songSort.sortBy == SongSortBy.OnDevice
    val databaseOrder = if (groupByDevice) SortOrder.Ascending else songSort.sortOrder
    val needsDownloadedIds = builtInPlaylist == BuiltInPlaylist.Downloaded || songSort.sortBy == SongSortBy.Downloaded
    val downloadedIds by remember(needsDownloadedIds, preview, downloads) {
        if (!needsDownloadedIds || preview != null) flowOf(emptySet<String>())
        else snapshotFlow { downloads.completedIds() }
    }.collectAsStateWithLifecycle(emptySet(), context = NzikDispatchers.DATA)

    val positionLock = remember( songSort.sortOrder ) { PositionLock(songSort.sortOrder) }
    val topPlaylists = PeriodSelector( HOME_SONGS_TOP_PLAYLIST_PERIOD, homeSongsTopSortMenuOrderKey, "top" )
    val hiddenSongs = HiddenSongs()

    // Auto-sync YTM liked songs when Favorites tab is displayed
    val doAutoSync by rememberPreference(autosyncLikesKey, false)
    var justSynced by rememberSaveable { mutableStateOf(!doAutoSync) }
    LaunchedEffect(justSynced, doAutoSync, builtInPlaylist) {
        if (builtInPlaylist == BuiltInPlaylist.Favorites && !justSynced && isYouTubeSyncEnabled()) {
            if (importYTMLikedSongs()) justSynced = true
        }
    }

    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect( itemSelector.isActive ) {
        if( itemSelector.isActive )
            positionLock.isFirstIcon = true
    }
    LaunchedEffect( positionLock.isFirstIcon ) {
        if( !positionLock.isFirstIcon ) {
            itemSelector.isActive = false
        }
    }

    val recommendationsNumber by rememberPreference( recommendationsNumberKey, RecommendationsNumber.Adaptive )
    var relatedSongs by remember { mutableStateOf(emptyList<Song>()) }
    var relatedSongsPositions by remember { mutableStateOf(emptyMap<Song, Int>()) }
    var isRecommendationsLoading by remember { mutableStateOf(false) }

    val topLimit = maxTopPlaylistItems.toInt(maxTopPlaylistItemsCustomValue)
    LaunchedEffect( builtInPlaylist, topPlaylists.period, topLimit, excludeSongWithDurationLimit, songSort.sortBy, songSort.sortOrder, hiddenSongs.isFirstIcon, refreshKey, sourceFilter, includeDeviceSongs,
        binder.takeIf { builtInPlaylist == BuiltInPlaylist.Offline }, downloadedIds ) {
        if (preview != null) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true

        fun List<Song>.sortByDownload(): List<Song> =
            if (songSort.sortOrder == SortOrder.Ascending) sortedByDescending { it.id in downloadedIds }
            else sortedBy { it.id in downloadedIds }

        val excludeHidden = hiddenSongs.isHiddenExcluded()
        val retrievedSongs = when( builtInPlaylist ) {
            BuiltInPlaylist.All, BuiltInPlaylist.Disliked -> {
                val dislikedOnly = builtInPlaylist == BuiltInPlaylist.Disliked
                if (songSort.sortBy == SongSortBy.Downloaded) {
                    Database.songTable
                        .sortAll(
                            SongSortBy.Title, SortOrder.Ascending,
                            excludeHidden = excludeHidden && !includeDeviceSongs, dislikedOnly = dislikedOnly,
                        )
                        .map { it.sortByDownload() }
                } else {
                    Database.songTable.sortAll(
                        songSort.sortBy, databaseOrder,
                        excludeHidden = excludeHidden && !includeDeviceSongs, dislikedOnly = dislikedOnly,
                    )
                }
            }

            BuiltInPlaylist.Downloaded -> {
                Database.songTable
                    .sortAll( songSort.sortBy, songSort.sortOrder )
                    .map { list ->
                        list.fastFilter { it.id in downloadedIds }
                    }
            }

            BuiltInPlaylist.Offline -> Database.formatTable
                .sortAllWithSongs(
                    songSort.sortBy,
                    if (songSort.sortBy == SongSortBy.Downloaded) SortOrder.Ascending else songSort.sortOrder,
                    excludeHidden = hiddenSongs.isHiddenExcluded(),
                )
                .map { list ->
                    val cachedSongs = list.fastFilter {
                        binder?.cache?.hasCachedStream(it.song.id, it.format.contentLength) == true
                    }.map( FormatWithSong::song )
                    if (songSort.sortBy == SongSortBy.Downloaded) cachedSongs.sortByDownload() else cachedSongs
                }

            BuiltInPlaylist.Favorites -> {
                if (songSort.sortBy == SongSortBy.Downloaded) {
                    Database.songTable.sortFavorites( SongSortBy.Title, SortOrder.Ascending )
                        .map { it.sortByDownload() }
                } else {
                    Database.songTable.sortFavorites( songSort.sortBy, databaseOrder )
                }
            }

            BuiltInPlaylist.Top -> Database.eventTable.findSongsMostPlayedBetween(
                from = topPlaylists.period.timeStampInMillis(),
                limit = if (includeDeviceSongs) Int.MAX_VALUE else topLimit,
            )

            BuiltInPlaylist.OnDevice -> flowOf( emptyList() )

        }

        val selectedPlaylist = builtInPlaylist
        val librarySongs = if (includeDeviceSongs) {
            val deviceLibrary = context.observeDeviceLibrary().onStart {
                if (!groupByDevice) emit(DeviceLibrarySnapshot())
            }
            retrievedSongs.combine(deviceLibrary) { songs, device ->
                projectHomeLibrarySongs(songs, device.songIds, excludeHidden = selectedPlaylist == BuiltInPlaylist.All && excludeHidden)
            }
        } else retrievedSongs.map { it.filterNot(Song::isLocal) }

        librarySongs.map { songs ->
                val tabSongs = if (selectedPlaylist == BuiltInPlaylist.Top) {
                    val rankedSongs = if (includeDeviceSongs) songs.take(topLimit) else songs
                    val durationFiltered = rankedSongs.fastFilter { song ->
                        excludeSongWithDurationLimit == DurationInMinutes.Disabled ||
                            song.durationText?.let { durationTextToMillis(it) < excludeSongWithDurationLimit.asMillis } == true
                    }
                    if (songSort.sortBy == SongSortBy.Downloaded) durationFiltered.sortByDownload() else durationFiltered
                } else songs
                when(sourceFilter) {
                    FilterBy.All -> tabSongs
                    FilterBy.YoutubeLibrary -> tabSongs.filter { song -> song.isYoutubeSong }
                    FilterBy.Local -> tabSongs.filterNot { song -> song.isYoutubeSong }
                }.let { list ->
                    if (selectedPlaylist != BuiltInPlaylist.Disliked) {
                        list.filter { song -> song.likedAt != -1L }
                    } else list
                }
            }
            .flowOn(NzikDispatchers.DATA)
            .distinctUntilChanged()
            .collect { songs ->
                items = songs
                isLoading = false
            }
    }

    LaunchedEffect(isRecommendationEnabled, items) {
        val remoteSongs = items.filterNot(Song::isLocal)
        if (!isRecommendationEnabled || remoteSongs.isEmpty()) {
            relatedSongs = emptyList()
            isRecommendationsLoading = false
            onRecommendationsLoadingChange(false)
            return@LaunchedEffect
        }

        if (relatedSongs.isNotEmpty() &&
            relatedSongs.size >= recommendationsNumber.calculateAdaptiveRecommendations(items.size) * 0.8) {
            return@LaunchedEffect
        }

        isRecommendationsLoading = true
        onRecommendationsLoadingChange(true)

        val targetRecommendations = recommendationsNumber.calculateAdaptiveRecommendations(items.size)
        val allRelatedSongs = mutableListOf<Song>()
        val existingSongIds = items.map { it.id }.toSet()

        val numberOfRequests = when {
            items.size <= 100 -> 1
            items.size <= 500 -> 3
            items.size <= 1000 -> 5
            items.size <= 2000 -> 8
            else -> 10
        }

        val seedSongs = remoteSongs.shuffled().take(numberOfRequests)

        for (seedSong in seedSongs) {
            try {
                val relatedSongsResult = when (val result = Innertube.relatedSongs(videoId = seedSong.id)) {
                    null -> {
                        // null result only happens on cancellation
                        Timber.tag(TAG).w("relatedSongs was cancelled for seed %s", seedSong.id)
                        null
                    }
                    else -> if (result.isFailure) {
                        Timber.tag(TAG).e(result.exceptionOrNull(), "relatedSongs failed for seed %s", seedSong.id)
                        null
                    } else {
                        result.getOrNull()
                    }
                }

                if (relatedSongsResult != null) {
                    val songs = relatedSongsResult.songs
                    if (songs.isNullOrEmpty()) {
                        Timber.tag(TAG).w("0 recommandation pour seed %s", seedSong.id)
                    } else {
                        songs.forEach { songItem ->
                            songItem.info?.let { info ->
                                info.endpoint?.videoId?.let { videoId ->
                                    if (!existingSongIds.contains(videoId)) {
                                        if (parentalControlEnabled && songItem.explicit) return@let
                                        val song = Song(
                                            // raw videoId + "e:" title prefix, as in Innertube.SongItem.asSong
                                            id = videoId,
                                            title = (if (songItem.explicit) EXPLICIT_PREFIX else "") + (info.name ?: ""),
                                            artistsText = songItem.authors.artistEntryNames().joinToString(", "),
                                            durationText = songItem.durationText,
                                            thumbnailUrl = songItem.thumbnail?.url
                                        )

                                        if (!allRelatedSongs.any { it.id == song.id }) {
                                            allRelatedSongs.add(song)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (numberOfRequests > 1) delay(200L)
            } catch (e: Exception) {
                // Rethrow cancellation so the LaunchedEffect stops instead of
                // storming the log with one line per remaining seed
                if (e is CancellationException) throw e
                Timber.tag(TAG).e(e, "seed %s threw while fetching recommendations", seedSong.id)
                continue
            }
        }

        relatedSongs = allRelatedSongs.take(targetRecommendations)
        relatedSongsPositions = smartShuffleInsertionPositions(items.size, relatedSongs)

        isRecommendationsLoading = false
        onRecommendationsLoadingChange(false)
    }

    // Contract §10.1 (since 1.12.0, `library.recommendations`): the PC bridge shows the same
    // recommendations at the same positions — published as displayed, cleared when switched off
    LaunchedEffect( builtInPlaylist, isRecommendationEnabled, relatedSongsPositions ) {
        if (isRecommendationEnabled && relatedSongsPositions.isNotEmpty()) {
            SongRecommendations.publish( builtInPlaylist, relatedSongsPositions )
        } else {
            SongRecommendations.clear()
        }
    }
    DisposableEffect( Unit ) {
        onDispose { SongRecommendations.clear() }
    }

    var completedSearchQuery by remember { mutableStateOf<String?>(null) }
    var hasCompletedSongProjection by persist("home/streamed-songs/loaded", false)
    val filterScrollAnchor = persist("home/streamed-songs/filter-scroll", HomeFilterScrollAnchor()).value
    val recommendationIds = remember(relatedSongs) { relatedSongs.map(Song::id) }
    val storedRecommendations by rememberSongRowMetadata(recommendationIds)
    val displayRecommendations = remember(relatedSongsPositions, storedRecommendations) {
        relatedSongsPositions.mapKeys { (song, _) -> resolveSongDisplayMetadata(song, storedRecommendations[song.id]) }
    }
    val baseInput = HomeSongsBaseInput(
        items = items,
        recommendationsEnabled = isRecommendationEnabled,
        recommendations = displayRecommendations,
        parentalControlEnabled = parentalControlEnabled,
        deviceSortOrder = songSort.sortOrder.takeIf { groupByDevice },
    )
    var baseSongs by remember { mutableStateOf(emptyList<Song>()) }
    var baseRecommendationIds by remember { mutableStateOf(emptySet<String>()) }
    var baseReady by remember(baseInput, isLoading) { mutableStateOf(false) }
    LaunchedEffect(baseInput, isLoading) {
        if (isLoading) return@LaunchedEffect
        baseSongs = projectHomeSongsBase(baseInput)
        baseRecommendationIds = if (baseInput.recommendationsEnabled) {
            smartShuffleRecommendationIds(baseInput.items, baseInput.recommendations)
        } else emptySet()
        baseReady = true
    }
    val query = search.filterQuery
    LaunchedEffect(baseReady, baseSongs, baseRecommendationIds, query) {
        if (!baseReady) return@LaunchedEffect
        val candidates = baseSongs
        val newItems = searchHomeSongs(candidates, query)
        if (itemsOnDisplay != newItems) {
            val index = lazyListState.firstVisibleItemIndex
            filterScrollAnchor.update(
                itemsOnDisplay.isNotEmpty(), newItems, Song::id,
                index, lazyListState.firstVisibleItemScrollOffset,
                lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key,
                lazyListState::requestScrollToItem,
            )
        }
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            if (itemsOnDisplay != newItems) {
                itemsOnDisplay.clear()
                itemsOnDisplay.addAll(newItems)
            }
            recommendationsOnDisplay.value = baseRecommendationIds
            completedSearchQuery = query
            hasCompletedSongProjection = true
        }
    }

    LaunchedEffect( relatedSongs.size, isRecommendationEnabled ) {
        if (isRecommendationEnabled) {
            onRecommendationCountChange(relatedSongs.size)
        } else {
            onRecommendationCountChange(0)
        }
    }

    val hasUnmatchedSongs by remember {
        derivedStateOf {
            itemsOnDisplay.any { (it.id.length != 11 || (it.durationText == "00:00" && it.totalPlayTimeMs == 1L)) && !it.id.startsWith( LOCAL_KEY_PREFIX ) }
        }
    }

    val localMatchButton = remember {
        object : MenuIcon,
            Descriptive {
            override val iconId: Int = R.drawable.alert
            override val messageId: Int = R.string.song_matching_action_open
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() { onMatchClick?.invoke() }
            override fun onLongClick() {}
        }
    }



    val hapticFeedback = LocalHapticFeedback.current
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = lazyListState
    ) { from, to ->
        val fromIndex = itemsOnDisplay.indexOfFirst { it.id == from.key }
        val toIndex = itemsOnDisplay.indexOfFirst { it.id == to.key }

        if (fromIndex != -1 && toIndex != -1) {
            val movedSong = itemsOnDisplay.removeAt(fromIndex)
            itemsOnDisplay.add(toIndex, movedSong)
        }
    }

    val showNoItems by remember {
        derivedStateOf {
            !isLoading && itemsOnDisplay.isEmpty() && (items.isEmpty() || search.filterQuery.isNotEmpty())
        }
    }

    val forceShimmer = forceLoadingShimmerPreview()
    val resultsPending = !baseReady || completedSearchQuery != search.filterQuery
    val showInitialSongPlaceholders = !hasCompletedSongProjection && itemsOnDisplay.isEmpty()
    SearchEmptyFeedback(
        query = search.filterQuery,
        empty = showNoItems,
        loading = resultsPending || forceShimmer,
    )

    val songIds = remember(baseSongs) { baseSongs.map(Song::id) }
    val artistCredits by rememberSongRowArtistCredits(songIds)
    val cachedSongIds by rememberSongRowCache(songIds)
    val showPlaylistIndicator by rememberPreference(playlistindicatorKey, false)
    val playlistStatesMap by remember(songIds, preview, showPlaylistIndicator) {
        if (preview != null || !showPlaylistIndicator) flowOf(emptyMap())
        else PlaylistStateManager.getPlaylistStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    // Hoisted swipe action preferences
    val playlistSwipeLeftAction by rememberPreference(playlistSwipeLeftActionKey, PlaylistSwipeAction.Favourite)
    val playlistSwipeRightAction by rememberPreference(playlistSwipeRightActionKey, PlaylistSwipeAction.PlayNext)

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalSongRowDownloads provides downloads,
            LocalSongRowCachedIds provides cachedSongIds,
        ) {
            LazyColumn(
                state = if (showInitialSongPlaceholders) rememberLazyListState() else lazyListState,
                userScrollEnabled = !isLoading || itemsOnDisplay.isNotEmpty(),
                contentPadding = rememberPageChromeContentPadding(
                    topInsetProvider = topContentInsetProvider,
                    base = PaddingValues(bottom = Dimensions.bottomSpacer),
                    extraTopProvider = visibleHeaderPadding,
                ),
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {

                if (showInitialSongPlaceholders) {
                    item(key = "song_loading") { SongItemPlaceholders(count = HomePlaceholderCount) }
                }
                itemsIndexed(
                    items = itemsOnDisplay.toList(),
                    key = { _, song -> song.id }
                ) { index, song ->
                    if (forceShimmer) {
                        SongItemPlaceholder(title = song.cleanTitle(), artist = song.cleanArtistsText())
                        return@itemsIndexed
                    }
                    ReorderableItem(
                        reorderableLazyListState,
                        key = song.id
                    ) { isDraggingItem ->
                        val isRecommended = song.id in recommendationsOnDisplay.value
                        val mediaItem = remember(song, isRecommended) { song.asMediaItem.asSmartShuffleRecommendation(isRecommended) }
                        val isLocal = mediaItem.isLocal
                        val isDownloaded = isLocal || downloads.isDownloaded(mediaItem.mediaId)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(2f)
                        ) {
                            SwipeablePlaylistItem(
                                mediaItem = mediaItem,
                                onPlayNext = { binder?.player?.addNext( mediaItem ) },
                                onDownload = {
                                    if (!isLocal) {
                                        binder?.cache?.removeResource(mediaItem.mediaId)
                                        Database.asyncTransaction {
                                            formatTable.updateContentLengthOf( mediaItem.mediaId )
                                        }
                                        manageDownload(
                                            context = context,
                                            mediaItem = mediaItem,
                                            downloadState = isDownloaded
                                        )
                                    }
                                },
                                onEnqueue = { binder?.player?.enqueue(mediaItem) },
                                downloadStateParam = downloads.rawState(mediaItem.mediaId),
                                downloadedStateMediaParam = downloads.mediaState(mediaItem.mediaId, song.id in cachedSongIds),
                                swipeLeftActionParam = playlistSwipeLeftAction,
                                swipeRightActionParam = playlistSwipeRightAction
                            ) {
                                SongItem(
                                    song = song,
                                    isLiked = song.rowLikeState(),
                                    observeLikeState = false,
                                    observeStoredMetadata = false,
                                    artistCredits = artistCredits[song.id].orEmpty(),
                                    inPlaylist = playlistStatesMap[song.id] == true,
                                    itemSelector = itemSelector,
                                    navController = navController,
                                    isRecommended = isRecommended,
                                    sourceMediaItem = if (isRecommended) ({ mediaItem }) else null,
                                    modifier = Modifier.animateItem(),
                                    trailingContent = {
                                        if (preview == null && song.isUnmatched) {
                                            Icon(
                                                painter = painterResource(R.drawable.alert),
                                                contentDescription = stringResource(R.string.song_matching_unmatched_label),
                                                tint = Color(0xFFFF9800),
                                                modifier = Modifier.padding(start = 8.dp).size(18.dp)
                                            )
                                        }
                                        if( builtInPlaylist != BuiltInPlaylist.Top && !positionLock.isLocked() && songSort.sortBy == SongSortBy.Custom && songSort.sortOrder == SortOrder.Ascending )
                                            Box( Modifier.width( 24.dp ) )
                                    },
                                    thumbnailOverlay = {
                                        if ( songSort.sortBy == SongSortBy.PlayTime || songSort.sortBy == SongSortBy.RelativePlayTime || songSort.sortBy == SongSortBy.PlayCount || builtInPlaylist == BuiltInPlaylist.Top ) {
                                            var text = if (songSort.sortBy == SongSortBy.PlayCount) {
                                                song.playCount.toString()
                                            } else {
                                                compactPlayTimeLabel(song.totalPlayTimeMs)
                                            }
                                            var typography = typography().xxs
                                            var alignment = Alignment.BottomCenter
                                            if( builtInPlaylist == BuiltInPlaylist.Top ) {
                                                text = (index + 1).toString()
                                                typography = typography().m
                                                alignment = Alignment.Center
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(thumbnailShape())
                                                    .background(colorPalette().overlay)
                                            ) {
                                                BasicText(
                                                    text = text,
                                                    style = typography.semiBold.center.color(colorPalette().onOverlay),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        .align(alignment)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        search.hideIfEmpty()
                                        if (preview == null && song.isUnmatched) {
                                            Toaster.w(R.string.player_matching_required_message)
                                        } else {
                                            val playerBinder = binder
                                            if (playerBinder != null) {
                                                val targets = itemSelector.actionTargets(
                                                    itemsOnDisplay.toList(), Song::id, Song::id, selectionOrder = true,
                                                )
                                                if (itemSelector.isActive && targets.none { it.id == song.id }) return@SongItem
                                                val recommendationIds = recommendationsOnDisplay.value
                                                playbackJob?.cancel()
                                                playbackJob = playbackScope.launch {
                                                    val mediaItems = withContext(NzikDispatchers.MEDIA) {
                                                        targets.filter { includeDeviceSongs || !it.isLocal }.map {
                                                            it.asMediaItem.asSmartShuffleRecommendation(it.id in recommendationIds)
                                                        }
                                                    }
                                                    val startIndex = mediaItems.indexOfFirst { it.mediaId == song.id }
                                                    if (startIndex >= 0) {
                                                        playerBinder.stopRadio()
                                                        playerBinder.player.forcePlayAtIndex(mediaItems, startIndex)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }

                            if ( builtInPlaylist != BuiltInPlaylist.Top && !positionLock.isLocked() && songSort.sortBy == SongSortBy.Custom && songSort.sortOrder == SortOrder.Ascending ) {
                                Icon(
                                    painter = painterResource( R.drawable.reorder ),
                                    contentDescription = null,
                                    tint = colorPalette().textSecondary,
                                    modifier = Modifier
                                        .align( Alignment.CenterEnd )
                                        .draggableHandle(
                                            onDragStarted = { hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress) },
                                            onDragStopped = {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val currentItems = itemsOnDisplay.toList()
                                                Database.asyncTransaction {
                                                    currentItems.forEachIndexed { index, song ->
                                                        songTable.updatePosition( song.id, index )
                                                    }
                                                }
                                            }
                                        )
                                        .padding(end = 12.dp)
                                        .size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

        }

        if (showNoItems && !showInitialSongPlaceholders) {
            Box(
                modifier = Modifier.fillMaxSize().padding(rememberPageChromeContentPadding(topInsetProvider = topContentInsetProvider, base = PaddingValues(top = visibleHeaderPadding(), bottom = Dimensions.bottomSpacer))),
                contentAlignment = Alignment.Center
            ) {
                if (search.filterQuery.isBlank()) SearchNoResults(text = stringResource(R.string.common_empty_items))
            }
        }
    }
}
