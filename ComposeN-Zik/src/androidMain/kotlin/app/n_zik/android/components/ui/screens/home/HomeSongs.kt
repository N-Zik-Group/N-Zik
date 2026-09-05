package app.n_zik.android.components.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.util.fastMap
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.enums.*
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.ui.styling.overlay
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.clip
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_DISLIKED_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_DISLIKED_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_FAVORITES_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_FAVORITES_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_OFFLINE_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_OFFLINE_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_DOWNLOADED_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_TOP_SORT_BY
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_TOP_SORT_ORDER
import app.it.fast4x.rimusic.utils.Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD
import app.it.fast4x.rimusic.utils.*
import app.kreate.android.me.knighthat.utils.Toaster
import app.kreate.android.themed.rimusic.component.playlist.PositionLock
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.Sort
import app.n_zik.android.components.song.PeriodSelector
import app.n_zik.android.components.tab.*

import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.ext.FormatWithSong
import app.it.fast4x.rimusic.utils.autosyncLikesKey
import app.it.fast4x.rimusic.utils.importYTMLikedSongs
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import androidx.compose.runtime.saveable.rememberSaveable
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.playback.services.isUnmatched
import app.it.fast4x.rimusic.enums.DownloadedStateMedia
import app.n_zik.android.thumbnailShape
import app.n_zik.android.typography
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.relatedSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon

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
    itemsOnDisplay: MutableList<Song>,
    getSongs: () -> List<Song>,
    matchButton: Button? = null,
    onRecommendationCountChange: (Int) -> Unit = {},
    onRecommendationsLoadingChange: (Boolean) -> Unit = {},
    isRecommendationEnabled: Boolean = false,
    refreshKey: Int = 0,
    onMatchClick: () -> Unit = {}
) {
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current

    val parentalControlEnabled by rememberPreference( parentalControlEnabledKey, false )
    val maxTopPlaylistItems by rememberPreference( MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10` )
    val includeLocalSongs by rememberPreference( includeLocalSongsKey, true )
    val excludeSongWithDurationLimit by rememberPreference( excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled )
    var filterBy by rememberPreference(filterByKey, FilterBy.All)

    var items by remember { mutableStateOf(emptyList<Song>()) }

    val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle()
    val downloadedIds by remember {
        derivedStateOf {
            downloadsMapState.values
                .filter { it.state == Download.STATE_COMPLETED }
                .mapTo(HashSet()) { it.request.id }
        }
    }

    var cachedSongIds by remember { mutableStateOf(emptySet<String>()) }

    val downloadStatesMap by remember {
        derivedStateOf {
            items.associate { song ->
                song.id to when {
                    song.id in downloadedIds && song.id in cachedSongIds -> DownloadedStateMedia.CACHED_AND_DOWNLOADED
                    song.id in downloadedIds -> DownloadedStateMedia.DOWNLOADED
                    song.id in cachedSongIds -> DownloadedStateMedia.CACHED
                    else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                }
            }
        }
    }

    LaunchedEffect(builtInPlaylist) {
        if (builtInPlaylist == BuiltInPlaylist.Offline) {
            withContext(Dispatchers.IO) {
                Database.formatTable.allWithSongs().first().mapNotNull { format ->
                    val contentLength = format.format.contentLength ?: return@mapNotNull null
                    if (binder?.cache?.isCached(format.song.id, 0, contentLength) == true) {
                        format.song.id
                    } else null
                }.toSet().also { cachedSongIds = it }
            }
        }
    }

    val songSort = when( builtInPlaylist ) {
        BuiltInPlaylist.Favorites -> Sort( HOME_SONGS_FAVORITES_SORT_BY, HOME_SONGS_FAVORITES_SORT_ORDER, homeSongsFavoritesSortMenuOrderKey, "favs" )
        BuiltInPlaylist.Offline -> Sort( HOME_SONGS_OFFLINE_SORT_BY, HOME_SONGS_OFFLINE_SORT_ORDER, homeSongsCachedSortMenuOrderKey, "off" )
        BuiltInPlaylist.Downloaded -> Sort( HOME_SONGS_DOWNLOADED_SORT_BY, HOME_SONGS_DOWNLOADED_SORT_ORDER, homeSongsDownloadedSortMenuOrderKey, "dl" )
        BuiltInPlaylist.Top -> Sort( HOME_SONGS_TOP_SORT_BY, HOME_SONGS_TOP_SORT_ORDER, homeSongsTopSortMenuOrderKey, "top" )
        BuiltInPlaylist.Disliked -> Sort( HOME_SONGS_DISLIKED_SORT_BY, HOME_SONGS_DISLIKED_SORT_ORDER, homeSongsDislikedSortMenuOrderKey, "disliked" )
        else -> Sort( HOME_SONGS_SORT_BY, HOME_SONGS_SORT_ORDER, homeSongsAllSortMenuOrderKey, "all" )
    }
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

    // Ensure "Downloaded" sort is not visible in Downloaded/Offline tabs
    LaunchedEffect( builtInPlaylist ) {
        if (builtInPlaylist == BuiltInPlaylist.Downloaded || builtInPlaylist == BuiltInPlaylist.Offline) {
            val prefix = when (builtInPlaylist) {
                BuiltInPlaylist.Offline -> "off"
                BuiltInPlaylist.Downloaded -> "dl"
                else -> ""
            }
            if (prefix.isNotEmpty()) {
                val prefs = context.getSharedPreferences("preferences", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("${prefix}_sort_Downloaded_visible", false).apply()
            }
        }
    }

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

    LaunchedEffect( builtInPlaylist, topPlaylists.period, songSort.sortBy, songSort.sortOrder, hiddenSongs.isFirstIcon, refreshKey ) {
        isLoading = true

        val retrievedSongs = when( builtInPlaylist ) {
            BuiltInPlaylist.All -> {
                if (songSort.sortBy == SongSortBy.Downloaded) {
                    Database.songTable
                        .sortAll( SongSortBy.Title, SortOrder.Ascending, excludeHidden = hiddenSongs.isHiddenExcluded() )
                        .map { list ->
                            list.fastFilter {
                                !includeLocalSongs || !it.id.startsWith( LOCAL_KEY_PREFIX, true )
                            }.let { filtered ->
                                if (songSort.sortOrder == SortOrder.Ascending) {
                                    filtered.sortedByDescending { it.id in downloadedIds }
                                } else {
                                    filtered.sortedBy { it.id in downloadedIds }
                                }
                            }
                        }
                } else {
                    Database.songTable
                        .sortAll( songSort.sortBy, songSort.sortOrder, excludeHidden = hiddenSongs.isHiddenExcluded() )
                        .map { list ->
                            list.fastFilter {
                                !includeLocalSongs || !it.id.startsWith( LOCAL_KEY_PREFIX, true )
                            }
                        }
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
                .sortAllWithSongs( songSort.sortBy, songSort.sortOrder, excludeHidden = hiddenSongs.isHiddenExcluded() )
                .map { list ->
                    list.fastFilter {
                        it.song.id in cachedSongIds
                    }.map( FormatWithSong::song )
                }

            BuiltInPlaylist.Favorites -> {
                if (songSort.sortBy == SongSortBy.Downloaded) {
                    Database.songTable.sortFavorites( SongSortBy.Title, SortOrder.Ascending )
                        .map { list ->
                            if (songSort.sortOrder == SortOrder.Ascending) {
                                list.sortedByDescending { it.id in downloadedIds }
                            } else {
                                list.sortedBy { it.id in downloadedIds }
                            }
                        }
                } else {
                    Database.songTable.sortFavorites( songSort.sortBy, songSort.sortOrder )
                }
            }

            BuiltInPlaylist.Top -> {
                if (songSort.sortBy == SongSortBy.Downloaded) {
                    Database.eventTable
                        .findSongsMostPlayedBetween(
                            from = topPlaylists.period.timeStampInMillis(),
                            limit = maxTopPlaylistItems.toInt()
                        )
                        .map { list ->
                            list.fastFilter { song ->
                                excludeSongWithDurationLimit == DurationInMinutes.Disabled
                                        || song.durationText
                                    ?.let { durationTextToMillis(it) < excludeSongWithDurationLimit.asMillis } == true
                            }.let { filtered ->
                                if (songSort.sortOrder == SortOrder.Ascending) {
                                    filtered.sortedByDescending { it.id in downloadedIds }
                                } else {
                                    filtered.sortedBy { it.id in downloadedIds }
                                }
                            }
                        }
                } else {
                    Database.eventTable
                        .findSongsMostPlayedBetween(
                            from = topPlaylists.period.timeStampInMillis(),
                            limit = maxTopPlaylistItems.toInt()
                        )
                        .map { list ->
                            list.fastFilter { song ->
                                excludeSongWithDurationLimit == DurationInMinutes.Disabled
                                        || song.durationText
                                    ?.let { durationTextToMillis(it) < excludeSongWithDurationLimit.asMillis } == true
                            }
                        }
                }
            }

            BuiltInPlaylist.OnDevice -> flowOf( emptyList() )

            BuiltInPlaylist.Disliked -> {
                Database.songTable.allDisliked()
            }
        }

        retrievedSongs.flowOn( Dispatchers.IO )
            .distinctUntilChanged()
            .collect {
                items = when(filterBy) {
                    FilterBy.All -> it
                    FilterBy.YoutubeLibrary -> it.filter { song -> song.isYoutubeSong }
                    FilterBy.Local -> it.filterNot { song -> song.isYoutubeSong }
                }
                isLoading = false
            }
    }

    LaunchedEffect(isRecommendationEnabled, items) {
        if (!isRecommendationEnabled || items.isEmpty()) {
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

        val seedSongs = items.shuffled().take(numberOfRequests)

        for (seedSong in seedSongs) {
            try {
                val relatedSongsResult = Innertube.relatedSongs(videoId = seedSong.id)?.getOrNull()

                relatedSongsResult?.songs?.forEach { songItem ->
                    songItem.info?.let { info ->
                        info.endpoint?.videoId?.let { videoId ->
                            if (!existingSongIds.contains(videoId)) {
                                if (parentalControlEnabled && songItem.explicit) return@let
                                val prefix = if (songItem.explicit) EXPLICIT_PREFIX else ""
                                val song = Song(
                                    id = "$prefix$videoId",
                                    title = info.name ?: "",
                                    artistsText = songItem.authors.parseArtists().joinToString(", "),
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

                if (numberOfRequests > 1) delay(200L)
            } catch (e: Exception) {
                continue
            }
        }

        relatedSongs = allRelatedSongs.take(targetRecommendations)
        val newPositions = relatedSongs.associate { song ->
            song to (0..items.size).random()
        }
        relatedSongsPositions = newPositions

        isRecommendationsLoading = false
        onRecommendationsLoadingChange(false)
    }

    LaunchedEffect( items, search.inputValue, isRecommendationEnabled, relatedSongsPositions ) {
        items.toMutableList()
            .apply {
                if (isRecommendationEnabled) {
                    relatedSongsPositions.forEach { (song, position) ->
                        val safePosition = position.coerceIn(0, size)
                        add( safePosition, song )
                    }
                }
            }
            .distinctBy( Song::id )
            .filter { !parentalControlEnabled || !it.title.startsWith( EXPLICIT_PREFIX, true ) }
            .filter { song ->
                val containsTitle = song.cleanTitle().contains( search.inputValue, true )
                val containsArtist = song.cleanArtistsText().contains( search.inputValue, true )
                containsTitle || containsArtist
            }
            .let { newItems ->
                androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                    itemsOnDisplay.clear()
                    itemsOnDisplay.addAll(newItems)
                }
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
            override val messageId: Int = R.string.match_album_audio_version
            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
            override fun onShortClick() { onMatchClick() }
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
            !isLoading && itemsOnDisplay.isEmpty() && (items.isEmpty() || search.inputValue.isNotEmpty())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
            LazyColumn(
                state = lazyListState,
                userScrollEnabled = !isLoading,
                contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer ),
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {

                itemsIndexed(
                    items = itemsOnDisplay.distinctBy { it.id },
                    key = { _, song -> song.id }
                ) { index, song ->
                    ReorderableItem(
                        reorderableLazyListState,
                        key = song.id
                    ) { isDraggingItem ->
                        val mediaItem = song.asMediaItem
                        val isLocal by remember { derivedStateOf { mediaItem.isLocal } }
                        val isDownloaded = isLocal || isDownloadedSong( mediaItem.mediaId )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(2f)
                        ) {
                            SwipeablePlaylistItem(
                                mediaItem = mediaItem,
                                onPlayNext = { binder?.player?.addNext( mediaItem ) },
                                onDownload = {
                                    if( builtInPlaylist != BuiltInPlaylist.OnDevice ) {
                                        binder?.cache?.removeResource(mediaItem.mediaId)
                                        Database.asyncTransaction {
                                            formatTable.updateContentLengthOf( mediaItem.mediaId )
                                        }
                                        if ( !isLocal )
                                            manageDownload(
                                                context = context,
                                                mediaItem = mediaItem,
                                                downloadState = isDownloaded
                                            )
                                    }
                                },
                                onEnqueue = { binder?.player?.enqueue(mediaItem) }
                            ) {
                                val isRecommended = song in relatedSongs
                                SongItem(
                                    song = song,
                                    itemSelector = itemSelector,
                                    navController = navController,
                                    isRecommended = isRecommended,
                                    modifier = Modifier.animateItem(),
                                    trailingContent = {
                                        if ((song.id.length != 11 || (song.durationText == "00:00" && song.totalPlayTimeMs == 1L)) && !song.id.startsWith(LOCAL_KEY_PREFIX)) {
                                            Icon(
                                                painter = painterResource(R.drawable.alert),
                                                contentDescription = stringResource(R.string.unmatched_song),
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
                                                song.formattedTotalPlayTime
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
                                        if (song.isUnmatched) {
                                            Toaster.w(R.string.playback_blocked_match_first)
                                        } else {
                                            binder?.stopRadio()
                                            val mediaItems = getSongs().fastMap( Song::asMediaItem )
                                            binder?.player?.forcePlayAtIndex( mediaItems, index )
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

        if (showNoItems) {
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 47.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = stringResource(R.string.no_items),
                    style = typography().m.semiBold.copy(
                        color = colorPalette().textSecondary
                    )
                )
            }
        }
    }
}
