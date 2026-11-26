package app.n_zik.android.ui.screens.statistics

import app.n_zik.android.components.navigation.rememberPageChromeContentPadding
import androidx.compose.ui.unit.Dp

import app.n_zik.android.components.glass.glassChipBackground

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import app.n_zik.android.core.database.BookmarkStateManager
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.database.Database

import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.cleanPrefix
import app.n_zik.android.colorPalette
import app.n_zik.android.enums.MaxStatisticsItems
import app.n_zik.android.components.navigation.onlinePlaylistRoute
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.enums.StatisticsCategory
import app.n_zik.android.enums.StatisticsType
import app.n_zik.android.models.Song
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.PlaylistPreview
import app.n_zik.android.typography
import app.n_zik.android.ui.components.ButtonsRow
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.SwipeablePlaylistItem
import app.n_zik.android.ui.components.forceLoadingShimmerPreview
import app.n_zik.android.ui.components.themed.HeaderWithIcon
import app.n_zik.android.ui.components.themed.ItemTextPlaceholder
import app.n_zik.android.ui.components.themed.MeasuredMetadataPlaceholder
import app.n_zik.android.ui.items.AlbumItem
import app.n_zik.android.ui.items.AlbumItemPlaceholder
import app.n_zik.android.components.menu.album.AlbumItemMenu
import app.n_zik.android.ui.items.ArtistItem
import app.n_zik.android.ui.items.ArtistItemPlaceholder
import app.n_zik.android.components.menu.artist.LocalArtistItemMenu
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.items.PlaylistItemPlaceholder
import app.n_zik.android.ui.items.ItemArtworkPlaceholder
import app.n_zik.android.ui.items.rememberPlaylistSongThumbnails
import app.n_zik.android.components.musicbrainz.insights.RankedInsightsSongPlaceholder
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.settings.common.SettingsEntry
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.ui.styling.px
import app.n_zik.android.ui.styling.overlay
import app.n_zik.android.ui.styling.onOverlay
import app.n_zik.android.utils.UpdateYoutubeAlbum
import app.n_zik.android.utils.UpdateYoutubeArtist
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.center
import app.n_zik.android.utils.color
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.forcePlayAtIndex
import app.n_zik.android.utils.formatAsTime
import app.n_zik.android.utils.maxStatisticsItemsKey
import app.n_zik.android.utils.maxStatisticsItemsCustomValueKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.semiBold
import app.n_zik.android.utils.showStatsListeningTimeKey
import app.n_zik.android.utils.statisticsCategoryKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import timber.log.Timber
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.enqueue
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.components.menu.playlist.LocalPlaylistItemMenu
import app.n_zik.android.thumbnailShape
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.LocalDownloadStatesMap
import app.n_zik.android.enums.DownloadedStateMedia
import app.n_zik.android.download.utils.MyDownloadHelper
import androidx.media3.exoplayer.offline.Download
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun StatisticsPage(
    navController: NavController,
    statisticsType: StatisticsType,
    topContentInsetProvider: () -> Dp,
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px
    val artistThumbnailSizeDp = 92.dp
    val artistThumbnailSizePx = artistThumbnailSizeDp.px
    val playlistThumbnailSizeDp = 108.dp
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px



    val showStatsListeningTime by rememberPreference(showStatsListeningTimeKey, true)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val context = LocalContext.current

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSize = thumbnailSizeDp.px

    val maxStatisticsItems by rememberPreference( maxStatisticsItemsKey, MaxStatisticsItems.`10` )
    val maxStatisticsItemsCustomValue by rememberPreference( maxStatisticsItemsCustomValueKey, 10 )
    val from = remember( statisticsType ) { statisticsType.timeStampInMillis() }

    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    val limit = maxStatisticsItems.toInt(maxStatisticsItemsCustomValue)
    val artistResults by remember(from, limit) {
        Database.eventTable
                .findArtistsMostPlayedBetween(
                    from = from,
                    limit = limit
                )
                .distinctUntilChanged()
    }.collectAsStateWithLifecycle<List<Artist>?>(null, context = NzikDispatchers.DATA)
    val albumResults by remember(from, limit) {
        Database.eventTable
                .findAlbumsMostPlayedBetween(
                    from = from,
                    limit = limit
                )
                .distinctUntilChanged()
    }.collectAsStateWithLifecycle<List<Album>?>(null, context = NzikDispatchers.DATA)
    val playlistResults by remember(from, limit) {
        Database.eventTable
                .findPlaylistMostPlayedBetweenAsPreview(
                    from = from,
                    limit = limit
                )
                .distinctUntilChanged()
    }.collectAsStateWithLifecycle<List<PlaylistPreview>?>(null, context = NzikDispatchers.DATA)
    val artists = artistResults.orEmpty()
    val albums = albumResults.orEmpty()
    val playlists = playlistResults.orEmpty()
    val totalPlayTimesFlow = remember(from) {
        Database.eventTable
            .getTotalPlayTimeBetween(from = from)
            .distinctUntilChanged()
    }
    val totalPlayTimes by totalPlayTimesFlow.collectAsStateWithLifecycle<Long?>(null, context = NzikDispatchers.DATA)

    val distinctSongsPlayedCountFlow = remember(from) {
        Database.eventTable
            .countDistinctSongsPlayedBetween(from = from)
            .distinctUntilChanged()
    }
    val distinctSongsPlayedCount by distinctSongsPlayedCountFlow.collectAsStateWithLifecycle<Int?>(null, context = NzikDispatchers.DATA)

    val songsWithLikeStates by remember(from, parentalControlEnabled, limit) {
        // Multiply in Long and cap to avoid Int overflow (Unlimited or large Custom values)
        val fetchLimit = (limit.toLong() *
            (if (parentalControlEnabled) 5L else 1L))
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        Database.eventTable
            .findSongsMostPlayedBetween(
                from = from,
                limit = fetchLimit
            )
            .distinctUntilChanged()
            .map { list ->
                list.filter { !parentalControlEnabled || !it.title.startsWith(EXPLICIT_PREFIX, true) }
                    .take(limit)
            }
            .flatMapLatest { songsList ->
                val songIds = songsList.map { it.id }
                if (songIds.isEmpty()) {
                    kotlinx.coroutines.flow.flowOf(Pair(emptyList<Song>(), emptyMap<String, Boolean?>()))
                } else {
                    LikeStateManager.getLikeStates(songIds)
                        .map { likeMap -> Pair(songsList, likeMap) }
                }
            }
    }.collectAsStateWithLifecycle<Pair<List<Song>, Map<String, Boolean?>>?>(null, context = NzikDispatchers.DATA)

    val songs = songsWithLikeStates?.first.orEmpty()
    val likeStatesMap = songsWithLikeStates?.second.orEmpty()
    val songIds = remember(songs) { songs.map { it.id } }
    val playlistStatesMap by remember(songIds) {
        PlaylistStateManager.getPlaylistStates(songIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    // Download state cache
    val downloadsMapState by MyDownloadHelper.downloads.collectAsStateWithLifecycle(initialValue = MyDownloadHelper.downloads.value, context = NzikDispatchers.DATA)
    val downloadedIds by remember {
        derivedStateOf {
            downloadsMapState.values
                .filter { it.state == Download.STATE_COMPLETED }
                .mapTo(HashSet()) { it.request.id }
        }
    }
    val downloadStatesMap by remember {
        derivedStateOf {
            songs.associate { song ->
                song.id to when {
                    song.id in downloadedIds -> DownloadedStateMedia.DOWNLOADED
                    else -> DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
                }
            }
        }
    }

    var statisticsCategory by rememberPreference(
        statisticsCategoryKey,
        StatisticsCategory.Songs
    )
    val buttonsList = listOf(
        StatisticsCategory.Songs to StatisticsCategory.Songs.text,
        StatisticsCategory.Artists to StatisticsCategory.Artists.text,
        StatisticsCategory.Albums to StatisticsCategory.Albums.text,
        StatisticsCategory.Playlists to StatisticsCategory.Playlists.text
    )
    val forceShimmer = forceLoadingShimmerPreview()
    val categoryLoading = when (statisticsCategory) {
        StatisticsCategory.Songs -> songsWithLikeStates == null
        StatisticsCategory.Artists -> artistResults == null
        StatisticsCategory.Albums -> albumResults == null
        StatisticsCategory.Playlists -> playlistResults == null
    }

    val artistBookmarkIds = remember(artists) { artists.map { it.id } }
    val artistBookmarkStatesMap by remember(artistBookmarkIds) {
        BookmarkStateManager.getArtistBookmarkStates(artistBookmarkIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val albumBookmarkIds = remember(albums) { albums.map { it.id } }
    val albumBookmarkStatesMap by remember(albumBookmarkIds) {
        BookmarkStateManager.getAlbumBookmarkStates(albumBookmarkIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
    val playlistBookmarkIds = remember(playlists) { playlists.map { it.playlist.id.toString() } }
    val playlistBookmarkStatesMap by remember(playlistBookmarkIds) {
        BookmarkStateManager.getPlaylistBookmarkStates(playlistBookmarkIds)
    }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalDownloadStatesMap provides downloadStatesMap) {
            val lazyGridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive(
                    if(statisticsCategory == StatisticsCategory.Songs) 200.dp else playlistThumbnailSizeDp
                ),
                contentPadding = rememberPageChromeContentPadding(
                    base = PaddingValues(bottom = Dimensions.bottomSpacer),
                    topInsetProvider = topContentInsetProvider,
                ),
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {

                item(
                    key = "header",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    HeaderWithIcon(
                        title = statisticsType.text,
                        iconId = statisticsType.iconId,
                        enabled = true,
                        showIcon = true,
                        modifier = Modifier,
                        onClick = {}
                    )
                }

                item(
                    key = "header_tabs",
                    span = { GridItemSpan(maxLineSpan) }
                ) {

                    ButtonsRow(
                        chips = buttonsList,
                        currentValue = statisticsCategory,
                        onValueUpdate = { statisticsCategory = it },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                    )

                }

                val isCurrentListEmpty = when (statisticsCategory) {
                    StatisticsCategory.Songs -> songs.isEmpty()
                    StatisticsCategory.Artists -> artists.isEmpty()
                    StatisticsCategory.Albums -> albums.isEmpty()
                    StatisticsCategory.Playlists -> playlists.isEmpty()
                }



                if (statisticsCategory == StatisticsCategory.Songs) {

                        if (showStatsListeningTime)
                            item(
                                key = "headerListeningTime",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                val title = distinctSongsPlayedCount?.let { count ->
                                    pluralStringResource(R.plurals.statistics_songs_heard_count, count, count)
                                }
                                val summary = totalPlayTimes?.let { duration ->
                                    stringResource(R.string.statistics_time_spent_format, formatAsTime(duration))
                                }
                                StatisticsListeningSummary(title, summary, forceShimmer)
                            }


                    items(
                        count = songs.count(),
                        key = { index -> songs[index].id }
                    ) {
                        if (forceShimmer) {
                            RankedInsightsSongPlaceholder(songs[it], it + 1)
                            return@items
                        }
                        SwipeablePlaylistItem(
                            mediaItem = songs[it].asMediaItem,
                            onPlayNext = { binder?.player?.addNext(songs[it].asMediaItem) },
                            onEnqueue = { binder?.player?.enqueue(songs[it].asMediaItem) }
                        ) {
                            SongItem(
                                song = songs[it],
                                isLiked = likeStatesMap[songs[it].id],
                                inPlaylist = playlistStatesMap[songs[it].id],
                                navController = navController,
                                onClick = {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(
                                        songs.map(Song::asMediaItem),
                                        it
                                    )
                                },
                                thumbnailOverlay = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(thumbnailShape())
                                            .background(colorPalette().overlay)
                                    ) {
                                        BasicText(
                                            text = "${it + 1}",
                                            style = typography().s.semiBold.center.color(colorPalette().onOverlay),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (statisticsCategory == StatisticsCategory.Artists)
                    items(
                        count = artists.count(),
                        key = { index -> artists[index].id },
                    ) {
                        if (forceShimmer) {
                            val artist = artists[it]
                            RankedStatisticsCard(artistThumbnailSizeDp, artistThumbnailShape(), it + 1) {
                                ArtistItemPlaceholder(artistThumbnailSizeDp, alternative = true, title = artist.name)
                            }
                            return@items
                        }

                        if (artists[it].thumbnailUrl.toString() == "null")
                            UpdateYoutubeArtist(artists[it].id)

                        ArtistItem(
                            artist = artists[it],
                            thumbnailSizePx = artistThumbnailSizePx,
                            thumbnailSizeDp = artistThumbnailSizeDp,
                            alternative = true,
                            bookmarkState = artistBookmarkStatesMap[artists[it].id],
                            observeBookmarkState = false,
                            modifier = Modifier
                                .clip(uiRoundnessShape()).combinedClickable(
                                    onClick = {
                                        if (artists[it].id != "") {
                                            navController.navigate("${NavRoutes.artist.name}/${artists[it].id}")
                                        }
                                    },
                                    onLongClick = {
                                        menuState.display {
                                            LocalArtistItemMenu(navController = navController, artist = artists[it]).MenuComponent()
                                        }
                                    }
                                ),
                            disableScrollingText = disableScrollingText,
                            thumbnailOverlay = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(artistThumbnailShape())
                                        .background(colorPalette().overlay)
                                ) {
                                    BasicText(
                                        text = "${it + 1}",
                                        style = typography().s.semiBold.center.color(colorPalette().onOverlay),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                    )
                                }
                            }
                        )
                    }

                if (statisticsCategory == StatisticsCategory.Albums)
                    items(
                        count = albums.count(),
                        key = { index -> albums[index].id },
                    ) {
                        if (forceShimmer) {
                            val album = albums[it]
                            val authors = album.authorsText
                            RankedStatisticsCard(albumThumbnailSizeDp, thumbnailShape(), it + 1) {
                                AlbumItemPlaceholder(
                                    albumThumbnailSizeDp, alternative = true, title = album.title,
                                    metadataLines = if (authors == null) 1 else 2,
                                    metadata = if (authors == null) listOf(album.year.orEmpty()) else listOf(authors, album.year.orEmpty()),
                                )
                            }
                            return@items
                        }

                        if (albums[it].thumbnailUrl.toString() == "null")
                            UpdateYoutubeAlbum(albums[it].id)

                        AlbumItem(
                            album = albums[it],
                            thumbnailSizePx = albumThumbnailSizePx,
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true,
                            showAuthors = true,
                            bookmarkState = albumBookmarkStatesMap[albums[it].id],
                            observeBookmarkState = false,
                            modifier = Modifier
                                .clip(uiRoundnessShape()).combinedClickable(
                                    onClick = {
                                        if (albums[it].id != "")
                                            navController.navigate("${NavRoutes.album.name}/${albums[it].id}")
                                    },
                                    onLongClick = {
                                        menuState.display {
                                            AlbumItemMenu(
                                                navController = navController,
                                                album = albums[it],
                                                binder = binder
                                            ).MenuComponent()
                                        }
                                    }
                                ),
                            disableScrollingText = disableScrollingText,
                            thumbnailOverlay = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(thumbnailShape())
                                        .background(colorPalette().overlay)
                                ) {
                                    BasicText(
                                        text = "${it + 1}",
                                        style = typography().s.semiBold.center.color(colorPalette().onOverlay),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                    )
                                }
                            }
                        )
                    }

                if (statisticsCategory == StatisticsCategory.Playlists) {
                    items(
                        count = playlists.count(),
                        key = { index -> playlists[index].playlist.id },
                    ) {
                        if (forceShimmer) {
                            RankedStatisticsCard(playlistThumbnailSizeDp, thumbnailShape(), it + 1) {
                                PlaylistItemPlaceholder(
                                    playlistThumbnailSizeDp, alternative = true, showChannel = false,
                                    title = playlists[it].playlist.name,
                                )
                            }
                            return@items
                        }
                        val thumbnails = rememberPlaylistSongThumbnails(
                            playlists[it].playlist.id,
                            playlistThumbnailSizePx / 2,
                        )

                        PlaylistItem(
                            thumbnailContent = {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Base ThumbnailRenderer contract: four covers render
                                    // the two-by-two collage, one to three a single
                                    // full-size cover, zero nothing.
                                    if (thumbnails == null) {
                                        ItemArtworkPlaceholder(playlistThumbnailSizeDp, card = true, shape = thumbnailShape())
                                    } else if (thumbnails.size in 1..3) {
                                        ImageCacheFactory.AsyncImage(
                                            shape = null,
                                            thumbnailUrl = thumbnails.first(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            onError = {error ->
                                                Timber.tag("StatisticsPage").e("Failed AsyncImage in PlaylistItem ${error.result.throwable.stackTraceToString()}")
                                            }
                                        )
                                    } else if (thumbnails.size >= 4) {
                                        // The quadrants are sized from the actual container
                                        // (like the base ThumbnailRenderer): the card can be
                                        // wider than playlistThumbnailSizeDp, a fixed Dp
                                        // left visible gaps around the four covers.
                                        BoxWithConstraints(
                                            modifier = Modifier
                                                .fillMaxSize()
                                        ) {
                                            val halfWidth = maxWidth / 2
                                            val halfHeight = maxHeight / 2
                                            listOf(
                                                Alignment.TopStart,
                                                Alignment.TopEnd,
                                                Alignment.BottomStart,
                                                Alignment.BottomEnd
                                            ).forEachIndexed { index, alignment ->
                                                val thumbnail = thumbnails.getOrNull(index)
                                                if (thumbnail != null)
                                                    ImageCacheFactory.AsyncImage(
                                                        shape = null,
                                                        thumbnailUrl = thumbnail,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .align(alignment)
                                                            .size(halfWidth, halfHeight),
                                                        onError = {error ->
                                                            Timber.tag("StatisticsPage").e("Failed AsyncImage 1 in PlaylistItem ${error.result.throwable.stackTraceToString()}")
                                                        }
                                                    )
                                            }
                                        }
                                    }
                                }
                            },
                                // Rank number as caller overlay: sits on top of the
                                // base composable's dark scrim instead of carrying
                                // its own (avoids a double scrim).
                                thumbnailOverlay = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicText(
                                            text = "${it + 1}",
                                            style = typography().s.semiBold.center.color(colorPalette().onOverlay),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                songCount = playlists[it].songCount,
                                name = playlists[it].playlist.name,
                                channelName = null,
                                thumbnailSizeDp = playlistThumbnailSizeDp,
                                alternative = true,
                                isYoutubePlaylist = playlists[it].playlist.isYoutubePlaylist,
                                modifier = Modifier
                                .clip(uiRoundnessShape()).combinedClickable(
                                    onClick = {
                                        val playlistId: String = playlists[it].playlist.id.toString()
                                        if ( playlistId.isEmpty() ) return@combinedClickable    // Fail-safe??

                                        val pBrowseId: String = cleanPrefix(playlists[it].playlist.browseId ?: "")
                                        val route: String =
                                            if ( pBrowseId.isNotEmpty() )
                                                onlinePlaylistRoute(pBrowseId)
                                            else
                                                "${NavRoutes.localPlaylist.name}/$playlistId"

                                        navController.navigate(route = route)
                                    },
                                    onLongClick = {
                                        menuState.display {
                                            LocalPlaylistItemMenu(
                                                navController = navController,
                                                playlistPreview = playlists[it]
                                            ).MenuComponent()
                                        }
                                    }
                                ),
                            disableScrollingText = disableScrollingText
                        )
                    }
                }
                if (categoryLoading) {
                    items(limit.coerceIn(1, 8), key = { "stats-loading-${statisticsCategory.name}-$it" }) {
                        when (statisticsCategory) {
                            StatisticsCategory.Songs -> RankedInsightsSongPlaceholder(null, it + 1)
                            StatisticsCategory.Artists ->
                                RankedStatisticsCard(artistThumbnailSizeDp, artistThumbnailShape(), it + 1) {
                                    ArtistItemPlaceholder(artistThumbnailSizeDp, alternative = true)
                                }
                            StatisticsCategory.Albums ->
                                RankedStatisticsCard(albumThumbnailSizeDp, thumbnailShape(), it + 1) {
                                    AlbumItemPlaceholder(albumThumbnailSizeDp, alternative = true, metadataLines = 2)
                                }
                            StatisticsCategory.Playlists ->
                                RankedStatisticsCard(playlistThumbnailSizeDp, thumbnailShape(), it + 1) {
                                    PlaylistItemPlaceholder(playlistThumbnailSizeDp, alternative = true, showChannel = false)
                                }
                        }
                    }
                } else if (isCurrentListEmpty) {
                    item(
                        key = "empty_state",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = stringResource(R.string.common_empty_items),
                                style = typography().m.semiBold.copy(
                                    color = colorPalette().textSecondary
                                )
                            )
                        }
                    }
                }
            }
        } // CompositionLocalProvider
    }
}

@Composable
private fun RankedStatisticsCard(
    artworkSize: Dp,
    artworkShape: Shape,
    rank: Int,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        Box(
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(horizontal = 16.dp).padding(top = Dimensions.itemsVerticalPadding)
                .width(artworkSize).aspectRatio(1f).clip(artworkShape)
                .background(colorPalette().overlay),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                rank.toString(),
                style = typography().s.semiBold.center.color(colorPalette().onOverlay),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatisticsListeningSummary(title: String?, summary: String?, forceShimmer: Boolean) {
    val palette = colorPalette()
    val panel = Modifier.glassChipBackground(color = palette.accent, shape = thumbnailShape())
    val icon: @Composable () -> Unit = {
        Image(
            painter = painterResource(R.drawable.musical_notes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.onAccent),
            modifier = Modifier.size(34.dp),
        )
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        if (!forceShimmer && title != null && summary != null) {
            SettingsEntry(title = title, text = summary, onClick = {}, trailingContent = icon, modifier = panel)
        } else {
            val style = typography().xs.semiBold
            Row(
                modifier = panel.clip(uiRoundnessShape()).padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val titleModifier = Modifier.padding(bottom = 4.dp)
                    if (title == null) ItemTextPlaceholder(null, style, titleModifier, width = 0.55f)
                    else if (forceShimmer) MeasuredMetadataPlaceholder(title, style, titleModifier)
                    else BasicText(title, titleModifier, style = style.copy(color = palette.text))

                    if (summary == null) ItemTextPlaceholder(null, style, width = 0.7f)
                    else if (forceShimmer) MeasuredMetadataPlaceholder(summary, style)
                    else BasicText(summary, style = style.copy(color = palette.textSecondary))
                }
                icon()
            }
        }
    }
}








