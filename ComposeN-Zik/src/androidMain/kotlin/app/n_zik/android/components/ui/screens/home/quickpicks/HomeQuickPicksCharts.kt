package app.n_zik.android.components.ui.screens.home.quickpicks

import java.util.Locale

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController

import app.n_zik.android.EXPLICIT_PREFIX
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.menu.artist.OnlineArtistItemMenu
import app.n_zik.android.components.menu.playlist.OnlinePlaylistItemMenu
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.core.database.BookmarkStateManager
import app.n_zik.android.core.database.LikeStateManager
import app.n_zik.android.core.database.PlaylistStateManager
import app.n_zik.android.enums.Countries
import app.n_zik.android.models.Song
import app.n_zik.android.typography
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.items.ArtistItem
import app.n_zik.android.ui.items.ArtistItemPlaceholder
import app.n_zik.android.ui.items.PlaylistItem
import app.n_zik.android.ui.items.SongItemPlaceholder
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.bold
import app.n_zik.android.utils.center
import app.n_zik.android.utils.color
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.semiBold
import app.n_zik.innertube.Innertube

@Composable
private fun RankedChartRow(index: Int, rankWidth: Dp, contentWidth: Dp, content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp),
    ) {
        BasicText(
            text = "${index + 1}",
            style = typography().l.bold.center.color(colorPalette().text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(rankWidth),
        )
        Box(Modifier.width(contentWidth)) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun ChartsSection(
    showCharts: Boolean,
    chartsPageInit: Innertube.ChartsPage?,
    selectedCountryCode: Countries,
    onCountryChange: (Countries) -> Unit,
    navController: NavController,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    endPaddingValues: PaddingValues,
    playlistThumbnailSizePx: Int,
    playlistThumbnailSizeDp: Dp,
    songThumbnailSizePx: Int,
    songThumbnailSizeDp: Dp,
    disableScrollingText: Boolean,
    parentalControlEnabled: Boolean,
    displayedSectionTitles: MutableSet<String>,
    itemInHorizontalGridWidth: Dp,
    forcePlaceholder: Boolean = false,
) {
    val binder = LocalPlayerServiceBinder.current
    val rankDigits = maxOf(chartsPageInit?.songs?.size ?: 0, chartsPageInit?.artists?.size ?: 0, 9)
        .toString().length
    val textMeasurer = rememberTextMeasurer()
    val rankStyle = typography().l.bold
    val rankWidth = with(LocalDensity.current) {
        ('0'..'9').maxOf { digit ->
            textMeasurer.measure(digit.toString().repeat(rankDigits), style = rankStyle).size.width
        }.toDp()
    }
    val menuState = LocalMenuState.current
    if (showCharts) {
        if (forcePlaceholder || chartsPageInit != null) {
            val hasPlaylists = chartsPageInit?.playlists?.isNotEmpty() == true
            val hasSongs = chartsPageInit?.songs?.isNotEmpty() == true
            val hasArtists = chartsPageInit?.artists?.isNotEmpty() == true

            if (forcePlaceholder || hasPlaylists || hasSongs || hasArtists) {
                displayedSectionTitles.add("Charts")
                Column {
                    HomeSectionTitle(
                        title = stringResource(R.string.recommendations_charts_country_format, stringResource(R.string.recommendations_charts_title), selectedCountryCode.displayLabel()),
                        onClick = {
                            menuState.display {
                                ListMenu.Menu(title = stringResource(R.string.recommendations_charts_title)) {
                                    Countries.entries.forEach { country ->
                                        ListMenu.Entry(
                                            icon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .iconBadgeBackground(
                                                            color = colorPalette().accent.copy(alpha = 0.1f),
                                                            shape = uiRoundnessShape()
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.arrow_right),
                                                        tint = colorPalette().accent,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            },
                                            text = country.displayLabel(),
                                            onClick = {
                                                onCountryChange(country)
                                                menuState.hide()
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        verticalPadding = 16.dp,
                    )

                    chartsPageInit?.playlists.orEmpty().let { playlists ->
                        if (forcePlaceholder || playlists.isNotEmpty()) {
                            val chartPlaylistKeys = remember(playlists) { playlists.map { it.key } }
                            val chartPlaylistBookmarkStatesMap by remember(chartPlaylistKeys) {
                                BookmarkStateManager.getPlaylistBookmarkStates(chartPlaylistKeys)
                            }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                            if (forcePlaceholder) {
                                ShelfCardPlaceholders(playlists, YtmShelfPlaceholderKind.Playlist, playlistThumbnailSizeDp, endPaddingValues)
                            } else LazyRow(contentPadding = endPaddingValues) {
                                items(
                                    items = playlists.distinctBy { it.key },
                                    key = Innertube.PlaylistItem::key,
                                    contentType = { "playlist" }
                                ) { playlist ->
                                    PlaylistItem(
                                        playlist = playlist,
                                        thumbnailSizePx = playlistThumbnailSizePx,
                                        thumbnailSizeDp = playlistThumbnailSizeDp,
                                        alternative = true,
                                        showSongsCount = false,
                                        isBookmarked = chartPlaylistBookmarkStatesMap[playlist.key],
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).combinedClickable(
                                                onClick = { onPlaylistClick(playlist.key) },
                                                onLongClick = { menuState.display { OnlinePlaylistItemMenu(navController = navController, playlist = playlist).MenuComponent() } }
                                            ).animateItem(),
                                        disableScrollingText = disableScrollingText
                                    )
                                }
                            }
                        }
                    } // end playlists let

                chartsPageInit?.songs.orEmpty().let { songs ->
                    if (forcePlaceholder || songs.isNotEmpty()) {
                        val chartSongIds = remember(songs) { songs.mapNotNull { it.key } }
                        val chartLikeStatesMap by remember(chartSongIds) {
                            LikeStateManager.getLikeStates(chartSongIds)
                        }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)
                        val chartPlaylistStatesMap by remember(chartSongIds) {
                            PlaylistStateManager.getPlaylistStates(chartSongIds)
                        }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                        BasicText(
                            text = stringResource(R.string.home_chart_top_songs_title),
                            style = typography().l.semiBold,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(top = 16.dp, bottom = 8.dp)
                        )

                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(3),
                            modifier = Modifier
                                .height(Dimensions.itemsVerticalPadding * 3 * 9)
                                .fillMaxWidth(),
                            flingBehavior = ScrollableDefaults.flingBehavior(),
                        ) {
                            val rankedSongs = songs.filter { !parentalControlEnabled || !it.asSong.title.startsWith(EXPLICIT_PREFIX) }
                                .distinctBy { it.key }
                            if (forcePlaceholder) items(rankedSongs.size.takeIf { it > 0 } ?: 9) { index ->
                                RankedChartRow(index, rankWidth, itemInHorizontalGridWidth) {
                                    val song = rankedSongs.getOrNull(index)?.asSong
                                    SongItemPlaceholder(title = song?.title, artist = song?.artistsText)
                                }
                            } else itemsIndexed(
                                items = rankedSongs,
                                key = { _, song -> song.key },
                                contentType = { _, _ -> "song" }
                            ) { index, song ->
                                RankedChartRow(index, rankWidth, itemInHorizontalGridWidth) {
                                    SongItem(
                                        song = song.asSong ?: Song.makePlaceholder(""),
                                        isLiked = chartLikeStatesMap[song.key],
                                        inPlaylist = chartPlaylistStatesMap[song.key],
                                        navController = navController,
                                        onClick = {
                                            val mediaItem = song.asMediaItem
                                            binder?.stopRadio()
                                            binder?.player?.forcePlay(mediaItem)
                                            binder?.player?.addMediaItems(songs.map { it.asMediaItem })
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                chartsPageInit?.artists.orEmpty().let { artists ->
                    if (forcePlaceholder || artists.isNotEmpty()) {
                        val chartArtistKeys = remember(artists) { artists.map { it.key } }
                        val chartArtistBookmarkStatesMap by remember(chartArtistKeys) {
                            BookmarkStateManager.getArtistBookmarkStates(chartArtistKeys)
                        }.collectAsStateWithLifecycle(emptyMap(), context = NzikDispatchers.DATA)

                        BasicText(
                            text = stringResource(R.string.home_chart_top_artists_title),
                            style = typography().l.semiBold,
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp)
                        )

                        LazyHorizontalGrid(
                            rows = GridCells.Fixed(3),
                            modifier = Modifier
                                .height(Dimensions.itemsVerticalPadding * 3 * 9)
                                .fillMaxWidth(),
                            flingBehavior = ScrollableDefaults.flingBehavior(),
                        ) {
                            val rankedArtists = artists.distinctBy { it.key }
                            if (forcePlaceholder) items(rankedArtists.size.takeIf { it > 0 } ?: 9) { index ->
                                RankedChartRow(index, rankWidth, 200.dp) {
                                    val artist = rankedArtists.getOrNull(index)
                                    ArtistItemPlaceholder(
                                        songThumbnailSizeDp,
                                        title = artist?.title,
                                        subscribers = artist?.subscribersCountText,
                                        showSubscribers = artist == null || artist.subscribersCountText != null,
                                    )
                                }
                            } else itemsIndexed(
                                items = rankedArtists,
                                key = { _, artist -> artist.key },
                                contentType = { _, _ -> "artist" }
                            ) { index, artist ->
                                RankedChartRow(index, rankWidth, 200.dp) {
                                    ArtistItem(
                                        artist = artist,
                                        thumbnailSizePx = songThumbnailSizePx,
                                        thumbnailSizeDp = songThumbnailSizeDp,
                                        alternative = false,
                                        bookmarkState = chartArtistBookmarkStatesMap[artist.key],
                                        observeBookmarkState = false,
                                        modifier = Modifier
                                            .clip(uiRoundnessShape()).combinedClickable(
                                                onClick = { onArtistClick(artist.key) },
                                                onLongClick = {
                                                    menuState.display { OnlineArtistItemMenu(navController = navController, artist = artist).MenuComponent() }
                                                }
                                            ).animateItem(),
                                        disableScrollingText = disableScrollingText
                                    )
                                }
                            }
                        }
                    }
                }
                } // end Column
            }
        }
    }
}

@Composable
private fun Countries.displayLabel(): String {
    if (this == Countries.ZZ) return stringResource(R.string.recommendations_charts_global)
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    return Locale.Builder().setRegion(name).build().getDisplayCountry(locale).ifBlank { countryName }
}
