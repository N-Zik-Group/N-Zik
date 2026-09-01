package app.it.fast4x.rimusic.ui.screens.statistics

import androidx.compose.ui.draw.clip

import app.n_zik.android.uiRoundnessShape

import app.n_zik.android.core.database.*

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.database.Database

import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.MaxStatisticsItems
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.enums.StatisticsCategory
import app.it.fast4x.rimusic.enums.StatisticsType
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.ButtonsRow
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.items.AlbumItem
import app.n_zik.android.components.menu.album.AlbumItemMenu
import app.it.fast4x.rimusic.ui.items.ArtistItem
import app.n_zik.android.components.menu.artist.LocalArtistItemMenu
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.n_zik.android.components.SongItem
import app.it.fast4x.rimusic.ui.screens.settings.SettingsEntry
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.px
import app.it.fast4x.rimusic.ui.styling.shimmer
import app.it.fast4x.rimusic.ui.styling.overlay
import app.it.fast4x.rimusic.ui.styling.onOverlay
import app.it.fast4x.rimusic.utils.UpdateYoutubeAlbum
import app.it.fast4x.rimusic.utils.UpdateYoutubeArtist
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.color
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.forcePlayAtIndex
import app.it.fast4x.rimusic.utils.formatAsTime
import app.it.fast4x.rimusic.utils.getDownloadState
import app.it.fast4x.rimusic.utils.isDownloadedSong
import app.it.fast4x.rimusic.utils.isNowPlaying
import app.it.fast4x.rimusic.utils.manageDownload
import app.it.fast4x.rimusic.utils.maxStatisticsItemsKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.showStatsListeningTimeKey
import app.it.fast4x.rimusic.utils.statisticsCategoryKey
import app.n_zik.android.core.coil.thumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import timber.log.Timber
import app.it.fast4x.rimusic.utils.addNext
import app.it.fast4x.rimusic.utils.enqueue
import app.n_zik.android.core.coil.ImageCacheFactory
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.n_zik.android.components.menu.playlist.LocalPlaylistItemMenu
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun StatisticsPage(
    navController: NavController,
    statisticsType: StatisticsType
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px
    val artistThumbnailSizeDp = 92.dp
    val artistThumbnailSizePx = artistThumbnailSizeDp.px
    val playlistThumbnailSizeDp = 108.dp
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    
    val showStatsListeningTime by rememberPreference(showStatsListeningTimeKey, true)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val context = LocalContext.current

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSize = thumbnailSizeDp.px

    val maxStatisticsItems by rememberPreference( maxStatisticsItemsKey, MaxStatisticsItems.`10` )
    val from = remember( statisticsType ) { statisticsType.timeStampInMillis() }

    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    val artists by remember {
        Database.eventTable
                .findArtistsMostPlayedBetween(
                    from = from,
                    limit = maxStatisticsItems.toInt()
                )
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )
    val albums by remember {
        Database.eventTable
                .findAlbumsMostPlayedBetween(
                    from = from,
                    limit = maxStatisticsItems.toInt()
                )
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )
    val playlists by remember {
        Database.eventTable
                .findPlaylistMostPlayedBetweenAsPreview(
                    from = from,
                    limit = maxStatisticsItems.toInt()
                )
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )
    var totalPlayTimes by remember { mutableLongStateOf(0L) }
    val totalPlayTimesFlow = remember(from) {
        Database.eventTable
            .findSongsMostPlayedBetween(
                from = from,
                limit = Int.MAX_VALUE
            )
            .distinctUntilChanged()
            .map { it.sumOf(Song::totalPlayTimeMs) }
    }
    val totalPlayTimesState = totalPlayTimesFlow.collectAsState(0L, Dispatchers.IO)
    totalPlayTimes = totalPlayTimesState.value

    val songs by remember(parentalControlEnabled, maxStatisticsItems) {
        Database.eventTable
            .findSongsMostPlayedBetween(
                from = from,
                limit = maxStatisticsItems.toInt() * (if (parentalControlEnabled) 5 else 1)
            )
            .distinctUntilChanged()
            .map { list -> 
                list.filter { !parentalControlEnabled || !it.title.startsWith(EXPLICIT_PREFIX, true) }
                    .take(maxStatisticsItems.toInt()) 
            }
    }.collectAsState(emptyList(), Dispatchers.IO)



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

    // Calcul of real listening time for the selected period (Songs category)
    var totalPlayTimesSongs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(songs, from) {
        var total = 0L
        songs.forEach { song ->
            // Get the sum of playtime for the period
            val playTime = Database.eventTable.getSongPlayTimeBetween(song.id, from).first()
            total += playTime
        }
        totalPlayTimesSongs = total
    }

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
            val lazyGridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive(
                    if(statisticsCategory == StatisticsCategory.Songs) 200.dp else playlistThumbnailSizeDp
                ),
                contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
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
                        modifier = Modifier.padding(horizontal = 12.dp)
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
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
                                    SettingsEntry(
                                        title = "${songs.size} ${stringResource(R.string.statistics_songs_heard)}",
                                        text = "${formatAsTime(totalPlayTimesSongs)} ${stringResource(R.string.statistics_of_time_taken)}",
                                        onClick = {},
                                        trailingContent = {
                                            Image(
                                                painter = painterResource(R.drawable.musical_notes),
                                                contentDescription = null,
                                                colorFilter = ColorFilter.tint(colorPalette().onAccent),
                                                modifier = Modifier
                                                    .size(34.dp)
                                            )
                                        },
                                        modifier = Modifier
                                            .background(
                                                color = colorPalette().accent,
                                                shape = thumbnailShape()
                                            )

                                    )
                                }
                            }


                    items(
                        count = songs.count(),
                    ) {
                        val currentDownloadState = getDownloadState(songs.get(it).asMediaItem.mediaId)
                        val isDownloaded = isDownloadedSong(songs.get(it).asMediaItem.mediaId)
                        var forceRecompose by remember { mutableStateOf(false) }
                        SwipeablePlaylistItem(
                            mediaItem = songs.get(it).asMediaItem,
                            onPlayNext = {
                                binder?.player?.addNext(songs.get(it).asMediaItem)
                            },
                            onEnqueue = {
                                binder?.player?.enqueue(songs.get(it).asMediaItem)
                            }
                        ) {
                            SongItem(
                                song = songs[it],
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
                        count = artists.count()
                    ) {

                        if (artists[it].thumbnailUrl.toString() == "null")
                            UpdateYoutubeArtist(artists[it].id)

                        ArtistItem(
                            thumbnailUrl = artists[it].thumbnailUrl,
                            name = cleanPrefix(artists[it].name ?: ""),
                            showName = true,
                            subscribersCount = null,
                            thumbnailSizePx = artistThumbnailSizePx,
                            thumbnailSizeDp = artistThumbnailSizeDp,
                            alternative = true,
                            modifier = Modifier
                                .clip(uiRoundnessShape()).combinedClickable(
                                    onClick = {
                                        if (artists[it].id != "") {
                                            navController.navigate("${NavRoutes.artist.name}/${artists[it].id}")
                                        }
                                    },
                                    onLongClick = {
                                        menuState.display {
                                            LocalArtistItemMenu(artist = artists[it]).MenuComponent()
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

                if (statisticsCategory == StatisticsCategory.Albums)
                    items(
                        count = albums.count()
                    ) {

                        if (albums[it].thumbnailUrl.toString() == "null")
                            UpdateYoutubeAlbum(albums[it].id)

                        AlbumItem(
                            thumbnailUrl = albums[it].thumbnailUrl,
                            title = albums[it].title,
                            authors = albums[it].authorsText,
                            year = albums[it].year,
                            thumbnailSizePx = albumThumbnailSizePx,
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true,
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
                        count = playlists.count()
                    ) {
                        val thumbnails by remember {
                            Database.songPlaylistMapTable
                                    .sortSongsByPlayTime( playlists[it].playlist.id )
                                    .distinctUntilChanged()
                                    .map { list ->
                                        list.takeLast( 4 ).map { song ->
                                            song.thumbnailUrl.thumbnail( playlistThumbnailSizePx / 2 )
                                        }
                                    }
                        }.collectAsState( emptyList(), Dispatchers.IO )

                        PlaylistItem(
                            thumbnailContent = {
                                Box(modifier = Modifier.fillMaxSize()) {
                                if (thumbnails.toSet().size == 1) {
                                    ImageCacheFactory.AsyncImage(
                                        thumbnailUrl = thumbnails.first(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        onError = {error ->
                                            Timber.tag("StatisticsPage").e("Failed AsyncImage in PlaylistItem ${error.result.throwable.stackTraceToString()}")
                                        }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                    ) {
                                        listOf(
                                            Alignment.TopStart,
                                            Alignment.TopEnd,
                                            Alignment.BottomStart,
                                            Alignment.BottomEnd
                                        ).forEachIndexed { index, alignment ->
                                            val thumbnail = thumbnails.getOrNull(index)
                                            if (thumbnail != null)
                                                ImageCacheFactory.AsyncImage(
                                                    thumbnailUrl = thumbnail,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .align(alignment)
                                                        .size(playlistThumbnailSizeDp /2),
                                                    onError = {error ->
                                                        Timber.tag("StatisticsPage").e("Failed AsyncImage 1 in PlaylistItem ${error.result.throwable.stackTraceToString()}")
                                                    }
                                                )
                                        }
                                    }
                                    } // end if-else
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
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                    } // end outer Box
                                },
                                songCount = playlists[it].songCount,
                                name = playlists[it].playlist.name,
                                channelName = null,
                                thumbnailSizeDp = playlistThumbnailSizeDp,
                                alternative = true,
                                modifier = Modifier
                                .clip(uiRoundnessShape()).combinedClickable(
                                    onClick = {
                                        val playlistId: String = playlists[it].playlist.id.toString()
                                        if ( playlistId.isEmpty() ) return@combinedClickable    // Fail-safe??

                                        val pBrowseId: String = cleanPrefix(playlists[it].playlist.browseId ?: "")
                                        val route: String =
                                            if ( pBrowseId.isNotEmpty() )
                                                "${NavRoutes.playlist.name}/$pBrowseId"
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
                if (isCurrentListEmpty) {
                    item(
                        key = "empty_state",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
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

        }
}








